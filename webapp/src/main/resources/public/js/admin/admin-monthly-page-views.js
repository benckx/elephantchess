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
        '/database/events',
        '/database/players',
        '/browse/player-vs-player',
        '/browse/player-vs-bot',
        '/userdata/games',
        '/userdata/botgames',
        '/userdata/puzzles',
        '/userdata/analysis',
        '/analysis',
        '/how-to-play-xiangqi',
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
    #databaseGameData = null;

    /**
     * @type {MultipleTimeSeriesDto|null}
     */
    #userOwnProfileData = null;

    /**
     * @type {MultipleTimeSeriesDto|null}
     */
    #userOtherProfileData = null;

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
        this.#pendingRequests = this.#eventPaths.length + 4; // +1 GAD, +1 database games, +2 own/other profile
        this.#totalRequests = this.#pendingRequests;
        this.#updateLoadingDisplay();

        // Fetch GAD data first
        this.#fetchGadData();
        this.#fetchDatabaseGameData();
        this.#fetchOwnUserProfileData();
        this.#fetchOtherUserProfileData();

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

    #fetchDatabaseGameData() {
        const url = `${ADMIN_URL_PREFIX}/page-view-stats-database-games`;

        getAndHandle(url, json => {
            this.#databaseGameData = new MultipleTimeSeriesDto(json);
            this.#pendingRequests--;
            this.#updateLoadingDisplay();
            this.#renderChartsIfReady();
        });
    }

    #fetchOwnUserProfileData() {
        const url = `${ADMIN_URL_PREFIX}/page-view-stats-user-profiles-own`;

        getAndHandle(url, json => {
            this.#userOwnProfileData = new MultipleTimeSeriesDto(json);
            this.#pendingRequests--;
            this.#updateLoadingDisplay();
            this.#renderChartsIfReady();
        });
    }

    #fetchOtherUserProfileData() {
        const url = `${ADMIN_URL_PREFIX}/page-view-stats-user-profiles-other`;

        getAndHandle(url, json => {
            this.#userOtherProfileData = new MultipleTimeSeriesDto(json);
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

        if (this.#databaseGameData && this.#databaseGameData.getAllPeriods().length > 0) {
            const databaseGameChartWrapper = document.createElement('div');
            databaseGameChartWrapper.style.marginBottom = '40px';

            const databaseGameChartDiv = document.createElement('div');
            databaseGameChartDiv.id = 'chart-database-games';
            databaseGameChartDiv.style.height = '400px';
            databaseGameChartWrapper.appendChild(databaseGameChartDiv);

            container.appendChild(databaseGameChartWrapper);

            new PageViewStatsLineChart(
                'chart-database-games',
                'Database game pages "/database/game"',
                this.#databaseGameData
            ).render();
        }

        if (this.#userOwnProfileData && this.#userOwnProfileData.getAllPeriods().length > 0) {
            const userProfileChartWrapper = document.createElement('div');
            userProfileChartWrapper.style.marginBottom = '40px';

            const userProfileChartDiv = document.createElement('div');
            userProfileChartDiv.id = 'chart-user-own-profiles';
            userProfileChartDiv.style.height = '400px';
            userProfileChartWrapper.appendChild(userProfileChartDiv);

            container.appendChild(userProfileChartWrapper);

            new PageViewStatsLineChart(
                'chart-user-own-profiles',
                'Users looking at their own profile',
                this.#userOwnProfileData
            ).render();
        }

        if (this.#userOtherProfileData && this.#userOtherProfileData.getAllPeriods().length > 0) {
            const userProfileChartWrapper = document.createElement('div');
            userProfileChartWrapper.style.marginBottom = '40px';

            const userProfileChartDiv = document.createElement('div');
            userProfileChartDiv.id = 'chart-user-other-profiles';
            userProfileChartDiv.style.height = '400px';
            userProfileChartWrapper.appendChild(userProfileChartDiv);

            container.appendChild(userProfileChartWrapper);

            new PageViewStatsLineChart(
                'chart-user-other-profiles',
                'Users looking at other users\' profiles',
                this.#userOtherProfileData
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
