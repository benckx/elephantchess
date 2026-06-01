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

const test = require('node:test');
const assert = require('node:assert/strict');

const {loadParsers, readFixture, toUciList} = require('./harness');

const ctx = loadParsers();

/**
 * Split a PGN file that may contain several games (one [Event ...] block each)
 * into individual game strings.
 *
 * @param pgn {string}
 * @return {string[]}
 */
function splitGames(pgn) {
    return pgn
        .split(/(?=\[Event )/)
        .map(game => game.trim())
        .filter(game => game.length > 0);
}

test('PgnParser reports zero / one based names', () => {
    assert.equal(new ctx.PgnParser('', true).name, 'PGN-0');
    assert.equal(new ctx.PgnParser('', false).name, 'PGN-1');
});

test('PGN-0 parses the issue_545 single-game fixture', () => {
    const moves = new ctx.PgnParser(readFixture('pgn0/issue_545.pgn'), true).parse();
    assert.equal(moves.length, 112);
    assert.deepEqual(toUciList(moves).slice(0, 4), ['b2e2', 'b9c7', 'b0c2', 'a9b9']);
});

test('PGN-0 attaches comments to the move they follow', () => {
    const moves = new ctx.PgnParser(readFixture('pgn0/issue_545.pgn'), true).parse();
    // "1. Cbe2 Hc7 {-0.27/28 29}" -> the comment belongs to the second half move
    assert.equal(moves[0].annotation, null);
    assert.equal(moves[1].annotation, '-0.27/28 29');
});

test('PGN-0 parses the Cyclone vs ElephantEye fixture', () => {
    const fixture = 'pgn0/Cyclone_v6.2_[develop_091218]_(UCI2WB)-ElephantEye_3.1_(UCCI2WB)_13.pgn';
    const moves = new ctx.PgnParser(readFixture(fixture), true).parse();
    assert.equal(moves.length, 131);
    assert.deepEqual(toUciList(moves).slice(0, 2), ['b2e2', 'h9g7']);
});

test('PGN-0 strips trailing check (+) and mate (#) indicators', () => {
    // The +/# indicators must be stripped before the move is resolved.
    assert.deepEqual(toUciList(new ctx.PgnParser('1. Cbe2+ Hg7', true).parse()), ['b2e2', 'h9g7']);
    assert.deepEqual(toUciList(new ctx.PgnParser('1. Cbe2# Hg7', true).parse()), ['b2e2', 'h9g7']);
});

test('PGN-1 parses the first davide2022 game (1-based ranks)', () => {
    const games = splitGames(readFixture('pgn1/davide2022.pgn'));
    const moves = new ctx.PgnParser(games[0], false).parse();
    assert.equal(moves.length, 80);
    assert.deepEqual(toUciList(moves).slice(0, 4), ['b2e2', 'b9c7', 'c3c4', 'a9b9']);
});

test('PGN-1 resolves every game in the davide2022 collection', () => {
    const games = splitGames(readFixture('pgn1/davide2022.pgn'));
    assert.equal(games.length, 26);
    games.forEach((game, index) => {
        const moves = new ctx.PgnParser(game, false).parse();
        assert.ok(moves.length > 0, `game ${index} should yield moves`);
    });
});
