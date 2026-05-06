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

class PageViewStatsLineChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param title {string}
     * @param data {MultipleTimeSeriesDto}
     */
    constructor(containerId, title, data) {
        super(containerId);

        if (data.getAllPeriods().length > 0) {
            const periods = data.getAllPeriods();
            const names = data.getAllNames();

            // Prepare series data (just actual values, no projection)
            const series = names.map(name => {
                const values = [];

                periods.forEach((period, index) => {
                    const allValues = data.getValuesAtIndex(index);
                    const nameIndex = names.indexOf(name);
                    values.push(allValues[nameIndex]);
                });

                return {
                    name: name,
                    data: values
                };
            });

            // Calculate totals per month for display
            const monthlyTotals = periods.map((period, index) => {
                const allValues = data.getValuesAtIndex(index);
                return allValues.reduce((sum, val) => sum + val, 0);
            });

            // Format periods as "MMM yyyy" for better display
            const formattedCategories = periods.map(period => {
                const [year, month] = period.split('-');
                const date = new Date(year, month - 1, 1);
                return date.toLocaleString('en-US', { month: 'short', year: 'numeric' });
            });

            this.chartOptions = {
                series: series,
                chart: {
                    type: 'bar',
                    stacked: true,
                    height: 400,
                    zoom: {
                        enabled: true,
                        type: 'x',
                        autoScaleYaxis: true
                    },
                    toolbar: {
                        autoSelected: 'zoom',
                        show: true
                    },
                    animations: {enabled: false}
                },
                plotOptions: {
                    bar: {
                        horizontal: false,
                        columnWidth: '85%',
                        dataLabels: {
                            total: {
                                enabled: true,
                                style: {
                                    fontSize: '13px',
                                    fontWeight: 900,
                                    color: '#373d3f'
                                },
                                formatter: function (val, opts) {
                                    return val.toFixed(0);
                                }
                            }
                        }
                    }
                },
                dataLabels: {
                    enabled: false
                },
                stroke: {
                    show: true,
                    width: 1,
                    colors: ['transparent']
                },
                legend: {
                    position: 'top',
                    horizontalAlign: 'left',
                    offsetY: 0,
                    markers: {
                        width: 12,
                        height: 12
                    }
                },
                colors: this.#generateBaseColors(names.length),
                xaxis: {
                    type: 'category',
                    categories: formattedCategories,
                    labels: {
                        rotate: -45,
                        rotateAlways: false
                    }
                },
                yaxis: {
                    labels: {
                        formatter: (val) => val.toFixed(0)
                    },
                    title: {
                        text: 'Page Views'
                    }
                },
                tooltip: {
                    shared: true,
                    intersect: false,
                    y: {
                        formatter: (val) => {
                            return val.toFixed(0) + ' views';
                        }
                    },
                    custom: function({ series, seriesIndex, dataPointIndex, w }) {
                        const categoryName = formattedCategories[dataPointIndex];
                        const total = monthlyTotals[dataPointIndex];

                        let tooltipHtml = '<div class="apexcharts-tooltip-custom" style="padding: 8px;">';
                        tooltipHtml += `<div style="font-weight: bold; margin-bottom: 8px;">${categoryName}</div>`;

                        // Show each series value
                        series.forEach((seriesData, idx) => {
                            const value = seriesData[dataPointIndex];
                            const seriesName = w.globals.seriesNames[idx];
                            const color = w.globals.colors[idx];
                            tooltipHtml += `<div style="margin: 4px 0;">`;
                            tooltipHtml += `<span style="display: inline-block; width: 12px; height: 12px; background-color: ${color}; margin-right: 8px; border-radius: 2px;"></span>`;
                            tooltipHtml += `<span style="font-weight: 500;">${seriesName}:</span> ${value.toFixed(0)} views`;
                            tooltipHtml += `</div>`;
                        });

                        // Show total
                        tooltipHtml += `<div style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #e0e0e0; font-weight: bold;">`;
                        tooltipHtml += `Total: ${total.toFixed(0)} views`;
                        tooltipHtml += `</div>`;

                        tooltipHtml += '</div>';
                        return tooltipHtml;
                    }
                },
                title: {
                    text: title,
                    align: 'center',
                    style: {
                        fontSize: '18px',
                        fontWeight: 'bold'
                    }
                }
            };

            this.enableRender();
        }
    }

    /**
     * Generate base colors for the chart series
     * @param count {number} - Number of base metrics
     * @returns {string[]}
     */
    #generateBaseColors(count) {
        const baseColors = [
            '#008FFB',
            '#00E396',
            '#FEB019',
            '#FF4560',
            '#775DD0',
            '#3F51B5',
            '#03A9F4',
            '#4CAF50',
            '#F9CE1D',
            '#FF9800'
        ];

        const colors = [];
        for (let i = 0; i < count; i++) {
            colors.push(baseColors[i % baseColors.length]);
        }
        return colors;
    }


}
