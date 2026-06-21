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
 * @param nodes {MoveTreeNode[]}
 * @param analysisMap {Map<string, InfoLineResult>}
 * @param startFen {string}
 * @param onClickNode {function(MoveTreeNode|null)}
 */
function scheduleEvalChartRender(nodes, analysisMap, startFen, onClickNode) {
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
        evalLineChart = new EvalLineChart('eval-line-chart-container', analysisMap, startFen, nodes, onClickNode);
        evalLineChart.render();
    }, EVAL_LINE_CHART_RENDER_DEBOUNCE_MS);
}

/**
 * @param gameId {GameId}
 * @param moveTreeWidget {MoveTreeWidget}
 */
function fetchDataAndrenderAnalysisSummaryReport(gameId, moveTreeWidget) {
    const client = new GameDataClient(gameId);
    client.fetchAnalysisStatus((analysisProgressStatus) => {
        if (analysisProgressStatus.status === AnalysisStatus.COMPLETED) {
            client.fetchAnalysisData((analysisResponse) => {
                client.fetchMetadata((gameMetadata) => {
                    const analysisMap = new Map();
                    analysisResponse.entries.forEach((infoLineResult) => {
                        analysisMap.set(resetFenFullMovesCount(infoLineResult.fen), infoLineResult);
                    });

                    renderAnalysisSummaryReport(
                        analysisMap,
                        gameMetadata.redPlayerName,
                        gameMetadata.blackPlayerName,
                        gameMetadata.outcome,
                        moveTreeWidget,
                        analysisResponse.moveAnnotations
                    );

                    if (moveTreeWidget != null) {
                        moveTreeWidget.applyAnnotationSymbols(analysisResponse.moveAnnotations);
                    }
                });
            });
        }
    });
}

/**
 * @param analysisMap {Map<string, InfoLineResult>}
 * @param redPlayerName {string|null}
 * @param blackPlayerName {string|null}
 * @param outcome {string|null}
 * @param moveTreeWidget {MoveTreeWidget}
 * @param moveAnnotations {GameMoveAnnotationDto[]}
 */
function renderAnalysisSummaryReport(
    analysisMap,
    redPlayerName,
    blackPlayerName,
    outcome,
    moveTreeWidget,
    moveAnnotations
) {
    const nodes = moveTreeWidget.getMainBranchNodes();
    const startFen = moveTreeWidget.startFen;
    const annotationsByMoveIndex = new Map(moveAnnotations.map(annotation => [annotation.moveIndex, annotation]));
    const onClickNode = (node) => {
        if (node != null) {
            moveTreeWidget.selectNodeById(node.nodeId);
        } else {
            moveTreeWidget.navigateToStart();
        }
    };

    /**
     * @param nodes {Array<MoveTreeNode>}
     * @param analysisMap {Map<string, InfoLineResult>}
     * @returns {boolean}
     */
    function isEngineDataComplete(nodes, analysisMap) {
        function hasDataForActualMoves() {
            return nodes.map(node => node.fenKey).every(fenKey => analysisMap.has(fenKey));
        }

        function hasDataForStartPosition() {
            return analysisMap.has(resetFenFullMovesCount(startFen));
        }

        return hasDataForActualMoves() &&
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
                const moveAnnotation = annotationsByMoveIndex.get(node.position);
                if (moveAnnotation != null) {
                    counter.set(moveAnnotation.annotation, counter.get(moveAnnotation.annotation) + 1 || 1);
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
