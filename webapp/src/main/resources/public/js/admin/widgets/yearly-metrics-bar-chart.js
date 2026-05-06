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

class YearlyMetricsBarChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param data {{categories: string[], series: {name: string, data: number[]}[]}}
     */
    constructor(containerId, data) {
        super(containerId);

        if (data.categories.length > 0) {
            this.chartOptions = {
                series: data.series,
                chart: {
                    height: 350,
                    type: 'bar',
                    toolbar: {show: false},
                    animations: {enabled: false}
                },
                plotOptions: {
                    bar: {
                        columnWidth: '70%'
                    }
                },
                dataLabels: {
                    enabled: false
                },
                colors: ['#008FFB', '#001f9d', '#00E396', '#FEB019'],
                xaxis: {
                    categories: data.categories
                },
                yaxis: {
                    labels: {
                        formatter: (val) => val.toFixed(0)
                    }
                },
                legend: {
                    position: 'top'
                },
                tooltip: {
                    shared: true,
                    intersect: false
                }
            };

            this.enableRender();
        }
    }

}

