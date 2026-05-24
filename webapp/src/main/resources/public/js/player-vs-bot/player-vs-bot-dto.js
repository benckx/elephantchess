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

class BotGameSpectatorUpdateDto {

    #moveIndex;

    /**
     * @type {HalfMove[]}
     */
    #newMoves = [];


    /**
     * @type {string}
     */
    #status;

    /**
     * @type {string|null}
     */
    #outcome;

    /**
     * @param json {object}
     */
    constructor(json) {
        this.#moveIndex = Number(json.moveIndex);
        for (let i = 0; i < json.newMoves.length; i++) {
            const uci = json.newMoves[i];
            this.#newMoves.push(HalfMove.parseUci(uci));
        }
        this.#status = json.status;
        this.#outcome = json.outcome;
    }

    /**
     * @return {number}
     */
    get moveIndex() {
        return this.#moveIndex;
    }

    /**
     * @return {HalfMove[]}
     */
    get newMoves() {
        return this.#newMoves;
    }

    /**
     * @returns {string}
     */
    get status() {
        return this.#status;
    }

    /**
     * @return {string|null}
     */
    get outcome() {
        return this.#outcome;
    }

}

class BotGameDto {

    #userId;
    #username;
    #userType;
    #userColor;
    #engine;
    #depth;
    #startFen;
    #variant;
    #status;
    #moveIndex;
    #fen;
    #created;
    #lastUpdated;
    #outcome;

    constructor(json) {
        this.#userId = json.userId;
        this.#username = json.username;
        this.#userType = json.userType;
        this.#userColor = json.userColor;
        this.#engine = json.engine;
        this.#depth = Number(json.depth);
        this.#startFen = json.startFen;
        if (this.#startFen == null) {
            this.#startFen = DEFAULT_START_FEN;
        }
        this.#variant = json.variant ?? Variant.XIANGQI;
        this.#status = json.status;
        this.#moveIndex = Number(json.moveIndex);
        this.#fen = json.fen;
        this.#created = Number(json.created);
        this.#lastUpdated = Number(json.lastUpdated);
        this.#outcome = json.outcome;
    }

    /**
     * Since the introduction of guest users, it's not possible to have no userId anymore,
     * but older games may have null userId.
     *
     * @return {boolean}
     */
    get isAnonymous() {
        return this.#userId == null || this.#username == null;
    }

    /**
     * userId of user who created the game
     *
     * @return {string|null}
     */
    get userId() {
        return this.#userId;
    }

    /**
     * @return {string|null}
     */
    get username() {
        return this.#username;
    }

    /**
     * @return {string}
     */
    get userType() {
        return this.#userType;
    }

    /**
     * @return {string}
     */
    get userColor() {
        return this.#userColor;
    }

    /**
     * @return {string}
     */
    get engine() {
        return this.#engine;
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
     * @return {string|null}
     */
    get startFen() {
        return this.#startFen;
    }

    /**
     * @return {string}
     */
    get variant() {
        return this.#variant;
    }

    /**
     * @return {boolean}
     */
    get isManchu() {
        return this.#variant === Variant.MANCHU;
    }

    /**
     * @return {string}
     */
    get status() {
        return this.#status;
    }

    /**
     * @param value {string}
     */
    set status(value) {
        this.#status = value;
    }

    /**
     * @return {number}
     */
    get moveIndex() {
        return this.#moveIndex;
    }

    /**
     * @param value {number}
     */
    set moveIndex(value) {
        this.#moveIndex = value;
    }

    /**
     * @return {string}
     */
    get fen() {
        return this.#fen;
    }

    /**
     * @return {string}
     */
    get formattedCreated() {
        return formatTimestampToDateTime(this.#created);
    }

    /**
     * @return {number}
     */
    get lastUpdatedMillis() {
        return this.#lastUpdated;
    }

    /**
     * @return {string|null}
     */
    get outcome() {
        return this.#outcome;
    }

    /**
     * @param value {string}
     */
    set outcome(value) {
        this.#outcome = value;
    }

    /**
     * @return {null|string}
     */
    get userOutcome() {
        if (this.outcome != null) {
            return gameOutcomeToUserOutcome(this.userColor, this.outcome);
        } else {
            return null;
        }
    }

    /**
     * @return {boolean}
     */
    isInProgress() {
        return this.#status === GameEventType.CREATED;
    }

    /**
     * Has the user played 1 move
     * @return {boolean}
     */
    hasUserPlayed() {
        return this.#moveIndex > 0 && this.#userColor === Color.RED ||
            this.#moveIndex > 1 && this.#userColor === Color.BLACK;
    }

    cancel() {
        this.#status = GameEventType.CANCELED;
    }

    resign() {
        this.#status = GameEventType.RESIGNED;
        switch (this.#userColor) {
            case Color.RED:
                this.#outcome = Outcome.BLACK_WINS;
                break;
            case Color.BLACK:
                this.#outcome = Outcome.RED_WINS;
                break;
        }
    }

}

class PlayMoveResponseDto {

    /**
     * @type {string}
     */
    #fen;

    /**
     * @type {number}
     */
    #position;

    /**
     * @type {HalfMove|null}
     */
    #botMove;

    /**
     * @type {string|null}
     */
    #statusUpdate;

    constructor(json) {
        this.#fen = json.fen;
        this.#position = Number(json.position);
        if (json.botMove != null) {
            this.#botMove = HalfMove.parseUci(json.botMove);
        } else {
            this.#botMove = null;
        }
        this.#statusUpdate = json.statusUpdate;
    }

    get botMove() {
        return this.#botMove;
    }

    get position() {
        return this.#position;
    }

    get statusUpdate() {
        return this.#statusUpdate;
    }

    hasGameEnded() {
        return this.#statusUpdate === GameEventType.CHECKMATED ||
            this.#statusUpdate === GameEventType.STALEMATED;
    }

    isBotVictory() {
        return this.hasGameEnded() && this.#botMove != null;
    }

    isUserVictory() {
        return this.hasGameEnded() && this.#botMove == null;
    }

}
