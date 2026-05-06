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

class AdminAnalyticsPage extends BasePage {

    /**
     * @type {HTMLTableElement}
     */
    #analysisPerUserTable = document.getElementById('analysis-per-user');

    /**
     * @type {HTMLElement}
     */
    #metricsFilter = document.getElementById('metrics-filter');

    /**
     * Stores fetched data for re-rendering when filters change
     * @type {Map<HTMLTableElement, {response: MultipleTimeSeriesDto, formatter: function}>}
     */
    #tableData = new Map();

    constructor() {
        super();
        makeCheckboxesClickable();

        const hourlyStatsTable = document.getElementById('hourly-stats');
        const dailyStatsTable = document.getElementById('daily-stats');
        const monthlyStatsTable = document.getElementById('monthly-stats');
        const yearlyStatsTable = document.getElementById('yearly-stats');
        const totalStatsTable = document.getElementById('total-stats');
        const dailyAvgByMonthStatsTable = document.getElementById('daily-avg-by-month-stats');

        this.#fetchAnalytics('/hourly-stats', hourlyStatsTable);
        this.#fetchAnalytics('/daily-stats', dailyStatsTable);
        this.#fetchAnalytics('/monthly-stats', monthlyStatsTable);
        this.#fetchAnalytics('/yearly-stats', yearlyStatsTable);
        this.#fetchAnalytics('/total-stats', totalStatsTable);
        this.#fetchAnalytics('/daily-avg-by-month', dailyAvgByMonthStatsTable, v => v.toFixed(1));
        this.#fetchAnalysisPerUser();

        // Add event listeners for checkboxes
        this.#metricsFilter.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
            checkbox.addEventListener('change', () => this.#onFilterChange());
        });
    }

    /**
     * @returns {string[]}
     */
    #getSelectedMetrics() {
        const selected = [];
        this.#metricsFilter.querySelectorAll('input[type="checkbox"]:checked').forEach(checkbox => {
            selected.push(checkbox.value);
        });
        return selected;
    }

    #onFilterChange() {
        this.#tableData.forEach(({response, formatter}, table) => {
            this.#clearTable(table);
            this.#formatTimeSeriesResponseToTable(response, table, formatter);
        });
    }

    #clearTable(table) {
        const thead = table.querySelector('thead');
        const tbody = table.querySelector('tbody');
        if (thead) thead.remove();
        if (tbody) tbody.remove();
    }

    /**
     * @param url {string}
     * @param table {HTMLTableElement}
     * @param formatter {function(number)}
     */
    #fetchAnalytics(url, table, formatter = (v) => formatNumber(v)) {
        getAndHandle(ADMIN_URL_PREFIX + url, json => {
            const response = new MultipleTimeSeriesDto(json);
            this.#tableData.set(table, {response, formatter});
            this.#formatTimeSeriesResponseToTable(response, table, formatter);
        });
    }

    /**
     *
     * @param response {MultipleTimeSeriesDto}
     * @param table {HTMLTableElement}
     * @param formatter {function(number)}
     */
    #formatTimeSeriesResponseToTable(response, table, formatter) {
        const selectedMetrics = this.#getSelectedMetrics();
        const allNames = response.getAllNames();

        let thead = document.createElement('thead');
        let tbody = document.createElement('tbody');
        let header = document.createElement('tr');

        let th = document.createElement('th');
        th.innerText = 'period';
        header.append(th);

        // Build header with only selected metrics
        allNames.forEach(name => {
            if (selectedMetrics.includes(name)) {
                let th = document.createElement('th');
                th.innerText = name;
                th.className = 'value-cell-larger';
                header.append(th);
            }
        });
        thead.append(header);
        table.append(thead, tbody);

        response.getAllPeriods().reverse().forEach(period => {
            let tr = tbody.insertRow();
            tr.insertCell().innerText = period;
            const values = response.valuesForPeriod(period);
            allNames.forEach((name, index) => {
                if (selectedMetrics.includes(name)) {
                    tr.insertCell().innerText = formatter(values[index]);
                }
            });
        });
    }

    #fetchAnalysisPerUser() {
        let table = this.#analysisPerUserTable;
        getAndHandle(ADMIN_URL_PREFIX + '/analysis-per-user', json => {
            emptyTable(table);
            let tbody = table.getElementsByTagName('tbody')[0];

            json.entries.forEach(entry => {
                let tr = document.createElement('tr');
                let userCell = document.createElement('td');
                let countCell = document.createElement('td');
                let lastUpdated = document.createElement('td');

                tr.append(userCell, countCell, lastUpdated);
                tbody.append(tr);

                // content
                userCell.append(buildUserLinkDiv(entry.username));
                countCell.innerText = entry.count;
                lastUpdated.innerText = formatTimestampToDateTime(entry.lastUpdated);
            });
        });
    }

}

window.onload = () => new AdminAnalyticsPage();
