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

/**
 * Dedicated admin page for PvP matching time (seconds between a game being created and joined).
 *
 * The per-month query is expensive, so the page first fetches the list of available months and then
 * loads the stats one month at a time, appending each month's row (for both the lobby and link
 * tables) as soon as it arrives instead of waiting for every month.
 */
class AdminMatchingTimePage extends BasePage {

    /**
     * @type {string[]}
     */
    #periods = [];

    /**
     * @type {number}
     */
    #pendingRequests = 0;

    /**
     * @type {number}
     */
    #totalRequests = 0;

    /**
     * @type {HTMLTableElement}
     */
    #lobbyTable = document.getElementById('table-lobby');

    /**
     * @type {HTMLTableElement}
     */
    #linkTable = document.getElementById('table-link');

    constructor() {
        super();
        this.#fetchPeriodsThenStats();
    }

    #fetchPeriodsThenStats() {
        getAndHandle(`${ADMIN_URL_PREFIX}/matching-time-periods`, json => {
            this.#periods = json.periods || [];
            this.#pendingRequests = this.#periods.length;
            this.#totalRequests = this.#periods.length;
            this.#updateLoadingDisplay();

            if (this.#periods.length === 0) {
                return;
            }

            this.#fetchMonth(0);
        });
    }

    /**
     * Fetches one month at a time (sequentially) so the expensive per-month query never runs more
     * than one request at once, and renders each month's row as soon as it is received.
     * @param index {number}
     */
    #fetchMonth(index) {
        const period = this.#periods[index];
        const url = `${ADMIN_URL_PREFIX}/matching-time-stats?period=${encodeURIComponent(period)}`;
        getAndHandle(url, json => {
            this.#appendRow(this.#lobbyTable, period, json.lobbyJoinModes || {});
            this.#appendRow(this.#linkTable, period, json.linkJoinSource || {});
            this.#pendingRequests--;
            this.#updateLoadingDisplay();
            if (index + 1 < this.#periods.length) {
                this.#fetchMonth(index + 1);
            }
        });
    }

    /**
     * @param table {HTMLTableElement}
     * @param period {string}
     * @param stat {Object}
     */
    #appendRow(table, period, stat) {
        const tbody = table.getElementsByTagName('tbody')[0];
        const row = tbody.insertRow();

        const monthCell = row.insertCell();
        monthCell.className = 'label-cell';
        monthCell.innerText = period;

        this.#appendValueCell(row, stat.count !== undefined && stat.count !== null ? stat.count.toString() : '0');
        this.#appendValueCell(row, AdminMatchingTimePage.formatSeconds(stat.minSeconds));
        this.#appendValueCell(row, AdminMatchingTimePage.formatSeconds(stat.medianSeconds));
        this.#appendValueCell(row, AdminMatchingTimePage.formatSeconds(stat.p90Seconds));
        this.#appendValueCell(row, AdminMatchingTimePage.formatSeconds(stat.p95Seconds));
        this.#appendValueCell(row, AdminMatchingTimePage.formatSeconds(stat.p99Seconds));
        this.#appendValueCell(row, AdminMatchingTimePage.formatSeconds(stat.maxSeconds));
    }

    /**
     * @param row {HTMLTableRowElement}
     * @param text {string}
     */
    #appendValueCell(row, text) {
        const cell = row.insertCell();
        cell.className = 'value-cell';
        cell.innerText = text;
    }

    #updateLoadingDisplay() {
        const completedDisplay = document.getElementById('completed-requests-display');
        const totalDisplay = document.getElementById('total-requests-display');
        const loadingStatus = document.getElementById('loading-status');

        const completedRequests = this.#totalRequests - this.#pendingRequests;

        if (completedDisplay) {
            completedDisplay.textContent = completedRequests.toString();
        }
        if (totalDisplay) {
            totalDisplay.textContent = this.#totalRequests.toString();
        }

        if (loadingStatus && this.#pendingRequests === 0) {
            loadingStatus.style.display = 'none';
        }
    }

    /**
     * Format a duration in seconds as a human-readable string (e.g. "42s", "3m 7s", "2h 5m", "2d 1h").
     * Larger units roll up so very long waits read as days/hours instead of thousands of minutes.
     * @param seconds {?number}
     * @returns {string}
     */
    static formatSeconds(seconds) {
        if (seconds === null || seconds === undefined) {
            return 'n/a';
        }
        const total = Math.round(seconds);
        if (total < 60) {
            return `${total}s`;
        }
        if (total < 3600) {
            const minutes = Math.floor(total / 60);
            const remainingSeconds = total % 60;
            return `${minutes}m ${remainingSeconds}s`;
        }
        if (total < 86400) {
            const hours = Math.floor(total / 3600);
            const minutes = Math.floor((total % 3600) / 60);
            return `${hours}h ${minutes}m`;
        }
        const days = Math.floor(total / 86400);
        const hours = Math.floor((total % 86400) / 3600);
        return `${days}d ${hours}h`;
    }

}

window.onload = () => new AdminMatchingTimePage();
