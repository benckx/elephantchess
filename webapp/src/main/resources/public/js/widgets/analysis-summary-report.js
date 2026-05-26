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

let scheduleEvalChartRenderTimeout = null;
let evalLineChart = null;
const EVAL_LINE_CHART_RENDER_DEBOUNCE_MS = 120;

/**
 * Builds a click callback for the eval chart that navigates the given {@link MoveTreeWidget} to the clicked node.
 *
 * @param moveTreeWidget {MoveTreeWidget}
 * @returns {function}
 */
function buildEvalChartClickCallback(moveTreeWidget) {
    return (node) => {
        if (node != null) {
            moveTreeWidget.selectNodeById(node.nodeId);
        } else {
            moveTreeWidget.navigateToStart();
        }
    };
}

/**
 * @param nodes {MoveTreeNode[]}
 * @param analysisMap {Map<string, InfoLineResult>}
 * @param startFen {string}
 * @param onClickNode {function|null}
 */
function scheduleEvalChartRender(nodes, analysisMap, startFen, onClickNode = null) {
    if (scheduleEvalChartRenderTimeout != null) {
        clearTimeout(scheduleEvalChartRenderTimeout);
    }

    scheduleEvalChartRenderTimeout = setTimeout(() => {
        scheduleEvalChartRenderTimeout = null;
        const evalChartContainer = document.getElementById('eval-line-chart-container');
        if (evalChartContainer == null) {
            return;
        }

        evalChartContainer.innerHTML = '';
        if (evalLineChart != null) {
            evalLineChart.destroy();
        }
        evalLineChart = new EvalLineChart('eval-line-chart-container', nodes, analysisMap, startFen, onClickNode);
        evalLineChart.render();
    }, EVAL_LINE_CHART_RENDER_DEBOUNCE_MS);
}

/**
 * @param gameId {GameId}
 * @param nodes {MoveTreeNode[]}
 * @param startFen {string}
 * @param moveTreeWidget {MoveTreeWidget|null} optional widget on which annotation symbols (??, ?!, etc.) will be
 *        applied to the move history once analysis data is fetched
 */
function renderAnalysisSummaryReportGeneric(gameId, nodes, startFen = DEFAULT_START_FEN, moveTreeWidget = null) {
    if (startFen == null) {
        startFen = DEFAULT_START_FEN;
    }

    const onClickNode = moveTreeWidget != null ? buildEvalChartClickCallback(moveTreeWidget) : null;

    const client = new GameDataClient(gameId);
    client.fetchAnalysisStatus((analysisProgressStatus) => {
        if (analysisProgressStatus.status === AnalysisStatus.COMPLETED) {
            client.fetchAnalysisData((infoLineResults) => {
                client.fetchMetadata((gameMetadata) => {
                    const analysisMap = new Map();
                    infoLineResults.forEach((infoLineResult) => {
                        analysisMap.set(resetFenFullMovesCount(infoLineResult.fen), infoLineResult);
                    });

                    renderAnalysisSummaryReport(
                        nodes,
                        analysisMap,
                        startFen,
                        gameMetadata.redPlayerName,
                        gameMetadata.blackPlayerName,
                        gameMetadata.outcome,
                        onClickNode
                    );

                    if (moveTreeWidget != null) {
                        moveTreeWidget.applyAnnotationSymbolsFromCache(analysisMap);
                    }
                });
            });
        }
    });
}

/**
 * @param nodes {MoveTreeNode[]}
 * @param analysisMap {Map<string, InfoLineResult>}
 * @param startFen {string}
 * @param redPlayerName {string|null}
 * @param blackPlayerName {string|null}
 * @param outcome {string|null}
 * @param onClickNode {function|null}
 */
function renderAnalysisSummaryReport(
    nodes,
    analysisMap,
    startFen,
    redPlayerName,
    blackPlayerName,
    outcome,
    onClickNode = null
) {

    /**
     * @param nodes {Array<MoveTreeNode>}
     * @param analysisMap {Map<string, InfoLineResult>}
     * @returns {boolean}
     */
    function isEngineDataComplete(nodes, analysisMap) {
        function hasDataForActualMoves() {
            return nodes.map(node => node.fenKey).every(fenKey => analysisMap.has(fenKey));
        }

        function hasDataForBestEngineMoves() {
            return nodes.every(node => {
                if (node.hasPrevious()) {
                    const previousNodeData = analysisMap.get(node.previous.fenKey);
                    if (previousNodeData == null) {
                        return false;
                    }

                    if (previousNodeData.pv.length > 0) {
                        const bestMove = previousNodeData.pv[0];
                        const board = new Board();
                        board.loadFen(previousNodeData.fen);
                        board.registerMove(bestMove);
                        const resultingFen = resetFenFullMovesCount(board.outputFen());
                        if (!analysisMap.has(resultingFen)) {
                            return false;
                        }
                    }
                }

                return true;
            });
        }

        function hasDataForStartPosition() {
            return analysisMap.has(resetFenFullMovesCount(startFen));
        }

        return hasDataForActualMoves() &&
            hasDataForBestEngineMoves() &&
            hasDataForStartPosition();
    }

    /**
     * @param color {String}
     * @returns {Map<string, number>}
     */
    function countMoveAnnotationTypesByColor(color) {
        const counter = new Map();
        nodes.forEach((node, i) => {
            let previousNodeData;
            if (i === 0) {
                previousNodeData = analysisMap.get(startFen);
            } else {
                previousNodeData = analysisMap.get(node.previous.fenKey);
            }

            if (previousNodeData.colorToPlay === color) {
                const dataFromEngineBestMove = findAnalysisDataFromEngineBestMove(analysisMap, previousNodeData);
                if (dataFromEngineBestMove != null) {
                    const annotationValue = calculateAnnotationValue(dataFromEngineBestMove, analysisMap.get(node.fenKey));
                    if (annotationValue != null) {
                        counter.set(annotationValue, counter.get(annotationValue) + 1 || 1);
                    }
                }
            }
        });

        return counter;
    }

    function formatPlayerName(playerName) {
        let name = playerName;
        if (name.startsWith('guest #')) {
            name = name.split(' ')[1];
        }
        return cropText(name, 12);
    }

    const summaryBlock = document.getElementById('analysis-summary')

    if (isEngineDataComplete(nodes, analysisMap)) {
        const counterRed = countMoveAnnotationTypesByColor(Color.RED);
        const counterBlack = countMoveAnnotationTypesByColor(Color.BLACK);

        for (let symbolType of moveAnnotationSymbolTypesArray) {
            const rowId = `analysis-summary-${symbolType.toLocaleLowerCase()}-row`;
            const row = document.getElementById(rowId);
            row.cells.item(1).innerText = (counterRed.get(symbolType) || 0).toString()
            row.cells.item(3).innerText = (counterBlack.get(symbolType) || 0).toString()
        }

        scheduleEvalChartRender(nodes, analysisMap, startFen, onClickNode);

        if (redPlayerName != null) {
            document
                .getElementById('analysis-summary-red-player-name')
                .innerText = formatPlayerName(redPlayerName);
        }
        if (blackPlayerName != null) {
            document
                .getElementById('analysis-summary-black-player-name')
                .innerText = formatPlayerName(blackPlayerName);
        }

        if (outcome != null) {
            switch (outcome) {
                case Outcome.RED_WINS:
                    document.getElementById('analysis-summary-red-player-wins').innerHTML = WINNER_BLUE_STAR_HTML;
                    break;
                case Outcome.BLACK_WINS:
                    document.getElementById('analysis-summary-black-player-wins').innerHTML = WINNER_BLUE_STAR_HTML;
                    break;
            }
        }

        summaryBlock.style.display = 'block';
    } else {
        if (scheduleEvalChartRenderTimeout != null) {
            clearTimeout(scheduleEvalChartRenderTimeout);
            scheduleEvalChartRenderTimeout = null;
        }
        const evalChartContainer = document.getElementById('eval-line-chart-container');
        if (evalChartContainer != null) {
            if (evalLineChart != null) {
                evalLineChart.destroy();
                evalLineChart = null;
            }
            evalChartContainer.innerHTML = '';
        }
        summaryBlock.style.display = 'none';
    }
}
