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

const EVENTS_INIT_COUNT = 250;

class EventsListPage extends InfiniteScrollPage {

    #eventsTableBody;
    #renderedCount = 0;
    #offset = EVENTS_INIT_COUNT;

    constructor() {
        super();
        this.#eventsTableBody = document.querySelector('.events-list-table tbody');
        this.#renderedCount = this.#eventsTableBody?.querySelectorAll('tr').length ?? 0;
        this.fetchItems();
    }

    shouldFetchNextPage() {
        const rows = this.#eventsTableBody?.querySelectorAll('tr');
        if (!rows || rows.length === 0) return false;
        const lastRow = rows[rows.length - 1];
        return isInViewport(lastRow);
    }

    baseUrl() {
        return '/api/database/list-events';
    }

    deserializeJsonEntry(jsonEntry) {
        return jsonEntry;
    }

    extractToken(entry) {
        return this.#offset.toString();
    }

    showNoItem(value) {
        // Not needed for events list as we always have items pre-rendered
    }

    additionalParameters() {
        const params = new Map();
        params.set('limit', '100');
        params.set('offset', this.#offset.toString());
        return params;
    }

    addEntries(entries) {
        entries.forEach((entry) => {
            this.#createEventRow(entry);
            this.#renderedCount++;
        });
        this.#offset += entries.length;
    }

    /**
     * @param entry {{id: string, name: string, date: string|null, maxRound: number|null, gameCount: number}}
     */
    #createEventRow(entry) {
        const row = document.createElement('tr');
        row.insertCell().append(buildLink(`/database/event?id=${entry.id}`, entry.name));
        row.insertCell().textContent = entry.date ? this.#formatDate(entry.date) : '-';
        row.insertCell().textContent = entry.maxRound ?? '-';
        row.insertCell().textContent = entry.gameCount.toString();

        this.#eventsTableBody.appendChild(row);
    }

    #formatDate(dateString) {
        const date = new Date(dateString);
        const day = date.getDate();
        const month = date.toLocaleDateString('en-US', {month: 'short'});
        const year = date.getFullYear();
        return `${day} ${month} ${year}`;
    }

}

window.onload = () => new EventsListPage();
