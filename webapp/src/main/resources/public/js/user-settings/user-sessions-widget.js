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
    #onUpdate;

    constructor(options = {}) {
        this.#table = document.getElementById(options.tableId || 'user-sessions-table');
        this.#actionsContainer = document.getElementById(options.actionsContainerId || 'sessions-actions-container');
        this.#selectAllCheckbox = document.getElementById(options.selectAllCheckboxId || 'sessions-select-all');
        this.#deleteButton = document.getElementById(options.deleteButtonId || 'delete-sessions-button');
        this.#emptyMessage = document.getElementById(options.emptyMessageId || 'user-sessions-empty-message');
        this.#allSessionsLink = document.getElementById(options.allSessionsLinkId || 'all-sessions-link');
        this.#limit = options.limit || 5;
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
        getAndHandle(`${USER_SESSIONS_URL}?limit=${this.#limit}`, (json) => this.#render(json));
    }

    #render(json) {
        const entries = json.entries || [];
        const total = Number(json.total || 0);

        const tbody = emptyTable(this.#table);
        entries.forEach(entry => {
            const row = tbody.insertRow();
            const checkboxCell = row.insertCell();

            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.dataset.sessionId = entry.id.toString();
            checkbox.className = 'user-session-checkbox';
            checkbox.addEventListener('change', () => this.#updateSelectAllCheckboxState());
            checkboxCell.append(checkbox);

            row.insertCell().innerText = entry.os;
            row.insertCell().innerText = entry.agentName;
            this.#insertCroppableCell(row, entry.countryName);
            this.#insertCroppableCell(row, entry.region);
            this.#insertCroppableCell(row, entry.city);
            row.insertCell().innerText = entry.remoteAddress;
            row.insertCell().innerText = formatTimestampToDateTime(entry.created);
            row.insertCell().innerText = formatTimestampToDateTime(entry.updated);
        });

        const hasEntries = entries.length > 0;
        if (this.#actionsContainer != null) {
            this.#actionsContainer.classList.toggle('hidden', !hasEntries);
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
