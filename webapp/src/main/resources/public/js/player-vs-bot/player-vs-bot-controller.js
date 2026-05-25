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

class PlayerVsBotController {

    /**
     * @type {string}
     */
    #gameId;

    /**
     * @type {string|null}
     */
    #authenticatedUserId = userIdOrNull();

    /**
     * @type {BotGameDto}
     */
    #botGameDto;

    /**
     * @type {PlayerVsBotClient}
     */
    #botGameClient;

    /**
     *  @type {function(HalfMove)}
     */
    #botPlayCallback;

    /**
     *  @type {function(HalfMove[])}
     */
    #movesHistoryCallback;

    /**
     * @type {function()}
     */
    #userWinsCallback;

    /**
     * @type {function()}
     */
    #botWinsCallback;

    /**
     * @param gameId {string}
     * @param initCallback {function(BotGameDto)}
     * @param loadFenCallback {function(string)}
     * @param movesHistoryCallback {function(HalfMove[])}
     * @param botPlayCallback {function(HalfMove)}
     * @param userWinsCallback {function()}
     * @param botWinsCallback {function()}
     */
    constructor(gameId, initCallback, loadFenCallback, movesHistoryCallback, botPlayCallback, userWinsCallback, botWinsCallback) {
        this.#gameId = gameId;
        this.#movesHistoryCallback = movesHistoryCallback;
        this.#botPlayCallback = botPlayCallback;
        this.#userWinsCallback = userWinsCallback;
        this.#botWinsCallback = botWinsCallback;

        this.#botGameClient = new PlayerVsBotClient(gameId);
        this.#botGameClient.fetchGameData(botGameDto => {
            this.#botGameDto = botGameDto;
            initCallback(botGameDto);

            this.#botGameClient.fetchMovesHistory(moves => {
                if (moves.length > 0) {
                    if (this.#botGameDto.isInProgress()) {
                        // load all moves but one
                        let lastMove = moves.pop();
                        let board = new Board();
                        board.loadFen(this.#botGameDto.startFen);
                        moves.forEach(move => board.registerMove(move));
                        loadFenCallback(board.outputFen());
                        movesHistoryCallback(moves);

                        // animate the last move
                        this.#botPlayCallback(lastMove);
                    } else {
                        // load all moves
                        loadFenCallback(this.#botGameDto.fen);
                        movesHistoryCallback(moves);
                    }
                } else {
                    loadFenCallback(this.#botGameDto.startFen);
                }
            });
        });
    }

    get gameId() {
        return this.#gameId;
    }

    /**
     * @param move {HalfMove}
     */
    playMove(move) {
        this.#botGameClient.playMove(move, response => {
            // update state
            this.#botGameDto.moveIndex = response.position;
            if (response.statusUpdate != null) {
                this.#botGameDto.status = response.statusUpdate;
            }

            // UI callbacks
            if (response.botMove != null) {
                this.#botPlayCallback(response.botMove);
            }

            if (response.isBotVictory()) {
                if (this.#botGameDto.userColor === Color.RED) {
                    this.#botGameDto.outcome = Outcome.BLACK_WINS;
                } else {
                    this.#botGameDto.outcome = Outcome.RED_WINS;
                }
                this.#botWinsCallback();
            } else if (response.isUserVictory()) {
                if (this.#botGameDto.userColor === Color.RED) {
                    this.#botGameDto.outcome = Outcome.RED_WINS;
                } else {
                    this.#botGameDto.outcome = Outcome.BLACK_WINS;
                }
                this.#userWinsCallback();
            }
        });
    }

    /**
     * @param callback {function()}
     */
    cancel(callback) {
        this.#botGameClient.cancel(() => {
            this.#botGameDto.status = GameEventType.CANCELED;
            callback();
        });
    }

    /**
     * @param callback {function()}
     */
    resign(callback) {
        this.#botGameClient.resign(() => {
            this.#botGameDto.status = GameEventType.RESIGNED;
            if (this.#botGameDto.userColor === Color.RED) {
                this.#botGameDto.outcome = Outcome.BLACK_WINS;
            } else {
                this.#botGameDto.outcome = Outcome.RED_WINS;
            }
            callback();
        });
    }

    /**
     * @return {boolean}
     */
    userCanPlay() {
        return this.#isGameInProgress() && this.#hasPermissionToPlay();
    }

    /**
     * @return {boolean}
     */
    #isGameInProgress() {
        return this.#botGameDto.isInProgress();
    }

    /**
     * @return {boolean}
     */
    #hasPermissionToPlay() {
        return (this.#botGameDto.isAnonymous && this.#authenticatedUserId == null) ||
            (!this.#botGameDto.isAnonymous && this.#authenticatedUserId != null && this.#authenticatedUserId === this.#botGameDto.userId);
    }

    /**
     * @return {boolean}
     */
    canResign() {
        return this.#botGameDto.status === GameEventType.CREATED &&
            this.#hasPermissionToPlay();
    }

    /**
     * @return {boolean}
     */
    canCancel() {
        return this.#botGameDto.status === GameEventType.CREATED &&
            !this.#botGameDto.hasUserPlayed() && this.#hasPermissionToPlay();
    }

    /**
     * @return {boolean}
     */
    shouldShowActionButtons() {
        return this.#hasPermissionToPlay();
    }

    /**
     * @return {string|null}
     */
    userId() {
        return this.#botGameDto.userId;
    }

    userOutcome() {
        return this.#botGameDto.userOutcome;
    }

    gameStatus() {
        return this.#botGameDto.status;
    }

    /**
     * @returns {string|null}
     */
    startFen() {
        return this.#botGameDto.startFen;
    }

    /**
     * @returns {boolean}
     */
    isManchu() {
        return this.#botGameDto.isManchu;
    }

    /**
     * When watching as spectator and receiving updates via WebSocket
     *
     * @param status {string}
     */
    updateStatus(status) {
        this.#botGameDto.status = status;
    }

    /**
     * When watching as spectator and receiving updates via WebSocket
     *
     * @param outcome {string}
     */
    updateOutcome(outcome) {
        this.#botGameDto.outcome = outcome
    }

    /**
     * @return {Map<string, string>}
     */
    buildPgnMetadata() {
        const dto = this.#botGameDto;
        const metadata = new Map();
        metadata.set('Site', window.location.href);
        metadata.set('Date', formatTimestampToPgnDate(dto.lastUpdatedMillis));

        switch (dto.userColor) {
            case Color.RED:
                metadata.set('White', dto.username);
                metadata.set('Black', `${dto.formattedEngine} (depth ${dto.depth})`);
                break;
            case Color.BLACK:
                metadata.set('White', `${dto.formattedEngine} (depth ${dto.depth})`);
                metadata.set('Black', dto.username);
                break;
        }

        switch (dto.outcome) {
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

        metadata.set('Variant', dto.isManchu ? 'Manchu' : 'Xiangqi');

        switch (dto.status) {
            case GameEventType.CHECKMATED:
            case GameEventType.STALEMATED:
                metadata.set('Termination', 'normal');
                break;
        }

        metadata.set('Mode', 'ICS');
        return metadata;
    }

}
