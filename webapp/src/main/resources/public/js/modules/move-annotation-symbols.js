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

const MoveAnnotationSymbolTypes = Object.freeze({
    BLUNDER: 'BLUNDER',
    MISTAKE: 'MISTAKE',
    INACCURACY: 'INACCURACY',
    INTERESTING: 'INTERESTING',
    GOOD: 'GOOD',
    BRILLIANT: 'BRILLIANT',
});

const moveAnnotationSymbolTypesArray = [
    MoveAnnotationSymbolTypes.BLUNDER,
    MoveAnnotationSymbolTypes.MISTAKE,
    MoveAnnotationSymbolTypes.INACCURACY,
    MoveAnnotationSymbolTypes.INTERESTING,
    MoveAnnotationSymbolTypes.GOOD,
    MoveAnnotationSymbolTypes.BRILLIANT,
]

// Only compare equally deep engine lines, and require at least depth 18 so
// shallow placeholder analyses do not produce misleading annotations.
const MIN_COMPARABLE_ANALYSIS_DEPTH = 18;

function moveAnnotationEnumToSymbol(annotation) {
    switch (annotation) {
        case MoveAnnotationSymbolTypes.BLUNDER:
            return '??';
        case MoveAnnotationSymbolTypes.MISTAKE:
            return '?';
        case MoveAnnotationSymbolTypes.INACCURACY:
            return '?!'
        case MoveAnnotationSymbolTypes.INTERESTING:
            return '!?';
        case MoveAnnotationSymbolTypes.GOOD:
            return '!';
        case MoveAnnotationSymbolTypes.BRILLIANT:
            return '!!';
        default:
            return '';
    }
}

/**
 * @param annotation {string} one of {@link MoveAnnotationSymbolTypes}
 * @return {string}
 */
function moveAnnotationEnumToLabel(annotation) {
    switch (annotation) {
        case MoveAnnotationSymbolTypes.BLUNDER:
            return 'Blunder';
        case MoveAnnotationSymbolTypes.MISTAKE:
            return 'Mistake';
        case MoveAnnotationSymbolTypes.INACCURACY:
            return 'Inaccuracy';
        case MoveAnnotationSymbolTypes.INTERESTING:
            return 'Interesting';
        case MoveAnnotationSymbolTypes.GOOD:
            return 'Good';
        case MoveAnnotationSymbolTypes.BRILLIANT:
            return 'Brilliant';
        default:
            return '';
    }
}

/**
 * @param delta {number}
 * @return {string|null}
 */
function centiPawnsLossToSymbol(delta) {
    if (Math.abs(delta) < 50) {
        return null;
    } else if (delta < 0) {
        const deltaLoss = Math.abs(delta);
        if (deltaLoss >= 300) {
            return MoveAnnotationSymbolTypes.BLUNDER;
        } else if (deltaLoss >= 100) {
            return MoveAnnotationSymbolTypes.MISTAKE;
        } else if (deltaLoss >= 50) {
            return MoveAnnotationSymbolTypes.INACCURACY;
        }
    } else if (delta > 0) {
        if (delta >= 300) {
            return MoveAnnotationSymbolTypes.BRILLIANT;
        } else if (delta >= 100) {
            return MoveAnnotationSymbolTypes.GOOD;
        } else if (delta >= 50) {
            return MoveAnnotationSymbolTypes.INTERESTING;
        }
    }

    return null;
}

/**
 * Map an {@link InfoLineResult} to a centi-pawn value.
 *
 * It's a heuristic in the sense that it maps a "mate" infoLineResult to a cp value.
 *
 * @param infoLineResult {InfoLineResult}
 * @returns {number|null}
 */
function infoLineResultToHeuristicCp(infoLineResult) {
    function cappedCp(cp) {
        if (cp > MAX_ABS_CP) {
            return MAX_ABS_CP;
        } else if (cp < -MAX_ABS_CP) {
            return -MAX_ABS_CP;
        } else {
            return cp;
        }
    }

    if (infoLineResult.cp != null) {
        return cappedCp(infoLineResult.cp);
    } else if (infoLineResult.mate != null) {
        const maxMate = 40;
        const mate = infoLineResult.mate;
        let additionalSlicesOfCp = (maxMate - Math.abs(mate))
        if (additionalSlicesOfCp < 0) {
            additionalSlicesOfCp = 0;
        }
        // each 1 mate fewer than 40 -> 8 cp
        // mate in 10 -> 30 * 8 = 240 cp
        const mateBonusInCp = additionalSlicesOfCp * 8;

        if (mate < 0) {
            return -MAX_ABS_CP - mateBonusInCp;
        } else {
            return MAX_ABS_CP + mateBonusInCp;
        }
    } else {
        return null;
    }
}

/**
 * Calculate the centi-pawn delta between the engine's best move and the actual move,
 * and map the delta to an annotation symbol, while keeping every intermediate value
 * used in the computation so it can be displayed (e.g. in a tooltip).
 *
 * @param engineBest {InfoLineResult}
 * @param actualMove {InfoLineResult}
 * @returns {{symbol: string, engineCp: number, actualMoveCp: number, delta: number}|null}
 */
function calculateAnnotationDetails(engineBest, actualMove) {
    if (actualMove.isCheckmate) {
        return null;
    }

    const engineCp = infoLineResultToHeuristicCp(engineBest);
    const actualMoveCp = infoLineResultToHeuristicCp(actualMove);

    if (engineCp == null ||
        actualMoveCp == null ||
        engineBest.depth < MIN_COMPARABLE_ANALYSIS_DEPTH ||
        actualMove.depth < MIN_COMPARABLE_ANALYSIS_DEPTH ||
        engineBest.depth !== actualMove.depth) {
        return null;
    }

    const delta = engineCp - actualMoveCp;
    const symbol = centiPawnsLossToSymbol(delta);

    if (symbol == null) {
        return null;
    }

    return {
        symbol: symbol,
        engineCp: engineCp,
        actualMoveCp: actualMoveCp,
        delta: delta,
    };
}

/**
 * Calculate centi-pawn delta between the previous position and the current position,
 * and map the delta to an annotation symbol enum value.
 *
 * @param engineBest {InfoLineResult}
 * @param actualMove {InfoLineResult}
 * @returns {string|null}
 */
function calculateAnnotationValue(engineBest, actualMove) {
    const details = calculateAnnotationDetails(engineBest, actualMove);
    return details != null ? details.symbol : null;
}

/**
 * @param analysisMap {Map<string, InfoLineResult>}
 * @param previousNodeData {InfoLineResult}
 * @returns {InfoLineResult|null}
 */
function findAnalysisDataFromEngineBestMove(analysisMap, previousNodeData) {
    if (previousNodeData.pv.length > 0) {
        const bestMove = previousNodeData.pv[0];
        const board = new Board();
        board.loadFen(previousNodeData.fen);
        board.registerMove(bestMove);
        const resultingFen = resetFenFullMovesCount(board.outputFen());
        return analysisMap.get(resultingFen);
    }
    return null;
}
