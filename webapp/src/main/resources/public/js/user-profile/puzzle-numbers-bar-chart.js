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

class PuzzleNumbersBarChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param historyDto {PuzzleNumbersHistoryDto}
     */
    constructor(containerId, historyDto) {
        super(containerId);
        if (historyDto.isNotEmpty()) {
            let heightPerDay = 60;
            if (historyDto.length === 1) {
                heightPerDay = 110;
            } else if (historyDto.length === 2) {
                heightPerDay = 90;
            } else if (historyDto.length <= 6) {
                heightPerDay = 70;
            }
            let height = historyDto.length * heightPerDay;

            this.chartOptions = {
                colors: ['#16aa04', '#8b8b8b', '#d20000'],
                series: [
                    {name: 'Solved', data: historyDto.seriesSolved},
                    {name: 'Skipped', data: historyDto.seriesSkipped},
                    {name: 'Failed', data: historyDto.seriesFailed}
                ],
                chart: {
                    type: 'bar',
                    height: height,
                    width: '100%',
                    stacked: true,
                    toolbar: {show: false},
                    animations: {enabled: false}
                },
                plotOptions: {
                    bar: {
                        horizontal: true,
                        dataLabels: {
                            total: {
                                enabled: true,
                                offsetX: 0,
                                style: {
                                    fontSize: '13px',
                                    fontWeight: 900
                                }
                            }
                        }
                    },
                },
                stroke: {width: 0},
                xaxis: {categories: historyDto.daysCategories},
                fill: {opacity: 1},
                legend: {show: false}
            };

            this.enableRender();
        }
    }

}
