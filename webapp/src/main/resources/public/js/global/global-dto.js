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

class LeaderboardEntryDto {

    #username;
    #countryCode;
    #rating;
    #maxRating;
    #totalPlayed;
    #lastPlayed;
    #solvedRate;
    #failedRate;

    constructor(json) {
        this.#username = json.username;
        this.#countryCode = json.countryCode;
        this.#rating = json.last;
        this.#maxRating = json.max;
        this.#totalPlayed = json.total;
        this.#lastPlayed = json.lastPlayed;
        this.#solvedRate = json.solvedRate;
        this.#failedRate = json.failedRate;
    }

    /**
     * @return {string}
     */
    get username() {
        return this.#username;
    }

    /**
     * @return {string|null}
     */
    get countryCode() {
        return this.#countryCode;
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

    /**
     * @return {string}
     */
    get formattedLastPlayed() {
        return formatTimestampToShortDate(this.#lastPlayed);
    }

    /**
     * @return {number}
     */
    get solvedRate() {
        return this.#solvedRate;
    }

    /**
     * @return {number}
     */
    get failedRate() {
        return this.#failedRate;
    }

    /**
     * @param json {string}
     * @return {LeaderboardEntryDto[]}
     */
    static parseJson(json) {
        let result = [];
        for (let i = 0; i < json.entries.length; i++) {
            result.push(new LeaderboardEntryDto(json.entries[i]));
        }
        return result;
    }

}

class PlayerVsPlayerLeaderboardEntryDto {

    #timeControlCategory;
    #userId;
    #username;
    #countryCode;
    #rating;
    #totalPlayed;
    #lastPlayed;

    constructor(json) {
        this.#timeControlCategory = json.category;
        this.#userId = json.userId;
        this.#username = json.username;
        this.#countryCode = json.countryCode;
        this.#rating = Number(json.rating);
        this.#totalPlayed = Number(json.totalPlayed);
        this.#lastPlayed = Number(json.lastPlayed);
    }

    /**
     * @return {string}
     */

    get timeControlCategory() {
        return this.#timeControlCategory;
    }

    /**
     * @return {number}
     */
    get userId() {
        return this.#userId;
    }

    /**
     * @return {string}
     */
    get username() {
        return this.#username;
    }

    /**
     * @return {string|null}
     */
    get countryCode() {
        return this.#countryCode;
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
    get totalPlayed() {
        return this.#totalPlayed;
    }

    /**
     * @returns {number}
     */
    get lastPlayed() {
        return this.#lastPlayed;
    }

    /**
     * @param json {string}
     * @return {PlayerVsPlayerLeaderboardEntryDto[]}
     */
    static parseJson(json) {
        let result = [];
        for (let i = 0; i < json.entries.length; i++) {
            result.push(new PlayerVsPlayerLeaderboardEntryDto(json.entries[i]));
        }
        return result;
    }

}
