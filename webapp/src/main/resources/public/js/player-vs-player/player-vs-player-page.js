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

const IS_ONLINE_FETCH_INTERVAL = 3_000;
const MAX_TIME_CONTROL_LABEL = 14;

class PlayGamePage extends BasePage {

    /**
     * @type {GameController}
     */
    #gameController = null;

    #boardGui = createWebappBoardGui();
    #perpetualCheckingTrackerWidget =
        new PerpetualCheckingTrackerWidget('perpetual-checking-tracker-info-box');

    /**
     * @type {MoveTreeWidget}
     */
    #moveTreeWidget = new MoveTreeWidget({
        containerId: 'move-tree-container',
        ...moveTreeResizeCookiePersistence('pvp', 'move-tree-container')
    });

    #redPlayerInfo = document.getElementById('red-player-info');
    #blackPlayerInfo = document.getElementById('black-player-info');
    #inviterPlayerInfo = document.getElementById('inviter-player-info');
    #inviterRow = document.getElementById('inviter-row');

    #createdLabel = document.getElementById('created-label');
    #timeControlBase = document.getElementById('time-control-base');
    #ratingMode = document.getElementById('rating-mode');
    #variantRow = document.getElementById('variant-row');
    #variantLabel = document.getElementById('variant-label');
    #gameStatusSpan = document.getElementById('game-status');
    #gameOutcomeSpan = document.getElementById('game-outcome');
    #outcomeRow = document.getElementById('outcome-row');

    #redPlayerOnlineIndicator = document.getElementById('red-online-status-indicator');
    #blackPlayerOnlineIndicator = document.getElementById('black-online-status-indicator');
    #inviterOnlineIndicator = document.getElementById('inviter-online-status-indicator');

    #gameActionsButtonsInfoBox = document.getElementById('game-actions-buttons-info-box');
    #resignButton = document.getElementById('resign-button');
    #proposeDrawButton = document.getElementById('propose-draw-button');
    #cancelButton = document.getElementById('cancel-button');
    #joinGameMaskButton = document.getElementById('join-game-mask-button');

    /**
     * @type {ChatBoxWidget}
     */
    #chatBoxWidget;

    #analyzeButtons = [
        document.getElementById('analyze-button-left-side'),
        document.getElementById('analyze-button-right-side')
    ];

    #topCounter = document.getElementById('top-counter');
    #bottomCounter = document.getElementById('bottom-counter');
    #miniCounterBox = document.getElementById('mini-counter-box');
    #miniTopCounter = document.getElementById('mini-top-counter');
    #miniBottomCounter = document.getElementById('mini-bottom-counter');

    /**
     * @type {HTMLElement[]}
     */
    #shareLinkActions = getElementsByClassNameArray('share-link-mask-action');

    #settingsManager = new SettingsManager();
    #joinAudio = new Audio('/audio/624598__eqylizer__high-pitched-two-note-notification.mp3');

    #hasRenderedJoinModal = false;

    /**
     * @type {string|null}
     */
    #source = null;

    /**
     * @type {string|null}
     */
    #sourceId = null;

    constructor() {
        super();

        // init board
        this.#boardGui.disablePlayerMove();

        // set up move tree widget
        this.#moveTreeWidget.addNavigationPanel({
            containerId: 'mobile-navigation-panel',
            isDownloadButtonEnabled: true
        });
        this.#moveTreeWidget.addNavigationPanel({
            containerId: 'move-history-navigation-panel',
            isDownloadButtonEnabled: true
        });
        this.#moveTreeWidget.boardWidget = this.#boardGui;
        this.#moveTreeWidget.metadataFetcher = () => this.#gameController.buildPgnMetadata();
        this.#moveTreeWidget.addClickedNodeListener(() => this.#handleNavigationEvent());
        this.#moveTreeWidget.addNavigationListener(() => this.#handleNavigationEvent());
        new SettingsGui(this.#boardGui, this.#moveTreeWidget);

        // hide the mobile mini timer when the main counter box is on screen
        this.#setUpMiniCounterVisibility();

        // handle params
        const params = new URLSearchParams(window.location.search);
        const gameIdParam = params.get('id');
        const colorIdParam = params.get('color');
        const channelIdParam = params.get('channelId');
        if (channelIdParam != null) {
            this.#source = GameJoinSource.DISCORD_NOTIFICATION;
            this.#sourceId = channelIdParam;
        } else {
            this.#source = GameJoinSource.LINK;
        }

        // init GameController
        if (gameIdParam === null) {
            window.location.href = '/';
        } else {
            this.#gameController = new GameController(
                gameIdParam,
                () => {
                    this.#initBoard();
                    this.#initOtherInfo();
                    this.#initClocks();
                    this.#updatePlayersInfo();
                    this.#updateGui();
                    this.#updateOnlineStatus();
                    this.#showJoinModalIfNeeded();
                    this.#updateBoardMaskMessage();
                },
                () => {
                    this.#boardGui.flipToColor(this.#gameController.gameDto.colorUserPlaysWith);
                    this.#updatePlayersInfo();
                    if (this.#gameController.gameDto.userStatus !== UserStatus.INVITEE) {
                        UI.pushInfoNotification(`${this.#gameController.gameDto.inviteeUsername} has joined the game`, 4_000);
                        if (this.#settingsManager.isPlaySoundsEnabled) {
                            this.#joinAudio
                                .play()
                                .catch(() => {
                                    // ignored, spam error in console in dev
                                });
                        }
                    }
                    this.#updateBoardMaskMessage();
                },
                () => {
                    this.#updateGui();
                },
                (move) => {
                    this.#moveTreeWidget.selectLastNode();
                    this.#boardGui.registerOpponentMove(move, true, () => {
                        this.#moveTreeWidget.addMoveAtTheEnd(move);
                        this.#perpetualCheckingTrackerWidget.addMove(move);
                        this.#updateGui();
                    });
                },
                () => {
                    this.#showGameFinishedModal(Modals.GAME_CANCELED);
                    this.#updateBoardMaskMessage();
                },
                () => this.#showGameFinishedModal(Modals.GAME_WIN),
                () => this.#showGameFinishedModal(Modals.GAME_LOSS),
                () => this.#showGameFinishedModal(Modals.OPPONENT_RESIGNED),
                () => this.#handleDrawPropositionReceived(),
                () => {
                    if (this.#gameController.gameDto.userStatus !== UserStatus.SPECTATOR) {
                        this.#showGameFinishedModal(Modals.OPPONENT_ACCEPTED_DRAW);
                    } else {
                        // TODO: it's a workaround for now - there should be a specific cb for that case
                        window.location.reload();
                    }
                },
                () => this.#showGameFinishedModal(Modals.OPPONENT_DECLINED_DRAW),
                () => this.#updateClocks(),
                (moves) => {
                    this.#moveTreeWidget.setMoves(moves);
                    this.#perpetualCheckingTrackerWidget.addMoves(moves);
                    this.#perpetualCheckingTrackerWidget.render();

                    // highlight last move played by opponent
                    const existsMoveToHighlight = moves.length > 0 && this.#gameController.isGameInProgress();
                    const shouldHighlightLastMove =
                        this.#gameController.gameDto.isUserTurn() ||
                        this.#gameController.gameDto.userStatus === UserStatus.SPECTATOR;

                    if (shouldHighlightLastMove && existsMoveToHighlight) {
                        const lastMove = moves[moves.length - 1];
                        this.#boardGui.highlightLastMove(lastMove);
                    }

                    this.#renderAnalysisSummaryReportIfAvailable();
                },
                (chatMessages, acks) => {
                    this.#handleChatMessages(chatMessages, acks);
                },
                (typingUsers) => {
                    this.#chatBoxWidget.notifyTypingUsers(typingUsers);
                }
            );

            UI.preloadModals(Modals.CONFIRMATION);

            setTimeout(() => {
                UI.preloadModals(
                    Modals.GAME_CANCELED,
                    Modals.GAME_WIN,
                    Modals.GAME_LOSS,
                    Modals.OPPONENT_RESIGNED,
                    Modals.OPPONENT_ACCEPTED_DRAW,
                    Modals.OPPONENT_DECLINED_DRAW
                )
            }, 1_000);

            this.#resignButton.addEventListener('click', (e) => {
                if (isInfoBoxButtonEnabled(e)) {
                    this.#handleResignButtonClick();
                }
            });

            this.#proposeDrawButton.addEventListener('click', (e) => {
                if (isInfoBoxButtonEnabled(e)) {
                    this.#handleProposeDrawButtonClick();
                }
            });

            this.#cancelButton.addEventListener('click', (e) => {
                if (isInfoBoxButtonEnabled(e)) {
                    this.#handleCancelButtonClick();
                }
            });

            this.#joinGameMaskButton.addEventListener('click', () => {
                this.#gameController.join(this.#source, this.#sourceId);
                this.#hideAllBoardMasks();
            });

            this.#shareLinkActions.forEach((link) =>
                link.addEventListener('click', () => {
                    navigator
                        .clipboard
                        .writeText(getFullHost() + '/game?id=' + this.#gameController.gameId)
                        .then(() => UI.pushInfoNotification('Game link copied to clipboard!'));
                })
            );

            this.#analyzeButtons.forEach((button) => {
                button.addEventListener('click', (e) => {
                    if (isAppButtonEnabled(e)) {
                        let gameId = new GameId(GameType.PVP, this.#gameController.gameId);
                        window.location.href = gameId.analysisUrl;
                    }
                });
            });
        }

        if (colorIdParam != null) {
            // in this case, it's a new game (created or joined), so can load the START_FEN
            this.#boardGui.loadFen(DEFAULT_START_FEN);
            this.#boardGui.flipToColor(colorIdParam);
        }

        // update is online indicator
        setIntervalNoDelay(() => {
            if (this.#gameController != null && this.#gameController.gameDto != null) {
                this.#updateOnlineStatus();
            }
        }, IS_ONLINE_FETCH_INTERVAL);

        // create chat box widget
        this.#chatBoxWidget = new ChatBoxWidget((msg) => this.#gameController.sendChat(msg));

        this.#chatBoxWidget.addInputGainsFocusListener(() => {
            this.#moveTreeWidget.disableKeyboardNavigation();
        });

        this.#chatBoxWidget.addInputLosesFocusListener(() => {
            this.#moveTreeWidget.enableKeyboardNavigation();
        });

        this.#chatBoxWidget.addInputTypingListener(() => {
            if (this.#gameController != null) {
                this.#gameController.sendTypingEvent();
            }
        });
    }

    #updateGui() {
        this.#updateBoardTurnToPlay();
        this.#updateGameStatusInfo();
        this.#updateButtonsEnabled();
        this.#chatBoxWidget.enable(this.#gameController.isAllowedToSendChat(userIdOrNull()));
    }

    #initBoard() {
        if (this.#gameController.gameDto.isManchu) {
            this.#moveTreeWidget.startFen = MANCHU_START_FEN;
        }

        if (!this.#gameController.isGameFinished()) {
            this.#boardGui.addAfterMoveListener((move) => {
                this.#gameController.registerPlayerMove(
                    move,
                    () => {
                        this.#updateBoardTurnToPlay();
                        this.#updateGameStatusInfo();
                        this.#perpetualCheckingTrackerWidget.addMove(move);
                        this.#perpetualCheckingTrackerWidget.render();
                    }
                );
                this.#moveTreeWidget.addMoveAtTheEnd(move);
            });
        }

        this.#boardGui.addAfterFlipListener((color) => {
            this.#updateClocksOrientation(color);
            this.#updateClocks();
        });
    }

    #initOtherInfo() {
        this.#createdLabel.innerText = formatTimestampDefaultDateFormat(this.#gameController.gameDto.created);
        this.#ratingMode.innerText = this.#gameController.gameDto.isRated ? 'Rated' : 'Casual';
        if (this.#gameController.gameDto.isManchu) {
            this.#variantLabel.innerText = 'Manchu';
            this.#variantRow.style.display = '';
        } else {
            this.#variantLabel.innerText = '';
            this.#variantRow.style.display = 'none';
        }
    }

    #initClocks() {
        /**
         * @param index {number}
         * @param value {string}
         * @return {HTMLTableRowElement}
         */
        function createLabelValueRow(index, value) {
            const labelDiv = document.createElement('div');
            labelDiv.innerText = value;

            const table = document.getElementById('game-description-sub-table');
            const row = table.insertRow(index);
            const labelCell = row.insertCell();
            const valueCell = row.insertCell();
            labelCell.className = 'labels';
            valueCell.className = 'values';
            valueCell.appendChild(labelDiv);
            return row;
        }

        let timeControl = this.#gameController.gameDto.timeControl;

        let completeLabel = timeControl.printShort(' +');
        if (timeControl.increment != null) {
            if (completeLabel.length >= MAX_TIME_CONTROL_LABEL) {
                // too big for 1 line
                this.#timeControlBase.innerText = timeControl.base.printShort();
                createLabelValueRow(3, '+' + timeControl.increment.printShort());
            } else {
                // small enough to fit on 1 line
                this.#timeControlBase.innerText = completeLabel;
            }
        } else {
            // no increment
            this.#timeControlBase.innerText = completeLabel;
        }

        // update clock orientation
        const colorUserPlaysWith = this.#gameController.gameDto.colorUserPlaysWith || Color.RED;
        this.#updateClocksOrientation(colorUserPlaysWith);

        // update clocks
        this.#updateClocks();

        // replace icon
        const icon = document.getElementById('main-icon');
        const category = this.#gameController.gameDto.timeControlCategory;
        icon.src = `${ICON_PATH}/${timeControlCategoryIconMap.get(category)}`;
        icon.style.opacity = '82%';
    }

    #updateBoardMaskMessage() {
        this.#hideAllBoardMasks();

        if (this.#gameController.gameDto.hasBeenJoined()) {
            this.#boardGui.disablePlaceholderMode();
            this.#boardGui.loadFen(this.#gameController.fen);
            this.#boardGui.flipToColor(this.#gameController.gameDto.colorUserPlaysWith);
            // this.#boardGui.updateHighlightedChecks();
            this.#updateBoardTurnToPlay();
        } else if (this.#gameController.gameDto.isCanceled()) {
            this.#boardGui.enablePlaceholderMode();
            document.getElementById('canceled-game-mask').style.display = 'flex';
        } else if (this.#gameController.gameDto.status === GameEventType.CREATED) {
            this.#boardGui.enablePlaceholderMode();

            let maskIdToRender = 'default-board-mask';
            if (this.#isUserPotentialJoiner()) {
                maskIdToRender = 'potential-joiner-mask';
            } else if (userIdOrNull() === this.#gameController.gameDto.inviterId) {
                if (this.#gameController.gameDto.isPrivateInvite) {
                    maskIdToRender = 'inviter-private-invite-board-mask';
                } else {
                    maskIdToRender = 'inviter-board-mask';
                }
            }

            const maskElement = document.getElementById(maskIdToRender);
            if (maskElement != null) {
                maskElement.style.display = 'flex';
            } else {
                console.warn(`Mask element not found: ${maskIdToRender}`);
            }
        }
    }

    #hideAllBoardMasks() {
        getElementsByClassNameArray('message-board-mask')
            .forEach((el) => el.style.display = 'none');
    }

    #handleNavigationEvent() {
        if (this.#moveTreeWidget.isLastMoveSelected()) {
            this.#updateBoardTurnToPlay();
        } else {
            this.#boardGui.disablePlayerMove();
        }
        this.#updateClocksAndChatForSelectedMove();
    }

    /**
     * When the user navigates the PvP game move tree, show the timer state and
     * highlight the most recent chat message at that point in time. When the
     * latest move is selected, restore the live clocks (driven by gameDto) and
     * clear the chat highlight.
     */
    #updateClocksAndChatForSelectedMove() {
        const selectedNode = this.#moveTreeWidget.selectedNode;

        // Compute the 0-based main-line index of the selected node, or null if
        // the node is not on the main branch (i.e. is part of an analysis side line).
        let moveIndex = null;
        if (selectedNode != null) {
            const nodesLeadingUp = selectedNode.getAllNodesLeadingUpTo();
            const allOnMainBranch = nodesLeadingUp.every(n => n.level === 0);
            if (allOnMainBranch) {
                moveIndex = nodesLeadingUp.length - 1;
            }
        }

        // show initial time when selecting the initial state (start FEN)
        const isLastMove = this.#moveTreeWidget.isLastMoveSelected();
        const isStartFenSelected = selectedNode === null && !isLastMove;
        if (isStartFenSelected) {
            const timeControl = this.#gameController.gameDto.timeControl;
            if (timeControl != null) {
                const baseMs = timeControl.base.toMillis();
                this.#renderClock(new TimeControlClock(baseMs, baseMs));
            }
            if (this.#chatBoxWidget != null) {
                this.#chatBoxWidget.clearHighlightedMessage();
            }
            return;
        }

        if (moveIndex == null || isLastMove) {
            // Back to "live" view: rely on the gameDto clock and clear highlight.
            this.#updateClocks();
            if (this.#chatBoxWidget != null) {
                this.#chatBoxWidget.clearHighlightedMessage();
            }
            return;
        }

        const historicalClock = this.#gameController.getClockAtMoveIndex(moveIndex);
        if (historicalClock != null) {
            this.#renderClock(historicalClock);
        }

        const moveTs = this.#gameController.getMoveTimestampAt(moveIndex);
        if (this.#chatBoxWidget != null) {
            this.#chatBoxWidget.highlightLatestMessageBefore(moveTs);
        }
    }

    #updateBoardTurnToPlay() {
        this.#boardGui.isPlayerMoveEnabled =
            this.#gameController.isGameInProgress() &&
            this.#gameController.gameState === GameState.USER_TURN;
    }

    /**
     * Called only on init and when invitee joins
     */
    #updatePlayersInfo() {
        const gameDto = this.#gameController.gameDto;
        const redPlaceholder = document.getElementById('red-player-info-placeholder');
        const blackPlaceholder = document.getElementById('black-player-info-placeholder');

        if (gameDto.hasInviterColor) {
            this.#deleteAllNodesButOnlineIndicator(this.#redPlayerInfo);
            this.#deleteAllNodesButOnlineIndicator(this.#blackPlayerInfo);

            // hide placeholders
            redPlaceholder.style.display = 'none';
            blackPlaceholder.style.display = 'none';

            // hide inviter only data
            this.#inviterRow.style.display = 'none';
            this.#inviterPlayerInfo.style.display = 'none';

            // inviter
            const inviterNameSpan = document.createElement('span');
            const inviterRatingSpan = document.createElement('span');

            const inviterRatingDelta = document.createElement('span');
            inviterRatingDelta.id = 'inviter-rating-delta';
            inviterRatingDelta.classList.add('user-rating-delta-value-box', 'user-rating-delta-value-smaller');

            inviterNameSpan.append(buildUsernameSpan(gameDto.inviterId, gameDto.inviterUsername, gameDto.inviterUserType));
            inviterRatingSpan.innerText = ' (' + gameDto.inviterRating + ')';

            // invitee
            const inviteeNameSpan = document.createElement('span');
            const inviteeRatingSpan = document.createElement('span');

            const inviteeRatingDelta = document.createElement('span');
            inviteeRatingDelta.id = 'invitee-rating-delta';
            inviteeRatingDelta.classList.add('user-rating-delta-value-box', 'user-rating-delta-value-smaller');

            if (gameDto.hasBeenJoined()) {
                inviteeNameSpan.append(buildUsernameSpan(gameDto.inviteeId, gameDto.inviteeUsername, gameDto.inviteeUserType));
                inviteeRatingSpan.innerText = ' (' + gameDto.inviteeRating + ')';
            }

            let inviterDiv = null;
            let inviteeDiv = null;

            switch (gameDto.inviterColor) {
                case Color.RED:
                    inviterDiv = this.#redPlayerInfo;
                    inviteeDiv = this.#blackPlayerInfo;
                    this.#redPlayerInfo.style.display = 'block';
                    if (gameDto.hasBeenJoined()) {
                        this.#blackPlayerInfo.style.display = 'block';
                    }
                    break;
                case Color.BLACK:
                    inviterDiv = this.#blackPlayerInfo;
                    inviteeDiv = this.#redPlayerInfo;
                    this.#blackPlayerInfo.style.display = 'block';
                    if (gameDto.hasBeenJoined()) {
                        this.#redPlayerInfo.style.display = 'block';
                    }
                    break;
                default:
                    throw new Error('Incorrect color: ' + gameDto.inviterColor);
            }

            inviterDiv.append(inviterNameSpan, inviterRatingSpan, inviterRatingDelta);
            if (gameDto.hasBeenJoined()) {
                inviteeDiv.append(inviteeNameSpan, inviteeRatingSpan, inviteeRatingDelta);
            }
        } else {
            // red and black are not yet determined, so we show inviter instead
            redPlaceholder.style.display = 'block';
            blackPlaceholder.style.display = 'block';
            this.#inviterPlayerInfo.append(buildUsernameSpan(gameDto.inviterId, gameDto.inviterUsername, gameDto.inviterUserType));
            this.#inviterPlayerInfo.style.display = 'block';
            this.#inviterRow.style.display = 'contents';
        }

        this.#updateChatAuthorColor();
    }

    #updateChatAuthorColor() {
        const gameDto = this.#gameController.gameDto;
        if (gameDto.hasBeenJoined()) {
            const colorMap = new Map([
                [gameDto.inviterId, gameDto.inviterColor],
                [gameDto.inviteeId, oppositeColor(gameDto.inviterColor)]
            ]);
            this.#chatBoxWidget.updateColorMapping(colorMap);
        } else if (gameDto.hasInviterColor) {
            const colorMap = new Map([
                [gameDto.inviterId, gameDto.inviterColor]
            ]);
            this.#chatBoxWidget.updateColorMapping(colorMap);
        }
    }

    /**
     * @param node {HTMLElement}
     */
    #deleteAllNodesButOnlineIndicator(node) {
        const toKeep = [];
        for (let child of node.children) {
            if (child.classList.contains('online-status-indicator')) {
                toKeep.push(child);
            }
        }
        node.innerHTML = '';
        toKeep.forEach(child => node.append(child));
    }

    #updateGameStatusInfo() {
        function colorToStatusText(color) {
            switch (color) {
                case Color.RED:
                    return 'Red to play';
                case Color.BLACK:
                    return 'Black to play';
                default:
                    throw new Error('Incorrect color: ' + color);
            }
        }

        /**
         * @param elementId {string}
         * @param delta {number}
         */
        function updateDelta(elementId, delta) {
            let span = document.getElementById(elementId);
            if (span != null) {
                if (delta > 0) {
                    span.classList.add('user-rating-delta-value-box-positive');
                    span.innerText = ' +' + delta;
                } else if (delta < 0) {
                    span.classList.add('user-rating-delta-value-box-negative');
                    span.innerText = ' ' + delta;
                }
            }
        }

        let statusText = '??';
        let outcomeText = '--';
        const dto = this.#gameController.gameDto;
        const gameStatus = this.#gameController.gameState;

        if (this.#gameController.isGameFinished()) {
            statusText = dto.formattedStatusOfFinishedGame;
            if (dto.outcome != null) {
                outcomeText = formatEnumValue(dto.outcome);
                if (dto.userHasWon()) {
                    outcomeText += ' (you won)';
                } else if (dto.userHasLost()) {
                    outcomeText += ' (you lost)';
                }
                this.#outcomeRow.style.display = 'contents';
            } else {
                this.#outcomeRow.style.display = 'none';
            }

            if (dto.isRated && dto.hasRatingUpdate) {
                const update = dto.ratingUpdate;
                updateDelta('inviter-rating-delta', update.inviterDelta);
                updateDelta('invitee-rating-delta', update.inviteeDelta);
            }
        } else if (gameStatus === GameState.WAITING_FOR_INVITEE) {
            statusText = 'Waiting for opponent';
        } else if (dto.userStatus === UserStatus.SPECTATOR) {
            statusText = colorToStatusText(dto.colorToPlay);
        } else if (gameStatus === GameState.OPPONENT_TURN) {
            statusText = 'Opponent\'s turn';
        } else if (gameStatus === GameState.USER_TURN) {
            statusText = 'Your turn ‼️';
        }

        this.#gameStatusSpan.innerText = statusText;
        this.#gameOutcomeSpan.innerText = outcomeText;

        this.#perpetualCheckingTrackerWidget.render();
    }

    #updateButtonsEnabled() {
        if (this.#gameController.gameDto.isUserPlaying() && !this.#gameController.isGameFinished()) {
            const status = this.#gameController.gameDto.status;
            const isInProgress = this.#gameController.isGameInProgress();
            setInfoBoxButtonEnabled(this.#resignButton, isInProgress);
            setInfoBoxButtonEnabled(this.#proposeDrawButton, isInProgress);
            setInfoBoxButtonEnabled(this.#cancelButton, status === GameEventType.CREATED);
            this.#showGameActionButtonsBlock(true);
        } else {
            this.#showGameActionButtonsBlock(false);
        }

        if (this.#gameController.isGameFinished() && !this.#gameController.gameDto.isManchu) {
            this.#analyzeButtons.forEach((button) => {
                button.classList.remove('app-buttons-disabled');
                addToolTip(button, 'You can analyse the game with the Analysis Board tool');
            });
        } else {
            const tooltip = this.#gameController.gameDto.isManchu
                ? 'Analysis is not supported for Manchu variant games'
                : 'Game must be finished before you can analyze it. If you want to analyze this game now, you have to resign first.';
            this.#analyzeButtons.forEach((button) => {
                button.classList.add('app-buttons-disabled');
                addToolTip(button, tooltip);
            });
        }
    }

    #updateClocks() {
        // Don't override the historical clock view when the user is navigating an
        // earlier move; #updateClocksAndChatForSelectedMove() owns the display in
        // that case and restores live clocks once the latest move is reselected.
        if (this.#moveTreeWidget != null
            && this.#moveTreeWidget.selectedNode != null
            && !this.#moveTreeWidget.isLastMoveSelected()) {
            return;
        }
        const clock = this.#gameController.gameDto.timeControlClock;
        if (clock != null) {
            this.#renderClock(clock);
        }
    }

    /**
     * Render an arbitrary clock state (used for both the live clock and the
     * historical clock shown when the user selects a past move).
     *
     * @param clock {TimeControlClock}
     */
    #renderClock(clock) {
        const category = this.#gameController.gameDto.timeControlCategory;
        const timeCounters = document.getElementsByClassName('time-counter');
        for (let i = 0; i < timeCounters.length; i++) {
            const timeCounter = timeCounters[i];
            if (timeCounter.classList.contains('red-counter')) {
                timeCounter.innerText = clock.red.printCounter(category);
            } else if (timeCounter.classList.contains('black-counter')) {
                timeCounter.innerText = clock.black.printCounter(category);
            }
        }
    }

    /**
     * @param color {string} color to put at the bottom (i.e. on the player's side)
     */
    #updateClocksOrientation(color) {
        const timeCounters = document.getElementsByClassName('time-counter');
        for (let i = 0; i < timeCounters.length; i++) {
            timeCounters[i].classList.remove('red-counter', 'black-counter');
        }

        switch (color) {
            case Color.RED:
                this.#topCounter.classList.add('black-counter');
                this.#bottomCounter.classList.add('red-counter');
                this.#miniTopCounter.classList.add('black-counter');
                this.#miniBottomCounter.classList.add('red-counter');
                break;
            case Color.BLACK:
                this.#topCounter.classList.add('red-counter');
                this.#bottomCounter.classList.add('black-counter');
                this.#miniTopCounter.classList.add('red-counter');
                this.#miniBottomCounter.classList.add('black-counter');
                break;
            default:
                throw new Error('Incorrect color: ' + color);
        }
    }

    #setUpMiniCounterVisibility() {
        const mainCounterBox = document.getElementById('counter-box');
        if (mainCounterBox == null || this.#miniCounterBox == null) {
            return;
        }
        if (typeof IntersectionObserver === 'undefined') {
            return;
        }
        const observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (entry.isIntersecting) {
                    this.#miniCounterBox.classList.add('mini-counter-box-hidden');
                } else {
                    this.#miniCounterBox.classList.remove('mini-counter-box-hidden');
                }
            });
        });
        observer.observe(mainCounterBox);
    }

    /**
     * @param value {boolean}
     */
    #showGameActionButtonsBlock(value) {
        this.#gameActionsButtonsInfoBox.style.display = value ? 'block' : 'none';
    }

    #updateOnlineStatus() {
        const allUserIds = [];
        if (this.#gameController.gameDto.redPlayerUserId != null) {
            allUserIds.push(this.#gameController.gameDto.redPlayerUserId);
        }
        if (this.#gameController.gameDto.blackPlayerUserId != null) {
            allUserIds.push(this.#gameController.gameDto.blackPlayerUserId);
        }
        allUserIds.push(this.#gameController.gameDto.inviterId);

        fetchAreOnline(allUserIds, (onlineUserIds) => {
            if (onlineUserIds.includes(this.#gameController.gameDto.redPlayerUserId)) {
                this.#redPlayerOnlineIndicator.classList.add(IS_ONLINE_CSS_CLASS);
            } else {
                this.#redPlayerOnlineIndicator.classList.remove(IS_ONLINE_CSS_CLASS);
            }

            if (onlineUserIds.includes(this.#gameController.gameDto.blackPlayerUserId)) {
                this.#blackPlayerOnlineIndicator.classList.add(IS_ONLINE_CSS_CLASS);
            } else {
                this.#blackPlayerOnlineIndicator.classList.remove(IS_ONLINE_CSS_CLASS);
            }

            if (onlineUserIds.includes(this.#gameController.gameDto.inviterId)) {
                this.#inviterOnlineIndicator.classList.add(IS_ONLINE_CSS_CLASS);
            } else {
                this.#inviterOnlineIndicator.classList.remove(IS_ONLINE_CSS_CLASS);
            }
        });
    }

    /**
     * When user opens the page from the link
     * (not from the lobby, where the page loads after the status has been updated to JOINED),
     */
    #showJoinModalIfNeeded(maxAttempts = 5) {
        if (!this.#hasRenderedJoinModal && this.#isUserPotentialJoiner()) {
            UI.showModalByName(Modals.JOIN_GAME_CONFIRMATION, () => {
                new JoinGameModalHandler(
                    this.#gameController.gameDto,
                    () => {
                        this.#gameController.join(this.#source, this.#sourceId);
                    });
            });
            this.#hasRenderedJoinModal = true;
        } else {
            // if guest token has not been received yet (e.g. if link of game has been shared with somebody who never used the site),
            // we retry this a few times (until hopefully the guest token has been received)
            const shouldAttemptAgain =
                maxAttempts > 0 &&
                this.#gameController.gameDto.status === GameEventType.CREATED &&
                this.#gameController.gameState === GameState.WAITING_FOR_INVITEE &&
                !this.#hasRenderedJoinModal;

            if (shouldAttemptAgain) {
                setTimeout(() => {
                    this.#showJoinModalIfNeeded(maxAttempts - 1);
                }, 1_000);
            }
        }
    }

    #isUserPotentialJoiner() {
        const dto = this.#gameController.gameDto;

        return dto.status === GameEventType.CREATED &&
            this.#gameController.gameState === GameState.WAITING_FOR_INVITEE &&
            isUserIdentified() &&
            userIdOrNull() !== dto.inviterId &&
            !(isUserIdentifiedAsGuest() && !dto.allowGuestsToJoin);
    }

    #handleResignButtonClick() {
        const text = buildSimpleSpan('Are you sure you want to resign?');
        const yesCallback = () => this.#gameController.resign();
        const yesButtonText = 'resign';
        const noCallback = () => UI.hideModal(null)
        const noButtonText = 'cancel';
        UI.showConfirmationModal(text, yesCallback, yesButtonText, noCallback, noButtonText);
    }

    #handleProposeDrawButtonClick() {
        const text = buildSimpleSpan('Are you sure you want to ask for a draw?');
        const yesCallback = () => this.#gameController.proposeDraw();
        const yesButtonText = 'draw';
        const noCallback = () => UI.hideModal(null);
        const noButtonText = 'cancel';
        UI.showConfirmationModal(text, yesCallback, yesButtonText, noCallback, noButtonText);
    }

    #handleCancelButtonClick() {
        const text = buildSimpleSpan('Are you sure you want to cancel this game?');
        const yesCallback = () => this.#gameController.cancel(() => {
            this.#updateBoardMaskMessage();
        });
        const yesButtonText = 'yes';
        const noCallback = () => UI.hideModal(null);
        const noButtonText = 'no';
        UI.showConfirmationModal(text, yesCallback, yesButtonText, noCallback, noButtonText);
    }

    #handleDrawPropositionReceived() {
        const text = buildSimpleSpan('Your opponent has proposed a draw. Do you accept?');
        const yesCallback = () => this.#gameController.respondToDrawProposition(true);
        const yesButtonText = 'accept draw';
        const noCallback = () => {
            UI.hideModal(null);
            this.#gameController.respondToDrawProposition(false)
        };
        const noButtonText = 'decline draw';
        UI.showConfirmationModal(text, yesCallback, yesButtonText, noCallback, noButtonText);
    }

    /**
     * @param modalName {string}
     */
    #showGameFinishedModal(modalName) {
        UI.showModalByName(modalName, () => {
            new GameEndedModalHandler(this.#gameController.userRatingUpdate);
            this.#updateGui();
        });
    }

    #renderAnalysisSummaryReportIfAvailable() {
        if (this.#gameController.isGameFinished()) {
            const gameId = new GameId(GameType.PVP, this.#gameController.gameId);
            fetchDataAndRenderAnalysisSummaryReport(gameId, this.#moveTreeWidget);
        }
    }

    /**
     * @param chatMessages {ChatMessageDto[]}
     * @param acks {number[]}
     */
    #handleChatMessages(chatMessages, acks) {
        for (let msg of chatMessages) {
            const author = msg.author;
            const userId = new UserId(author.userType, author.userId);
            const chatMessage = new ChatBoxMessage(
                msg.content,
                userId,
                author.username,
                msg.messageTime
            );

            this.#chatBoxWidget.addMessage(chatMessage);
        }
    }

}

window.onload = () => new PlayGamePage();
