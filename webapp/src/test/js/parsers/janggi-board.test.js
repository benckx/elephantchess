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

const setups = {
    INNER_ELEPHANT: 'rnba1abnr',
    OUTER_ELEPHANT: 'rbna1anbr',
    RIGHT_ELEPHANT: 'rnba1anbr',
    LEFT_ELEPHANT: 'rbna1abnr',
    CENTRAL_CHARIOT: 'bnra1arnb',
};

test('Janggi setups expose the five specified lowercase FEN fragments', () => {
    assert.deepEqual({...ctx.JanggiSetup}, setups);
    assert.ok(Object.isFrozen(ctx.JanggiSetup));
});

test('Janggi default remains inner elephants on both sides', () => {
    const expected = 'rnba1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RNBA1ABNR w - - 0 1';
    assert.equal(ctx.generateJanggiStartFen(), expected);
    assert.equal(ctx.JANGGI_START_FEN, expected);
    assert.equal(ctx.generateJanggiStartFen(ctx.JanggiSetup.OUTER_ELEPHANT),
        ctx.generateJanggiStartFen(ctx.JanggiSetup.OUTER_ELEPHANT, ctx.JanggiSetup.INNER_ELEPHANT));
    assert.equal(ctx.generateJanggiStartFen(undefined, ctx.JanggiSetup.CENTRAL_CHARIOT),
        ctx.generateJanggiStartFen(ctx.JanggiSetup.INNER_ELEPHANT, ctx.JanggiSetup.CENTRAL_CHARIOT));
});

test('Janggi start FEN composes all 25 independent setups with Blue playing first', () => {
    const fens = new Set();
    Object.values(setups).forEach(blue => {
        Object.values(setups).forEach(red => {
            const fen = ctx.generateJanggiStartFen(blue, red);
            assert.equal(fen, `${red}/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/${blue.toUpperCase()} w - - 0 1`);
            const board = new ctx.JanggiBoard();
            board.loadFen(fen);
            assert.equal(board.outputFen().split(' ').slice(0, 2).join(' '), fen.split(' ').slice(0, 2).join(' '));
            assert.equal(board.isInCheck(ctx.Color.WHITE), false);
            assert.equal(board.isInCheck(ctx.Color.BLACK), false);
            fens.add(fen);
        });
    });
    assert.equal(fens.size, 25);
});

test('Janggi start FEN rejects invalid setups on either side', () => {
    [null, '', 'INNER_ELEPHANT', 'invalid', {}, 1].forEach(value => {
        assert.throws(() => ctx.generateJanggiStartFen(value), /Invalid Janggi setup/);
        assert.throws(() => ctx.generateJanggiStartFen(ctx.JanggiSetup.INNER_ELEPHANT, value), /Invalid Janggi setup/);
    });
});

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
