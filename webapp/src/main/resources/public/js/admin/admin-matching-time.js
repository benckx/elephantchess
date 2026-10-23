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
     * @type {HTMLTableElement}
     */
    #lobbyTable = document.getElementById('table-lobby');

    /**
     * @type {HTMLTableElement}
     */
    #linkTable = document.getElementById('table-link');

    /**
     * Per-category tables for the lobby/matchmaking breakdown, keyed by category name.
     * @type {Object<string, HTMLTableElement>}
     */
    #lobbyCategoryTables = {};

    /**
     * Per-category tables for the LINK breakdown, keyed by category name.
     * @type {Object<string, HTMLTableElement>}
     */
    #linkCategoryTables = {};

    constructor() {
        super();
        this.#lobbyCategoryTables = this.#collectCategoryTables('tables-lobby-by-category');
        this.#linkCategoryTables = this.#collectCategoryTables('tables-link-by-category');
        this.#fetchPeriodsThenStats();
    }

    /**
     * Collects the (statically declared) per-category-group tables under the given container, keyed
     * by category group name. The tables and their headings start hidden and are revealed the first
     * time a month has data for them.
     * @param containerId {string}
     * @returns {Object<string, HTMLTableElement>}
     */
    #collectCategoryTables(containerId) {
        const container = document.getElementById(containerId);
        const tables = {};
        container.querySelectorAll('table.stats-table').forEach(table => {
            const category = table.id.substring(table.id.lastIndexOf('-') + 1);
            tables[category] = table;
        });
        return tables;
    }

    #fetchPeriodsThenStats() {
        getAndHandle(`${ADMIN_URL_PREFIX}/matching-time-periods`, json => {
            this.#periods = json.periods || [];

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
            this.#appendCategoryRows(this.#lobbyCategoryTables, period, json.lobbyJoinModesByCategory || {});
            this.#appendCategoryRows(this.#linkCategoryTables, period, json.linkJoinSourceByCategory || {});
            if (index + 1 < this.#periods.length) {
                this.#fetchMonth(index + 1);
            }
        });
    }

    /**
     * Appends a row to each per-category table that has data for this month, revealing the table the
     * first time it is used. Categories with no joined game this month are skipped.
     * @param tablesByCategory {Object<string, HTMLTableElement>}
     * @param period {string}
     * @param byCategory {Object<string, Object>}
     */
    #appendCategoryRows(tablesByCategory, period, byCategory) {
        Object.keys(byCategory).forEach(category => {
            const stat = byCategory[category];
            if (!stat || !stat.count) {
                return;
            }
            const table = tablesByCategory[category];
            if (!table) {
                return;
            }
            if (table.style.display === 'none') {
                table.style.display = '';
                const heading = table.previousElementSibling;
                if (heading) {
                    heading.style.display = '';
                }
            }
            this.#appendRow(table, period, stat);
        });
    }

    /**
     * Matching-time stats for a single month and join-source (or category group). Every duration is
     * in milliseconds between a game being created and the earliest JOINED event; the percentiles and
     * min/max are null when no game was joined that month.
     * @typedef {Object} MatchingTimeStat
     * @property {number} count - number of joined games considered this month
     * @property {?number} minMillis - fastest matching time
     * @property {?number} medianMillis - p50
     * @property {?number} p90Millis - 90th percentile
     * @property {?number} p95Millis - 95th percentile
     * @property {?number} p99Millis - 99th percentile
     * @property {?number} maxMillis - slowest matching time
     */

    /**
     * @param table {HTMLTableElement}
     * @param period {string}
     * @param stat {MatchingTimeStat}
     */
    #appendRow(table, period, stat) {
        const tbody = table.getElementsByTagName('tbody')[0];
        const row = tbody.insertRow();

        const monthCell = row.insertCell();
        monthCell.className = 'label-cell';
        monthCell.innerText = period;

        this.#appendValueCell(row, stat.count !== undefined && stat.count !== null ? stat.count.toString() : '0');
        this.#appendDurationCell(row, stat.minMillis);
        this.#appendDurationCell(row, stat.medianMillis);
        this.#appendDurationCell(row, stat.p90Millis);
        this.#appendDurationCell(row, stat.p95Millis);
        this.#appendDurationCell(row, stat.p99Millis);
        this.#appendDurationCell(row, stat.maxMillis);
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

    /**
     * Appends a duration cell, formatting the value and color-coding long waits (orange from 10m to
     * 1h, purple above 24h) so outliers stand out at a glance.
     * @param row {HTMLTableRowElement}
     * @param millis {?number}
     */
    #appendDurationCell(row, millis) {
        const cell = row.insertCell();
        cell.className = 'value-cell';
        cell.innerText = AdminMatchingTimePage.formatMillis(millis);
        const colorClass = AdminMatchingTimePage.durationColorClass(millis);
        if (colorClass) {
            cell.classList.add(colorClass);
        }
    }

    /**
     * Format a duration in milliseconds as a human-readable string (e.g. "42s", "3m 7s", "2h 5m",
     * "2d 1h"), delegating to the shared formatDurationShorthand. Returns "n/a" for missing values
     * (months with no matching game).
     * @param millis {?number}
     * @returns {string}
     */
    static formatMillis(millis) {
        if (millis === null || millis === undefined) {
            return 'n/a';
        }
        return formatDurationShorthand(millis);
    }

    /**
     * Returns the CSS class used to color-code a matching-time value: orange between 10m and 1h,
     * purple above 24h, and none (empty string) otherwise or when the value is missing.
     * @param millis {?number}
     * @returns {string}
     */
    static durationColorClass(millis) {
        if (millis === null || millis === undefined) {
            return '';
        }
        if (millis > 24 * 60 * 60 * 1_000) {
            return 'duration-very-long';
        }
        if (millis >= 10 * 60 * 1_000 && millis < 60 * 60 * 1_000) {
            return 'duration-long';
        }
        return '';
    }

}

window.onload = () => new AdminMatchingTimePage();
