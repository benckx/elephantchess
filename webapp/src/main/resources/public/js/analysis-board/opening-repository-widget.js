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

class OpeningRepositoryWidget {

    /**
     * @type {BoardGui}
     */
    #boardGui;
    #settingsManager = new SettingsManager();

    #openingBox = document.getElementById('opening-repository-container');
    #openingBoxMask = document.getElementById('opening-repository-container-loading-mask');
    #openingBoxMaskLabel = document.getElementById('opening-repository-mask-label-indicator');
    #table = document.getElementById('opening-repository-table');

    // if user goes quickly through moves, they might request moves that will come too late
    #requestedMovesAsUci = [];

    #startFen = DEFAULT_START_FEN;
    #useDefaultFen = true;

    // endpoint and request body builder (overridable, e.g. for player-specific openings)
    #url = '/api/analysis/openings/next-moves-info';
    #buildBody = (movesAsUci) => ({ 'moves': movesAsUci });

    /**
     * @param boardGui {BoardGui}
     * @param options {{url?: string, buildBody?: function(string[]): Object}} optional overrides
     *        for the endpoint and request body (defaults to the analysis openings endpoint)
     */
    constructor(boardGui, options = {}) {
        this.#boardGui = boardGui;
        if (options.url !== undefined) {
            this.#url = options.url;
        }
        if (options.buildBody !== undefined) {
            this.#buildBody = options.buildBody;
        }
    }

    /**
     * @param value {string}
     */
    set startFen(value) {
        this.#startFen = value;
        this.#useDefaultFen = (value === DEFAULT_START_FEN);
    }

    /**
     * @param previousMoves {HalfMove[]}
     */
    fetchOpeningsNextMoves(previousMoves) {
        this.#showLoadingMask();
        emptyTable(this.#table);

        if (!this.#useDefaultFen) {
            this.#showCustomFenNoResultMask();
            return;
        }

        let previousMovesAsUci = previousMoves.map(move => move.toUci());
        let moveFormat = this.#settingsManager.moveFormat.toString();
        this.#requestedMovesAsUci = previousMovesAsUci;

        // TODO: move to a client
        let url = this.#url;
        let body = this.#buildBody(previousMovesAsUci);
        postAndHandle(url, body, json => {
            if (this.#areMovesEqual(this.#requestedMovesAsUci, json.moves)) {
                if (json.entries.length > 0) {
                    let tbody = this.#table.getElementsByTagName('tbody')[0];
                    let totalOccurrences = json.entries.reduce((sum, e) => sum + e.occurrences, 0);
                    for (let i = 0; i < json.entries.length; i++) {
                        let entry = json.entries[i];
                        let nextMove = HalfMove.parseUci(entry.nextMove);
                        let allMoves = previousMoves.concat(nextMove);

                        try {
                            let translated = translateMovesFormatTakeLast(allMoves, moveFormat, this.#startFen);
                            let moveLabel = translated != null ? translated : nextMove.toAlgebraic();

                            // html
                            let tr = document.createElement('tr');
                            let moveCell = document.createElement('td');
                            let occurrencesCell = document.createElement('td');
                            let outcomeCell = document.createElement('td');
                            tr.id = 'tr_' + nextMove.toUci();
                            moveCell.className = 'move-cell';
                            occurrencesCell.className = 'occurrence-cell';
                            outcomeCell.className = 'outcome-cell';

                            // content
                            moveCell.innerText = moveLabel;

                            let pct = totalOccurrences > 0 ? (entry.occurrences / totalOccurrences * 100) : 0;
                            let occurrenceBar = document.createElement('div');
                            occurrenceBar.className = 'occurrence-bar';
                            occurrenceBar.style.width = `${pct.toFixed(1)}%`;

                            let occurrenceCellContent = document.createElement('div');
                            occurrenceCellContent.className = 'occurrence-cell-content';

                            let pctSpan = document.createElement('span');
                            pctSpan.className = 'occurrence-pct';
                            pctSpan.innerText = `${pct.toFixed(1)}%`;

                            // optional general-population share (player openings only), shown in brackets
                            if (entry.generalPopulationRate !== undefined && entry.generalPopulationRate !== null) {
                                let generalPctSpan = document.createElement('span');
                                generalPctSpan.className = 'occurrence-pct-general';
                                generalPctSpan.innerText = ` (${(entry.generalPopulationRate * 100).toFixed(1)}%)`;
                                pctSpan.append(generalPctSpan);
                            }

                            let countSpan = document.createElement('span');
                            countSpan.className = 'occurrence-count';
                            countSpan.innerText = formatNumber(entry.occurrences);

                            occurrenceCellContent.append(pctSpan, countSpan);
                            occurrencesCell.append(occurrenceBar, occurrenceCellContent);

                            let indicator = new GameOutcomeIndicator(entry.redWinsRate, entry.blackWinsRate);
                            outcomeCell.innerHTML = '';
                            outcomeCell.append(indicator.render());

                            tr.addEventListener('click', (e) => {
                                if (this.#boardGui.isPlayerMoveEnabled) {
                                    let move = this.#findMoveFromMouseEvent(e);
                                    this.#boardGui.registerMoveIfLegal(move);
                                    this.#boardGui.hideAllHighlightedDynamicMoves();
                                } else {
                                    // TODO: should be a warning
                                    UI.pushErrorNotification('Can not create branch when no move is selected');
                                }
                            });

                            tr.addEventListener('mouseover', (e) => {
                                let move = this.#findMoveFromMouseEvent(e);
                                this.#boardGui.hideAllHighlightedDynamicMoves();
                                this.#boardGui.highlightDynamicMove(move);
                            });

                            tr.addEventListener('mouseout', () => {
                                this.#boardGui.hideAllHighlightedDynamicMoves();
                            });

                            tr.append(moveCell, occurrencesCell, outcomeCell);
                            tbody.append(tr);
                        } catch (e) {
                            console.warn('Error while processing moves ' + allMoves.map(move => move.toAlgebraic()));
                        }
                    }

                    this.#hideMask();
                } else {
                    this.#showNoResultMask();
                }
            } else {
                // result are not valid anymore
                console.log('has requested for other moves: ' + this.#requestedMovesAsUci + ' vs. ' + json.moves);
                this.#showNoResultMask();
            }
        });
    }

    #showLoadingMask() {
        this.#showMask('Loading...');
    }

    #showNoResultMask() {
        this.#showMask('No opening data');
    }

    #showCustomFenNoResultMask() {
        this.#showMask('No opening data for custom start FEN');
    }

    #showMask(text) {
        this.#openingBox.style.overflowY = 'hidden';
        this.#openingBoxMask.style.visibility = 'visible';
        this.#openingBoxMaskLabel.innerText = text;
    }

    #hideMask() {
        this.#openingBox.style.overflowY = 'scroll';
        this.#openingBoxMask.style.visibility = 'hidden';
        this.#openingBoxMaskLabel.innerText = '';
    }

    /**
     * Find the first tr element in the parent hierarchy from the target of the mouse event (which can be a td, a div, the bar indicator, etc.)
     * and parse the id of the tr which contains the move UCI
     */
    #findMoveFromMouseEvent(mouseEvent) {
        function findTrParent(element) {
            if (element.tagName === 'TR') {
                return element;
            } else {
                return findTrParent(element.parentElement);
            }
        }

        let tr = findTrParent(mouseEvent.target);
        let uci = this.#rowIdToUci(tr.id)
        return HalfMove.parseUci(uci);
    }

    #rowIdToUci(id) {
        return id.substring(3);
    }

    #areMovesEqual(moves1, moves2) {
        if (moves1.length !== moves2.length) {
            return false;
        }
        for (let i = 0; i < moves1.length; i++) {
            if (moves1[i] !== moves2[i]) {
                return false;
            }
        }
        return true;
    }

}
