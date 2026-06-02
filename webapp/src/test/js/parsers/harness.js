/*
 * Copyright (C) 2026  Encelade SRL
 * Copyright (C) 2026  elephantchess.io
 * Copyright (C) 2026  Benoît Vleminckx (benckx)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

'use strict';

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const MODULES_DIR = path.resolve(
    __dirname,
    '../../../main/resources/public/js/modules'
);

const FIXTURES_DIR = path.resolve(__dirname, 'fixtures');

/*
 * The production parsers are plain browser scripts that declare classes and
 * functions as globals (no module system). They are loaded in the browser as a
 * sequence of <script> tags. To exercise them in Node we evaluate the same
 * files, in the same order, inside a single shared VM context.
 */
const SCRIPT_LOAD_ORDER = [
    'enums.js',
    'utils.js',
    'xiangqi.js',
    'parser-common.js',
    'parser-pgn.js',
    'parser-uci.js',
    'parser-iccs.js',
    'parser-wxf.js'
];

/**
 * Build a fresh sandbox with the production parser code loaded.
 *
 * The parsers are verbose: they log progress with console.log / console.warn.
 * We silence those so the test output stays readable, while keeping the real
 * console available through `realConsole` for debugging if needed.
 *
 * @return {object} the VM context exposing every global declared by the scripts
 */
function loadParsers() {
    const realConsole = console;
    const silentConsole = {
        log: () => {
        },
        info: () => {
        },
        warn: () => {
        },
        error: () => {
        },
        debug: () => {
        }
    };

    const sandbox = {
        console: silentConsole,
        realConsole: realConsole,
        // utils.js references window / document inside functions that the
        // parsers never call; stub them so accidental access fails loudly.
        window: {location: {search: ''}},
        document: {},
        URLSearchParams: URLSearchParams,
        setTimeout: setTimeout,
        clearTimeout: clearTimeout,
        // utils.js calls dayjs.extend(...) at load time; the parsers never use
        // dayjs, so a no-op stub is enough to evaluate the file.
        dayjs: Object.assign(() => ({}), {extend: () => {
        }}),
        dayjs_plugin_utc: {}
    };
    sandbox.globalThis = sandbox;

    const context = vm.createContext(sandbox);

    /*
     * In the browser every <script> shares one global lexical scope, so
     * `class` / `const` declarations in one file are visible to the next.
     * vm.runInContext, by contrast, gives each evaluated script its own
     * top-level lexical scope, so cross-file references would break. We
     * therefore concatenate the files into a single script (mirroring the
     * browser's shared scope) and append a footer that re-exports the public
     * symbols onto globalThis so the tests can reach them.
     */
    const exportedSymbols = [
        'ParsingToken',
        'MoveAndAnnotation',
        'SimpleTokenParser',
        'MoveParser',
        'parseToMoves',
        'PgnParser',
        'UciMoveParser',
        'AlgebraicParser',
        'IccsParser',
        'WxfParser',
        'Board',
        'Position',
        'HalfMove',
        'Color',
        'DEFAULT_START_FEN'
    ];

    const combinedCode = SCRIPT_LOAD_ORDER
        .map(fileName => fs.readFileSync(path.join(MODULES_DIR, fileName), 'utf8'))
        .join('\n;\n');

    const footer = '\n;\n' + exportedSymbols
        .map(symbol => `globalThis.${symbol} = (typeof ${symbol} !== 'undefined') ? ${symbol} : undefined;`)
        .join('\n');

    vm.runInContext(combinedCode + footer, context, {filename: 'parsers-combined.js'});

    return context;
}

/**
 * @param relativePath {string} path relative to the fixtures directory
 * @return {string} the file content
 */
function readFixture(relativePath) {
    return fs.readFileSync(path.join(FIXTURES_DIR, relativePath), 'utf8');
}

/**
 * Map a list of {@link MoveAndAnnotation} to their UCI string representation.
 *
 * @param movesAndAnnotations {Array}
 * @return {string[]}
 */
function toUciList(movesAndAnnotations) {
    // Rebuild a host-realm array of host-realm strings. The parser output lives
    // in the VM context, so its arrays/strings have a different prototype and
    // would fail assert.deepStrictEqual's reference-equality checks otherwise.
    const result = [];
    movesAndAnnotations.forEach(moveAndAnnotation => {
        result.push(String(moveAndAnnotation.move.toUci()));
    });
    return result;
}

module.exports = {
    loadParsers,
    readFixture,
    toUciList,
    MODULES_DIR,
    FIXTURES_DIR
};
