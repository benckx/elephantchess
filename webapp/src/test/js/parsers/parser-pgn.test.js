/*
 * Copyright (C) 2023-2026 Encelade SRL
 * Copyright (C) 2023-2026 elephantchess.io
 * Copyright (C) 2023-2026 Benoît Vleminckx (benckx)
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

test('PGN-0 resolves ambiguous cannon move Ce2 (both cannons can reach e2)', () => {
    // From the start position both red cannons (b2 and h2) can reach e2 along rank 2,
    // so "Ce2" is ambiguous.  Before the fix the parser threw on ambiguous moves.
    // A subsequent unambiguous move (2. Ch5 or 2. Cb5) prunes the wrong branch.

    // Branch resolved by 2. Ch5: h-cannon was still at h2 → b-cannon moved to e2.
    const movesB = new ctx.PgnParser('1. Ce2 Nc7 2. Ch5', true).parse();
    assert.equal(movesB.length, 3);
    assert.deepEqual(toUciList(movesB), ['b2e2', 'b9c7', 'h2h5']);

    // Branch resolved by 2. Cb5: b-cannon was still at b2 → h-cannon moved to e2.
    const movesH = new ctx.PgnParser('1. Ce2 Nc7 2. Cb5', true).parse();
    assert.equal(movesH.length, 3);
    assert.deepEqual(toUciList(movesH), ['h2e2', 'b9c7', 'b2b5']);
});

test('PGN-1 resolves the full 38-move game with multiple ambiguous moves', () => {
    // This game contains several ambiguous moves (e.g. "5. Cbc3 Cb6").
    // Before the fix the parser threw; it must now parse all 75 half-moves cleanly.
    //
    // The expected FEN is the position BEFORE the final mating move (38. Rbxc10+),
    // i.e. after 74 half-moves, with red to play ('w').  Move counters are ignored.
    const pgn =
        '1. Che3 Nhg8 2. Nhg3 Ch2 3. Rih1 Chf2 4. Ra2 Cfb2 5. Cbc3 Cb6 ' +
        '6. Rh6 Cbg6 7. Pg5 Cb6 8. Pxg6 Afe9 9. Pxg7 Pe6 10. Rab2 Cbc6 ' +
        '11. Rhxe6 Ngi9 12. Rb3 Bca8 13. Pc5 Rih10 14. Rb7 Nbd9 15. Pxc6 Nih7 ' +
        '16. Reh6 Rhi10 17. Cxc7 Nhi9 18. Cea3 Nde7 19. Pcd6 Ra9 20. Cxa7 Nef5 ' +
        '21. Ngxf5 Nih7 22. Cxa9 Bgi8 23. Rxh7 Rif10 24. Ca10+ Bac10 25. Rh5 Rfg10 ' +
        '26. Cce7+ Aef10 27. Ce6 Pi6 28. Nfe7+ Afe9 29. Nec8+ Aef10 30. Ca8 Rg9 ' +
        '31. Pg8 Rg10 32. Nbc3 Pi5 33. Pxi5 Rxg8 34. Caxg8 Bca8 35. Cg10+ Bixg10 ' +
        '36. Nce7+ Ade9 37. Rb10+ Bac10 38. Rbxc10+';

    const moves = new ctx.PgnParser(pgn, false).parse();
    assert.equal(moves.length, 75);

    // Replay first 74 moves onto a fresh board.
    const board = new ctx.XiangqiBoard();
    board.loadFen(String(ctx.DEFAULT_START_FEN));
    for (let i = 0; i < moves.length - 1; i++) {
        board.registerMove(moves[i].move);
    }

    // Compare position + side-to-move only (ignore half-move clock and full-move number).
    const fenPosAndColor = board.outputFen().split(' ').slice(0, 2).join(' ');
    assert.equal(fenPosAndColor, '1Rb1kab2/4a4/9/4N4/3PC4/7RP/P3P4/2N6/9/2BAKAB2 w');
});
