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

class OnlineUsersDayOfWeekChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param data {{entries: Array<{dayOfWeek: number, minTotal: number, maxTotal: number, avgTotal: number}>}}
     */
    constructor(containerId, data) {
        super(containerId);

        if (data && data.entries && data.entries.length > 0) {
            // ISO day numbering: 1=Monday, 2=Tuesday, ..., 7=Sunday
            const isoNames = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
            const categories = data.entries.map(entry => isoNames[entry.dayOfWeek]);
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
                    type: 'bar',
                    toolbar: {show: false},
                    animations: {enabled: false}
                },
                plotOptions: {
                    bar: {
                        horizontal: false,
                        columnWidth: '85%',
                        dataLabels: {
                            position: 'top'
                        }
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
                    categories: categories
                },
                yaxis: {
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

