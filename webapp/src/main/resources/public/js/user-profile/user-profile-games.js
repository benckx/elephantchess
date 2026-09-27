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
 * Loads the latest games of a given type ('pvp' or 'pvb') for a user and
 * renders them into the pre-rendered {@code .{type}-game-thumb} divs of the
 * user profile page.
 *
 * Uses the {@code /api/game-data/list-latest-{type}-games-by-user} endpoint.
 */
class UserProfileGamesSection {

    #username;
    #gameType;
    #subsection;

    /**
     * @type {HTMLDivElement[]}
     */
    #thumbDivs;

    /**
     * @type {GameThumb[]}
     */
    #thumbs = [];

    /**
     * @param username {string}
     * @param gameType {'pvp'|'pvb'}
     */
    constructor(username, gameType) {
        this.#username = username;
        this.#gameType = gameType;
        this.#subsection = document.getElementById(`${gameType}-games-subsection`);
        this.#thumbDivs = getElementsByClassNameArray(`${gameType}-game-thumb`);

        this.#thumbs = this.#thumbDivs.map((div, i) => {
            const boardId = `last-${gameType}-game-board-${i}`;
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
    }

    /**
     * @param onDone {function(boolean): void} called with whether any games were rendered
     */
    fetchGames(onDone) {
        const limit = this.#thumbs.length;
        const url = `/api/game-data/list-latest-${this.#gameType}-games-by-user`
            + `?limit=${limit}`
            + `&username=${encodeURIComponent(this.#username)}`;

        getAndHandle(url, (json) => {
            const entries = (json.entries || []).map((entry) => new GameMetadataDto(entry));
            const hasGames = this.#renderEntries(entries);
            onDone(hasGames);
        });
    }

    /**
     * @param entries {GameMetadataDto[]}
     * @returns {boolean} whether any games were rendered
     */
    #renderEntries(entries) {
        if (entries.length === 0) {
            return false;
        }

        if (this.#subsection != null) {
            this.#subsection.style.display = 'block';
        }

        for (let i = 0; i < this.#thumbs.length; i++) {
            if (i < entries.length) {
                this.#thumbs[i].render(entries[i], 'user_profile');
            } else {
                // Hide unused pre-rendered thumbs when fewer games than slots
                this.#thumbDivs[i].style.display = 'none';
            }
        }

        return true;
    }
}

/**
 * Coordinates the PvP and PvB game sections of the user profile page,
 * showing each one only if the profile owner enabled it, and revealing the
 * overall "Latest Games" section if any games are shown.
 */
class UserProfileGames {

    #section = document.getElementById('latest-games-section');

    /**
     * @param username {string}
     * @param showPvpGames {boolean}
     * @param showPvbGames {boolean}
     */
    constructor(username, showPvpGames, showPvbGames) {
        let pending = 0;
        let anyShown = false;

        const onSectionDone = (hasGames) => {
            anyShown = anyShown || hasGames;
            pending--;
            if (pending === 0 && anyShown && this.#section != null) {
                this.#section.style.display = 'block';
            }
        };

        if (showPvpGames) {
            pending++;
        }
        if (showPvbGames) {
            pending++;
        }

        if (showPvpGames) {
            new UserProfileGamesSection(username, 'pvp').fetchGames(onSectionDone);
        }
        if (showPvbGames) {
            new UserProfileGamesSection(username, 'pvb').fetchGames(onSectionDone);
        }
    }
}
