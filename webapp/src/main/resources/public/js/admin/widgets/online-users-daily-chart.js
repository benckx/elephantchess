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

class OnlineUsersDailyChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param data {{entries: Array<{day: string, minTotal: number, maxTotal: number, avgTotal: number}>}}
     */
    constructor(containerId, data) {
        super(containerId);

        if (data && data.entries && data.entries.length > 0) {
            const categories = data.entries.map(entry => entry.day);
            const maxData = data.entries.map(entry => entry.maxTotal);
            const avgData = data.entries.map(entry => entry.avgTotal);

            this.chartOptions = {
                series: [{
                    name: 'Avg Users',
                    data: avgData
                }, {
                    name: 'Max Users',
                    data: maxData
                }],
                chart: {
                    height: 400,
                    type: 'line',
                    toolbar: {show: false},
                    animations: {enabled: false}
                },
                stroke: {
                    curve: 'stepline',
                    width: 2
                },
                markers: {
                    size: 4,
                    hover: {
                        size: 6
                    }
                },
                dataLabels: {
                    enabled: false
                },
                colors: ['#7FC4FF', '#008FFB'],
                legend: {
                    show: false
                },
                xaxis: {
                    categories: categories,
                    title: {
                        text: 'Date'
                    },
                    labels: {
                        rotate: -45,
                        rotateAlways: true
                    }
                },
                yaxis: {
                    min: 0,
                    labels: {
                        formatter: (val) => val.toFixed(0)
                    }
                },
                tooltip: {
                    y: {
                        formatter: (val) => val.toFixed(0) + ' users'
                    }
                }
            };

            this.enableRender();
        }
    }

}


