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

// Number of half moves expected for every WXF fixture in the issue archive.
const TAGGED_GAMES = {
    'wxf/game1.wxf': {count: 138, firstFour: ['h2f2', 'b7e7', 'b0c2', 'b9c7']},
    'wxf/game2.wxf': {count: 101},
    'wxf/game3.wxf': {count: 86},
    'wxf/game4.wxf': {count: 99},
    'wxf/game5.wxf': {count: 122}
};

const NO_TAG_GAMES = {
    'wxf/no_tag_game1.wxf': {count: 94, firstFour: ['h2d2', 'h9g7', 'h0g2', 'i9h9']},
    'wxf/no_tag_game2.wxf': {count: 100},
    'wxf/no_tag_game3.wxf': {count: 99}
};

test('WxfParser reports its name', () => {
    assert.equal(new ctx.WxfParser('').name, 'WXF');
});

for (const [fixture, expected] of Object.entries(TAGGED_GAMES)) {
    test(`WxfParser parses ${fixture} (with FORMAT/GAME tags)`, () => {
        const moves = new ctx.WxfParser(readFixture(fixture)).parse();
        assert.equal(moves.length, expected.count);
        if (expected.firstFour) {
            assert.deepEqual(toUciList(moves).slice(0, 4), expected.firstFour);
        }
    });
}

for (const [fixture, expected] of Object.entries(NO_TAG_GAMES)) {
    test(`WxfParser parses ${fixture} (bare move list)`, () => {
        const moves = new ctx.WxfParser(readFixture(fixture)).parse();
        assert.equal(moves.length, expected.count);
        if (expected.firstFour) {
            assert.deepEqual(toUciList(moves).slice(0, 4), expected.firstFour);
        }
    });
}

test('WxfParser resolves a short cannon / horse opening', () => {
    // C2.5 central cannon, H8+7 horse to the third file, etc.
    const moves = new ctx.WxfParser('1. C2.5 H8+7\n2. H2+3 R9.8').parse();
    assert.equal(moves.length, 4);
    assert.deepEqual(toUciList(moves), ['h2e2', 'h9g7', 'h0g2', 'i9h9']);
});

test('WxfParser accepts sign-prefixed WXF moves', () => {
    const original = readFixture('wxf/no_tag_game1.wxf');
    assert.match(original, /\bR\+=1\b/);

    const signPrefixed = original.replace('R+=1', '+R=1');

    assert.deepEqual(
        toUciList(new ctx.WxfParser(signPrefixed).parse()),
        toUciList(new ctx.WxfParser(original).parse())
    );
});
