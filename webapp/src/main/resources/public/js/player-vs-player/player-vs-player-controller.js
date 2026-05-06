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

const WARNING_TIME_OUT = 4_000;

/**
 * Manages the state of the game and timers, and push state changes to the UI (via callbacks).
 * The UI only interacts with this class to fetch game state related data.
 */
class GameController {

    /**
     * @type {string}
     */
    #gameId;

    /**
     * @type {GameDto|null}
     */
    #gameDto = null;

    /**
     * @type {string}
     */
    #gameState;

    #updateClockTimerId = null;

    /**
     * @type {GameClient}
     */
    #client;

    /**
     * @type {WebSocket|null}
     */
    #webSocket = null;

    #inviteeJoinedCallback = () => console.log('invitee joined');
    #stateUpdateCallback = () => console.log('state updated');
    #opponentMoveReceivedCallback = () => console.log('opponent move received');
    #canceledReceivedCallback = () => console.log('game canceled');
    #winCallback = () => console.log('you win');
    #lossCallback = () => console.log('you loose');
    #resignReceivedCallback = () => console.log('resign received');
    #drawReceivedCallback = () => console.log('received draw');
    #drawAcceptedCallback = () => console.log('draw accepted');
    #drawDeclinedCallback = () => console.log('draw declined');
    #updateClocksCallback = () => console.log('update clocks');
    #fetchMovesCallback = (moves) => console.log('fetch moves ' + moves);
    #receivedChatMessages = (chatMessages, acks) => console.log('received chat messages ' + chatMessages);

    /**
     * @param gameId {string}
     * @param initCallback {function()}
     * @param inviteeJoinedCallback {function()}
     * @param stateUpdateCallback {function()} Called when user opens the page (for initialization) and when opponent joins
     * @param opponentMoveReceivedCallback {function()}
     * @param canceledReceivedCallback {function()}
     * @param winCallback {function()}
     * @param lossCallback {function()}
     * @param resignReceivedCallback {function()}
     * @param drawReceivedCallback {function()}
     * @param drawAcceptedCallback {function()}
     * @param drawDeclinedCallback {function()}
     * @param updateClocksCallback {function()}
     * @param fetchMovesCallback {function(HalfMove[])}
     * @param receivedChatMessages {function(ChatMessageDto[], number[])}
     */
    constructor(
        gameId,
        initCallback,
        inviteeJoinedCallback,
        stateUpdateCallback,
        opponentMoveReceivedCallback,
        canceledReceivedCallback,
        winCallback,
        lossCallback,
        resignReceivedCallback,
        drawReceivedCallback,
        drawAcceptedCallback,
        drawDeclinedCallback,
        updateClocksCallback,
        fetchMovesCallback,
        receivedChatMessages
    ) {
        this.#gameId = gameId;
        this.#inviteeJoinedCallback = inviteeJoinedCallback;
        this.#stateUpdateCallback = stateUpdateCallback;
        this.#opponentMoveReceivedCallback = opponentMoveReceivedCallback;
        this.#canceledReceivedCallback = canceledReceivedCallback;
        this.#winCallback = winCallback;
        this.#lossCallback = lossCallback;
        this.#resignReceivedCallback = resignReceivedCallback;
        this.#drawReceivedCallback = drawReceivedCallback;
        this.#drawAcceptedCallback = drawAcceptedCallback;
        this.#drawDeclinedCallback = drawDeclinedCallback;
        this.#updateClocksCallback = updateClocksCallback;
        this.#fetchMovesCallback = fetchMovesCallback;
        this.#receivedChatMessages = receivedChatMessages;
        this.#client = new GameClient(gameId);

        this.#client.getData(gameDto => {
            this.#updateGameDto(gameDto);
            connectToWs();
            if (!this.isGameFinished()) {
                if (this.isGameInProgress()) {
                    // if draw has been proposed to user, reload the pop-up on page refresh
                    if (this.#gameDto.hasReceiveDrawProposition()) {
                        this.#drawReceivedCallback();
                    }
                }
                this.#startUpdateClocks();
            }

            this.#client.getMovesHistory(moves => this.#fetchMovesCallback(moves));
            this.#client.getChatHistory(messages => {
                this.#receivedChatMessages(messages, []);
            });
            initCallback();
        });

        const connectToWs = () => {
            const user = new User();

            if (!user.isIdentified) {
                console.log('userId not found yet, re-attempting in 1 second');
                setTimeout(() => {
                    connectToWs();
                }, 1_000);
                return;
            }

            const handle = openReconnectingWebSocket({
                endpoint: 'pvp/game',
                logLabel: 'pvp/game',
                networkBlockedMessage:
                    'Your network seems to be blocking real-time connections. ' +
                    'The game may not update in real time. ' +
                    'Try a different network, or disable your VPN / antivirus / browser extensions.',
                buildParams: () => new Map([
                    ['gameId', gameId],
                    ['token', getToken()],
                ]),
                onOpen: (ws) => {
                    this.#webSocket = ws;
                },
                onMessage: (e) => {
                    const json = JSON.parse(e.data);
                    const dto = this.#gameDto;
                    const statusHasChanged = json.status !== dto.status;
                    const isWaitingForDrawResponse = dto.isWaitingForDrawPropositionResponse();

                    let newMoveDto = null;
                    if (json.newMove != null) {
                        newMoveDto = new NewMoveDto(json.newMove);
                    }

                    let ratingUpdate = null;
                    if (json.ratingUpdate != null) {
                        ratingUpdate = new RatingUpdateDto(json.ratingUpdate);
                    }

                    let timeRemainingDto = null;
                    if (json.timeRemaining != null) {
                        timeRemainingDto = new TimeRemainingDto(json.timeRemaining);
                    }

                    const chatMessages = [];
                    if (json.chatMessages != null) {
                        for (const message of json.chatMessages) {
                            chatMessages.push(new ChatMessageDto(message));
                        }
                    }

                    this.#gameDto.status = json.status;

                    if (statusHasChanged) {
                        switch (this.#gameDto.status) {
                            case GameEventType.CANCELED:
                                this.#gameDto.updateForCanceled();
                                if (this.#gameDto.userStatus !== UserStatus.INVITER) {
                                    this.#canceledReceivedCallback();
                                }
                                break;
                            case GameEventType.FLAGGED:
                                this.#handleFlagged(ratingUpdate);
                                break;
                            case GameEventType.RESIGNED:
                                this.#handleResignationReceived(ratingUpdate);
                                break;
                            case GameEventType.DRAW_PROPOSED:
                                const isPlayer = dto.userStatus !== UserStatus.SPECTATOR;
                                // TODO: should isPlayer be moved to page logic?
                                if (!isWaitingForDrawResponse && isPlayer) {
                                    this.#gameDto.updateForDrawPropositionReceived(json.drawPropositionUser);
                                    this.#drawReceivedCallback();
                                }
                                break;
                            case GameEventType.DRAW_ACCEPTED:
                                if (isWaitingForDrawResponse) {
                                    dto.updateForDrawResponse(true);
                                    this.#drawAcceptedCallback();
                                }
                                break;
                            case GameEventType.DRAW_DECLINED:
                                if (isWaitingForDrawResponse) {
                                    dto.updateForDrawResponse(false);
                                    this.#drawDeclinedCallback();
                                }
                                break;
                        }
                    }
                    if (json.hasJoined != null) {
                        const hasJoinedStatus = new HasJoinedStatusDto(json.hasJoined);
                        this.#gameDto.updateOpponentHasJoined(hasJoinedStatus);
                        this.#updateGameState();
                        this.#inviteeJoinedCallback();
                    }
                    if (newMoveDto != null) {
                        if (this.#gameDto.moveIndex + 1 === newMoveDto.updatedIndex) {
                            this.#handleOpponentMove(newMoveDto, ratingUpdate);
                        } else if (this.#gameDto.moveIndex !== newMoveDto.updatedIndex) {
                            // if this.#gameDto.moveIndex === newMoveDto.updatedIndex,
                            // player receives their own move, don't need to do anything
                            // but otherwise, it means the game state is incorrect

                            // TODO: should add check that given the index the move comes indeed from the opponent?
                            //  quick fix here is too reload, would be nice to know how often it happens
                            window.location.reload();
                        }

                        if (this.#gameDto.moveIndex === 6) {
                            gtagReportPvpMove3Conversion(window.location.href);
                        }
                    }
                    if (timeRemainingDto != null) {
                        this.#gameDto.updateTimeRemaining(timeRemainingDto);
                        this.#updateClocksCallback();
                    }
                    this.#handleReceivedChatMessages(chatMessages);
                }
            });

            // expose the current socket so sendChat() can write to it
            this.#webSocket = handle.getSocket();
        };
    }

    /**
     * @return {string}
     */
    get gameId() {
        return this.#gameId;
    }

    /**
     * @return {GameDto}
     */
    get gameDto() {
        return this.#gameDto;
    }

    /**
     * @returns {string}
     */
    get gameState() {
        return this.#gameState;
    }

    get fen() {
        return this.#gameDto.fen;
    }

    /**
     * @return {Map<string, string>}
     */
    buildPgnMetadata() {
        return this.#gameDto.buildPgnMetadata();
    }

    /**
     * @returns {boolean}
     */
    isGameInProgress() {
        return isStatusInProgress(this.#gameDto.status);
    }

    /**
     * @returns {boolean}
     */
    isGameFinished() {
        return isStatusFinished(this.#gameDto.status);
    }

    /**
     * @return {UserRatingUpdate|null}
     */
    get userRatingUpdate() {
        if (this.#gameDto != null && this.#gameDto.hasRatingUpdate && this.#gameDto.ratingUpdate.isRated) {
            let ratingUpdate = this.#gameDto.ratingUpdate;
            switch (this.#gameDto.userStatus) {
                case UserStatus.INVITER:
                    return new UserRatingUpdate(ratingUpdate.inviterRatingFrom, ratingUpdate.inviterRatingTo);
                case UserStatus.INVITEE:
                    return new UserRatingUpdate(ratingUpdate.inviteeRatingFrom, ratingUpdate.inviteeRatingTo);
                default:
                    return null;
            }
        }
    }

    /**
     * @param source {string|null}
     * @param sourceId {string|null}
     */
    join(source, sourceId) {
        if (this.#gameDto.status === GameEventType.CREATED && isUserIdentified()) {
            // TODO: what if game already join -> handle error
            this.#client.postJoin(source, sourceId, (color, rating) => {
                this.#gameDto.updateUserHasJoined(new User(), color, rating);
                this.#updateGameState();
            });
        } else {
            UI.pushErrorNotification('You can not join that game', WARNING_TIME_OUT);
        }
    }

    /**
     * @param cb {function()} When the call has been completed, this callback is called. Allows to update UI.
     */
    // TODO: maybe the same cb pattern should be used with other methods instead of calling stateUpdateCallback, which is a bit vague
    cancel(cb) {
        if (this.#gameDto.status === GameEventType.CREATED && isUserIdentified()) {
            this.#client.postCancel(() => {
                this.#gameDto.updateForCanceled();
                this.#updateGameState(true);
                cb();
            });
        } else {
            UI.pushErrorNotification('You can not cancel that game', WARNING_TIME_OUT);
        }
    }

    resign() {
        if (!this.isGameFinished()) {
            this.#gameDto.updateForResignationSent(null);
            this.#updateGameState();
            this.#client.postResign((maybeRatingUpdate) => {
                this.#gameDto.updateForResignationSent(maybeRatingUpdate);
                this.#stateUpdateCallback();
            });
        } else {
            UI.pushErrorNotification('Game has already ended', WARNING_TIME_OUT);
        }
    }

    proposeDraw() {
        if (!this.isGameFinished()) {
            this.#client.postProposeDraw(() => {
                this.#gameDto.updateForDrawPropositionSent();
            });
        } else {
            UI.pushErrorNotification('Game has already ended', WARNING_TIME_OUT);
        }
    }

    /**
     * @param accept {boolean}
     */
    respondToDrawProposition(accept) {
        if (!this.isGameFinished()) {
            this.#client.postRespondToDraw(accept, () => {
                this.#gameDto.updateForDrawResponse(accept);
                this.#updateGameState();
            });
        } else {
            console.warn('game finished');
        }
    }

    /**
     * @param move {HalfMove}
     * @param okCb {function()} Called if move was successfully registered
     */
    registerPlayerMove(move, okCb) {
        if (!this.isGameFinished()) {
            this.#client.postMove(move, (playMoveResponse) => {
                this.#gameDto.updateForUserMove(playMoveResponse);
                this.#updateClocksCallback();
                this.#updateGameState();
                if (this.#gameDto.userHasWon()) {
                    this.#winCallback();
                } else if (this.#gameDto.userHasLost()) {
                    this.#lossCallback();
                }
                okCb();
            });
        } else {
            console.warn('game finished');
        }
    }

    /**
     * @param message {string}
     */
    sendChat(message) {
        if (this.#webSocket != null) {
            this.#webSocket.send(JSON.stringify({"message": message}));
        }
    }

    /**
     * @param userId {string|null}
     */
    isAllowedToSendChat(userId) {
        return (userId != null && this.#gameDto.status === GameEventType.CREATED) || this.#gameDto.isUserPlaying();
    }

    /**
     * @param newMoveDto {NewMoveDto}
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    #handleOpponentMove(newMoveDto, ratingUpdate) {
        this.#gameDto.updateForOpponentMove(newMoveDto, ratingUpdate);
        this.#updateGameState();
        this.#updateClocksCallback();
        this.#opponentMoveReceivedCallback(newMoveDto.move);
        if (this.#gameDto.userStatus !== UserStatus.SPECTATOR && this.#gameDto.userHasLost()) {
            this.#lossCallback();
        }
        if (this.#gameDto.status === GameEventType.PERPETUAL_CHECKING) {
            this.#handlePerpetualCheckingReceived(ratingUpdate);
        }
    }

    /**
     * @param gameDto {GameDto}
     */
    #updateGameDto(gameDto) {
        this.#gameDto = gameDto;
        this.#updateGameState();
    }

    /**
     * @param forceCallback {boolean}. Normally the UI callback is only called on state changes. This flag forces UI callback.
     */
    #updateGameState(forceCallback = false) {
        const before = this.#gameState;

        if (this.isGameFinished()) {
            this.#gameState = GameState.FINISHED;
            this.#stopUpdateClocks();
        } else if (!this.#gameDto.hasBeenJoined()) {
            this.#gameState = GameState.WAITING_FOR_INVITEE;
        } else if (this.#gameDto.isUserTurn()) {
            this.#gameState = GameState.USER_TURN;
        } else {
            this.#gameState = GameState.OPPONENT_TURN;
        }

        if (forceCallback || before !== this.#gameState) {
            this.#stateUpdateCallback();
        }
    }

    // TODO: add increment when there is a move
    #startUpdateClocks() {
        if (this.#gameDto.hasTimeControl()) {
            this.#updateClockTimerId = setIntervalNoDelay(() => {
                this.#gameDto.decrementCounter();
                this.#updateClocksCallback();
            }, 1_000);
        }
    }

    #stopUpdateClocks() {
        clearInterval(this.#updateClockTimerId);
    }

    /**
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    #handleFlagged(ratingUpdate) {
        this.#stopUpdateClocks();
        const clock = this.#gameDto.timeControlClock;
        const redMillis = clock.red.toMillis();
        const blackMillis = clock.black.toMillis();
        if (redMillis < blackMillis) {
            // black wins
            this.#updateForFlagged(Color.BLACK, ratingUpdate);
        } else if (redMillis > blackMillis) {
            // red wins
            this.#updateForFlagged(Color.RED, ratingUpdate);
        } else {
            console.warn('equal remaining counter, case not handled');
        }
    }

    #updateForFlagged(winnerColor, ratingUpdate) {
        this.#stopUpdateClocks();
        this.#gameDto.updateForFlagged(winnerColor, ratingUpdate);
        if (this.#gameDto.userStatus !== UserStatus.SPECTATOR) {
            if (this.#gameDto.colorUserPlaysWith === winnerColor) {
                this.#stateUpdateCallback();
                this.#winCallback();
            } else if (this.#gameDto.colorUserPlaysWith === reverseColor(winnerColor)) {
                this.#stateUpdateCallback();
                this.#lossCallback();
            }
        } else {
            // TODO: it's a workaround for now
            //  we would need to call a service to know which color flagged
            window.location.reload();
        }
    }

    /**
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    #handleResignationReceived(ratingUpdate) {
        this.#stopUpdateClocks();
        if (this.#gameDto.userStatus !== UserStatus.SPECTATOR) {
            const opponentColor = reverseColor(this.#gameDto.colorUserPlaysWith);
            this.#gameDto.updateForResignationReceived(opponentColor, ratingUpdate);
            this.#resignReceivedCallback();
        } else {
            // TODO: it's a workaround for now
            //  we would need to call a service to know which color resigned
            window.location.reload();
        }
    }

    /**
     * @param ratingUpdate {RatingUpdateDto|null}
     */
    #handlePerpetualCheckingReceived(ratingUpdate) {
        this.#stopUpdateClocks();
        if (this.#gameDto.userStatus !== UserStatus.SPECTATOR) {
            const opponentColor = reverseColor(this.#gameDto.colorUserPlaysWith);
            this.#gameDto.updateForPerpetualCheckingReceived(opponentColor, ratingUpdate);
            this.#winCallback();
        } else {
            // TODO: it's a workaround for now
            //  we would need to call a service to know which color got perpetual checks
            window.location.reload();
        }
    }

    /**
     * @param chatMessages {ChatMessageDto[]}
     */
    #handleReceivedChatMessages(chatMessages) {
        const userId = new User().userId;
        if (userId != null && chatMessages.length > 0) {
            const acks = [];
            const receivedMessages = [];

            for (const message of chatMessages) {
                if (message.author.userId === userId) {
                    acks.push(message.index);
                } else {
                    receivedMessages.push(message);
                }
            }
            this.#receivedChatMessages(receivedMessages, acks);
        }
    }

}
