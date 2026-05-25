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

/**
 * Loads the latest PvP games of a given user and renders them into the
 * pre-rendered {@code .pvp-game-thumb} divs of the user profile page.
 *
 * Uses the {@code /api/game-data/list-latest-pvp-games-by-user} endpoint.
 */
class UserProfileGames {

    #username;
    #section = document.getElementById('latest-games-section');

    /**
     * @type {HTMLDivElement[]}
     */
    #thumbDivs = getElementsByClassNameArray('pvp-game-thumb');

    /**
     * @type {GameThumb[]}
     */
    #thumbs = [];

    /**
     * @param username {string}
     */
    constructor(username) {
        this.#username = username;

        this.#thumbs = this.#thumbDivs.map((div, i) => {
            const boardId = `last-pvp-game-board-${i}`;
            const boardGui = createWebappBoardGui({
                elementId: boardId,
                showCoordinates: false,
                mini: true,
            });
            return new GameThumb(div, boardGui);
        });

        for (let i = 2; i < this.#thumbDivs.length; i++) {
            this.#thumbDivs[i].classList.add('only-desktop-flex');
        }

        this.#fetchGames();
    }

    #fetchGames() {
        const limit = this.#thumbs.length;
        const url = `/api/game-data/list-latest-pvp-games-by-user`
            + `?limit=${limit}`
            + `&username=${encodeURIComponent(this.#username)}`;

        getAndHandle(url, (json) => {
            const entries = (json.entries || []).map((entry) => new GameMetadataDto(entry));
            this.#renderEntries(entries);
        });
    }

    /**
     * @param entries {GameMetadataDto[]}
     */
    #renderEntries(entries) {
        if (entries.length === 0) {
            // No games to show: keep the whole "Latest Games" section hidden.
            return;
        }

        if (this.#section != null) {
            this.#section.style.display = 'block';
        }

        for (let i = 0; i < this.#thumbs.length; i++) {
            if (i < entries.length) {
                this.#thumbs[i].render(entries[i], 'user_profile');
            } else {
                // Hide unused pre-rendered thumbs when fewer games than slots
                this.#thumbDivs[i].style.display = 'none';
            }
        }
    }
}
