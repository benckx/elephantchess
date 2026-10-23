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
 * Bar chart showing min / avg / max matching time (seconds) per month.
 */
class MatchingTimeChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param data {{entries: Array<{period: string, minSeconds: ?number, avgSeconds: ?number, maxSeconds: ?number}>}}
     */
    constructor(containerId, data) {
        super(containerId);

        if (data && data.entries && data.entries.length > 0) {
            const categories = data.entries.map(entry => entry.period);
            const minData = data.entries.map(entry => entry.minSeconds);
            const avgData = data.entries.map(entry => entry.avgSeconds);
            const maxData = data.entries.map(entry => entry.maxSeconds);

            this.chartOptions = {
                series: [{
                    name: 'min',
                    data: minData
                }, {
                    name: 'avg',
                    data: avgData
                }, {
                    name: 'max',
                    data: maxData
                }],
                chart: {
                    height: 400,
                    type: 'bar',
                    toolbar: {show: false},
                    animations: {enabled: false}
                },
                plotOptions: {
                    bar: {
                        horizontal: false,
                        columnWidth: '75%'
                    }
                },
                dataLabels: {
                    enabled: false
                },
                colors: ['#00E396', '#008FFB', '#FEB019'],
                legend: {
                    show: true
                },
                xaxis: {
                    categories: categories,
                    title: {
                        text: 'Month'
                    },
                    labels: {
                        rotate: -45,
                        rotateAlways: true
                    }
                },
                yaxis: {
                    min: 0,
                    labels: {
                        formatter: (val) => MatchingTimeChart.formatSeconds(val)
                    }
                },
                tooltip: {
                    y: {
                        formatter: (val) => MatchingTimeChart.formatSeconds(val)
                    }
                }
            };

            this.enableRender();
        }
    }

    /**
     * Format a duration in seconds as a human-readable string (e.g. "42s", "3m 7s").
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
        const minutes = Math.floor(total / 60);
        const remainingSeconds = total % 60;
        return `${minutes}m ${remainingSeconds}s`;
    }

}
