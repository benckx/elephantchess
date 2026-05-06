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

class PuzzleRatingHistoryLineChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param historyDto {PuzzleRatingHistoryDto}
     */
    constructor(containerId, historyDto) {
        super(containerId);
        if (historyDto.isNotEmpty()) {
            let dataLabelsEnabled = historyDto.length <= NO_LABEL_DAYS;
            let markerSize = dataLabelsEnabled ? 1 : 0;

            this.chartOptions = {
                series: [
                    {name: "Max Rating", data: historyDto.seriesMax},
                    {name: "Last Rating", data: historyDto.seriesLast}
                ],
                chart: {
                    height: 350,
                    width: '100%',
                    type: 'line',
                    toolbar: {show: false},
                    animations: {enabled: false}
                },
                colors: ['#0054b4', '#52677d'],
                dataLabels: {enabled: dataLabelsEnabled},
                stroke: {curve: 'smooth'},
                grid: {
                    borderColor: '#e7e7e7',
                    row: {
                        // takes an array which will be repeated
                        colors: ['#f3f3f3', 'transparent'], opacity: 0.5
                    },
                },
                markers: {size: markerSize},
                xaxis: {
                    categories: historyDto.daysCategories,
                    labels: {
                        rotate: 0,
                        rotateAlways: false,
                        style: {
                            fontSize: '12px'
                        },
                        formatter: function(value) {
                            // Only show labels for the 1st of each month
                            if (value && value.endsWith('-01')) {
                                return value;
                            }
                            return '';
                        }
                    }
                },
                yaxis: {
                    min: historyDto.roundedMin,
                    max: historyDto.roundedMax
                },
                tooltip: {
                    x: {
                        formatter: function(value, {dataPointIndex}) {
                            return historyDto.history[dataPointIndex].date;
                        }
                    }
                },
                legend: {show: false}
            };

            this.enableRender();
        }
    }

}
