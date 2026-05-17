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

const SESSIONS_PAGE_SIZE = 50;

class UserSessionsPage extends InfiniteScrollPage {

    #widget;
    #offset = 0;

    constructor() {
        super();
        this.#widget = new UserSessionsWidget({
            onDelete: () => this.#reload(),
        });
        this.#widget.clear();
        this.fetchItems();
    }

    shouldFetchNextPage() {
        const rows = this.#widget.table.tBodies[0]?.querySelectorAll('tr');
        if (!rows || rows.length === 0) return false;
        return isInViewport(rows[rows.length - 1]);
    }

    baseUrl() {
        return USER_SESSIONS_URL;
    }

    deserializeJsonEntry(jsonEntry) {
        return jsonEntry;
    }

    extractToken(_entry) {
        return this.#offset.toString();
    }

    showNoItem(value) {
        const emptyMessage = document.getElementById('user-sessions-empty-message');
        if (emptyMessage != null) {
            emptyMessage.classList.toggle('hidden', !value);
        }
    }

    additionalParameters() {
        const params = new Map();
        params.set('limit', SESSIONS_PAGE_SIZE.toString());
        params.set('offset', this.#offset.toString());
        return params;
    }

    addEntries(entries) {
        this.#widget.appendEntries(entries);
        this.#offset += entries.length;
    }

    #reload() {
        this.#offset = 0;
        this.resetPagination();
        this.#widget.clear();
        this.fetchItems();
    }

}

window.onload = () => new UserSessionsPage();
