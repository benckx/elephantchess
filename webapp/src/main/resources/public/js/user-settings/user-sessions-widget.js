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

const USER_SESSIONS_URL = '/api/user/settings/sessions';
const DELETE_USER_SESSIONS_URL = USER_SESSIONS_URL + '/delete';
const DELETE_ALL_USER_SESSIONS_URL = USER_SESSIONS_URL + '/delete-all';
const MAX_CHARS_IN_CELL = 16;

class UserSessionsWidget {

    #table;
    #actionsContainer;
    #deleteButton;
    #deleteAllButton;
    #emptyMessage;
    #allSessionsLink;
    #limit;
    #fetchAll;
    #selectable;
    #onUpdate;
    #onDelete;

    constructor(options = {}) {
        this.#table = document.getElementById(options.tableId || 'user-sessions-table');
        this.#actionsContainer = document.getElementById(options.actionsContainerId || 'sessions-actions-container');
        this.#deleteButton = document.getElementById(options.deleteButtonId || 'delete-sessions-button');
        this.#deleteAllButton = document.getElementById(options.deleteAllButtonId || 'delete-all-sessions-button');
        this.#emptyMessage = document.getElementById(options.emptyMessageId || 'user-sessions-empty-message');
        this.#allSessionsLink = document.getElementById(options.allSessionsLinkId || 'all-sessions-link');
        this.#limit = options.limit || 5;
        this.#fetchAll = options.fetchAll === true;
        this.#selectable = options.selectable !== false;
        this.#onUpdate = options.onUpdate || (() => {
        });
        this.#onDelete = options.onDelete || null;

        if (this.#deleteButton != null) {
            this.#deleteButton.addEventListener('click', () => this.#deleteSelectedSessions());
        }
        if (this.#deleteAllButton != null) {
            this.#deleteAllButton.addEventListener('click', () => this.#confirmDeleteAllSessions());
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
        if (!this.#fetchAll) {
            getAndHandle(`${USER_SESSIONS_URL}?limit=${this.#limit}`, (json) => this.#render(json));
            return;
        }

        getAndHandle(`${USER_SESSIONS_URL}?limit=1`, (json) => {
            const total = Number(json.total || 0);
            const allLimit = total > 0 ? total : 1;
            getAndHandle(`${USER_SESSIONS_URL}?limit=${allLimit}`, (allJson) => this.#render(allJson));
        });
    }

    /**
     * Clear the table body and reset the widget's footer/actions UI to its
     * empty state. Intended for paginators that drive rendering externally.
     */
    clear() {
        emptyTable(this.#table);
        if (this.#actionsContainer != null) {
            this.#actionsContainer.classList.add('hidden');
        }
        if (this.#emptyMessage != null) {
            this.#emptyMessage.classList.add('hidden');
        }
        if (this.#allSessionsLink != null) {
            this.#allSessionsLink.classList.add('hidden');
        }
    }

    /**
     * Append rows for the given entries to the table body, without clearing
     * what is already rendered. Toggles the "actions" toolbar on the first
     * batch that contains rows.
     * @param entries {Array<Object>}
     */
    appendEntries(entries) {
        if (!entries || entries.length === 0) return;

        const tbody = this.#table.tBodies[0] || this.#table.appendChild(document.createElement('tbody'));
        entries.forEach(entry => this.#appendRow(tbody, entry));

        if (this.#actionsContainer != null && this.#selectable) {
            this.#actionsContainer.classList.remove('hidden');
        }
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
        if (this.#allSessionsLink != null) {
            this.#allSessionsLink.classList.toggle('hidden', total <= entries.length);
        }

        this.#onUpdate(entries, total);
    }

    #appendRow(tbody, entry) {
        const row = tbody.insertRow();
        if (this.#selectable) {
            const checkboxCell = row.insertCell();
            checkboxCell.className = 'select-cell';

            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.dataset.sessionId = entry.id.toString();
            checkbox.className = 'user-session-checkbox';
            checkboxCell.append(checkbox);
        }

        row.insertCell().append(this.#buildOsCellContent(entry.os));
        row.insertCell().innerText = entry.agentName;
        row.insertCell().append(this.#buildCountryCellContent(entry.countryCode, entry.countryName));
        this.#insertCroppableCell(row, entry.region, 'session-region-column');
        this.#insertCroppableCell(row, entry.city, 'session-city-column');
        row.insertCell().innerText = entry.remoteAddress;
        row.insertCell().innerText = formatTimestampToDateTime(entry.created);
        row.insertCell().innerText = formatTimestampToDateTime(entry.updated);
    }

    #insertCroppableCell(row, value, className) {
        const cell = row.insertCell();
        if (className != null) {
            cell.className = className;
        }
        cell.innerText = cropText(value, MAX_CHARS_IN_CELL);
        if (value != null && value.length > MAX_CHARS_IN_CELL) {
            cell.id = randomId();
            addToolTip(cell, value);
        }
    }

    #buildCountryCellContent(countryCode, countryName) {
        const container = document.createElement('div');
        container.className = 'session-country-cell';

        if (countryCode != null && countryCode !== '-' && typeof buildFlagIconImg === 'function') {
            try {
                container.append(buildFlagIconImg(countryCode));
            } catch (e) {
                console.warn('Could not render country flag icon', e);
            }
        }

        const countryLabelText =
            countryName
            || (countryCode != null && typeof getCountryName === 'function' ? getCountryName(countryCode) : countryCode);

        const countryLabel = document.createElement('span');
        countryLabel.innerText = cropText(countryLabelText, MAX_CHARS_IN_CELL);
        if (countryLabelText != null && countryLabelText.length > MAX_CHARS_IN_CELL) {
            countryLabel.id = randomId();
            addToolTip(countryLabel, countryLabelText);
        }
        container.append(countryLabel);
        return container;
    }

    #buildOsCellContent(osName) {
        const container = document.createElement('div');
        container.className = 'session-os-cell';

        const iconSrc = this.#mapOsNameToIcon(osName);
        let icon;
        if (iconSrc != null) {
            icon = document.createElement('img');
            icon.src = iconSrc;
            icon.alt = osName || 'unknown operating system';
        } else {
            icon = document.createElement('span');
            icon.innerText = '❔';
            icon.setAttribute('role', 'img');
            icon.setAttribute('aria-label', osName || 'unknown operating system');
        }
        icon.className = 'session-os-icon';
        icon.title = osName;

        const label = document.createElement('span');
        label.innerText = osName;

        container.append(icon, label);
        return container;
    }

    #mapOsNameToIcon(osName) {
        const lower = (osName || '').toLowerCase();
        const base = '/images/icons/os/';
        if (lower.startsWith('android')) return base + 'android.png';
        if (lower.startsWith('linux')) return base + 'linux.png';
        if (lower === 'mac' || lower.startsWith('mac os') || lower.startsWith('macos') || lower.startsWith('ios')) return base + 'apple.png';
        if (lower.startsWith('windows')) return base + 'windows.png';
        return null;
    }


    #deleteSelectedSessions() {
        const selectedIds = Array.from(this.#table.querySelectorAll('.user-session-checkbox:checked'))
            .map(checkbox => Number(checkbox.dataset.sessionId));

        if (selectedIds.length === 0) {
            UI.pushErrorNotification('No sessions deleted.', 2_500);
            return;
        }

        postAndHandle(DELETE_USER_SESSIONS_URL, {sessionIds: selectedIds}, (json) => {
            this.#handleDeleteResponse(json);
        });
    }

    #confirmDeleteAllSessions() {
        const message = buildSimpleSpan(
            'Are you sure you want to delete all your sessions?'
        );
        UI.showConfirmationModal(
            message,
            () => this.#deleteAllSessions(),
            'delete all',
            () => UI.hideModal(null),
            'cancel'
        );
    }

    #deleteAllSessions() {
        postAndHandle(DELETE_ALL_USER_SESSIONS_URL, null, (json) => {
            this.#handleDeleteResponse(json);
        });
    }

    #handleDeleteResponse(json) {
        const deletedCount = json.deletedCount || 0;
        UI.pushInfoNotification(`${deletedCount} session(s) deleted.`, 2_500);
        if (this.#onDelete != null) {
            this.#onDelete(deletedCount);
        } else {
            this.fetchAndRender();
        }
    }

}
