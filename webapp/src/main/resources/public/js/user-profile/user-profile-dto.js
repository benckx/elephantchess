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

class PuzzleSummaryStatsDto {

    #rating;
    #maxRating;
    #totalPlayed;

    constructor(json) {
        this.#rating = json.rating;
        this.#maxRating = json.maxRating;
        this.#totalPlayed = json.totalPlayed;
    }

    /**
     * @return {number}
     */
    get rating() {
        return this.#rating;
    }

    /**
     * @return {number}
     */
    get maxRating() {
        return this.#maxRating;
    }

    /**
     * @return {number}
     */
    get totalPlayed() {
        return this.#totalPlayed;
    }

}

class PuzzleRatingHistoryEntryDto {

    #date;
    #last;
    #max;

    constructor(json) {
        this.#date = json.date;
        this.#last = Number(json.last);
        this.#max = Number(json.max);
    }

    /**
     * @return {string}
     */
    get date() {
        return this.#date;
    }

    /**
     * @return {number}
     */
    get last() {
        return this.#last;
    }

    /**
     * @return {number}
     */
    get max() {
        return this.#max;
    }

}

class PuzzleRatingHistoryDto {

    /**
     * @type {PuzzleRatingHistoryEntryDto[]}
     */
    #history = [];
    #roundedMin;
    #roundedMax;
    #abridgeDate = false;

    constructor(json) {
        this.#history = json.history.map(entry => new PuzzleRatingHistoryEntryDto(entry));
        this.#abridgeDate = json.history.length > NO_LABEL_DAYS;

        let min = 1000_000;
        let max = 0;
        this.#history.forEach(entry => {
            if (entry.max > max) {
                max = entry.max;
            }
            if (entry.last < min) {
                min = entry.last;
            }
        });

        this.#roundedMin = (Math.floor(min / 100) * 100);
        this.#roundedMax = (Math.ceil(max / 100) * 100);
    }

    /**
     * @return {PuzzleRatingHistoryEntryDto[]}
     */
    // TODO: not used?
    get history() {
        return this.#history;
    }

    /**
     * @return {number}
     */
    get length() {
        return this.#history.length;
    }

    /**
     * @return {number}
     */
    get roundedMin() {
        return this.#roundedMin;
    }

    /**
     * @return {number}
     */
    get roundedMax() {
        return this.#roundedMax;
    }

    /**
     *
     * @return {number[]}
     */
    get seriesMax() {
        return this.#history.map(entry => entry.max);
    }

    /**
     *
     * @return {number[]}
     */
    get seriesLast() {
        return this.#history.map(entry => entry.max);
    }

    /**
     * @return {string[]}
     */
    get daysCategories() {
        return this.#history.map(entry => formatDate(entry.date, this.#abridgeDate));
    }

    isEmpty() {
        return this.#history.length === 0;
    }

    isNotEmpty() {
        return !this.isEmpty();
    }

}

class PuzzleNumbersHistoryEntryDto {

    #date;
    #solved;
    #skipped;
    #failed;

    constructor(json) {
        this.#date = json.date;
        this.#solved = json.solved;
        this.#skipped = json.skipped;
        this.#failed = json.failed;
    }

    /**
     * @return {string}
     */
    get date() {
        return this.#date;
    }

    /**
     * @return {number}
     */
    get solved() {
        return this.#solved;
    }

    /**
     * @return {number}
     */
    get skipped() {
        return this.#skipped;
    }

    /**
     * @return {number}
     */
    get failed() {
        return this.#failed;
    }

}

class PuzzleNumbersHistoryDto {

    /**
     * @type {PuzzleNumbersHistoryEntryDto[]}
     */
    #history = [];
    #abridgeDate = false;

    constructor(json) {
        this.#history = json.history.map(entry => new PuzzleNumbersHistoryEntryDto(entry)).reverse(); // TODO: proper ordering
        this.#abridgeDate = json.history.length > NO_LABEL_DAYS;
    }

    /**
     * @return {number}
     */
    get length() {
        return this.#history.length;
    }

    get daysCategories() {
        return this.#history.map(entry => formatDate(entry.date, this.#abridgeDate));
    }

    /**
     * @return {number[]}
     */
    get seriesSolved() {
        return this.#history.map(entry => entry.solved);
    }

    /**
     * @return {number[]}
     */
    get seriesSkipped() {
        return this.#history.map(entry => entry.skipped);
    }

    /**
     * @return {number[]}
     */
    get seriesFailed() {
        return this.#history.map(entry => entry.failed);
    }

    isEmpty() {
        return this.#history.length === 0;
    }

    isNotEmpty() {
        return !this.isEmpty();
    }

}

/**
 * @param date {string}
 * @param abridgeDate {boolean}
 * @return {string}
 */
// TODO: remove this thing
function formatDate(date, abridgeDate) {
    // const forceShow = date.endsWith('01'); // first day of month
    //
    // if (!forceShow && abridgeDate) {
    //     return ''
    // } else {
    //     return date;
    // }
    return date;
}
