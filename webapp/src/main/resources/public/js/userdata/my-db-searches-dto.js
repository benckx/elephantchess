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

class MyDbSearchEntryDto {

    #queryId;
    #updateTime;
    #playerName;
    #playerColor;
    #eventName;
    #searchStart;
    #searchEnd;
    #fen;
    #numberOfResults;

    constructor(json) {
        this.#queryId = json.queryId;
        this.#updateTime = json.updateTime;
        this.#playerName = json.playerName;
        this.#playerColor = json.playerColor;
        this.#eventName = json.eventName;
        this.#searchStart = json.searchStart;
        this.#searchEnd = json.searchEnd;
        this.#fen = json.fen;
        this.#numberOfResults = json.numberOfResults;
    }

    /**
     * @returns {string}
     */
    get queryId() {
        return this.#queryId;
    }

    /**
     * @returns {number}
     */
    get updateTime() {
        return this.#updateTime;
    }

    /**
     * @returns {string|null}
     */
    get playerName() {
        return this.#playerName;
    }

    /**
     * @returns {string|null}
     */
    get playerColor() {
        return this.#playerColor;
    }

    /**
     * @returns {string|null}
     */
    get eventName() {
        return this.#eventName;
    }

    /**
     * @returns {string|null}
     */
    get searchStart() {
        return this.#searchStart;
    }

    /**
     * @returns {string|null}
     */
    get searchEnd() {
        return this.#searchEnd;
    }

    /**
     * @returns {string|null}
     */
    get fen() {
        return this.#fen;
    }

    /**
     * @returns {number}
     */
    get numberOfResults() {
        return this.#numberOfResults;
    }


}
