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
