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

test('UciMoveParser reports its name', () => {
    assert.equal(new ctx.UciMoveParser('').name, 'UCI');
});

test('UciMoveParser parses a single line of moves', () => {
    const moves = new ctx.UciMoveParser('b2e2 b9c7 b0c2').parse();
    assert.deepEqual(toUciList(moves), ['b2e2', 'b9c7', 'b0c2']);
});

test('UciMoveParser strips move numbers and spans multiple lines', () => {
    const moves = new ctx.UciMoveParser('1. b2e2 b9c7\n2. b0c2 a9b9').parse();
    assert.deepEqual(toUciList(moves), ['b2e2', 'b9c7', 'b0c2', 'a9b9']);
});

test('UciMoveParser parses the example1 fixture', () => {
    const moves = new ctx.UciMoveParser(readFixture('uci/example1.uci')).parse();
    // 41 numbered full moves, the last one being a single half move pair
    assert.equal(moves.length, 82);
    assert.deepEqual(toUciList(moves).slice(0, 5), ['b2e2', 'b9c7', 'b0c2', 'a9b9', 'a0b0']);
    assert.equal(toUciList(moves).at(-1), 'f8f6');
});

test('UciMoveParser leaves annotations null', () => {
    const moves = new ctx.UciMoveParser('b2e2 b9c7').parse();
    assert.equal(moves[0].annotation, null);
    assert.equal(moves[1].annotation, null);
});

test('AlgebraicParser reports its name', () => {
    assert.equal(new ctx.AlgebraicParser('').name, 'Algebraic');
});

test('AlgebraicParser decrements 1-based ranks to 0-based UCI', () => {
    // b3e3 -> b2e2, b10c8 -> b9c7, b1c3 -> b0c2
    const moves = new ctx.AlgebraicParser('1. b3e3 b10c8 2. b1c3').parse();
    assert.deepEqual(toUciList(moves), ['b2e2', 'b9c7', 'b0c2']);
});
