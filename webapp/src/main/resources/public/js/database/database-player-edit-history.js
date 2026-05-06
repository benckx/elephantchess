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

class DatabasePlayerEditHistory extends BasePage {

    /**
     * @type {string}
     */
    #playerName;

    /**
     * @type {string}
     */
    #playerId;

    constructor() {
        super();
        this.#playerName = extractPlayerNameFromUrl();
        this.#playerId = document.querySelector('body').dataset.playerId;
        this.#loadPlayerHistory();
    }

    #loadPlayerHistory() {
        const url = `/api/database/info/player/edit-history-by-player-id?playerId=${this.#playerId}`;
        getAndHandle(url, (json) => {
            this.#displayPlayerHistory(new DatabasePlayerVersionHistoryDto(json));
        });
    }

    /**
     * @param playerData {DatabasePlayerVersionHistoryDto}
     */
    #displayPlayerHistory(playerData) {
        // hide loading message and show history
        document.getElementById('loading-message').style.display = 'none';
        document.getElementById('history-content').style.display = 'block';

        // display version history
        const historyContainer = document.getElementById('version-history-container');
        renderPlayerProfileVersionHistory(playerData.versionHistory, historyContainer);

        // show link to player profile
        const playerProfileLink = document.getElementById('player-profile-link');
        if (playerProfileLink) {
            playerProfileLink.href = `/database/player/${encodePlayerNameForUrl(this.#playerName)}`;
        }
    }

}

window.onload = () => new DatabasePlayerEditHistory();

