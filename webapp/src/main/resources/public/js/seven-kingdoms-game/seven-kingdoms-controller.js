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

class SevenKingdomsController {

    /**
     * @type {string}
     */
    #gameId;

    /**
     * @type {GameClient7k}
     */
    #client;

    #board = new Board7k();

    /**
     *
     * @param gameId {string}
     * @param initCallCb {function(GameDto7k)}
     * @param fetchMovesCb {function(Move[])}
     */
    constructor(
        gameId,
        initCallCb,
        fetchMovesCb
    ) {
        this.#gameId = gameId;
        this.#client = new GameClient7k(gameId);
        this.#client.getData(gameDto => {
            initCallCb(gameDto);
            this.#client.getMoves(moves => {
                fetchMovesCb(moves);
                this.#board.registerMoves(moves);
            });
        });
    }

    /**
     * @param i {number}
     * @returns {Board7k}
     */
    getBoardForMoveAt(i) {
        const moves = this
            .#board
            .historicalMoves
            .slice(0, i + 1)
            .map(historicalMove => historicalMove.move)

        const board = new Board7k();
        board.registerMoves(moves);
        return board;
    }

    /**
     * @returns {number}
     */
    getCurrentIndex() {
        return this.#board.currentIndex;
    }

}
