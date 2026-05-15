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

const HIGHLIGHT_PV_MOVES = false;

class EngineAnalysisWidget {

    /**
     * @type {AnalysisCache}
     */
    #analysisCache

    /**
     * @type {BoardGui}
     */
    #boardGui;

    /**
     * @type {MoveTreeWidget}
     */
    #moveTreeWidget;

    /**
     * @type {function(MoveTreeNode)}
     */
    #selectNodeCb = (node) => {
        console.log('selectNodeCb not set', node);
    };

    #settingsManager = new SettingsManager();

    #startFen = DEFAULT_START_FEN;
    #useDefaultFen = true;

    #movesUpToSelection = [];

    #evalBarContainer = document.getElementById('eval-bar-container');
    #depthSpan = document.getElementById('engine-depth');
    #enginePvDiv = document.getElementById('engine-pv');
    #engineRawLine = document.getElementById('engine-raw-line');

    #pvMiniBoardGui = null;
    #pvMiniBoardDiv = null;

    /**
     * @param analysisCache {AnalysisCache}
     * @param boardGui {BoardGui}
     * @param moveTreeWidget {MoveTreeWidget}
     * @param selectNodeCb {function(MoveTreeNode)}
     */
    constructor(analysisCache, boardGui, moveTreeWidget, selectNodeCb) {
        this.#analysisCache = analysisCache;
        this.#boardGui = boardGui;
        this.#moveTreeWidget = moveTreeWidget;
        this.#selectNodeCb = selectNodeCb;
    }

    /**
     * @param value {string}
     */
    set startFen(value) {
        this.#startFen = value;
        this.#useDefaultFen = (value === DEFAULT_START_FEN);
    }

    /**
     *  @param movesUpToSelection {HalfMove[]}
     */
    update(movesUpToSelection) {
        this.#movesUpToSelection = movesUpToSelection;
        const fen = this.#moveTreeWidget.getFenAtSelection();
        const selectedNode = this.#moveTreeWidget.selectedNode;

        this.#analysisCache.getPrincipalVariationFor(fen, selectedNode, infoLineResult => {
            this.#updateEvalBar(infoLineResult);

            this.#depthSpan.innerText = '';
            this.#engineRawLine.innerText = '';

            if (infoLineResult.rawLine != null) {
                this.#engineRawLine.innerText = infoLineResult.rawLineNoPv;
            }
            if (infoLineResult.depth != null) {
                this.#depthSpan.innerText = 'depth ' + infoLineResult.depth;
            }

            if (!infoLineResult.isCheckmate) {
                this.#enginePvDiv.innerHTML = '';
                let allMoves = movesUpToSelection.concat(infoLineResult.pv);
                let translatedMoves = this.#safeTranslateMoves(allMoves);

                infoLineResult.pv.forEach((move, i) => {
                    let enginePvMoveDiv = document.createElement('div');
                    enginePvMoveDiv.innerText = translatedMoves[i + movesUpToSelection.length];

                    if (i < infoLineResult.pv.length - 1) {
                        enginePvMoveDiv.append(', ')
                    }

                    enginePvMoveDiv.id = 'engine-pv-move-' + move.toUci();
                    enginePvMoveDiv.className = 'engine-pv-move';
                    this.#enginePvDiv.append(enginePvMoveDiv);

                    enginePvMoveDiv.addEventListener('click', (e) => {
                        if (this.#boardGui.isPlayerMoveEnabled) {
                            this.#selectEngineLine(this.#findAllMovesBefore(e.target.id));
                        } else {
                            // TODO: should be a warning
                            UI.pushErrorNotification('Can not create branch when no move is selected');
                        }
                    });

                    enginePvMoveDiv.addEventListener('mouseover', (e) => {
                        // add engine pv move green coloring
                        this
                            .#findAllPvMoveElementsBefore(e.target.id)
                            .forEach(element => element.classList.add('engine-pv-move-hovered'));

                        // show highlights moves to board
                        if (HIGHLIGHT_PV_MOVES) {
                            this.#boardGui.hideAllHighlightedDynamicMoves();
                            this.#findAllMovesBefore(e.target.id).forEach(move => {
                                this.#boardGui.highlightDynamicMove(move);
                            });
                        }

                        // show mini board with the resulting position
                        const pvMoves = this.#findAllMovesBefore(e.target.id);
                        const resultFen = calculateFen([...this.#movesUpToSelection, ...pvMoves], this.#startFen);
                        this.#showPvMiniBoard(resultFen);
                    });

                    enginePvMoveDiv.addEventListener('mouseout', () => {
                        // remove engine pv move green coloring
                        for (let element of this.#findAllPvMoveElements()) {
                            element.classList.remove('engine-pv-move-hovered');
                        }

                        // hide highlights moves to board
                        if (HIGHLIGHT_PV_MOVES) {
                            this.#boardGui.hideAllHighlightedDynamicMoves();
                        }

                        // hide mini board
                        this.#hidePvMiniBoard();
                    });
                });
            } else {
                // TODO: make the eval bar completely red or black
                this.#enginePvDiv.innerText = 'n/a';
            }
        });
    }

    /**
     * For debugging purposes, it should not raise an error
     *
     * @param allMoves {HalfMove[]}
     * @return {string[]}
     */
    #safeTranslateMoves(allMoves) {
        return safeTranslateMovesFormat(
            allMoves,
            this.#settingsManager.moveFormat.toString(),
            this.#startFen
        );
    }

    /**
     * @param id {string}
     * @return {HalfMove[]}
     */
    #findAllMovesBefore(id) {
        return this.#findAllPvMoveElementsBefore(id).map(element => HalfMove.parseUci(element.id.split('-').pop()));
    }

    /**
     * Including matching element itself
     */
    #findAllPvMoveElementsBefore(id) {
        let allMovesDivs = this.#enginePvDiv.getElementsByClassName('engine-pv-move');
        let selectedElements = [];
        for (let i = 0; i < allMovesDivs.length; i++) {
            let enginePvDiv = allMovesDivs[i];
            selectedElements.push(enginePvDiv);

            if (enginePvDiv.id === id) {
                break;
            }
        }

        return selectedElements;
    }

    #findAllPvMoveElements() {
        return this.#enginePvDiv.getElementsByClassName('engine-pv-move');
    }

    /**
     *  @param engineResponse {InfoLineResult}
     */
    #updateEvalBar(engineResponse) {
        let indicator = new EvalBarIndicator(engineResponse.cp, engineResponse.mate, engineResponse.eval);
        this.#evalBarContainer.innerHTML = '';
        this.#evalBarContainer.append(indicator.render());
    }

    #ensurePvMiniBoard() {
        if (this.#pvMiniBoardDiv) return;

        const miniBoardId = 'engine-pv-mini-board';
        this.#pvMiniBoardDiv = document.createElement('div');
        this.#pvMiniBoardDiv.id = miniBoardId;
        this.#pvMiniBoardDiv.classList.add(
            'board-container',
            'mini-board-container',
            'mini-board-overview'
        );
        document.body.appendChild(this.#pvMiniBoardDiv);

        this.#pvMiniBoardGui = createWebappBoardGui({
            elementId: miniBoardId,
            showCoordinates: false,
            mini: true,
            forceRenderChecks: true,
        });
        this.#pvMiniBoardGui.flipToColor(this.#boardGui.bottomColor);

        // keep mini board orientation in sync with the main board
        this.#boardGui.addAfterFlipListener(color => {
            this.#pvMiniBoardGui.flipToColor(color);
        });
    }

    /**
     * @param fen {string}
     */
    #showPvMiniBoard(fen) {
        this.#ensurePvMiniBoard();
        this.#pvMiniBoardGui.loadFen(fen);

        const TOP_MARGIN = 8;
        const pvRect = this.#enginePvDiv.getBoundingClientRect();
        const left = pvRect.left + window.scrollX;
        const top = pvRect.bottom + TOP_MARGIN + window.scrollY;
        this.#pvMiniBoardDiv.style.top = `${top}px`;
        this.#pvMiniBoardDiv.style.left = `${left}px`;
        this.#pvMiniBoardDiv.style.display = 'block';
    }

    #hidePvMiniBoard() {
        if (this.#pvMiniBoardDiv) {
            this.#pvMiniBoardDiv.style.display = 'none';
        }
    }

    /**
     *
     * @param engineMoves {HalfMove[]}
     */
    #selectEngineLine(engineMoves) {
        if (engineMoves.length > 0) {
            const selectedBranchMoves = this.#moveTreeWidget.getMovesUpToSelection();

            // render to board
            /** @type {HalfMove[]} */
            const movesToRender = [];
            movesToRender.push(...selectedBranchMoves);
            movesToRender.push(...engineMoves);
            this.#boardGui.loadFen(calculateFen(movesToRender, this.#startFen), false);

            // update move tree
            const selectedNode = this.#moveTreeWidget.addToTree(engineMoves);
            this.#selectNodeCb(selectedNode);

            // self-update
            this.update(movesToRender);

            // update move history gui with cache analysis data
            this.#moveTreeWidget.refreshAllMoveNodeEval(this.#analysisCache.asMap());
        }
    }

}
