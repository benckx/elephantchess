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

const DEPTH_TO_ANALYZE = [2, 4, 8, 16, 20];

/**
 * Fetch and keep in store the results of engine requests (i.e, the {@class InfoLineResult})
 */
class AnalysisCache {

    /**
     * @type {Map<string, InfoLineResult>}
     */
    #cache = new Map();
    #minDepth = DEPTH_TO_ANALYZE[0];
    #selectedNode = null;
    #newPvListeners = [];

    /**
     * @type {string|null}
     */
    #analysisId;

    /**
     * @type {string|null}
     */
    #analysisStatus;

    /**
     * Keep track of all positions which are in the move tree
     *
     * @type {function():string[]}
     */
    #allNodesPositions = () => [];

    #isIdle = true;

    constructor() {
        const validAnalysisStatus =
            this.#analysisStatus == null ||
            this.#analysisStatus === AnalysisStatus.NOT_STARTED ||
            this.#analysisStatus === AnalysisStatus.PARTIALLY_COMPLETED ||
            this.#analysisStatus === AnalysisStatus.COMPLETED ||
            this.#analysisStatus === AnalysisStatus.CANCELLED;

        setInterval(() => {
            if (this.#isIdle && this.#nextPositionToAnalyzeFromTreeNode != null && validAnalysisStatus) {
                const nextFenKey = this.#nextPositionToAnalyze();
                if (nextFenKey != null) {
                    this.#isIdle = false;
                    // console.log('(idle) fetching next fen key ' + nextFenKey);
                    this.#fetchPv(nextFenKey, 20, infoLineResult => {
                        this.#pushToCache(nextFenKey, infoLineResult);
                        this.#isIdle = true;
                    });
                }
            }
        }, 2_000);
    }

    /**
     * @returns {null|string}
     */
    #nextPositionToAnalyze() {
        const nextFenKey = this.#nextPositionToAnalyzeFromTreeNode();
        if (nextFenKey != null) {
            // console.log(`fetching analysis node position ${nextFenKey}`);
            return nextFenKey;
        } else {
            const nextEngineBestMovePosition = this.#nextPositionToAnalyzeFromEngineBestMove();
            if (nextEngineBestMovePosition != null) {
                // console.log(`fetching analysis for engine best move position ${nextEngineBestMovePosition}`);
                return nextEngineBestMovePosition;
            } else {
                // console.log('no more position to analyze');
                return null;
            }
        }
    }

    /**
     * @returns {null|string}
     */
    #nextPositionToAnalyzeFromTreeNode() {
        const allNodesPositions = this.#allNodesPositions();
        const analyzedFenKeys = this.#cache.keys().toArray();
        const positionsLeftToAnalyze = allNodesPositions.filter(fen => !analyzedFenKeys.includes(fen));

        // console.log('analyzed: ' + analyzedFenKeys.length + ', total nodes: ' + allNodesPositions.length + ', left to analyze: ' + positionsLeftToAnalyze.length);

        if (positionsLeftToAnalyze.length > 0) {
            return positionsLeftToAnalyze[0];
        } else {
            return null;
        }
    }

    /**
     * @returns {null|string}
     */
    #nextPositionToAnalyzeFromEngineBestMove() {
        const allNodesPositions = this.#allNodesPositions();

        for (const [fen, infoLineResult] of this.#cache.entries()) {
            // to avoid infinitely recursive analysis of engine best moves positions
            const isNodePosition = allNodesPositions.includes(fen);
            if (isNodePosition && infoLineResult.pv.length > 0) {
                const bestMove = infoLineResult.pv[0];
                const board = new Board();
                board.loadFen(fen);
                board.registerMove(bestMove);
                const fenKeyFromBestMove = resetFenFullMovesCount(board.outputFen());
                if (!this.#cache.has(fenKeyFromBestMove)) {
                    return fenKeyFromBestMove;
                }
            }
        }

        return null;
    }

    /**
     * @param analysisId {string}
     */
    set analysisId(analysisId) {
        this.#analysisId = analysisId;
    }

    /**
     *  @param analysisStatus {string}
     */
    set analysisStatus(analysisStatus) {
        this.#analysisStatus = analysisStatus;
    }

    /**
     * @param allNodesPositions {function(): string[]}
     */
    set allNodesPositions(allNodesPositions) {
        this.#allNodesPositions = allNodesPositions;
    }

    addNewPvListener(listener) {
        this.#newPvListeners.push(listener);
    }

    populateCache(entries) {
        entries.forEach(entry => this.#pushToCache(entry.fen, entry));
    }

    /**
     * @param fen {string}
     * @param selectedNode {MoveTreeNode}
     * @param cb {function(InfoLineResult)}
     */
    getPrincipalVariationFor(fen, selectedNode, cb) {
        this.#selectedNode = selectedNode;

        let fenKey = this.#fenToKey(fen);

        if (this.#cache.has(fenKey)) {
            let cachedPv = this.#cache.get(fenKey);
            // console.log('found in cache with depth ' + cachedPv.depth + ' for key ' + key);
            cb(cachedPv);
            let nextDepth = this.#nextDepth(cachedPv.depth);
            if (nextDepth != null) {
                this.#fetchInitEngineEval(fenKey, cb, nextDepth);
            }
        } else {
            this.#fetchInitEngineEval(fenKey, cb, this.#minDepth);
        }
    }

    /**
     * @param cb {function(string, InfoLineResult)}
     */
    iterateAllEntries(cb) {
        this.#cache.forEach((pv, fenKey) => cb(fenKey, pv));
    }

    /**
     * @returns {Map<string, InfoLineResult>}
     */
    asMap() {
        return new Map(this.#cache);
    }

    /**
     * @return {number}
     */
    get size() {
        return this.#cache.size;
    }

    /**
     *
     * @return {Map<string, InfoLineResult>}
     */
    serializeToDtos() {
        return new Map(this.#cache);
    }

    #fetchInitEngineEval(fen, cb, depth) {
        this.#fetchEngineEval(fen, cb, depth, this.#selectedNode);
    }

    #fetchEngineEval(fen, cb, depth, selectedNode) {
        this.#isIdle = false;
        this.#fetchPv(fen, depth, infoLineResult => {
            this.#pushToCache(fen, infoLineResult);
            let hasRequestedOtherMove = selectedNode != null && MoveTreeNode.areNotEquals(selectedNode, this.#selectedNode);

            if (!hasRequestedOtherMove) {
                cb(infoLineResult);
                if (!infoLineResult.isCheckmate) {
                    // recursion (go deeper)
                    let nextDepth = this.#nextDepth(infoLineResult.depth);
                    let key = this.#fenToKey(fen);
                    let alreadyHasDeeperData = this.#cache.has(key) && this.#cache.get(key).depth >= nextDepth;

                    if (nextDepth != null && !alreadyHasDeeperData) {
                        this.#fetchEngineEval(fen, cb, nextDepth, selectedNode);
                    } else {
                        this.#isIdle = true;
                    }
                }
            } else {
                console.log('move has switched from ' + selectedNode + ' to ' + this.#selectedNode);
                this.#isIdle = true;
            }
        });
    }

    /**
     * @param fen {string}
     * @param infoLineResult {InfoLineResult}
     */
    #pushToCache(fen, infoLineResult) {
        let key = this.#fenToKey(fen);
        if (this.#cache.has(key)) {
            let current = this.#cache.get(key);
            if (current.depth < infoLineResult.depth) {
                this.#cache.set(key, infoLineResult);
                this.#newPvListeners.forEach(listener => listener(infoLineResult));
            }
        } else {
            this.#cache.set(key, infoLineResult);
            this.#newPvListeners.forEach(listener => listener(infoLineResult));
        }
    }

    /**
     * Reset full count to 0 (key is only the FEN abridged + the color to play)
     */
    #fenToKey(fen) {
        return resetFenFullMovesCount(fen);
    }

    #nextDepth(currentDepth) {
        const depthIndex = DEPTH_TO_ANALYZE.indexOf(currentDepth);
        const canGoDeeper = depthIndex >= 0 && depthIndex < DEPTH_TO_ANALYZE.length - 1;
        if (canGoDeeper) {
            return DEPTH_TO_ANALYZE[depthIndex + 1];
        } else {
            return null;
        }
    }


    /**
     * @param fen {string}
     * @param depth {number}
     * @param cb {function(InfoLineResult)}
     */
    #fetchPv(fen, depth, cb) {
        let url = '/api/analysis/query-engine';
        if (this.#analysisId != null) {
            url += '?analysisId=' + this.#analysisId;
        }
        const body = {'fen': fen, 'engine': 'PIKAFISH', 'depth': depth};
        postAndHandle(url, body, json => cb(new InfoLineResult(json)));
    }

}
