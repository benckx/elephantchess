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

const USER_PAGE_VIEWS_URL = '/api/user/settings/views';
const ARCHIVE_USER_PAGE_VIEWS_URL = `${USER_PAGE_VIEWS_URL}/archive-now`;

class UserPageViewsWidget {

    #table;
    #archiveButton;
    #emptyMessage;
    #allViewsLink;
    #limit;
    #onUpdate;
    #onArchive;

    constructor(options = {}) {
        this.#table = document.getElementById(options.tableId || 'user-page-views-table');
        this.#archiveButton = document.getElementById(options.archiveButtonId || 'archive-page-views-button');
        this.#emptyMessage = document.getElementById(options.emptyMessageId || 'user-page-views-empty-message');
        this.#allViewsLink = document.getElementById(options.allViewsLinkId || 'all-page-views-link');
        this.#limit = options.limit || 5;
        this.#onUpdate = options.onUpdate || (() => {
        });
        this.#onArchive = options.onArchive || null;

        if (this.#archiveButton != null) {
            this.#archiveButton.addEventListener('click', () => this.#confirmArchiveAll());
            UI.preloadModal(Modals.CONFIRMATION);
        }
    }

    /**
     * The underlying <table> element. Exposed so external paginators can
     * inspect rendered rows (e.g. `InfiniteScrollPage.shouldFetchNextPage`).
     * @returns {HTMLTableElement}
     */
    get table() {
        return this.#table;
    }

    fetchAndRender() {
        getAndHandle(`${USER_PAGE_VIEWS_URL}?limit=${this.#limit}`, (json) => this.#render(json));
    }

    /**
     * Clear the table body and reset the widget's footer UI to its empty state.
     * Intended for paginators that drive rendering externally.
     */
    clear() {
        emptyTable(this.#table);
        if (this.#emptyMessage != null) {
            this.#emptyMessage.classList.add('hidden');
        }
        if (this.#allViewsLink != null) {
            this.#allViewsLink.classList.add('hidden');
        }
    }

    /**
     * Append rows for the given entries to the table body, without clearing
     * what is already rendered.
     * @param entries {Array<Object>}
     */
    appendEntries(entries) {
        if (!entries || entries.length === 0) return;

        const tbody = this.#table.tBodies[0] || this.#table.appendChild(document.createElement('tbody'));
        entries.forEach(entry => this.#appendRow(tbody, entry));

        if (this.#emptyMessage != null) {
            this.#emptyMessage.classList.add('hidden');
        }
    }

    #render(json) {
        const entries = json.entries || [];
        const total = Number(json.total || 0);

        this.clear();
        this.appendEntries(entries);

        const hasEntries = entries.length > 0;
        if (this.#emptyMessage != null) {
            this.#emptyMessage.classList.toggle('hidden', hasEntries);
        }
        if (this.#allViewsLink != null) {
            this.#allViewsLink.classList.toggle('hidden', total <= entries.length);
        }

        this.#onUpdate(entries, total);
    }

    #appendRow(tbody, entry) {
        const row = tbody.insertRow();
        row.insertCell().innerText = entry.url;
        row.insertCell().innerText = formatTimestampToDateTime(entry.time);
    }

    #confirmArchiveAll() {
        const message = buildSimpleSpan(
            'Are you sure you want to archive all your page views now? ' +
            'They will no longer be associated with your account.'
        );
        UI.showConfirmationModal(
            message,
            () => this.#archiveAll(),
            'archive now',
            () => UI.hideModal(null),
            'cancel'
        );
    }

    #archiveAll() {
        postAndHandle(ARCHIVE_USER_PAGE_VIEWS_URL, null, (json) => {
            const archivedCount = json.archivedCount || 0;
            UI.pushInfoNotification(`${formatNumberWithSuffix(archivedCount)} page view(s) archived.`, 2_500);
            if (this.#onArchive != null) {
                this.#onArchive(archivedCount);
            } else {
                this.fetchAndRender();
            }
        });
    }

}
