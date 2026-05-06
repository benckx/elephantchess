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

const GAME_API = '/api/game';

class GameClient {

    /**
     * @type {string}
     */
    #gameId;

    constructor(gameId) {
        this.#gameId = gameId;
    }

    getData(cb) {
        getAndHandle(`${GAME_API}/data?gameId=${this.#gameId}`, (json) => {
            cb(new GameDto(userIdOrNull(), json));
        });
    }

    /**
     * @param cb {function(HalfMove[])}
     */
    getMovesHistory(cb) {
        /**
         * @param movesAsUci {string[]}
         * @return {HalfMove[]}
         */
        function parseUci(movesAsUci) {
            // embedded in a function, otherwise IntelliJ shows a weird warning
            return movesAsUci.map(uci => HalfMove.parseUci(uci));
        }

        const url = `${GAME_API}/moves-history?gameId=${this.#gameId}`;
        getAndHandle(url, (json) => cb(parseUci(json.moves)))
    }

    /**
     *
     * @param cb {function(ChatMessageDto[])}
     */
    getChatHistory(cb) {
        const url = `${GAME_API}/chat-history?gameId=${this.#gameId}`;
        getAndHandle(url, (json) => {
            const messages = [];
            for (let i = 0; i < json.messages.length; i++) {
                messages.push(new ChatMessageDto(json.messages[i]));
            }
            cb(messages);
        });
    }

    /**
     *
     * @param source {string|null}
     * @param sourceId {string|null}
     * @param cb {function(string, number)}
     */
    postJoin(source, sourceId, cb) {
        const url = `${GAME_API}/join`;
        const body = {
            'gameId': this.#gameId,
            'source': source,
            'sourceId': sourceId
        };
        postAndHandle(url, body, (json) => {
            // noinspection JSUnresolvedReference
            const color = json.inviteeColor;
            const rating = Number(json.inviteeRating);
            cb(color, rating);
        });
    }

    postCancel(cb) {
        let url = GAME_API + '/cancel';
        let body = {'gameId': this.#gameId};
        postAndHandle(url, body, () => {
            cb();
        });
    }

    /**
     * @param cb {function(RatingUpdateDto|null)}
     */
    postResign(cb) {
        let url = GAME_API + '/resign';
        let body = {'gameId': this.#gameId};
        postAndHandle(url, body, (json) => {
            let ratingUpdate = null;
            if (json.ratingUpdate != null) {
                ratingUpdate = new RatingUpdateDto(json.ratingUpdate);
            }
            cb(ratingUpdate)
        });
    }

    postProposeDraw(cb) {
        const url = `${GAME_API}/propose-draw`;
        const body = {'gameId': this.#gameId};
        postAndHandle(url, body, () => cb());
    }

    postRespondToDraw(accept, cb) {
        const url = `${GAME_API}/respond-to-draw`;
        const body = {'gameId': this.#gameId, 'accept': accept};
        postAndHandle(url, body, () => cb());
    }

    /**
     * @param move {HalfMove}
     * @param cb {function(PlayMoveResponse)}
     */
    postMove(move, cb) {
        const url = `${GAME_API}/play-move`;
        const body = {'gameId': this.#gameId, 'move': move.toUci()};
        postAndHandle(url, body, (json) => cb(new PlayMoveResponse(json)));
    }

}
