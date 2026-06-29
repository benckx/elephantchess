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

class MoveAnalysisByGame {

    /**
     * @return {GameId}
     */
    #gameId;

    /**
     * @type {number}
     */
    #first;

    /**
     * @type {number}
     */
    #last;

    /**
     * @type {number}
     */
    #totalAnalyzedMoves;

    /**
     * @type {string}
     */
    #analysisStatus

    /**
     * @type {boolean}
     */
    #analyzedFromBatch

    constructor(json) {
        this.#gameId = new GameId(json.gameId.type, json.gameId.id);
        this.#first = Number(json.first);
        this.#last = Number(json.last);
        // noinspection JSUnresolvedReference
        this.#totalAnalyzedMoves = Number(json.totalAnalyzedMoves);
        this.#analysisStatus = json.analysisStatus;
        this.#analyzedFromBatch = json.analyzedFromBatch;
    }

    /**
     * @return {GameId}
     */
    get gameId() {
        return this.#gameId;
    }

    /**
     * @return {number}
     */
    get first() {
        return this.#first;
    }

    get firstFormatted() {
        return formatTimestampToDateTime(this.#first);
    }

    /**
     * @return {number}
     */
    get last() {
        return this.#last;
    }

    get lastFormatted() {
        return formatTimestampToDateTime(this.#last);
    }

    /**
     * @return {number}
     */
    get totalAnalyzedMoves() {
        return this.#totalAnalyzedMoves;
    }

    /**
     * @return {string}
     */
    get analysisStatus() {
        return this.#analysisStatus;
    }

    /**
     * @return {boolean}
     */
    get analyzedFromBatch() {
        return this.#analyzedFromBatch;
    }

}

class StatusPerYearEntryDto {

    /**
     * @return {number}
     */
    #year;

    /**
     * @return {string}
     */
    #status;

    /**
     * @return {number}
     */
    #count;

    constructor(json) {
        this.#year = Number(json.year);
        this.#status = json.status;
        this.#count = Number(json.count);
    }

    /**
     * @return {number}
     */
    get year() {
        return this.#year;
    }

    /**
     * @return {string}
     */
    get status() {
        return this.#status;
    }


    /**
     * @return {number}
     */
    get count() {
        return this.#count;
    }

    /**
     * @param json
     * @returns {StatusPerYearEntryDto[]}
     */
    static parse(json) {
        let entries = [];
        json.entries.map(jsonEntry => entries.push(new StatusPerYearEntryDto(jsonEntry)));
        return entries;
    }

}

class StatusByGameTypeEntryDto {

    /**
     * @type {string}
     */
    #gameType;

    /**
     * @type {string}
     */
    #status;

    /**
     * @type {number}
     */
    #count;

    constructor(json) {
        this.#gameType = json.gameType;
        this.#status = json.status;
        this.#count = Number(json.count);
    }

    /**
     * @return {string}
     */
    get gameType() {
        return this.#gameType;
    }

    /**
     * @return {string}
     */
    get status() {
        return this.#status;
    }

    /**
     * @return {number}
     */
    get count() {
        return this.#count;
    }

    /**
     * @param json
     * @returns {StatusByGameTypeEntryDto[]}
     */
    static parse(json) {
        return json.entries.map(jsonEntry => new StatusByGameTypeEntryDto(jsonEntry));
    }
}
