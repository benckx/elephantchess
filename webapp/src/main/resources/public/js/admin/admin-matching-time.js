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
 * The per-month query is expensive, so instead of one blocking request the page first fetches the
 * list of available months and then loads the stats one month at a time, updating the progress
 * counter as each month arrives.
 */
class AdminMatchingTimePage extends BasePage {

    /**
     * Stats keyed by period (YYYY-MM).
     * @type {Object<string, Object>}
     */
    #statsByPeriod = {};

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
                this.#renderCharts();
                return;
            }

            this.#periods.forEach(period => this.#fetchMonth(period));
        });
    }

    #fetchMonth(period) {
        const url = `${ADMIN_URL_PREFIX}/matching-time-stats?period=${encodeURIComponent(period)}`;
        getAndHandle(url, json => {
            this.#statsByPeriod[period] = json;
            this.#pendingRequests--;
            this.#updateLoadingDisplay();
            if (this.#pendingRequests === 0) {
                this.#renderCharts();
            }
        });
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

    #renderCharts() {
        const container = document.getElementById('charts-container');

        this.#renderChart(container, 'lobbyJoinModes', 'matching time (lobby/matchmaking PvP > 3)', 'chart-matching-time-lobby');
        this.#renderChart(container, 'linkJoinSource', 'matching time (LINK PvP > 3)', 'chart-matching-time-link');
    }

    /**
     * @param container {HTMLElement}
     * @param statKey {string} - 'lobbyJoinModes' or 'linkJoinSource'
     * @param titleText {string}
     * @param chartId {string}
     */
    #renderChart(container, statKey, titleText, chartId) {
        const chartWrapper = document.createElement('div');
        chartWrapper.style.marginBottom = '40px';

        const title = document.createElement('h2');
        title.innerText = titleText;
        chartWrapper.appendChild(title);

        const hr = document.createElement('hr');
        chartWrapper.appendChild(hr);

        const chartDiv = document.createElement('div');
        chartDiv.id = chartId;
        chartDiv.style.height = '400px';
        chartWrapper.appendChild(chartDiv);

        container.appendChild(chartWrapper);

        const entries = this.#periods.map(period => {
            const stat = (this.#statsByPeriod[period] || {})[statKey] || {};
            return {
                period: period,
                minSeconds: stat.minSeconds ?? null,
                avgSeconds: stat.avgSeconds ?? null,
                maxSeconds: stat.maxSeconds ?? null
            };
        });

        new MatchingTimeChart(chartId, {entries: entries}).render();
    }

}

window.onload = () => new AdminMatchingTimePage();
