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

const {loadParsers, toUciList} = require('./harness');

const ctx = loadParsers();

test('IccsParser reports its name', () => {
    assert.equal(new ctx.IccsParser('').name, 'ICCS');
});

test('IccsParser strips dashes and lower-cases coordinates', () => {
    const moves = new ctx.IccsParser('1. B2-E2 B9-C7 2. B0-C2 A9-B9').parse();
    assert.deepEqual(toUciList(moves), ['b2e2', 'b9c7', 'b0c2', 'a9b9']);
});

test('IccsParser skips metadata and result lines', () => {
    const input = [
        '[Event "Test"]',
        '[Result "1-0"]',
        '',
        '1. H2-G4 H9-G7',
        '1-0'
    ].join('\n');
    const moves = new ctx.IccsParser(input).parse();
    assert.deepEqual(toUciList(moves), ['h2g4', 'h9g7']);
});

test('IccsParser accepts already lower-case coordinates without dashes', () => {
    const moves = new ctx.IccsParser('b2e2 b9c7').parse();
    assert.deepEqual(toUciList(moves), ['b2e2', 'b9c7']);
});
