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

class AdminMonthlyMetricsPage extends BasePage {

    /**
     * @type {MultipleTimeSeriesDto}
     */
    #monthlyData = null;

    /**
     * @type {Object|null}
     */
    #onlineUsersData = null;

    /**
     * @type {Object|null}
     */
    #pvpJoinSourceData = null;

    /**
     * @type {string[]}
     */
    #metrics = ['PvP > 3', 'PvB > 3', 'puzzles', 'new users', 'new guests'];

    /**
     * @type {string[]}
     */
    #yearlyMetrics = ['PvP > 3', 'PvB > 3', 'puzzles', 'new guests'];

    constructor() {
        super();
        this.#fetchMonthlyData();
        this.#fetchOnlineUsersData();
        this.#fetchPvpJoinSourceData();
    }

    #fetchMonthlyData() {
        getAndHandle(`${ADMIN_URL_PREFIX}/monthly-stats`, json => {
            this.#monthlyData = new MultipleTimeSeriesDto(json);
            this.#renderChartsIfReady();
        });
    }

    #fetchOnlineUsersData() {
        getAndHandle(`${ADMIN_URL_PREFIX}/online-users-stats-by-month?months=12`, json => {
            this.#onlineUsersData = json;
            this.#renderChartsIfReady();
        });
    }

    #fetchPvpJoinSourceData() {
        getAndHandle(`${ADMIN_URL_PREFIX}/pvp-join-source-stats`, json => {
            this.#pvpJoinSourceData = json;
            this.#renderChartsIfReady();
        });
    }

    #renderChartsIfReady() {
        if (this.#monthlyData && this.#onlineUsersData && this.#pvpJoinSourceData) {
            this.#renderAllCharts();
        }
    }

    /**
     * Returns the current month in YYYY-MM format
     * @returns {string}
     */
    #getCurrentMonthPeriod() {
        const now = new Date();
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        return `${year}-${month}`;
    }

    /**
     * Returns the number of days in the given month
     * @param period {string} - Format: YYYY-MM
     * @returns {number}
     */
    #getDaysInMonth(period) {
        const [year, month] = period.split('-').map(Number);
        return new Date(year, month, 0).getDate();
    }

    /**
     * Returns the current day of the month
     * @returns {number}
     */
    #getCurrentDayOfMonth() {
        return new Date().getDate();
    }

    /**
     * @param metricName {string}
     * @returns {{categories: string[], values: number[], projectedIndex: number}}
     */
    #extractLast12MonthsDataForMetric(metricName) {
        const periods = this.#monthlyData.getAllPeriods();
        const names = this.#monthlyData.getAllNames();
        const metricIndex = names.indexOf(metricName);

        // Get the last 12 months
        const last12Periods = periods.slice(-12);
        const values = [];
        const currentMonthPeriod = this.#getCurrentMonthPeriod();
        let projectedIndex = -1;

        last12Periods.forEach((period, index) => {
            const periodIndex = periods.indexOf(period);
            const allValues = this.#monthlyData.getValuesAtIndex(periodIndex);
            let value = allValues[metricIndex];

            // If this is the current month, apply linear projection
            if (period === currentMonthPeriod) {
                const daysInMonth = this.#getDaysInMonth(period);
                const currentDay = this.#getCurrentDayOfMonth();
                if (currentDay > 0 && currentDay < daysInMonth) {
                    value = Math.round(value * daysInMonth / currentDay);
                    projectedIndex = index;
                }
            }

            values.push(value);
        });

        return {
            categories: last12Periods,
            values: values,
            projectedIndex: projectedIndex
        };
    }

    #renderAllCharts() {
        const container = document.getElementById('charts-container');

        // render the 2 online users charts first
        this.#renderOnlineUsersCharts(container);

        // render combined PvP + PvB stacked chart
        this.#renderCombinedPvPPvBChart(container);

        // render PvP > 3 percentage chart
        this.#renderPvpPercentageChart(container);

        // render PvP > 3 by join source chart
        this.#renderPvpJoinSourceChart(container);

        // render PvP, PvB, puzzles, new users, new guests charts
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
            chartDiv.style.height = '300px';
            chartWrapper.appendChild(chartDiv);

            container.appendChild(chartWrapper);

            // Render chart
            const data = this.#extractLast12MonthsDataForMetric(metricName);
            new SingleMetricBarChart(`chart-${index}`, metricName, data).render();
        });

        // Render yearly chart
        this.#renderYearlyChart(container);
    }

    /**
     * Render online users average and maximum charts
     * @param container {HTMLElement}
     */
    #renderOnlineUsersCharts(container) {
        const avgData = this.#extractOnlineUsersData('avgTotal');
        const maxData = this.#extractOnlineUsersData('maxTotal');

        this.#renderOnlineUserChart(container, 'online users (avg)', 'chart-online-avg', avgData);
        this.#renderOnlineUserChart(container, 'online users (max)', 'chart-online-max', maxData);
    }

    /**
     * Render combined PvP + PvB stacked bar chart
     * @param container {HTMLElement}
     */
    #renderCombinedPvPPvBChart(container) {
        const pvpData = this.#extractLast12MonthsDataForMetric('PvP > 3');
        const pvbData = this.#extractLast12MonthsDataForMetric('PvB > 3');

        const chartWrapper = document.createElement('div');
        chartWrapper.style.marginBottom = '40px';

        const title = document.createElement('h2');
        title.innerText = 'PvP & PvB > 3';
        chartWrapper.appendChild(title);

        const hr = document.createElement('hr');
        chartWrapper.appendChild(hr);

        const chartDiv = document.createElement('div');
        chartDiv.id = 'chart-combined-pvp-pvb';
        chartDiv.style.height = '300px';
        chartWrapper.appendChild(chartDiv);

        container.appendChild(chartWrapper);

        const data = {
            categories: pvpData.categories,
            series: [
                {name: 'PvP > 3', data: pvpData.values, color: '#008FFB', projectedColor: '#7FC4FF'},
                {name: 'PvB > 3', data: pvbData.values, color: '#00E396', projectedColor: '#7FFFDB'}
            ],
            projectedIndex: pvpData.projectedIndex
        };

        new StackedMonthlyMetricsBarChart('chart-combined-pvp-pvb', data).render();
    }

    /**
     * Render PvP > 3 percentage chart (percentage of total PvP that reached > 3 moves)
     * @param container {HTMLElement}
     */
    #renderPvpPercentageChart(container) {
        if (!this.#pvpJoinSourceData) return;

        const chartWrapper = document.createElement('div');
        chartWrapper.style.marginBottom = '40px';

        const title = document.createElement('h2');
        title.innerText = 'PvP > 3 (% of total PvP)';
        chartWrapper.appendChild(title);

        const hr = document.createElement('hr');
        chartWrapper.appendChild(hr);

        const chartDiv = document.createElement('div');
        chartDiv.id = 'chart-pvp-percentage';
        chartDiv.style.height = '300px';
        chartWrapper.appendChild(chartDiv);

        container.appendChild(chartWrapper);

        // Get last 12 months of data
        const periods = this.#pvpJoinSourceData.periods;
        const values = this.#pvpJoinSourceData.percentageOver3;
        const last12Index = Math.max(0, periods.length - 12);
        const last12Periods = periods.slice(last12Index);
        const last12Values = values.slice(last12Index);

        const data = {
            categories: last12Periods,
            values: last12Values,
            projectedIndex: -1 // No projection for percentage
        };

        new SingleMetricBarChart('chart-pvp-percentage', 'PvP > 3 %', data, {
            yAxisFormatter: (val) => val.toFixed(1) + '%',
            tooltipFormatter: (val) => val.toFixed(1) + '%'
        }).render();
    }

    /**
     * Render PvP > 3 breakdown by join source (stacked bar chart)
     * @param container {HTMLElement}
     */
    #renderPvpJoinSourceChart(container) {
        if (!this.#pvpJoinSourceData || !this.#pvpJoinSourceData.joinSourceBreakdown) return;

        const chartWrapper = document.createElement('div');
        chartWrapper.style.marginBottom = '40px';

        const title = document.createElement('h2');
        title.innerText = 'PvP > 3 by Join Source';
        chartWrapper.appendChild(title);

        const hr = document.createElement('hr');
        chartWrapper.appendChild(hr);

        const chartDiv = document.createElement('div');
        chartDiv.id = 'chart-pvp-join-source';
        chartDiv.style.height = '300px';
        chartWrapper.appendChild(chartDiv);

        container.appendChild(chartWrapper);

        // Get last 12 months of data
        const periods = this.#pvpJoinSourceData.periods;
        const last12Index = Math.max(0, periods.length - 12);
        const last12Periods = periods.slice(last12Index);

        // Define colors for each join source
        const sourceColors = {
            'LOBBY': '#008FFB',
            'MATCHED': '#00E396',
            'LINK': '#FEB019',
            'DISCORD_NOTIFICATION': '#FF4560',
            'UNKNOWN': '#775DD0'
        };

        const series = this.#pvpJoinSourceData.joinSourceBreakdown.map(ts => ({
            name: ts.name,
            data: ts.values.slice(last12Index),
            color: sourceColors[ts.name] || '#999999',
            projectedColor: sourceColors[ts.name] ? sourceColors[ts.name] + '80' : '#99999980'
        }));

        const data = {
            categories: last12Periods,
            series: series,
            projectedIndex: -1
        };

        new StackedMonthlyMetricsBarChart('chart-pvp-join-source', data).render();
    }

    /**
     * Extract online users data (backend already returns requested number of months)
     * @param field {string} - 'avgTotal' or 'maxTotal'
     * @returns {{categories: string[], values: number[], projectedIndex: number}}
     */
    #extractOnlineUsersData(field) {
        if (!this.#onlineUsersData || !this.#onlineUsersData.entries) {
            return {categories: [], values: [], projectedIndex: -1};
        }

        // Backend already returns the requested number of months, no need to slice
        const entries = this.#onlineUsersData.entries;

        return {
            categories: entries.map(e => e.month),
            values: entries.map(e => e[field]),
            projectedIndex: -1  // No projection for aggregated values
        };
    }

    /**
     * Helper to render "online users" chart
     *
     * @param container {HTMLElement}
     * @param title {string}
     * @param chartId {string}
     * @param data {{categories: string[], values: number[], projectedIndex: number}}
     */
    #renderOnlineUserChart(container, title, chartId, data) {
        const chartWrapper = document.createElement('div');
        chartWrapper.style.marginBottom = '40px';

        const titleElement = document.createElement('h2');
        titleElement.innerText = title;
        chartWrapper.appendChild(titleElement);

        const hr = document.createElement('hr');
        chartWrapper.appendChild(hr);

        const chartDiv = document.createElement('div');
        chartDiv.id = chartId;
        chartDiv.style.height = '300px';
        chartWrapper.appendChild(chartDiv);

        container.appendChild(chartWrapper);

        new SingleMetricBarChart(chartId, title, data).render();
    }

    /**
     * @param container {HTMLElement}
     */
    #renderYearlyChart(container) {
        const chartWrapper = document.createElement('div');
        chartWrapper.style.marginBottom = '40px';

        const title = document.createElement('h2');
        title.innerText = 'yearly';
        chartWrapper.appendChild(title);

        const hr = document.createElement('hr');
        chartWrapper.appendChild(hr);

        const chartDiv = document.createElement('div');
        chartDiv.id = 'yearly-chart';
        chartDiv.style.height = '350px';
        chartWrapper.appendChild(chartDiv);

        container.appendChild(chartWrapper);

        const data = this.#extractYearlyData();
        const chart = new YearlyMetricsBarChart('yearly-chart', data);
        chart.render();
    }

    /**
     * Extracts yearly aggregated data for all metrics
     * @returns {{categories: string[], series: {name: string, data: number[]}[]}}
     */
    #extractYearlyData() {
        const periods = this.#monthlyData.getAllPeriods();
        const names = this.#monthlyData.getAllNames();

        // Group data by year
        const yearlyData = {};

        periods.forEach((period, periodIndex) => {
            const year = period.split('-')[0];
            if (!yearlyData[year]) {
                yearlyData[year] = {};
                this.#yearlyMetrics.forEach(metric => yearlyData[year][metric] = 0);
            }

            const allValues = this.#monthlyData.getValuesAtIndex(periodIndex);
            this.#yearlyMetrics.forEach(metricName => {
                const metricIndex = names.indexOf(metricName);
                yearlyData[year][metricName] += allValues[metricIndex];
            });
        });

        const years = Object.keys(yearlyData).sort();

        const series = this.#yearlyMetrics.map(metricName => ({
            name: metricName,
            data: years.map(year => yearlyData[year][metricName])
        }));

        return {
            categories: years,
            series: series
        };
    }

}

window.onload = () => new AdminMonthlyMetricsPage();
