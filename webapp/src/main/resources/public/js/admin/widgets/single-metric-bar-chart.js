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

class SingleMetricBarChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param metricName {string}
     * @param data {{categories: string[], values: number[], projectedIndex: number}}
     * @param options {{yAxisFormatter?: function, tooltipFormatter?: function}}
     */
    constructor(containerId, metricName, data, options = {}) {
        super(containerId);

        if (data.categories.length > 0) {
            // Create colors array - use lighter color for projected bar
            const colors = data.values.map((_, index) =>
                index === data.projectedIndex ? '#7FC4FF' : '#008FFB'
            );

            // Use custom formatters if provided, otherwise default to integer formatting
            const yAxisFormatter = options.yAxisFormatter || ((val) => val.toFixed(0));
            const tooltipFormatter = options.tooltipFormatter || ((val) => val.toFixed(0));

            // Build yaxis configuration
            const yaxisConfig = {
                labels: {
                    formatter: yAxisFormatter
                }
            };

            this.chartOptions = {
                series: [{
                    name: metricName,
                    data: data.values
                }],
                chart: {
                    height: 300,
                    type: 'bar',
                    toolbar: {show: false},
                    animations: {enabled: false}
                },
                plotOptions: {
                    bar: {
                        columnWidth: '85%',
                        distributed: true
                    }
                },
                dataLabels: {
                    enabled: false
                },
                colors: colors,
                legend: {
                    show: false
                },
                xaxis: {
                    categories: data.categories,
                    labels: {
                        rotate: -45,
                        rotateAlways: true
                    }
                },
                yaxis: yaxisConfig,
                tooltip: {
                    y: {
                        formatter: (val, { dataPointIndex }) => {
                            if (dataPointIndex === data.projectedIndex) {
                                return `${tooltipFormatter(val)} (projected)`;
                            }
                            return tooltipFormatter(val);
                        }
                    }
                }
            };

            this.enableRender();
        }
    }

}
