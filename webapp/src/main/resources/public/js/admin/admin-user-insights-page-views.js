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

const ADMIN_USER_PAGE_VIEWS_PAGE_SIZE = 50;

class AdminUserInsightsPageViewsPage extends InfiniteScrollPage {

    #table = document.getElementById('admin-user-page-views-table');
    #emptyMessage = document.getElementById('admin-user-page-views-empty-message');
    #identifier = document.body.dataset.userIdentifier;
    #offset = 0;

    constructor() {
        super();
        emptyTable(this.#table);
        this.fetchItems();
    }

    shouldFetchNextPage() {
        const rows = this.#table.tBodies[0]?.querySelectorAll('tr');
        if (!rows || rows.length === 0) return false;
        return isInViewport(rows[rows.length - 1]);
    }

    baseUrl() {
        return `${ADMIN_URL_PREFIX}/user-insights/${encodeURIComponent(this.#identifier)}/page-views`;
    }

    deserializeJsonEntry(jsonEntry) {
        return jsonEntry;
    }

    extractToken(_entry) {
        return this.#offset.toString();
    }

    showNoItem(value) {
        this.#emptyMessage.style.display = value ? 'block' : 'none';
    }

    additionalParameters() {
        const params = new Map();
        params.set('limit', ADMIN_USER_PAGE_VIEWS_PAGE_SIZE.toString());
        params.set('offset', this.#offset.toString());
        return params;
    }

    addEntries(entries) {
        if (!entries || entries.length === 0) return;
        const tbody = this.#table.tBodies[0] || this.#table.appendChild(document.createElement('tbody'));
        entries.forEach(entry => {
            const row = tbody.insertRow();
            const link = document.createElement('a');
            link.href = entry.url;
            link.innerText = entry.url;
            row.insertCell().append(link);
            row.insertCell().innerText = formatTimestampDefaultDateFormat(entry.time);
        });
        this.#offset += entries.length;
    }
}

window.onload = () => new AdminUserInsightsPageViewsPage();
