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

class PuzzleMoveTreeWidget extends MoveTreeWidget {

    #isSolutionRevealed = false;

    /**
     * @type {Map<string, string>}
     */
    #moveIdToCssClass = new Map();

    constructor(options) {
        super(options);
    }

    addSubmittedMove(move) {
        return this.#addMoveWithCssClass(move, 'submitted-move');
    }

    renderSubmittedMovesAsSolution() {
        this.#updateClassOfSubmittedMoves('solution-move');
    }

    /**
     * @param {number} n
     */
    renderLastMovesAsSolution(n) {
        const nodes = this.getMainBranchNodes();
        if (nodes.length < n) {
            console.warn('not enough moves');
        } else {
            for (let i = 0; i < n; i++) {
                let node = nodes[nodes.length - 1 - i];
                this.#moveIdToCssClass.set(node.nodeId, 'solution-move');
            }
            this.#reApplyCssClasses();
        }
    }

    renderSubmittedMovesAsErroneous() {
        this.#updateClassOfSubmittedMoves('erroneous-move')
    }

    #addMoveWithCssClass(move, cssClass) {
        const node = this.addMoveAtTheEnd(move);
        this.#moveIdToCssClass.set(node.nodeId, cssClass);
        this.#reApplyCssClasses();
        return node;
    }

    #updateClassOfSubmittedMoves(cssClass) {
        for (let nodeId of this.#moveIdToCssClass.keys()) {
            const moveContainer = document.getElementById('move-container-' + nodeId);
            if (moveContainer != null) {
                moveContainer.classList.remove('submitted-move');
                moveContainer.classList.add(cssClass);
            }
        }
    }

    /**
     * Otherwise they disappear between renders
     */
    #reApplyCssClasses() {
        for (const [nodeId, cssClass] of this.#moveIdToCssClass.entries()) {
            const moveContainer = document.getElementById('move-container-' + nodeId);
            if (moveContainer != null) {
                moveContainer.classList.add(cssClass);
            }
        }
    }

    resetFlaggedAsRevealed() {
        this.#isSolutionRevealed = false;
    }

    flagAsRevealed() {
        this.#isSolutionRevealed = true;
    }

    isSolutionRevealed() {
        return this.#isSolutionRevealed;
    }

}
