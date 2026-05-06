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

const API_BOT_GAME = "/api/botgame";

class PlayerVsBotClient {

    #gameId;

    constructor(gameId) {
        this.#gameId = gameId;
    }

    /**
     * @param callback {function(BotGameDto)}
     */
    fetchGameData(callback) {
        getAndHandle(`${API_BOT_GAME}/data?gameId=${this.#gameId}`, json => {
            callback(new BotGameDto(json));
        });
    }

    /**
     * @param callback {function(HalfMove[])}
     */
    fetchMovesHistory(callback) {
        getAndHandle(`${API_BOT_GAME}/moves-history?gameId=${this.#gameId}`, json => {
            // noinspection JSCheckFunctionSignatures
            callback(json.moves.map(HalfMove.parseUci));
        });
    }

    /**
     * @param move {HalfMove}
     * @param callback {function(PlayMoveResponseDto)}
     */
    playMove(move, callback) {
        let body = {
            gameId: this.#gameId,
            move: move.toUci()
        }
        postAndHandle(`${API_BOT_GAME}/play-move`, body, json => {
            callback(new PlayMoveResponseDto(json));
        });
    }

    /**
     * @param callback {function()}
     */
    resign(callback) {
        let body = {gameId: this.#gameId}
        postAndHandle(`${API_BOT_GAME}/resign`, body, () => {
            callback();
        });
    }

    /**
     * @param callback {function()}
     */
    cancel(callback) {
        let body = {gameId: this.#gameId}
        postAndHandle(`${API_BOT_GAME}/cancel`, body, () => {
            callback();
        });
    }

}
