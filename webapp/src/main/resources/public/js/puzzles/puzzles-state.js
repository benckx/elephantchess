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

class PuzzleState extends PuzzleDto {

    #board = new Board();
    #preRecordedSolutionMovesIndex = 0;

    /**
     * @type {HalfMove[]}
     */
    #playedMoves = [];

    constructor(json) {
        super(json);
        this.#board.loadFen(this.startFen);
    }

    get moveHistoryIndex() {
        return super.moves.length + this.#playedMoves.length - 1;
    }

    /**
     * @return {HalfMove}
     */
    get currentSolutionMove() {
        return super.preRecordedSolutionMoves[this.#preRecordedSolutionMovesIndex];
    }

    /**
     * @return {HalfMove}
     */
    get nextOpponentMove() {
        return super.preRecordedSolutionMoves[this.#preRecordedSolutionMovesIndex + 1];
    }

    incrementPreRecordedSolution() {
        this.#preRecordedSolutionMovesIndex += 2;
    }

    isOutOfMoves() {
        return this.#playedMoves.length >= super.preRecordedSolutionMoves.length;
    }

    // only exists for debug
    hasNextOpponentMove() {
        return this.#preRecordedSolutionMovesIndex + 1 < super.preRecordedSolutionMoves.length;
    }

    /**
     * @param actualMove {HalfMove}
     * @return {boolean}
     */
    isMoveCorrect(actualMove) {
        return HalfMove.areEquals(actualMove, this.currentSolutionMove);
    }

    /**
     * @param move {HalfMove}
     */
    addPlayedMove(move) {
        this.#board.registerMove(move);
        this.#playedMoves.push(move);
    }

    outputFen() {
        return this.#board.outputFen();
    }

    isCheckmate(color) {
        return this.#board.isCheckmate(color);
    }

    isStalemate(color) {
        return this.#board.isStalemate(color);
    }

    isMated(color) {
        return this.isCheckmate(color) || this.isStalemate(color);
    }

}
