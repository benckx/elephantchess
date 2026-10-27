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

class GameEntryDto {

    #gameId;
    #status;
    #moveIndex;
    #currentFen;
    #userHasToPlay;
    #color;
    #isRated;
    #timeControlCategory;
    #timeControl;
    #opponentUserType;
    #opponentUserId;
    #opponentUsername;
    #outcome;
    #ratingFrom;
    #ratingTo;
    #numberOfMessages;
    #isPreAnalyzed;
    #variant;

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
        this.#status = json.status;
        this.#moveIndex = json.moveIndex;
        this.#currentFen = json.currentFen;
        this.#userHasToPlay = json.userHasToPlay;
        this.#color = json.color;
        this.#isRated = json.isRated;
        this.#timeControlCategory = json.timeControlCategory;
        try {
            this.#timeControl = TimeControl.fromJson(json);
        } catch (_) {
            this.#timeControl = null;
        }
        this.#opponentUserType = json.opponentUserType;
        this.#opponentUserId = json.opponentUserId;
        this.#opponentUsername = json.opponentUsername;
        this.#outcome = json.outcome;
        this.#ratingFrom = json.ratingFrom;
        this.#ratingTo = json.ratingTo;
        this.#created = json.created;
        this.#lastUpdated = json.lastUpdated;
        this.#numberOfMessages = json.numberOfMessages;
        this.#isPreAnalyzed = json.isPreAnalyzed;
        this.#variant = json.variant ?? Variant.XIANGQI;
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
    get gameUrl() {
        return '/game?id=' + this.gameId;
    }

    /**
     * @returns {string}
     */
    get status() {
        return this.#status;
    }

    /**
     * @returns {boolean}
     */
    isCanceled() {
        return this.#status === GameEventType.CANCELED || this.#status === GameEventType.AUTO_CANCELED;
    }

    /**
     * @return {number}
     */
    get fullMoveIndex() {
        return currentMoveIndexToFullMove(this.#moveIndex);
    }

    /**
     * @returns {string}
     */
    get currentFen() {
        return this.#currentFen;
    }

    /**
     * @return {boolean}
     */
    get isUserTurnToPlay() {
        return this.#userHasToPlay;
    }

    /**
     * @return {string|null}
     */
    get color() {
        return this.#color;
    }

    get timeControlCategory() {
        return this.#timeControlCategory;
    }

    /**
     * @return {TimeControl|null}
     */
    get timeControl() {
        return this.#timeControl;
    }

    /**
     * @returns {boolean}
     */
    get isRated() {
        return this.#isRated;
    }

    /**
     * @return {boolean}
     */
    get hasOpponent() {
        return this.#opponentUserId != null && this.#opponentUsername != null;
    }

    /**
     * @returns {string|null}
     */
    get opponentUserType() {
        return this.#opponentUserType;
    }

    /**
     * @returns {string|null}
     */
    get opponentUserId() {
        return this.#opponentUserId;
    }

    /**
     * @return {string|null}
     */
    get opponentUsername() {
        return this.#opponentUsername;
    }

    /**
     * @see UserOutcome
     * @returns {string|null}
     */
    get userOutcome() {
        return this.#outcome;
    }

    /**
     * @return {number}
     */
    get ratingFrom() {
        return this.#ratingFrom;
    }

    /**
     * @return {number|null}
     */
    get ratingDelta() {
        if (this.#ratingFrom != null && this.#ratingTo != null) {
            return this.#ratingTo - this.#ratingFrom;
        } else {
            return null;
        }
    }

    get formattedRatingDelta() {
        let delta = this.ratingDelta;
        if (delta == null) {
            return null;
        } else if (delta > 0) {
            return '+' + delta;
        } else {
            return delta.toString();
        }
    }

    get hasRatingDelta() {
        return this.ratingDelta != null;
    }

    get isRatingUpdatePositive() {
        let delta = this.ratingDelta;
        return delta != null && this.ratingDelta > 0;
    }

    get isRatingUpdateNegative() {
        let delta = this.ratingDelta;
        return delta != null && delta < 0;
    }

    /**
     * UTC timestamp in milliseconds
     *
     * @return {number}
     */
    get created() {
        return this.#created;
    }

    /**
     * UTC timestamp in milliseconds
     *
     * @return {number}
     */
    get lastUpdated() {
        return this.#lastUpdated;
    }

    /**
     * @return {number}
     */
    get numberOfMessages() {
        return Number(this.#numberOfMessages);
    }

    /**
     * @return {boolean}
     */
    get isPreAnalyzed() {
        return this.#isPreAnalyzed;
    }

    /**
     * @return {string}
     */
    get variant() {
        return this.#variant;
    }

}
