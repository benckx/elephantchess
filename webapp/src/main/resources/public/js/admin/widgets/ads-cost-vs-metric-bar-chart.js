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

class AdsCostVsMetricBarChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param metricName {string}
     * @param data {{categories: string[], adsCost: number[], metric: number[]}}
     */
    constructor(containerId, metricName, data) {
        super(containerId);

        if (data.categories.length > 0) {
            this.chartOptions = {
                series: [
                    {
                        name: 'Ads Cost (€)',
                        data: data.adsCost
                    },
                    {
                        name: metricName,
                        data: data.metric
                    }
                ],
                chart: {
                    height: 400,
                    type: 'bar',
                    toolbar: {show: false},
                    animations: {enabled: false}
                },
                plotOptions: {
                    bar: {
                        columnWidth: '50%'
                    }
                },
                dataLabels: {
                    enabled: false
                },
                colors: ['#00cf5a', '#008FFB'],
                fill: {
                    opacity: [0.85, 1]
                },
                xaxis: {
                    categories: data.categories,
                    labels: {
                        rotate: -45,
                        rotateAlways: true
                    }
                },
                yaxis: [
                    {
                        title: {
                            text: 'Ads Cost (€)',
                        },
                        labels: {
                            formatter: (val) => val.toFixed(0)
                        }
                    },
                    {
                        opposite: true,
                        title: {
                            text: metricName,
                        },
                        labels: {
                            formatter: (val) => val.toFixed(0)
                        }
                    }
                ],
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

