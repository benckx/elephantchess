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

class AdminPageViewStatsPage extends BasePage {

    /**
     * @type {string[]}
     */
    #eventPaths = [
        '/',
        '/about',
        '/about/faq',
        '/about/changelog',
        '/global',
        '/database/search',
        '/browse/player-vs-player',
        '/browse/player-vs-bot',
        '/userdata/games',
        '/userdata/botgames',
        '/userdata/puzzles',
        '/userdata/analysis',
        '/user/settings',
    ];

    /**
     * @type {Map<string, MultipleTimeSeriesDto>}
     */
    #dataByPath = new Map();

    /**
     * @type {MultipleTimeSeriesDto|null}
     */
    #gadData = null;

    /**
     * @type {MultipleTimeSeriesDto|null}
     */
    #userProfileData = null;

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
        this.#fetchAllData();
    }

    #fetchAllData() {
        this.#pendingRequests = this.#eventPaths.length + 2; // +1 for GAD data, +1 for user profile data
        this.#totalRequests = this.#pendingRequests;
        this.#updateLoadingDisplay();

        // Fetch GAD data first
        this.#fetchGadData();
        this.#fetchUserProfileData();

        this.#eventPaths.forEach(eventPath => {
            this.#fetchPageViewStats(eventPath);
        });
    }

    #fetchGadData() {
        const url = `${ADMIN_URL_PREFIX}/page-view-stats-gad`;

        getAndHandle(url, json => {
            this.#gadData = new MultipleTimeSeriesDto(json);
            this.#pendingRequests--;
            this.#updateLoadingDisplay();
            this.#renderChartsIfReady();
        });
    }

    #fetchUserProfileData() {
        const url = `${ADMIN_URL_PREFIX}/page-view-stats-user-profiles`;

        getAndHandle(url, json => {
            this.#userProfileData = new MultipleTimeSeriesDto(json);
            this.#pendingRequests--;
            this.#updateLoadingDisplay();
            this.#renderChartsIfReady();
        });
    }

    /**
     * @param eventPath {string}
     */
    #fetchPageViewStats(eventPath) {
        const encodedPath = encodeURIComponent(eventPath);
        const url = `${ADMIN_URL_PREFIX}/page-view-stats-by-event-path?path=${encodedPath}`;

        getAndHandle(url, json => {
            this.#dataByPath.set(eventPath, new MultipleTimeSeriesDto(json));
            this.#pendingRequests--;
            this.#updateLoadingDisplay();
            this.#renderChartsIfReady();
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

        // Hide loading status when complete
        if (loadingStatus && this.#pendingRequests === 0) {
            loadingStatus.style.display = 'none';
        }
    }

    #renderChartsIfReady() {
        if (this.#pendingRequests === 0) {
            this.#renderAllCharts();
        }
    }

    #renderAllCharts() {
        const container = document.getElementById('charts-container');

        // Clear container
        container.innerHTML = '';

        // Render GAD chart first at the top
        if (this.#gadData && this.#gadData.getAllPeriods().length > 0) {
            const gadChartWrapper = document.createElement('div');
            gadChartWrapper.style.marginBottom = '40px';

            const gadChartDiv = document.createElement('div');
            gadChartDiv.id = 'chart-gad';
            gadChartDiv.style.height = '400px';
            gadChartWrapper.appendChild(gadChartDiv);

            container.appendChild(gadChartWrapper);

            // Render GAD chart
            new PageViewStatsLineChart('chart-gad', 'Total page views', this.#gadData).render();
        }

        if (this.#userProfileData && this.#userProfileData.getAllPeriods().length > 0) {
            const userProfileChartWrapper = document.createElement('div');
            userProfileChartWrapper.style.marginBottom = '40px';

            const userProfileChartDiv = document.createElement('div');
            userProfileChartDiv.id = 'chart-user-profiles';
            userProfileChartDiv.style.height = '400px';
            userProfileChartWrapper.appendChild(userProfileChartDiv);

            container.appendChild(userProfileChartWrapper);

            new PageViewStatsLineChart(
                'chart-user-profiles',
                'Total user profile views',
                this.#userProfileData
            ).render();
        }

        this.#eventPaths.forEach((eventPath, index) => {
            const data = this.#dataByPath.get(eventPath);

            if (!data || data.getAllPeriods().length === 0) {
                // Show message if no data
                const noDataDiv = document.createElement('div');
                noDataDiv.style.marginBottom = '40px';

                const message = document.createElement('p');
                message.innerText = 'No data available for this path';
                message.style.fontStyle = 'italic';
                message.style.color = '#666';
                noDataDiv.appendChild(message);

                container.appendChild(noDataDiv);
                return;
            }

            // Create chart wrapper
            const chartWrapper = document.createElement('div');
            chartWrapper.style.marginBottom = '40px';

            const chartDiv = document.createElement('div');
            chartDiv.id = `chart-${index}`;
            chartDiv.style.height = '400px';
            chartWrapper.appendChild(chartDiv);

            container.appendChild(chartWrapper);

            // Render chart
            new PageViewStatsLineChart(`chart-${index}`, `Page views "${eventPath}"`, data).render();
        });
    }

}

window.onload = () => new AdminPageViewStatsPage();
