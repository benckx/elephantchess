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

const GAME_API_7K = '/api/7k';

class GameClient7k {

    #gameId;

    /**
     * @param gameId {string}
     */
    constructor(gameId) {
        this.#gameId = gameId;
    }

    /**
     * @param cb {function(GameDto7k)}
     */
    getData(cb) {
        getAndHandle(`${GAME_API_7K}/fetch-game-data?gameId=${this.#gameId}`, (json) => {
            cb(new GameDto7k(this.#gameId, json));
        });
    }

    /**
     * @param cb {function(Move[])}
     */
    getMoves(cb) {
        getAndHandle(`${GAME_API_7K}/fetch-moves?gameId=${this.#gameId}`, (json) => {
            const parsedMoves = [];
            for (const move of json.moves) {
                parsedMoves.push(Move.parseFromUci(move));
            }
            cb(parsedMoves);
        });
    }

}
