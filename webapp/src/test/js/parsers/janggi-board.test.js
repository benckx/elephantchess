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

function fenFromPieces(pieces, colorToPlay = 'w') {
    const board = Array.from({length: 10}, () => Array(9).fill(null));
    Object.entries(pieces).forEach(([uci, piece]) => {
        const position = ctx.Position.parseUci(uci);
        board[position.y][position.x] = piece;
    });

    const rows = [];
    for (let y = 9; y >= 0; y--) {
        let row = '';
        let emptyCount = 0;
        for (let x = 0; x < 9; x++) {
            const piece = board[y][x];
            if (piece == null) {
                emptyCount++;
            } else {
                if (emptyCount > 0) {
                    row += String(emptyCount);
                    emptyCount = 0;
                }
                row += piece;
            }
        }
        if (emptyCount > 0) {
            row += String(emptyCount);
        }
        rows.push(row);
    }

    return `${rows.join('/')} ${colorToPlay} - - 0 1`;
}

function targetsFrom(board, fromUci) {
    const result = [];
    board
        .listAllMovesFrom(ctx.Position.parseUci(fromUci))
        .forEach(move => result.push(String(move.to.toUci())));
    return result.sort();
}

test('Janggi general uses palace diagonals and advisors can move orthogonally in palace', () => {
    const board = new ctx.JanggiBoard();
    board.loadFen(ctx.JANGGI_START_FEN);

    assert.deepEqual(targetsFrom(board, 'e1'), ['d1', 'd2', 'e0', 'e2', 'f1', 'f2']);
    assert.deepEqual(targetsFrom(board, 'd0'), ['d1', 'e0']);
});

test('Janggi elephant moves one orthogonal then two diagonals and ignores river', () => {
    const board = new ctx.JanggiBoard();
    board.loadFen(fenFromPieces({
        e4: 'B',
    }));

    assert.deepEqual(targetsFrom(board, 'e4'), ['b2', 'b6', 'c1', 'c7', 'g1', 'g7', 'h2', 'h6']);
});

test('Janggi elephant is blocked by occupied intermediate squares', () => {
    const board = new ctx.JanggiBoard();
    board.loadFen(fenFromPieces({
        e4: 'B',
        e5: 'P',
    }));

    assert.deepEqual(targetsFrom(board, 'e4'), ['b2', 'b6', 'c1', 'g1', 'h2', 'h6']);
});

test('Janggi chariot and cannon can move on palace diagonals', () => {
    const chariotBoard = new ctx.JanggiBoard();
    chariotBoard.loadFen(fenFromPieces({
        d0: 'R',
        c0: 'P',
        d1: 'P',
        e0: 'P',
        f2: 'p',
    }));
    assert.deepEqual(targetsFrom(chariotBoard, 'd0'), ['e1', 'f2']);

    const cannonBoard = new ctx.JanggiBoard();
    cannonBoard.loadFen(fenFromPieces({
        d0: 'C',
        e1: 'P',
        f2: 'p',
    }));
    assert.deepEqual(targetsFrom(cannonBoard, 'd0'), ['f2']);
});

test('Janggi cannon screen cannot be cannon and cannon cannot capture cannon', () => {
    const screenIsCannon = new ctx.JanggiBoard();
    screenIsCannon.loadFen(fenFromPieces({
        d0: 'C',
        e1: 'C',
        f2: 'p',
    }));
    assert.deepEqual(targetsFrom(screenIsCannon, 'd0'), []);

    const targetIsCannon = new ctx.JanggiBoard();
    targetIsCannon.loadFen(fenFromPieces({
        d0: 'C',
        e1: 'P',
        f2: 'c',
    }));
    assert.deepEqual(targetsFrom(targetIsCannon, 'd0'), []);
});

test('Janggi soldier moves forward or sideways without river, and diagonally forward in enemy palace', () => {
    const board = new ctx.JanggiBoard();
    board.loadFen(fenFromPieces({
        e3: 'P',
        d7: 'P',
    }));

    assert.deepEqual(targetsFrom(board, 'e3'), ['d3', 'e4', 'f3']);
    assert.deepEqual(targetsFrom(board, 'd7'), ['c7', 'd8', 'e7', 'e8']);
});
