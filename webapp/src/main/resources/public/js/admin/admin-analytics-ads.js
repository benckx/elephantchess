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

class AdminAnalyticsChartsPage extends BasePage {

    /**
     * @type {MultipleTimeSeriesDto}
     */
    #monthlyData = null;

    /**
     * @type {HTMLSelectElement}
     */
    #timeRangeSelector;

    /**
     * @type {string[]}
     */
    #metrics = ['PvP > 3', 'PvB > 3', 'puzzles', 'new guests', 'new users'];

    constructor() {
        super();

        this.#timeRangeSelector = document.getElementById('time-range-selector');
        this.#timeRangeSelector.addEventListener('change', () => this.#onTimeRangeChange());

        this.#fetchMonthlyData();
    }

    #fetchMonthlyData() {
        getAndHandle(`${ADMIN_URL_PREFIX}/monthly-stats`, json => {
            this.#monthlyData = new MultipleTimeSeriesDto(json);
            this.#renderAllCharts();
        });
    }

    #getSelectedTimeRange() {
        return this.#timeRangeSelector.value;
    }

    #onTimeRangeChange() {
        if (this.#monthlyData) {
            this.#renderAllCharts();
        }
    }

    /**
     * @param metricName {string}
     * @returns {{categories: string[], adsCost: number[], metric: number[]}}
     */
    #extractChartData(metricName) {
        // API returns both periods and values in the SAME order (oldest first)
        // periods[0] = "2023-03" and values[0] = value for 2023-03
        // So we use direct index matching

        const periods = this.#monthlyData.getAllPeriods();
        const names = this.#monthlyData.getAllNames();
        const adsCostIndex = names.indexOf('ads cost');
        const metricIndex = names.indexOf(metricName);

        // Find the last index where ads cost > 0 (to exclude trailing months without ads data)
        let lastAdsCostIndex = periods.length - 1;
        while (lastAdsCostIndex >= 0) {
            const values = this.#monthlyData.getValuesAtIndex(lastAdsCostIndex);
            if (values[adsCostIndex] > 0) {
                break;
            }
            lastAdsCostIndex--;
        }

        // Determine start index based on time range selection
        const timeRange = this.#getSelectedTimeRange();
        let startIndex = 0;
        if (timeRange !== 'all') {
            const monthsToShow = parseInt(timeRange);
            startIndex = Math.max(0, lastAdsCostIndex - monthsToShow + 1);
        }

        const adsCostData = [];
        const metricData = [];
        const categories = [];

        // Direct index matching - periods[i] corresponds to values[i]
        // Only go up to lastAdsCostIndex to exclude trailing months without ads data
        for (let i = startIndex; i <= lastAdsCostIndex; i++) {
            const period = periods[i];
            const allValues = this.#monthlyData.getValuesAtIndex(i);
            const adsCost = allValues[adsCostIndex];
            const metricValue = allValues[metricIndex];

            if (adsCost > 0 || metricValue > 0) {
                categories.push(period);
                adsCostData.push(adsCost);
                metricData.push(metricValue);
            }
        }

        return {
            categories: categories,
            adsCost: adsCostData,
            metric: metricData
        };
    }

    #renderAllCharts() {
        const container = document.getElementById('charts-container');
        container.innerHTML = ''; // Clear existing charts

        this.#metrics.forEach((metricName, index) => {
            // Create chart container
            const chartWrapper = document.createElement('div');
            chartWrapper.style.marginBottom = '40px';

            const title = document.createElement('h2');
            title.innerText = metricName;
            chartWrapper.appendChild(title);

            const hr = document.createElement('hr');
            chartWrapper.appendChild(hr);

            const chartDiv = document.createElement('div');
            chartDiv.id = `chart-${index}`;
            chartDiv.style.height = '400px';
            chartWrapper.appendChild(chartDiv);

            container.appendChild(chartWrapper);

            // Render chart using the widget
            const data = this.#extractChartData(metricName);
            const chart = new AdsCostVsMetricBarChart(`chart-${index}`, metricName, data);
            chart.render();
        });
    }


}

window.onload = () => new AdminAnalyticsChartsPage();
