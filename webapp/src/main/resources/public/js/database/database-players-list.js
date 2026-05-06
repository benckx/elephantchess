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

const PLAYERS_INIT_COUNT = 250;

class PlayersListPage extends InfiniteScrollPage {

    #playersTableBody;
    #renderedCount = 0;
    #offset = PLAYERS_INIT_COUNT;

    constructor() {
        super();
        this.#playersTableBody = document.querySelector('.standings-table tbody');
        this.#renderedCount = this.#playersTableBody?.querySelectorAll('tr').length ?? 0;
        this.fetchItems();
    }

    shouldFetchNextPage() {
        const rows = this.#playersTableBody?.querySelectorAll('tr');
        if (!rows || rows.length === 0) return false;
        const lastRow = rows[rows.length - 1];
        return isInViewport(lastRow);
    }

    baseUrl() {
        return '/api/database/list-players';
    }

    deserializeJsonEntry(jsonEntry) {
        return jsonEntry;
    }

    extractToken(entry) {
        return this.#offset.toString();
    }

    showNoItem(value) {
        // Not needed for players list as we always have items pre-rendered
    }

    additionalParameters() {
        const params = new Map();
        params.set('limit', '100');
        params.set('offset', this.#offset.toString());
        return params;
    }

    addEntries(entries) {
        entries.forEach((entry) => {
            this.#createPlayerRow(entry);
            this.#renderedCount++;
        });
        this.#offset += entries.length;
    }

    /**
     * @param entry {{slug: string, name: string, wins: number, draws: number, losses: number, totalGames: number}}
     */
    #createPlayerRow(entry) {
        const row = document.createElement('tr');
        row.insertCell().append(buildLink(`/database/player/${entry.slug}`, entry.name));
        row.insertCell().textContent = entry.wins.toString();
        row.insertCell().textContent = entry.draws.toString();
        row.insertCell().textContent = entry.losses.toString();
        row.insertCell().textContent = entry.totalGames.toString();

        this.#playersTableBody.appendChild(row);
    }

}

window.onload = () => new PlayersListPage();
