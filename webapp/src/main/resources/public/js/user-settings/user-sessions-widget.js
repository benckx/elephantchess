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
const MAX_CHARS_IN_CELL = 16;

class UserSessionsWidget {

    #table;
    #actionsContainer;
    #selectAllCheckbox;
    #deleteButton;
    #emptyMessage;
    #allSessionsLink;
    #limit;
    #fetchAll;
    #selectable;
    #onUpdate;

    constructor(options = {}) {
        this.#table = document.getElementById(options.tableId || 'user-sessions-table');
        this.#actionsContainer = document.getElementById(options.actionsContainerId || 'sessions-actions-container');
        this.#selectAllCheckbox = document.getElementById(options.selectAllCheckboxId || 'sessions-select-all');
        this.#deleteButton = document.getElementById(options.deleteButtonId || 'delete-sessions-button');
        this.#emptyMessage = document.getElementById(options.emptyMessageId || 'user-sessions-empty-message');
        this.#allSessionsLink = document.getElementById(options.allSessionsLinkId || 'all-sessions-link');
        this.#limit = options.limit || 5;
        this.#fetchAll = options.fetchAll === true;
        this.#selectable = options.selectable !== false;
        this.#onUpdate = options.onUpdate || (() => {
        });

        if (this.#selectAllCheckbox != null) {
            this.#selectAllCheckbox.addEventListener('change', () => this.#toggleSelectAll());
        }
        if (this.#deleteButton != null) {
            this.#deleteButton.addEventListener('click', () => this.#deleteSelectedSessions());
        }
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

    #render(json) {
        const entries = json.entries || [];
        const total = Number(json.total || 0);

        const tbody = emptyTable(this.#table);
        entries.forEach(entry => {
            const row = tbody.insertRow();
            if (this.#selectable) {
                const checkboxCell = row.insertCell();
                checkboxCell.className = 'select-cell';

                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.dataset.sessionId = entry.id.toString();
                checkbox.className = 'user-session-checkbox';
                checkbox.addEventListener('change', () => this.#updateSelectAllCheckboxState());
                checkboxCell.append(checkbox);
            }

            row.insertCell().append(this.#buildOsCellContent(entry.os));
            row.insertCell().innerText = entry.agentName;
            row.insertCell().append(this.#buildCountryCellContent(entry.countryCode, entry.countryName));
            this.#insertCroppableCell(row, entry.region);
            this.#insertCroppableCell(row, entry.city);
            row.insertCell().innerText = entry.remoteAddress;
            row.insertCell().innerText = formatTimestampToDateTime(entry.created);
            row.insertCell().innerText = formatTimestampToDateTime(entry.updated);
        });

        const hasEntries = entries.length > 0;
        if (this.#actionsContainer != null) {
            this.#actionsContainer.classList.toggle('hidden', !hasEntries || !this.#selectable);
        }
        if (this.#emptyMessage != null) {
            this.#emptyMessage.classList.toggle('hidden', hasEntries);
        }
        if (this.#allSessionsLink != null) {
            this.#allSessionsLink.classList.toggle('hidden', total <= entries.length);
        }

        if (this.#selectAllCheckbox != null) {
            this.#selectAllCheckbox.checked = false;
        }
        this.#onUpdate(entries, total);
    }

    #insertCroppableCell(row, value) {
        const cell = row.insertCell();
        cell.innerText = cropText(value, MAX_CHARS_IN_CELL);
        if (value != null && value.length > MAX_CHARS_IN_CELL) {
            cell.id = randomId();
            addToolTip(cell, value);
        }
    }

    #buildCountryCellContent(countryCode, countryName) {
        const container = document.createElement('div');
        container.className = 'session-country-cell';

        if (countryCode != null && typeof buildFlagIconImg === 'function') {
            container.append(buildFlagIconImg(countryCode));
        }

        const countryLabel = document.createElement('span');
        countryLabel.innerText = cropText(countryName, MAX_CHARS_IN_CELL);
        if (countryName != null && countryName.length > MAX_CHARS_IN_CELL) {
            countryLabel.id = randomId();
            addToolTip(countryLabel, countryName);
        }
        container.append(countryLabel);
        return container;
    }

    #buildOsCellContent(osName) {
        const container = document.createElement('div');
        container.className = 'session-os-cell';

        const icon = document.createElement('span');
        icon.className = 'session-os-icon';
        icon.innerText = this.#mapOsNameToIcon(osName);
        icon.title = osName;

        const label = document.createElement('span');
        label.innerText = osName;

        container.append(icon, label);
        return container;
    }

    #mapOsNameToIcon(osName) {
        const lower = (osName || '').toLowerCase();
        if (lower.includes('android')) return '🤖';
        if (lower.includes('linux')) return '🐧';
        if (lower.includes('mac')) return '🍎';
        if (lower.includes('windows')) return '🪟';
        return '❔';
    }

    #toggleSelectAll() {
        const isChecked = this.#selectAllCheckbox.checked;
        this.#table.querySelectorAll('.user-session-checkbox').forEach(checkbox => {
            checkbox.checked = isChecked;
        });
    }

    #updateSelectAllCheckboxState() {
        if (this.#selectAllCheckbox == null) {
            return;
        }
        const checkboxes = this.#table.querySelectorAll('.user-session-checkbox');
        if (checkboxes.length === 0) {
            this.#selectAllCheckbox.checked = false;
            return;
        }
        this.#selectAllCheckbox.checked =
            Array.from(checkboxes).every(checkbox => checkbox.checked);
    }

    #deleteSelectedSessions() {
        const selectedIds = Array.from(this.#table.querySelectorAll('.user-session-checkbox:checked'))
            .map(checkbox => Number(checkbox.dataset.sessionId));

        if (selectedIds.length === 0) {
            UI.pushInfoNotification('No sessions selected.', 2_500);
            return;
        }

        postAndHandle(DELETE_USER_SESSIONS_URL, {sessionIds: selectedIds}, (json) => {
            const deletedCount = json.deletedCount || 0;
            UI.pushInfoNotification(`${deletedCount} session(s) deleted.`, 2_500);
            this.fetchAndRender();
        });
    }

}
