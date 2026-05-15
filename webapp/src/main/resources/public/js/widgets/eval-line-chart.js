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

function formatEvalTooltip(val) {
    if (val === null || val === undefined) {
        return '';
    }
    return (val > 0 ? '+' : '') + val.toFixed(1);
}

class EvalLineChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param nodes {MoveTreeNode[]}
     * @param analysisMap {Map<string, InfoLineResult>}
     * @param startFen {string}
     */
    constructor(containerId, nodes, analysisMap, startFen) {
        super(containerId);

        if (!document.getElementById(containerId)) {
            return;
        }

        const evalData = [];
        const categories = [];

        // Add eval of the starting position
        const startFenKey = resetFenFullMovesCount(startFen);
        const startInfoLine = analysisMap.get(startFenKey);
        if (startInfoLine != null && startInfoLine.eval != null) {
            evalData.push(parseFloat(startInfoLine.eval.toFixed(1)));
            categories.push('Start');
        }

        // Add eval for each played move
        nodes.forEach(node => {
            const infoLine = analysisMap.get(node.fenKey);
            if (infoLine != null && infoLine.eval != null) {
                evalData.push(parseFloat(infoLine.eval.toFixed(1)));
                const moveNum = node.fullMoveCount;
                const isRedMove = node.position % 2 === 0;
                categories.push(isRedMove ? `${moveNum}.` : `${moveNum}...`);
            }
        });

        if (evalData.length > 1) {
            // Split into two series: red for positive (Red advantage), dark for negative (Black advantage).
            // At each zero-crossing, the departing series adds a 0 at that index so the segment ends at
            // the axis rather than leaving an abrupt gap; the arriving series picks up its real value.
            const redData = [];
            const blackData = [];
            for (let i = 0; i < evalData.length; i++) {
                const v = evalData[i];
                const prev = i > 0 ? evalData[i - 1] : null;
                if (v >= 0) {
                    redData.push(v);
                    // Transition from negative to positive: cap the black segment at 0
                    blackData.push((prev !== null && prev <= 0) ? 0 : null);
                } else {
                    // Transition from positive to negative: cap the red segment at 0
                    redData.push((prev !== null && prev >= 0) ? 0 : null);
                    blackData.push(v);
                }
            }

            this.chartOptions = {
                series: [
                    {name: 'Red advantage', data: redData},
                    {name: 'Black advantage', data: blackData}
                ],
                chart: {
                    type: 'line',
                    height: 150,
                    toolbar: {show: false},
                    animations: {enabled: false},
                    background: 'transparent'
                },
                colors: ['#D32F2F', '#222222'],
                stroke: {
                    curve: 'smooth',
                    width: [2, 2]
                },
                dataLabels: {enabled: false},
                xaxis: {
                    categories: categories,
                    tickAmount: Math.min(8, categories.length - 1),
                    labels: {
                        style: {colors: '#aaaaaa', fontSize: '10px'},
                        rotate: 0
                    },
                    axisBorder: {color: '#555'},
                    axisTicks: {color: '#555'}
                },
                yaxis: {
                    min: -100,
                    max: 100,
                    tickAmount: 4,
                    labels: {
                        style: {colors: '#aaaaaa', fontSize: '10px'},
                        formatter: (val) => (val > 0 ? '+' : '') + Math.round(val)
                    }
                },
                grid: {
                    borderColor: '#444',
                    xaxis: {lines: {show: false}},
                    yaxis: {lines: {show: true}}
                },
                annotations: {
                    yaxis: [{
                        y: 0,
                        borderColor: '#888',
                        strokeDashArray: 3,
                        borderWidth: 1
                    }]
                },
                tooltip: {
                    theme: 'dark',
                    x: {show: true},
                    y: {formatter: formatEvalTooltip}
                },
                legend: {show: false},
                theme: {mode: 'dark'}
            };

            this.enableRender();
        }
    }

}
