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

const {loadParsers} = require('./harness');

const ctx = loadParsers();

test('board variants inherit from the intended base and preserve their type when copied', () => {
    assert.ok(ctx.XiangqiBoard.prototype instanceof ctx.AbstractBoard);
    assert.ok(ctx.ManchuBoard.prototype instanceof ctx.XiangqiBoard);
    assert.ok(ctx.JanggiBoard.prototype instanceof ctx.AbstractBoard);

    for (const [BoardType, startFen] of [
        [ctx.XiangqiBoard, ctx.DEFAULT_START_FEN],
        [ctx.ManchuBoard, ctx.MANCHU_START_FEN],
        [ctx.JanggiBoard, ctx.DEFAULT_START_FEN]
    ]) {
        const board = new BoardType();
        board.loadFen(startFen);
        const copy = board.copy();
        assert.ok(copy instanceof BoardType);
        assert.equal(copy.outputFen(), board.outputFen());
    }
});

test('Janggi move listing remains empty until its rules are implemented', () => {
    const board = new ctx.JanggiBoard();
    board.loadFen(ctx.DEFAULT_START_FEN);

    assert.equal(Object.hasOwn(ctx.JanggiBoard.prototype, 'listLegalMovesFrom'), false);
    assert.equal(board.listLegalMovesFrom(new ctx.Position(0, 3)).length, 0);
    assert.equal(board.listAllMovesFromAsPositions(new ctx.Position(0, 3)).length, 0);
    assert.equal(board.listAllLegalMoves().length, 0);
});

test('Xiangqi and Manchu still generate legal moves', () => {
    const xiangqi = new ctx.XiangqiBoard();
    xiangqi.loadFen(ctx.DEFAULT_START_FEN);
    assert.ok(xiangqi.listAllLegalMoves().length > 0);

    const manchu = new ctx.ManchuBoard();
    manchu.loadFen(ctx.MANCHU_START_FEN);
    assert.ok(manchu.listLegalMovesFrom(new ctx.Position(0, 0)).length > 0);
});

test('piece-specific move generators return no moves for other piece types', () => {
    const board = new ctx.XiangqiBoard();
    board.loadFen(ctx.DEFAULT_START_FEN);

    assert.equal(board.listMovesForCannon(new ctx.Position(1, 0)).length, 0);
    assert.equal(board.listMovesForChariot(new ctx.Position(1, 2)).length, 0);
    assert.equal(board.listMovesForHorse(new ctx.Position(0, 0)).length, 0);
});
