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

class StackedMonthlyMetricsBarChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param data {{categories: string[], series: {name: string, data: number[], color: string, projectedColor: string}[], projectedIndex: number}}
     */
    constructor(containerId, data) {
        super(containerId);

        if (data.categories.length > 0) {
            // Create series with colors
            const series = data.series.map(s => ({
                name: s.name,
                data: s.data
            }));

            const projectedIndex = data.projectedIndex;

            this.chartOptions = {
                series: series,
                chart: {
                    height: 300,
                    type: 'bar',
                    stacked: true,
                    toolbar: {show: false},
                    animations: {enabled: false}
                },
                plotOptions: {
                    bar: {
                        columnWidth: '85%',
                        dataLabels: {
                            total: {
                                enabled: true,
                                offsetY: -5,
                                style: {
                                    fontSize: '13px',
                                    fontWeight: 600
                                },
                                formatter: function(val) {
                                    return val.toFixed(0);
                                }
                            }
                        }
                    }
                },
                dataLabels: {
                    enabled: false
                },
                colors: data.series.map(s => {
                    return function({dataPointIndex}) {
                        if (dataPointIndex === projectedIndex) {
                            return s.projectedColor;
                        }
                        return s.color;
                    };
                }),
                legend: {
                    position: 'top',
                    horizontalAlign: 'left'
                },
                xaxis: {
                    categories: data.categories,
                    labels: {
                        rotate: -45,
                        rotateAlways: true
                    }
                },
                yaxis: {
                    labels: {
                        formatter: (val) => val.toFixed(0)
                    }
                },
                tooltip: {
                    y: {
                        formatter: (val, {seriesIndex, dataPointIndex}) => {
                            if (dataPointIndex === projectedIndex) {
                                return `${val.toFixed(0)} (projected)`;
                            }
                            return val.toFixed(0);
                        }
                    }
                }
            };

            this.enableRender();
        }
    }

}
