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

class AdminUserEloStatsPage extends BasePage {

    /**
     * @type {HTMLTableElement}
     */
    #authenticatedTable = document.getElementById('authenticated-elo-stats');

    /**
     * @type {HTMLTableElement}
     */
    #guestTable = document.getElementById('guest-elo-stats');

    constructor() {
        super();
        this.#fetchStats();
    }

    #fetchStats() {
        getAndHandle(ADMIN_URL_PREFIX + '/elo-stats', json => this.#render(json));
    }

    /**
     * @param json {object}
     */
    #render(json) {
        this.#renderTable(this.#authenticatedTable, json.authenticatedEntries || []);
        this.#renderTable(this.#guestTable, json.guestEntries || []);
    }

    /**
     * @param table {HTMLTableElement}
     * @param entries {Array}
     */
    #renderTable(table, entries) {
        const tbody = emptyTable(table);

        entries.forEach(entry => {
            const row = tbody.insertRow();

            row.insertCell().innerText = this.#formatEnum(entry.variant);
            row.insertCell().innerText = this.#formatEnum(entry.timeControlCategory);

            this.#renderUserCell(row.insertCell(), entry.minUserId, entry.minUsername, entry.minRating);

            const averageCell = row.insertCell();
            averageCell.className = 'numeric-cell';
            averageCell.innerText = this.#formatAverage(entry.averageRating);

            this.#renderUserCell(row.insertCell(), entry.maxUserId, entry.maxUsername, entry.maxRating);
        });
    }

    /**
     * @param cell {HTMLTableCellElement}
     * @param userId {string|null}
     * @param username {string|null}
     * @param rating {number|null}
     */
    #renderUserCell(cell, userId, username, rating) {
        if (rating == null) {
            cell.innerText = '-';
            return;
        }

        if (username) {
            const link = document.createElement('a');
            link.href = `/@/${username}`;
            link.innerText = username;
            cell.appendChild(link);
        } else if (userId) {
            const guestName = document.createElement('span');
            guestName.className = 'guest-name';
            guestName.innerText = `guest #${userId}`;
            cell.appendChild(guestName);
        } else {
            cell.innerText = '-';
            return;
        }

        const ratingText = document.createElement('span');
        ratingText.innerText = ` (${formatNumber(rating)})`;
        cell.appendChild(ratingText);
    }

    /**
     * @param value {string}
     * @returns {string}
     */
    #formatEnum(value) {
        return value
            .toLowerCase()
            .split('_')
            .map(part => capitalize(part))
            .join(' ');
    }

    /**
     * @param value {number|null}
     * @returns {string}
     */
    #formatAverage(value) {
        if (value == null) {
            return '-';
        }

        return Number(value).toLocaleString('en-US', {
            minimumFractionDigits: 0,
            maximumFractionDigits: 1
        });
    }

}

window.onload = () => new AdminUserEloStatsPage();
