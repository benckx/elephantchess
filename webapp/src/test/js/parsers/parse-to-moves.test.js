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

test('parseToMoves auto-detects the UCI format', () => {
    const moves = ctx.parseToMoves(readFixture('uci/example1.uci'));
    assert.equal(moves.length, 82);
    assert.deepEqual(toUciList(moves).slice(0, 3), ['b2e2', 'b9c7', 'b0c2']);
});

test('parseToMoves auto-detects the PGN-0 format', () => {
    const moves = ctx.parseToMoves(readFixture('pgn0/issue_545.pgn'));
    assert.equal(moves.length, 112);
});

test('parseToMoves auto-detects the WXF format (tagged game)', () => {
    const moves = ctx.parseToMoves(readFixture('wxf/game1.wxf'));
    assert.equal(moves.length, 138);
});

test('parseToMoves auto-detects the WXF format (bare move list)', () => {
    const moves = ctx.parseToMoves(readFixture('wxf/no_tag_game1.wxf'));
    assert.equal(moves.length, 94);
});

test('parseToMoves throws an aggregated error for unparseable input', () => {
    assert.throws(() => ctx.parseToMoves('this is not a game'), /.+/);
});
