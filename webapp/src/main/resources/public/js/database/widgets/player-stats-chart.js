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

class PlayerStatsChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param stats {{player: Object, withDuplicates: Object|null}}
     */
    constructor(containerId, stats) {
        super(containerId);

        const categories = [];
        const winsData = [];
        const drawsData = [];
        const lossesData = [];

        // add player stats (without duplicates)
        categories.push('Playing as Red');
        winsData.push(stats.player.redWins);
        drawsData.push(stats.player.redDraws);
        lossesData.push(stats.player.redLosses);

        categories.push('Playing as Black');
        winsData.push(stats.player.blackWins);
        drawsData.push(stats.player.blackDraws);
        lossesData.push(stats.player.blackLosses);

        let width = 700;
        let height = 350;

        // add stats with duplicates if available
        if (stats.withDuplicates) {
            categories.push('Playing as Red (*)');
            winsData.push(stats.withDuplicates.redWins);
            drawsData.push(stats.withDuplicates.redDraws);
            lossesData.push(stats.withDuplicates.redLosses);

            categories.push('Playing as Black (*)');
            winsData.push(stats.withDuplicates.blackWins);
            drawsData.push(stats.withDuplicates.blackDraws);
            lossesData.push(stats.withDuplicates.blackLosses);

            width = 850;
            height = 400;
        }

        this.chartOptions = {
            series: [
                {
                    name: 'Wins',
                    data: winsData
                },
                {
                    name: 'Draws',
                    data: drawsData
                },
                {
                    name: 'Losses',
                    data: lossesData
                }
            ],
            chart: {
                height: height,
                width: width,
                type: 'bar',
                toolbar: {show: false},
                animations: {enabled: false}
            },
            plotOptions: {
                bar: {
                    horizontal: false,
                    columnWidth: '75%',
                    dataLabels: {
                        position: 'top'
                    }
                }
            },
            dataLabels: {
                enabled: true,
                offsetY: -20,
                style: {
                    fontSize: '12px',
                    colors: ["#304758"]
                }
            },
            colors: ['#20df56', '#f5c362', '#ff4545'],
            xaxis: {
                categories: categories,
                labels: {
                    style: {
                        fontSize: '12px'
                    }
                }
            },
            yaxis: {
                labels: {
                    formatter: (val) => val.toFixed(0)
                }
            },
            legend: {
                show: true,
                position: 'bottom',
                horizontalAlign: 'center',
                markers: {
                    strokeWidth: 0
                }
            },
            tooltip: {
                shared: true,
                intersect: false,
                y: {
                    formatter: (val) => val.toFixed(0)
                }
            }
        };

        this.enableRender();
    }

}
