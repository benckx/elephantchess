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

class DailyPageViewsChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param data {{entries: Array<{day: string, pageViews: number}>}}
     */
    constructor(containerId, data) {
        super(containerId);

        if (data && data.entries && data.entries.length > 0) {
            const categories = data.entries.map(entry => entry.day);
            const values = data.entries.map(entry => entry.pageViews);

            this.chartOptions = {
                series: [{
                    name: 'Page Views',
                    data: values
                }],
                chart: {
                    height: 300,
                    type: 'line',
                    toolbar: {show: false},
                    animations: {enabled: false},
                    zoom: {enabled: false}
                },
                stroke: {
                    curve: 'stepline',
                    width: 2
                },
                markers: {
                    size: 4
                },
                colors: ['#008FFB'],
                xaxis: {
                    categories: categories,
                    labels: {
                        rotate: -45,
                        rotateAlways: true
                    },
                    title: {
                        text: 'Day'
                    }
                },
                yaxis: {
                    min: 0,
                    labels: {
                        formatter: (val) => val.toFixed(0)
                    },
                    title: {
                        text: 'Page Views'
                    }
                },
                tooltip: {
                    y: {
                        formatter: (val) => val.toFixed(0) + ' views'
                    }
                }
            };

            this.enableRender();
        }
    }

}
