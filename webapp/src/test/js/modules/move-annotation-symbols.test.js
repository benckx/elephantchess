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

const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const test = require('node:test');
const vm = require('vm');

const MODULE_PATH = path.resolve(
    __dirname,
    '../../../main/resources/public/js/modules/move-annotation-symbols.js'
);

function loadMoveAnnotationSymbols() {
    const sandbox = {
        MAX_ABS_CP: 7_706
    };
    sandbox.globalThis = sandbox;

    const context = vm.createContext(sandbox);
    const source = fs.readFileSync(MODULE_PATH, 'utf8');
    const footer = '\n;globalThis.calculateAnnotationDetails = calculateAnnotationDetails;';
    vm.runInContext(source + footer, context, {filename: 'move-annotation-symbols.js'});
    return context;
}

test('calculateAnnotationDetails requires matching depths of at least 18', () => {
    const {calculateAnnotationDetails} = loadMoveAnnotationSymbols();

    assert.equal(
        calculateAnnotationDetails(
            {cp: 360, mate: null, depth: 20, isCheckmate: false},
            {cp: 0, mate: null, depth: 18, isCheckmate: false}
        ),
        null
    );
    assert.equal(
        calculateAnnotationDetails(
            {cp: 360, mate: null, depth: 17, isCheckmate: false},
            {cp: 0, mate: null, depth: 17, isCheckmate: false}
        ),
        null
    );
});

test('calculateAnnotationDetails requires heuristic scores on both sides', () => {
    const {calculateAnnotationDetails} = loadMoveAnnotationSymbols();

    assert.equal(
        calculateAnnotationDetails(
            {cp: null, mate: null, depth: 20, isCheckmate: false},
            {cp: 0, mate: null, depth: 20, isCheckmate: false}
        ),
        null
    );
    const annotationDetails = calculateAnnotationDetails(
        {cp: 360, mate: null, depth: 20, isCheckmate: false},
        {cp: 0, mate: null, depth: 20, isCheckmate: false}
    );

    assert.deepEqual(
        JSON.parse(JSON.stringify(annotationDetails)),
        {
            symbol: 'BRILLIANT',
            engineCp: 360,
            actualMoveCp: 0,
            delta: 360
        }
    );
});
