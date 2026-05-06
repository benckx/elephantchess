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

const UserStatus = Object.freeze({
    INVITER: 'INVITER',
    INVITEE: 'INVITEE',
    SPECTATOR: 'SPECTATOR'
});

const GameState = Object.freeze({
    WAITING_FOR_INVITEE: 'WAITING_FOR_INVITEE',
    OPPONENT_TURN: 'OPPONENT_TURN',
    USER_TURN: 'USER_TURN',
    FINISHED: 'FINISHED'
});

class TimeRemainingDto {

    /*
     * @type {number}
     */
    #redMillis = 0;

    /*
     * @type {number}
     */
    #blackMillis = 0;

    constructor(json) {
        this.#redMillis = json.red;
        this.#blackMillis = json.black;
    }

    toString() {
        return `TimeRemainingDto(redMillis=${this.#redMillis}, blackMillis=${this.#blackMillis})`;
    }

    /**
     * @return {TimeControlClock}
     */
    toClock() {
        return new TimeControlClock(this.#redMillis, this.#blackMillis);
    }

}

class TimeControlClock {

    #redMillis = 0;
    #blackMillis = 0;

    constructor(red, black) {
        this.#redMillis = red;
        this.#blackMillis = black;
    }

    /**
     * @return {TimeControlDuration}
     */
    get red() {
        return TimeControlDuration.fromMillis(this.#redMillis);
    }

    /**
     * @return {TimeControlDuration}
     */
    get black() {
        return TimeControlDuration.fromMillis(this.#blackMillis);
    }

    decrement(color) {
        switch (color) {
            case Color.RED:
                this.#redMillis -= 1_000;
                if (this.#redMillis < 0) {
                    this.#redMillis = 0;
                }
                break;
            case Color.BLACK:
                this.#blackMillis -= 1_000;
                if (this.#blackMillis < 0) {
                    this.#blackMillis = 0;
                }
                break;
            default:
                throw new Error(`Invalid color: ${color}`);
        }
    }

    /**
     *
     * @param color {string}
     * @param millis {number}
     */
    increment(color, millis) {
        switch (color) {
            case Color.RED:
                this.#redMillis += millis;
                break;
            case Color.BLACK:
                this.#blackMillis += millis;
                break;
            default:
                throw new Error(`Invalid color: ${color}`);
        }
    }

    /**
     *
     * @param base {TimeControlDuration}
     * @param color {string}
     */
    resetToBaseTime(base, color) {
        switch (color) {
            case Color.RED:
                this.#redMillis = base.toMillis();
                break;
            case Color.BLACK:
                this.#blackMillis = base.toMillis();
                break;
            default:
                throw new Error(`Invalid color: ${color}`);
        }
    }

}

/**
 * Game fields as they come from the backend
 * Doesn't contain any logic per se (except for the 'smart' getter/setter)
 */
class GameDto {

    #userId;
    #inviterId
    #inviterUsername;
    #inviterRating;
    #inviterUserType;
    #inviteeId;
    #inviteeUsername;
    #inviteeRating;
    #inviteeUserType;
    #inviterColor;
    #isRated;
    #created;
    #timeControlCategory;
    #timeControl;
    #timeControlMode;
    #allowGuestsToJoin;
    #privateInvite;
    #fen;
    #moveIndex;
    #timeControlClock;
    #gameEventType = null;
    #outcome = null;
    #drawPropositionUser = null;
    #ratingUpdate = null;

    constructor(userId, json) {
        this.#userId = userId;
        this.#inviterId = json.inviterId;
        this.#inviterRating = json.inviterRating;
        this.#inviterUserType = json.inviterUserType;
        this.#inviterUsername = json.inviterUsername;
        this.#inviteeId = json.inviteeId;
        this.#inviteeUsername = json.inviteeUsername;
        this.#inviteeRating = json.inviteeRating;
        this.#inviteeUserType = json.inviteeUserType;
        this.#inviterColor = json.inviterColor;
        this.#isRated = json.isRated;
        this.#created = json.created;
        this.#timeControlCategory = json.timeControlCategory;
        this.#timeControl = TimeControl.fromJson(json);
        this.#timeControlMode = json.timeControlMode;
        this.#allowGuestsToJoin = json.allowGuestsToJoin;
        this.#privateInvite = json.privateInvite;
        this.#fen = json.fen;
        this.#moveIndex = Number(json.moveIndex);
        if (json.timeRemaining != null) {
            this.#timeControlClock = new TimeRemainingDto(json.timeRemaining).toClock();
        }
        this.#gameEventType = json.gameEventType;
        this.#outcome = json.outcome;
        if (json.ratingUpdate != null) {
            this.#ratingUpdate = new RatingUpdateDto(json.ratingUpdate);
        }
        this.#drawPropositionUser = json.drawPropositionUser;
    }

    /**
     * @return {string}
     */
    get inviterId() {
        return this.#inviterId;
    }

    /**
     * @returns {string}
     */
    get inviterUsername() {
        return this.#inviterUsername;
    }

    /**
     * @returns {number}
     */
    get inviterRating() {
        return this.#inviterRating;
    }

    /**
     * @returns {string}
     */
    get inviterUserType() {
        return this.#inviterUserType;
    }

    /**
     * @returns {string|null}
     */
    get inviteeId() {
        return this.#inviteeId;
    }

    /**
     * @returns {string|null}
     */
    get inviteeUsername() {
        return this.#inviteeUsername;
    }

    /**
     * @returns {number|null}
     */
    get inviteeRating() {
        return this.#inviteeRating;
    }

    /**
     * @returns {string|null}
     */
    get inviteeUserType() {
        return this.#inviteeUserType;
    }

    /**
     * Have colors been attributed?
     *
     * @return {boolean}
     */
    get hasInviterColor() {
        return this.inviterColor != null;
    }

    /**
     * @return {string|null}
     */
    get inviterColor() {
        switch (this.#inviterColor) {
            case Color.RED:
            case Color.BLACK:
                return this.#inviterColor;
            default:
                return null;
        }
    }

    /**
     * @returns {boolean}
     */
    get isRated() {
        return this.#isRated;
    }

    /**
     * @returns {number}
     */
    get created() {
        return this.#created;
    }

    /**
     * @return {string}
     */
    get timeControlCategory() {
        return this.#timeControlCategory;
    }

    /**
     * @return {boolean}
     */
    hasTimeControl() {
        return this.#timeControl != null;
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
    get allowGuestsToJoin() {
        return this.#allowGuestsToJoin;
    }

    /**
     * @returns {boolean}
     */
    get isPrivateInvite() {
        return this.#privateInvite;
    }

    /**
     * @returns {string}
     */
    get fen() {
        return this.#fen;
    }

    /**
     * @returns {number}
     */
    get moveIndex() {
        return this.#moveIndex;
    }

    /**
     * @return {TimeControlClock|null}
     */
    get timeControlClock() {
        return this.#timeControlClock;
    }

    /**
     * @returns {string|null}
     */
    get outcome() {
        return this.#outcome;
    }

    /**
     * @param outcome {string}
     */
    set outcome(outcome) {
        this.#outcome = outcome;
    }

    /**
     * @return {string|null}
     */
    get status() {
        return this.#gameEventType;
    }

    /**
     * @param status {string}
     */
    set status(status) {
        this.#gameEventType = status;
    }

    get formattedStatusOfFinishedGame() {
        switch (this.status) {
            case GameEventType.CREATED:
            case GameEventType.JOINED:
            case GameEventType.DRAW_PROPOSED:
            case GameEventType.DRAW_DECLINED:
                console.warn('Game is finished but status is ' + this.status);
                return '--';
            case GameEventType.CANCELED:
                return 'Canceled';
            case GameEventType.AUTO_CANCELED:
                return 'Canceled (auto)';
            case GameEventType.CHECKMATED:
                return 'Checkmate';
            case GameEventType.STALEMATED:
                return 'Stalemate';
            case GameEventType.DRAW_ACCEPTED:
                return 'Draw';
            case GameEventType.RESIGNED:
                if (this.userHasWon()) {
                    return 'Opponent resigned';
                } else if (this.userHasLost()) {
                    return 'You resigned';
                } else {
                    return 'Resigned';
                }
            case GameEventType.FLAGGED:
                if (this.userHasLost()) {
                    return 'Lost on time';
                } else {
                    return 'Won on time';
                }
            case GameEventType.PERPETUAL_CHECKING:
                if (this.userHasLost()) {
                    return 'Lost by perpetual checking';
                } else {
                    return 'Won by perpetual checking';
                }
            default:
                return '??';
        }
    }

    /**
     * Which color has to play now (i.e. whose turn is it)
     * @return {string}
     */
    get colorToPlay() {
        return this.#moveIndex % 2 === 0 ? Color.RED : Color.BLACK;
    }

    /**
     * @return {string}
     */
    get userStatus() {
        if (this.#userId == null) {
            return UserStatus.SPECTATOR;
        } else if (this.#userId === this.#inviterId) {
            return UserStatus.INVITER;
        } else if (this.#userId === this.#inviteeId) {
            return UserStatus.INVITEE;
        } else {
            return UserStatus.SPECTATOR;
        }
    }

    /**
     * @return {string|null}
     */
    get colorUserPlaysWith() {
        switch (this.userStatus) {
            case UserStatus.INVITER:
                // can be null if inviter picked 'any color' option and invitee has not joined yet
                return this.#inviterColor;
            case UserStatus.INVITEE:
                // if user is the invitee, then he joined and colors are guaranteed to have been determined
                return reverseColor(this.#inviterColor);
            default:
                return null;
        }
    }

    /*
     * @return {string|null}
     */
    get opponentColor() {
        let userColor = this.colorUserPlaysWith;
        if (userColor != null) {
            return reverseColor(userColor);
        } else {
            return null;
        }
    }

    /**
     * @return {string|null}
     */
    get redPlayerUserId() {
        if (this.inviterColor == null) {
            return null;
        } else {
            return this.inviterColor === Color.RED ? this.inviterId : this.inviteeId;
        }
    }

    /**
     * @return {string|null}
     */
    get redPlayerUsername() {
        if (this.inviterColor == null) {
            return null;
        } else {
            return this.inviterColor === Color.RED ? this.inviterUsername : this.inviteeUsername;
        }
    }

    /**
     * @return {string|null}
     */
    get blackPlayerUserId() {
        if (this.inviterColor == null) {
            return null;
        } else {
            return this.inviterColor === Color.BLACK ? this.inviterId : this.inviteeId;
        }
    }

    /**
     * @return {string|null}
     */
    get blackPlayerUsername() {
        if (this.inviterColor == null) {
            return null;
        } else {
            return this.inviterColor === Color.BLACK ? this.inviterUsername : this.inviteeUsername;
        }
    }

    isUserPlaying() {
        return this.userStatus !== UserStatus.SPECTATOR;
    }

    isUserTurn() {
        return this.colorUserPlaysWith === this.colorToPlay;
    }

    /**
     * @return {boolean}
     */
    hasReceiveDrawProposition() {
        const userStatus = this.userStatus;
        const receivedDrawAsInviter = userStatus === UserStatus.INVITER && this.#hasInviteeSentDraw();
        const receivedDrawAsInvitee = userStatus === UserStatus.INVITEE && this.#hasInviterSentDraw();
        return receivedDrawAsInviter || receivedDrawAsInvitee;
    }

    /**
     * @return {boolean}
     */
    isWaitingForDrawPropositionResponse() {
        const userStatus = this.userStatus;
        const sentDrawAsInviter = userStatus === UserStatus.INVITER && this.#hasInviterSentDraw();
        const sentDrawAsInvitee = userStatus === UserStatus.INVITEE && this.#hasInviteeSentDraw();
        return sentDrawAsInviter || sentDrawAsInvitee;
    }

    #hasInviterSentDraw() {
        return this.#drawPropositionUser === this.#inviterId
            && this.#gameEventType === GameEventType.DRAW_PROPOSED;
    }

    #hasInviteeSentDraw() {
        return this.#drawPropositionUser === this.#inviteeId
            && this.#gameEventType === GameEventType.DRAW_PROPOSED;
    }

    updateForCanceled() {
        this.#gameEventType = GameEventType.CANCELED;
    }

    /**
     * @param hasJoinedStatus {HasJoinedStatusDto}
     */
    updateOpponentHasJoined(hasJoinedStatus) {
        this.#inviteeId = hasJoinedStatus.inviteeId;
        this.#inviteeUsername = hasJoinedStatus.inviteeUsername;
        this.#inviteeRating = hasJoinedStatus.inviteeRating;
        this.#inviteeUserType = hasJoinedStatus.inviteeUserType;
        this.#inviterColor = hasJoinedStatus.inviterColor;
        this.#gameEventType = GameEventType.JOINED;
    }

    /**
     * @param user {User}
     * @param inviteeColor {string}
     * @param inviteeRating {number}
     */
    updateUserHasJoined(user, inviteeColor, inviteeRating) {
        if (this.#userId == null) {
            console.log('User has joined but userId is null');
            this.#userId = user.userId;
        }
        this.#inviteeId = this.#userId;
        this.#inviteeUsername = user.username;
        this.#inviteeRating = inviteeRating;
        this.#inviterColor = inviteeColor;
        this.#gameEventType = GameEventType.JOINED;
    }

    /**
     * Handle state change triggered by the user playing a move
     *
     * If that move results in checkmate or stalemate,
     * outcome will be updated and ratingUpdate might be updated
     * (depending on whether the game is rating)
     *
     * @param playMoveResponse {PlayMoveResponse}
     */
    updateForUserMove(playMoveResponse) {
        const wasMate = this.isMate();
        this.#fen = playMoveResponse.updatedFen;
        this.#moveIndex = playMoveResponse.updatedIndex;
        this.#gameEventType = playMoveResponse.gameEventType;
        if (playMoveResponse.ratingUpdate != null) {
            this.#ratingUpdate = playMoveResponse.ratingUpdate;
        }
        this.#updateClockForIncrement(this.colorUserPlaysWith);
        this.#updateClockIfMoveTimeGame();
        if (!wasMate && this.isMate()) {
            this.#outcome = winnerColorToOutcome(this.colorUserPlaysWith);
        } else if (playMoveResponse.gameEventType === GameEventType.PERPETUAL_CHECKING) {
            this.#outcome = loserColorToOutcome(this.colorUserPlaysWith);
        }
    }

    /**
     * @param newMoveDto {NewMoveDto}
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    updateForOpponentMove(newMoveDto, ratingUpdate) {
        this.#updateNewMoveData(newMoveDto, ratingUpdate);
        this.#updateClockForIncrement(this.opponentColor);
        this.#updateClockIfMoveTimeGame();
        if (this.isMate()) {
            this.#outcome = loserColorToOutcome(this.colorToPlay);
        }
    }

    /**
     * Re-sync clock
     *
     * @param timeRemainingDto {TimeRemainingDto}
     */
    updateTimeRemaining(timeRemainingDto) {
        this.#timeControlClock = timeRemainingDto.toClock();
    }

    decrementCounter() {
        if (isStatusInProgress(this.#gameEventType)) {
            this.#timeControlClock?.decrement(this.colorToPlay);
        }
    }

    #updateClockIfMoveTimeGame() {
        if (this.#timeControlMode === TimeControlMode.MOVE_TIME) {
            let base = this.#timeControl?.base;
            switch (this.colorToPlay) {
                case Color.RED:
                    this.#timeControlClock?.resetToBaseTime(base, Color.BLACK);
                    break;
                case Color.BLACK:
                    this.#timeControlClock?.resetToBaseTime(base, Color.RED);
                    break;
                default:
                    throw new Error(`Invalid color: ${this.colorToPlay}`);
            }
        }
    }

    /**
     * @param color {string}
     */
    #updateClockForIncrement(color) {
        let increment = this.#timeControl?.increment;
        if (increment != null) {
            let incrementMillis = increment.toMillis();
            this.#timeControlClock?.increment(color, incrementMillis);
        }
    }

    /**
     * @param newMoveDto {NewMoveDto}
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    #updateNewMoveData(newMoveDto, ratingUpdate) {
        this.#fen = newMoveDto.updatedFen;
        this.#moveIndex = newMoveDto.updatedIndex;
        if (ratingUpdate != null) {
            this.#ratingUpdate = ratingUpdate;
        }
    }

    /**
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    updateForResignationSent(ratingUpdate) {
        this.#outcome = loserColorToOutcome(this.colorUserPlaysWith);
        this.#gameEventType = GameEventType.RESIGNED;
        if (ratingUpdate != null) {
            this.#ratingUpdate = ratingUpdate;
        }
    }

    /**
     * @param resigningColor {string}
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    updateForResignationReceived(resigningColor, ratingUpdate) {
        this.#outcome = loserColorToOutcome(resigningColor);
        this.#gameEventType = GameEventType.RESIGNED;
        if (ratingUpdate != null) {
            this.#ratingUpdate = ratingUpdate;
        }
    }

    /**
     * @param loserColor {string}
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    updateForPerpetualCheckingReceived(loserColor, ratingUpdate) {
        this.#outcome = loserColorToOutcome(loserColor);
        this.#gameEventType = GameEventType.PERPETUAL_CHECKING;
        if (ratingUpdate != null) {
            this.#ratingUpdate = ratingUpdate;
        }
    }

    updateForDrawPropositionSent() {
        this.#gameEventType = GameEventType.DRAW_PROPOSED;
        this.#drawPropositionUser = this.#userId;
    }

    /**
     * @param user {string}
     */
    updateForDrawPropositionReceived(user) {
        this.#gameEventType = GameEventType.DRAW_PROPOSED;
        this.#drawPropositionUser = user;
    }

    /**
     * @param accepted {boolean}
     */
    updateForDrawResponse(accepted) {
        if (accepted) {
            this.#gameEventType = GameEventType.DRAW_ACCEPTED;
            this.#outcome = Outcome.DRAW;
        } else {
            this.#gameEventType = GameEventType.DRAW_DECLINED;
            this.#drawPropositionUser = null;
        }
    }

    /**s
     * @param winnerColor {string}
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    updateForFlagged(winnerColor, ratingUpdate) {
        this.#outcome = winnerColorToOutcome(winnerColor);
        this.#gameEventType = GameEventType.FLAGGED;
        if (ratingUpdate != null) {
            this.#ratingUpdate = ratingUpdate;
        }
    }

    hasBeenJoined() {
        return this.#inviteeId !== null;
    }

    isCanceled() {
        return this.#gameEventType === GameEventType.CANCELED || this.#gameEventType === GameEventType.AUTO_CANCELED;
    }

    isCheckmate() {
        return this.#gameEventType === GameEventType.CHECKMATED;
    }

    isStalemate() {
        return this.#gameEventType === GameEventType.STALEMATED;
    }

    isMate() {
        return this.isCheckmate() || this.isStalemate();
    }

    userHasWon() {
        const userColor = this.colorUserPlaysWith;
        return (userColor === Color.RED && this.#outcome === Outcome.RED_WINS) ||
            (userColor === Color.BLACK && this.#outcome === Outcome.BLACK_WINS);
    }

    userHasLost() {
        const userColor = this.colorUserPlaysWith;
        return (userColor === Color.RED && this.#outcome === Outcome.BLACK_WINS) ||
            (userColor === Color.BLACK && this.#outcome === Outcome.RED_WINS);
    }

    get hasRatingUpdate() {
        return this.#ratingUpdate != null;
    }

    /**
     * @return {RatingUpdateDto|null}
     */
    get ratingUpdate() {
        return this.#ratingUpdate;
    }

    /**
     * @return {Map<string, string>}
     */
    buildPgnMetadata() {
        const metadata = new Map();
        metadata.set('Site', window.location.href);
        metadata.set('Date', formatTimestampToPgnDate(this.created));

        const redPlayerUsername = this.redPlayerUsername;
        if (redPlayerUsername != null) {
            metadata.set('White', redPlayerUsername);
        }

        const blackPlayerUsername = this.blackPlayerUsername;
        if (blackPlayerUsername != null) {
            metadata.set('Black', blackPlayerUsername);
        }

        switch (this.outcome) {
            case Outcome.RED_WINS:
                metadata.set('Result', '1-0');
                break;
            case Outcome.BLACK_WINS:
                metadata.set('Result', '0-1');
                break;
            case Outcome.DRAW:
                metadata.set('Result', '1/2-1/2');
                break;
        }

        if (this.hasTimeControl()) {
            metadata.set('TimeControl', this.timeControl.printPgnFormat());
        }

        if (this.inviterRating != null && this.inviteeRating != null) {
            switch (this.inviterColor) {
                case Color.RED:
                    metadata.set('WhiteElo', this.inviterRating);
                    metadata.set('BlackElo', this.inviteeRating);
                    break;
                case Color.BLACK:
                    metadata.set('WhiteElo', this.inviteeRating);
                    metadata.set('BlackElo', this.inviterRating);
                    break;
            }
        }

        metadata.set('Variant', 'Xiangqi');

        switch (this.status) {
            case GameEventType.FLAGGED:
                metadata.set('Termination', 'time forfeit');
                break;
            case GameEventType.RESIGNED:
                metadata.set('Termination', 'abandoned');
                break;
            case GameEventType.CHECKMATED:
            case GameEventType.STALEMATED:
                metadata.set('Termination', 'normal');
                break;
        }

        metadata.set('Mode', 'ICS');
        return metadata;
    }
}

class HasJoinedStatusDto {

    #inviteeId;
    #inviteeUsername;
    #inviteeRating;
    #inviteeUserType;
    #inviterColor;
    #status;

    constructor(json) {
        this.#inviteeId = json.inviteeId;
        this.#inviteeUsername = json.inviteeUsername;
        this.#inviteeRating = json.inviteeRating;
        this.#inviteeUserType = json.inviteeUserType;
        this.#inviterColor = json.inviterColor;
        this.#status = json.status;
    }

    /**
     * @return {string|null}
     */
    get inviteeId() {
        return this.#inviteeId;
    }

    /**
     * @return {string|null}
     */
    get inviteeUsername() {
        return this.#inviteeUsername;
    }

    /**
     * @return {number|null}
     */
    get inviteeRating() {
        return this.#inviteeRating;
    }

    /**
     * @return {string|null}
     */
    get inviteeUserType() {
        return this.#inviteeUserType;
    }

    /**
     * @return {string|null}
     */
    get inviterColor() {
        return this.#inviterColor;
    }

    hasJoined() {
        return this.#inviteeId !== null &&
            this.#inviteeUsername !== null &&
            this.#inviteeRating !== null &&
            this.#inviterColor !== null &&
            this.#status === GameEventType.JOINED;
    }

}

class RatingUpdateDto {

    #isRated;
    #inviterRatingFrom;
    #inviterRatingTo;
    #inviteeRatingFrom;
    #inviteeRatingTo;

    constructor(json) {
        this.#isRated = json.isRated;
        this.#inviterRatingFrom = json.inviterRatingFrom;
        this.#inviterRatingTo = json.inviterRatingTo;
        this.#inviteeRatingFrom = json.inviteeRatingFrom;
        this.#inviteeRatingTo = json.inviteeRatingTo;
    }

    get isRated() {
        return this.#isRated;
    }

    get inviterRatingFrom() {
        return this.#inviterRatingFrom;
    }

    get inviterRatingTo() {
        return this.#inviterRatingTo;
    }

    get inviteeRatingFrom() {
        return this.#inviteeRatingFrom;
    }

    get inviteeRatingTo() {
        return this.#inviteeRatingTo;
    }

    /**
     * @return {number}
     */
    get inviterDelta() {
        return this.#inviterRatingTo - this.#inviterRatingFrom;
    }

    /**
     * @return {number}
     */
    get inviteeDelta() {
        return this.#inviteeRatingTo - this.#inviteeRatingFrom;
    }

    toString() {
        return `RatingUpdateDto(isRated=${this.#isRated}, inviterRatingFrom=${this.#inviterRatingFrom}, inviterRatingTo=${this.#inviterRatingTo}, inviteeRatingFrom=${this.#inviteeRatingFrom}, inviteeRatingTo=${this.#inviteeRatingTo})`;
    }

}

class UserRatingUpdate {

    #ratingFrom;
    #ratingTo;

    constructor(ratingFrom, ratingTo) {
        this.#ratingFrom = ratingFrom;
        this.#ratingTo = ratingTo;
    }

    get ratingFrom() {
        return this.#ratingFrom;
    }

    get delta() {
        return this.#ratingTo - this.#ratingFrom;
    }

    get isActuallyUpdated() {
        return Math.abs(this.delta) > 0;
    }

}

class NewMoveDto {

    /**
     * @type {HalfMove}
     */
    #move;

    /**
     * @type {number}
     */
    #updatedIndex;

    /**
     * @type {string}
     */
    #updatedFen;

    constructor(json) {
        this.#move = HalfMove.parseUci(json.move);
        this.#updatedIndex = Number(json.updatedIndex);
        this.#updatedFen = json.updatedFen;
    }

    get move() {
        return this.#move;
    }

    get updatedIndex() {
        return this.#updatedIndex;
    }

    get updatedFen() {
        return this.#updatedFen;
    }

    toString() {
        return `NewMoveDto(move=${this.#move}, updatedIndex=${this.#updatedIndex}, updatedFen=${this.#updatedFen})`;
    }

}

class PlayMoveResponse {

    #move;
    #updatedIndex;
    #updatedFen;
    #gameEventType = null;
    #ratingUpdate = null;

    constructor(json) {
        this.#move = HalfMove.parseUci(json.move);
        this.#updatedIndex = Number(json.updatedIndex);
        this.#updatedFen = json.updatedFen;
        if (json.gameEventType != null) {
            this.#gameEventType = json.gameEventType;
        }
        if (json.ratingUpdate != null) {
            this.#ratingUpdate = new RatingUpdateDto(json.ratingUpdate);
        }
    }

    /**
     * @returns {string}
     */
    get move() {
        return this.#move;
    }

    /**
     * @returns {number}
     */
    get updatedIndex() {
        return this.#updatedIndex;
    }

    /**
     * @returns {string}
     */
    get updatedFen() {
        return this.#updatedFen;
    }

    /**
     * @returns {GameEventType|null}
     */
    get gameEventType() {
        return this.#gameEventType;
    }

    /**
     * @returns {RatingUpdateDto|null}
     */
    get ratingUpdate() {
        return this.#ratingUpdate;
    }

    toString() {
        return `PlayMoveResponse(move=${this.#move}, updatedIndex=${this.#updatedIndex}, updatedFen=${this.#updatedFen}, gameEventType=${this.#gameEventType}, ratingUpdate=${this.#ratingUpdate})`;
    }

}

class ChatMessageDto {

    #index;
    #author;
    #messageTime;
    #content;

    /**
     * @param {Object} json
     * @param {number} json.index
     * @param {UserDto} json.author
     * @param {number} json.messageTime
     * @param {string} json.content
     */
    constructor(json) {
        this.#index = json.index;
        this.#author = new UserDto(json.author);
        this.#messageTime = json.messageTime;
        this.#content = json.content;
    }

    /**
     * @returns {number}
     */
    get index() {
        return this.#index;
    }

    /**
     * @returns {UserDto}
     */
    get author() {
        return this.#author;
    }

    /**
     * @returns {number}
     */
    get messageTime() {
        return this.#messageTime;
    }

    /**
     * @returns {string}
     */
    get content() {
        return this.#content;
    }

    toString() {
        return `ChatMessage(index=${this.#index}, author=${this.#author}, messageTime=${this.#messageTime}, content=${this.#content})`;
    }
}
