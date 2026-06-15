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
    #table = document.getElementById('elo-stats');

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
        const tbody = this.#table.getElementsByTagName('tbody')[0];
        emptyTable(this.#table);

        (json.entries || []).forEach(entry => {
            const row = tbody.insertRow();

            row.insertCell().innerText = this.#formatEnum(entry.variant);
            row.insertCell().innerText = this.#formatEnum(entry.timeControlCategory);

            const countCell = row.insertCell();
            countCell.className = 'numeric-cell';
            countCell.innerText = formatNumber(entry.userCount || 0);

            this.#renderUserCell(row.insertCell(), entry.minUsername, entry.minRating);

            const averageCell = row.insertCell();
            averageCell.className = 'numeric-cell';
            averageCell.innerText = this.#formatAverage(entry.averageRating);

            this.#renderUserCell(row.insertCell(), entry.maxUsername, entry.maxRating);
        });
    }

    /**
     * @param cell {HTMLTableCellElement}
     * @param username {string|null}
     * @param rating {number|null}
     */
    #renderUserCell(cell, username, rating) {
        if (!username || rating == null) {
            cell.innerText = '-';
            return;
        }

        const link = document.createElement('a');
        link.href = `/@/${username}`;
        link.innerText = username;
        cell.appendChild(link);

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
