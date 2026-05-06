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

class PlayedPuzzleDto {

    #puzzleId;
    #playerColor;
    #startFen;
    #categories;
    #outcome;
    #ratingFrom;
    #ratingTo;
    #date;

    constructor(json) {
        this.#puzzleId = json.puzzleId;
        this.#playerColor = json.playerColor;
        this.#startFen = json.startFen;
        this.#categories = json.categories;
        this.#outcome = json.outcome;
        this.#ratingFrom = Number(json.ratingFrom);
        this.#ratingTo = Number(json.ratingTo);
        this.#date = Number(json.date);
    }

    /**
     * @returns {string}
     */
    get puzzleId() {
        return this.#puzzleId;
    }

    /**
     * @return {string}
     */
    get idUrl() {
        return getFullHost() + '/puzzles?id=' + this.puzzleId;
    }

    /**
     * @return {string}
     */
    get playerColor() {
        return this.#playerColor;
    }

    /**
     * @returns {string}
     */
    get startFen() {
        return this.#startFen;
    }

    /**
     * @return {string}
     */
    get categoriesUrl() {
        return addCategoriesParamsToUrl(getFullHost() + '/puzzles', this.#categories);
    }

    /**
     * @return {string[]}
     */
    get formattedCategories() {
        return this.#categories.map(category => formatEnumValue(category));
    }

    /**
     * @returns {string}
     */
    get outcome() {
        return this.#outcome;
    }

    get formattedOutcome() {
        return formatEnumValue(this.#outcome);
    }

    /**
     * @return {number}
     */
    get ratingFrom() {
        return this.#ratingFrom;
    }

    /**
     * @return {number}
     */
    get ratingDelta() {
        return this.#ratingTo - this.#ratingFrom;
    }

    /**
     * UTC timestamp in millis
     *
     * @return {number}
     */
    get date() {
        return this.#date;
    }

}
