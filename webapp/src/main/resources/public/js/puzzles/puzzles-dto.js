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

class PuzzleDto {

    /**
     * @type {string}
     */
    #id;

    #attempts = 0;

    /**
     * @type {number}
     */
    #rating;

    /**
     * @type {string[]}
     */
    #categories;

    /**
     * @type {string}
     */
    #playerColor;

    /**
     * @type {string}
     */
    #startFen;

    /**
     * @type {HalfMove[]}
     */
    #movesHistory;

    /**
     * @type {HalfMove[]}
     */
    #preRecordedSolutionMoves;

    /**
     * @type {string|null}
     */
    #outcome = null;

    /**
     * @type {boolean}
     */
    #enabled;

    constructor(json) {
        this.#id = json.id;
        this.#attempts = Number(json.attempts);
        this.#rating = Number(json.rating);
        this.#categories = json.categories;
        this.#playerColor = json.color;
        this.#startFen = json.fen;
        this.#movesHistory = HalfMove.parseUciMultipleMoves(json.moves);
        this.#preRecordedSolutionMoves = HalfMove.parseUciMultipleMoves(json.solution);
        this.#enabled = json.enabled;
    }

    get id() {
        return this.#id;
    }

    /**
     * @returns {boolean}
     */
    get enabled() {
        return this.#enabled;
    }

    /**
     * @returns {number}
     */
    get attempts() {
        return this.#attempts;
    }

    get rating() {
        return this.#rating;
    }

    /**
     * @return {string[]}
     */
    get categories() {
        return this.#categories;
    }

    /**
     * @return {string[]}
     */
    get formattedCategories() {
        return this.#categories.map(category => formatEnumValue(category));
    }

    /**
     * @return {string}
     */
    get playerColor() {
        return this.#playerColor;
    }

    /**
     * @return {string}
     */
    get opponentColor() {
        return reverseColor(this.#playerColor);
    }

    get startFen() {
        return this.#startFen;
    }

    /**
     * @return {HalfMove[]}
     */
    get moves() {
        return this.#movesHistory;
    }

    /**
     * @return {HalfMove[]}
     */
    get preRecordedSolutionMoves() {
        return this.#preRecordedSolutionMoves;
    }

    /**
     * @return {HalfMove[]}
     */
    get allMoves() {
        return this.#movesHistory.concat(this.#preRecordedSolutionMoves);
    }

    /**
     * Actually the number of plies
     *
     * @return {number}
     */
    get allocatedNumberOfMoves() {
        return this.#preRecordedSolutionMoves.length;
    }

    get outcome() {
        return this.#outcome;
    }

    set outcome(value) {
        this.#outcome = value;
    }

    hasOutcome() {
        return this.outcome != null;
    }

    isSolved() {
        return this.outcome === PuzzleOutcome.SOLVED;
    }

}

class PuzzleRatingUpdateDto {

    /**
     * @type {number|null}
     */
    #oldRating;

    /**
     * @type {number|null}
     */
    #newRating;

    constructor(json) {
        if (json.oldRating != null) {
            this.#oldRating = Number(json.oldRating);
        }
        if (json.newRating != null) {
            this.#newRating = Number(json.newRating);
        }
    }

    isDefined() {
        return this.#oldRating != null && this.#newRating != null;
    }

    get oldRating() {
        return this.#oldRating;
    }

    get newRating() {
        return this.#newRating;
    }

}
