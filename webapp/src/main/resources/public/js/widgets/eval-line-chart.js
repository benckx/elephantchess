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

/**
 * Line chart showing the engine evaluation history over the course of a game.
 * Positive values indicate a Red advantage, negative values a Black advantage.
 */
class EvalLineChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param nodes {MoveTreeNode[]}
     * @param analysisMap {Map<string, InfoLineResult>}
     * @param startFen {string}
     * @param onClickNode {function|null} optional callback invoked when a data point is clicked; receives the
     *        corresponding {@link MoveTreeNode}, or null when the 'Start' position is clicked
     */
    constructor(containerId, nodes, analysisMap, startFen, onClickNode = null) {
        super(containerId);

        if (!document.getElementById(containerId)) {
            return;
        }

        const MAX_CHART_POINTS = 220;
        let evalData = [];
        let categories = [];
        let nodesForDataPoints = [];

        // Add eval of the starting position
        const startFenKey = resetFenFullMovesCount(startFen);
        const startInfoLine = analysisMap.get(startFenKey);
        if (startInfoLine != null && startInfoLine.eval != null) {
            evalData.push(parseFloat(startInfoLine.eval.toFixed(1)));
            categories.push('Start');
            nodesForDataPoints.push(null);
        }

        // Add eval for each played move
        nodes.forEach(node => {
            const infoLine = analysisMap.get(node.fenKey);
            if (infoLine != null && infoLine.eval != null) {
                evalData.push(parseFloat(infoLine.eval.toFixed(1)));
                const moveNum = node.fullMoveCount;
                const isRedMove = node.position % 2 === 0;
                categories.push(`${moveNum} (${isRedMove ? 'r' : 'b'})`);
                nodesForDataPoints.push(node);
            }
        });

        if (evalData.length > MAX_CHART_POINTS) {
            const sampledEvalData = [];
            const sampledCategories = [];
            const sampledNodesForDataPoints = [];
            const maxIndex = evalData.length - 1;
            const intervals = Math.max(1, MAX_CHART_POINTS - 1);

            for (let i = 0; i < MAX_CHART_POINTS; i++) {
                const sampledIndex = Math.floor((i * maxIndex) / intervals);
                sampledEvalData.push(evalData[sampledIndex]);
                sampledCategories.push(categories[sampledIndex]);
                sampledNodesForDataPoints.push(nodesForDataPoints[sampledIndex]);
            }

            evalData = sampledEvalData;
            categories = sampledCategories;
            nodesForDataPoints = sampledNodesForDataPoints;
        }

        if (onClickNode != null) {
            document.getElementById(containerId).style.cursor = 'pointer';
        }

        if (evalData.length > 1) {
            this.chartOptions = {
                series: [
                    {name: 'Advantage', data: evalData}
                ],
                chart: {
                    type: 'line',
                    height: 150,
                    toolbar: {show: false},
                    animations: {enabled: false},
                    background: 'transparent',
                    ...(onClickNode != null ? {
                        events: {
                            dataPointSelection: (event, chartContext, config) => {
                                console.log('event: ' + event);
                                console.log('config: ' + config);
                                onClickNode(nodesForDataPoints[config.dataPointIndex]);
                            }
                        }
                    } : {})
                },
                colors: ['#022e7d'],
                stroke: {
                    curve: 'smooth',
                    width: [2]
                },
                dataLabels: {enabled: false},
                xaxis: {
                    categories: categories,
                    tickAmount: Math.min(8, categories.length - 1),
                    tooltip: {enabled: false},
                    labels: {
                        style: {colors: '#555555', fontSize: '10px'},
                        rotate: 0,
                        formatter: (val) => (typeof val === 'string' && val.endsWith('(b)')) ? '' : (typeof val === 'string' ? val.replace('(r)', '') : val)
                    },
                    axisBorder: {color: '#888'},
                    axisTicks: {color: '#888'}
                },
                yaxis: {
                    min: -100,
                    max: 100,
                    tickAmount: 4,
                    labels: {
                        style: {colors: '#555555', fontSize: '10px'},
                        formatter: (val) => (val > 0 ? '+' : '') + Math.round(val)
                    }
                },
                grid: {
                    borderColor: '#888',
                    xaxis: {lines: {show: false}},
                    yaxis: {lines: {show: true}}
                },
                annotations: {
                    yaxis: [
                        {
                            y: 0,
                            y2: 100,
                            fillColor: '#D32F2F',
                            opacity: 0.08,
                            borderColor: 'transparent'
                        },
                        {
                            y: -100,
                            y2: 0,
                            fillColor: '#222222',
                            opacity: 0.25,
                            borderColor: 'transparent'
                        },
                        {
                            y: 0,
                            borderColor: '#555',
                            strokeDashArray: 3,
                            borderWidth: 1
                        }
                    ]
                },
                tooltip: {
                    theme: 'dark',
                    // Custom tooltip: display Black advantage as a positive value (e.g. "+0.9" instead of "-0.9").
                    custom: ({dataPointIndex}) => {
                        const cat = categories[dataPointIndex];
                        const val = evalData[dataPointIndex];
                        const isPositive = val >= 0;
                        const color = '#022e7d';
                        const label = isPositive ? 'Red advantage' : 'Black advantage';
                        const display = '+' + Math.abs(val).toFixed(1);
                        return '<div class="apexcharts-tooltip-title" style="font-size:12px;">' + cat + '</div>'
                            + '<div style="padding:4px 10px 6px 10px;font-size:12px;">'
                            + '<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:' + color + ';margin-right:6px;vertical-align:middle;"></span>'
                            + label + ':&nbsp;<strong>' + display + '</strong>'
                            + '</div>';
                    }
                },
                legend: {show: false}
            };

            this.enableRender();
        }
    }

}
