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

const USER_PROFILE_GAMES_LIMIT = 3;

class UserProfileGamesSection {

    #username;
    #noGamesMessage = document.getElementById('no-games-message');

    /**
     * @param username {string}
     */
    constructor(username) {
        this.#username = username;
        this.#fetchGames();
    }

    #fetchGames() {
        const params = new Map();
        params.set('username', this.#username);
        params.set('limit', USER_PROFILE_GAMES_LIMIT.toString());
        params.set('distinctByUsers', 'false');
        const url = '/api/game-data/list-latest-pvp-games-by-user' + paramMapToQueryString(params);

        const thumbDivs = getElementsByClassNameArray('pvp-game-thumb');
        const thumbs = thumbDivs.map((thumbDiv, i) => {
            const boardId = `last-pvp-game-board-${i}`;
            const boardElement = document.getElementById(boardId);
            if (!boardElement) {
                console.error(`Board element not found: ${boardId}`);
                return null;
            }
            const options = {elementId: boardId, showCoordinates: false, mini: true};
            return new GameThumb(thumbDiv, createWebappBoardGui(options));
        }).filter(t => t !== null);

        const handler = new ResponseHandlerWithError(
            (json) => {
                const entries = json.entries.map(e => new GameMetadataDto(e));
                if (entries.length === 0) {
                    this.#noGamesMessage.style.display = 'block';
                }
                entries.slice(0, thumbs.length).forEach((entry, i) => {
                    thumbs[i].render(entry, 'browse_pvp', this.#username);
                });
            },
            (responseText) => {
                console.error('Failed to load games: ' + responseText);
            }
        );

        getAndHandleWith(url, handler);
    }

}
