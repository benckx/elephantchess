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

class CurrentPuzzleDto {

    #puzzleId;
    #color;
    #fen;

    constructor(json) {
        this.#puzzleId = json.id;
        this.#color = json.color;
        this.#fen = json.fen;
    }

    get puzzleId() {
        return this.#puzzleId;
    }

    get color() {
        return this.#color;
    }

    get fen() {
        return this.#fen;
    }

}

class UpcomingEventDto {

    #start;
    #end;
    #description;
    #link;

    /**
     * @param {Object} json
     */
    constructor(json) {
        this.#start = json.start;
        this.#end = json.end;
        this.#description = json.description;
        this.#link = json.link;
    }

    /**
     * @returns {string}
     */
    get start() {
        return this.#start;
    }

    /**
     * @returns {string}
     */
    get end() {
        return this.#end;
    }

    /**
     * @returns {string}
     */
    get description() {
        return this.#description;
    }

    /**
     * @returns {string}
     */
    get link() {
        return this.#link;
    }
}

class SupporterEntryDto {

    #userId;
    #username;
    #timestamp;

    /**
     * @param {Object} json
     */
    constructor(json) {
        this.#userId = json.userId;
        this.#username = json.username;
        this.#timestamp = json.timestamp;
    }

    /**
     * @returns {string}
     */
    get userId() {
        return this.#userId;
    }

    /**
     * @returns {string}
     */
    get username() {
        return this.#username;
    }

    /**
     * @returns {number}
     */
    get timestamp() {
        return this.#timestamp;
    }
}

class GetSupportersResponse {

    #entries;

    /**
     * @param {Object} json
     */
    constructor(json) {
        this.#entries = json.entries.map(entry => new SupporterEntryDto(entry));
    }

    /**
     * @returns {SupporterEntryDto[]}
     */
    get entries() {
        return this.#entries;
    }
}
