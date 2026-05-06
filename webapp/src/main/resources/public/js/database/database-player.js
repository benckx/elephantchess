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

class DatabasePlayerPage extends BasePage {

    /**
     * @type {GameThumb[]}
     */
    #thumbs = [];

    /**
     * @type {HTMLElement[]}
     */
    #thumbDivs = getElementsByClassNameArray('latest-game-thumb');

    /**
     * @type {string}
     */
    #playerName;

    constructor() {
        super();
        this.#playerName = decodePlayerNameFromUrl(decodeURIComponent(window.location.pathname.split('/').pop()));

        // init latest games boards
        this.#thumbDivs.forEach((div, i) => {
            const options = {
                elementId: `last-db-game-board-${i}`,
                showCoordinates: false,
                mini: true,
            };

            const boardGui = createWebappBoardGui(options);
            boardGui.enablePlaceholderMode();
            this.#thumbs.push(new GameThumb(div, boardGui));
        });

        this.#loadLatestGames();

        // if looking at a specific version, show link to current version
        const version = getQueryParam('version');
        if (version != null) {
            document.getElementById('view-current-version-link').style.display = 'flex';
        }

        // load game stats
        this.#loadGameStats();

        // handle duplicates
        this.#loadDuplicatesIfAny();
    }

    #loadLatestGames() {
        const limit = this.#thumbDivs.length;
        const encodedPlayerName = encodePlayerNameForUrl(this.#playerName);
        const url = `/api/game-data/list-db-player-games?playerName=${encodedPlayerName}&limit=${limit}`;

        getAndHandle(url, (json) => {
            const gameMetadataDtos = [];
            for (let i = 0; i < json.entries.length; i++) {
                gameMetadataDtos.push(new GameMetadataDto(json.entries[i]));
            }

            const count = Math.min(gameMetadataDtos.length, this.#thumbs.length);
            for (let i = 0; i < count; i++) {
                this.#thumbs[i].render(gameMetadataDtos[i], null, this.#playerName);
            }
        });
    }

    #loadGameStats() {
        const playerId = document.querySelector('body').dataset.playerId;
        const url = `/api/database/info/player/game-stats?playerId=${playerId}`;
        getAndHandle(url, (stats) => {
            new PlayerStatsChart('player-stats-chart', stats).render();
            if (stats.withDuplicates != null) {
                document.getElementById('include-possible-duplicates-data-warning').style.display = 'flex';
            }
        });
    }

    #loadDuplicatesIfAny() {
        const playerId = document.querySelector('body').dataset.playerId;
        const url = `/api/database/info/player/find-possible-duplicates?playerId=${playerId}`;
        getAndHandle(url, (json) => {
            if (json.entries && json.entries.length > 0) {
                const container = document.getElementById('possible-duplicate-block');
                const ul = container.getElementsByTagName('ul')[0];

                json.entries.forEach(duplicate => {
                    const url = `/database/player/${duplicate.slug}?medium=possible-duplicate`;
                    const li = document.createElement('li');
                    li.appendChild(buildLink(url, duplicate.displayName));
                    ul.appendChild(li);
                });

                container.style.display = 'block';
            }
        });
    }
}

window.onload = () => new DatabasePlayerPage();
