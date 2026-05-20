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

class BotGameEntryDto {

    #gameId;
    #color;
    #engine;
    #depth;
    #customStartFen;
    #currentFen;
    #status;
    #outcome;
    #moveIndex;
    #isPreAnalyzed;

    /**
     * @type {number}
     */
    #created;

    /**
     * @type {number}
     */
    #lastUpdated;

    constructor(json) {
        this.#gameId = json.gameId;
        this.#color = json.color;
        this.#engine = json.engine;
        this.#depth = Number(json.depth);
        this.#customStartFen = json.customStartFen;
        this.#currentFen = json.currentFen;
        this.#status = json.status;
        this.#outcome = json.outcome;
        this.#moveIndex = json.moveIndex;
        this.#isPreAnalyzed = json.isPreAnalyzed;
        this.#created = json.created;
        this.#lastUpdated = json.lastUpdated;
    }

    /**
     * @return {string}
     */
    get gameId() {
        return this.#gameId;
    }

    /**
     * @return {string}
     */
    get color() {
        return this.#color;
    }

    /**
     * @return {string}
     */
    get gameUrl() {
        return `/playbot?id=${this.gameId}`;
    }

    /**
     * @return {string}
     */
    get formattedEngine() {
        return formatEngineName(this.#engine);
    }

    /**
     * @return {number}
     */
    get depth() {
        return this.#depth;
    }

    /**
     * @return {boolean}
     */
    get hasCustomStartFen() {
        return this.#customStartFen;
    }

    /**
     * @returns {string}
     */
    get currentFen() {
        return this.#currentFen;
    }

    /**
     * @return {string}
     */
    get formattedStatus() {
        switch (this.#status) {
            case GameEventType.CANCELED:
                return 'canceled';
            case GameEventType.AUTO_CANCELED:
                return 'canceled (auto)';
            case GameEventType.CREATED:
                return 'ongoing';
            case GameEventType.CHECKMATED:
                return 'checkmated';
            case GameEventType.STALEMATED:
                return 'stalemated';
            case GameEventType.RESIGNED:
                return 'resigned';
            case GameEventType.AUTO_RESIGNED:
                return 'resigned (auto)';
            default:
                return this.#status.toLowerCase();
        }
    }

    get userOutcome() {
        if (this.#outcome != null) {
            return gameOutcomeToUserOutcome(this.#color, this.#outcome);
        } else {
            return null;
        }
    }

    /**
     * @return {string}
     */
    get formattedOutcome() {
        if (this.#outcome != null) {
            return formatEnumValue(gameOutcomeToUserOutcome(this.#color, this.#outcome));
        } else {
            return '--';
        }
    }

    get fullMoveIndex() {
        return currentMoveIndexToFullMove(this.#moveIndex);
    }

    /**
     * @return {boolean}
     */
    get isPreAnalyzed() {
        return this.#isPreAnalyzed;
    }

    get created() {
        return this.#created;
    }

    get lastUpdated() {
        return this.#lastUpdated;
    }

}
