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

    /**
     * Builds the URL to repeat this search on the database search page.
     * @returns {string}
     */
    get repeatSearchUrl() {
        const params = new URLSearchParams();
        if (this.#playerName !== null) {
            params.set('playerName', this.#playerName);
        }
        if (this.#playerColor !== null) {
            params.set('playerColor', this.#playerColor);
        }
        if (this.#eventName !== null) {
            params.set('eventName', this.#eventName);
        }
        if (this.#searchStart !== null) {
            params.set('dateStart', this.#searchStart);
        }
        if (this.#searchEnd !== null) {
            params.set('dateEnd', this.#searchEnd);
        }
        if (this.#fen !== null) {
            params.set('fen', this.#fen);
        }
        const queryString = params.toString();
        return `/database/search${queryString ? '?' + queryString : ''}`;
    }

    /**
     * Builds a human-readable summary of the search parameters.
     * @returns {string}
     */
    get searchSummary() {
        const parts = [];
        if (this.#playerName !== null) {
            let playerPart = this.#playerName;
            if (this.#playerColor !== null) {
                playerPart += ` (${this.#playerColor.toLowerCase()})`;
            }
            parts.push(playerPart);
        }
        if (this.#eventName !== null) {
            parts.push(this.#eventName);
        }
        if (this.#searchStart !== null || this.#searchEnd !== null) {
            const start = this.#searchStart ?? '...';
            const end = this.#searchEnd ?? '...';
            parts.push(`${start} → ${end}`);
        }
        if (this.#fen !== null) {
            parts.push(`FEN: ${this.#fen}`);
        }
        if (parts.length === 0) {
            return 'All games';
        }
        return parts.filter(part => part.trim().length > 0).join(', ');
    }

}
