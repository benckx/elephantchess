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

const ANALYZE_BUTTON_TOOLTIP_ENABLED = 'You can analyse the game with the Analysis Board tool';
const ANALYZE_BUTTON_TOOLTIP_DISABLED = `Game must be finished before you can analyze them. If you want to analyze this game now, you have to resign first.`;

class BotGameSpectatorWebSocketSession {

    /**
     * @param gameId {string}
     * @param moveIndex {number}
     * @param boardGui {BoardGui}
     * @param moveTreeWidget {MoveTreeWidget}
     * @param statusUpdateCallback {function(string, string)} to refresh the status once the game ends
     */
    constructor(
        gameId,
        moveIndex,
        boardGui,
        moveTreeWidget,
        statusUpdateCallback
    ) {
        const params = new Map([['gameId', gameId], ['moveIndex', moveIndex]]);
        const ws = buildWebSocketEndpoint('botgame/watch-as-spectator', params);

        /**
         * @type {HalfMove[]}
         */
        let moveQueue = [];

        /**
         * Process move in front of the queue and call recursively until the queue is empty
         * (i.e. when the board is up to date with the received data)
         */
        function processMoveQueue() {
            const move = moveQueue.shift();
            if (move) {
                boardGui.registerOpponentMove(move, false, () => {
                    moveTreeWidget.addMoveAtTheEnd(move);
                    if (moveQueue.length > 0) {
                        setInterval(processMoveQueue, 800);
                    }
                });
            }
        }

        ws.onopen = () => {
            console.log('connected as spectator');
        }
        ws.onerror = (e) => {
            // reload page?
            console.warn(e);
        }
        ws.onmessage = (e) => {
            const update = new BotGameSpectatorUpdateDto(JSON.parse(e.data));
            moveQueue = moveQueue.concat(update.newMoves);
            processMoveQueue();

            if (update.status !== GameEventType.CREATED) {
                statusUpdateCallback(update.status, update.outcome);
            }
        };
    }

}

class PlayerVsBotPage extends BasePage {

    /**
     * @type {PlayerVsBotController}
     */
    #controller;

    #boardGui = createWebappBoardGui();
    #moveTreeWidget = new MoveTreeWidget({containerId: 'move-tree-container'});

    #isOnlineIndicator = document.getElementById('online-status-indicator');

    #resignButton = document.getElementById('resign-button');
    #cancelButton = document.getElementById('cancel-button');
    #analyzeButtons = [
        document.getElementById('analyze-button-left-side'),
        document.getElementById('analyze-button-right-side')
    ];
    #infoOutcomeLabel = document.getElementById('info-outcome');

    constructor() {
        super();

        // handle url params
        let gameId = getQueryParam('id');

        // widgets
        this.#boardGui.disablePlayerMove();
        this.#moveTreeWidget.addNavigationPanel({
            containerId: 'mobile-navigation-panel',
            isDownloadButtonEnabled: true
        });
        this.#moveTreeWidget.addNavigationPanel({
            containerId: 'move-history-navigation-panel',
            isDownloadButtonEnabled: true
        });
        this.#moveTreeWidget.boardWidget = this.#boardGui;
        new SettingsGui(this.#boardGui, this.#moveTreeWidget);

        this.#controller = new PlayerVsBotController(
            gameId,
            (botGameDto) => {
                this.#boardGui.flipToColor(botGameDto.userColor);
                this.#moveTreeWidget.startFen = botGameDto.startFen;
                let infoPlayer = document.getElementById('info-player');
                if (botGameDto.isAnonymous) {
                    infoPlayer.innerText = 'anonymous';
                } else {
                    infoPlayer.append(
                        buildUsernameSpan(
                            botGameDto.userId,
                            botGameDto.username,
                            botGameDto.userType
                        )
                    );
                }
                document.getElementById('info-user-color').append(buildColorSpan(botGameDto.userColor));
                document.getElementById('info-engine').innerText = botGameDto.formattedEngine;
                document.getElementById('info-depth').innerText = botGameDto.depth.toString();
                document.getElementById('info-created').innerText = botGameDto.formattedCreated;
                if (botGameDto.isManchu) {
                    document.getElementById('info-variant').innerText = 'Manchu (一统棋)';
                    document.getElementById('info-variant-row').style.display = '';
                }
                this.#updateOutcomeLabel();
                this.#updateButtonsEnabled();
                this.#enablePlayerMoveIfPermitted();
                this.#startSpectatorSessionIfNeeded(gameId, botGameDto);
                this.#setupIsOnlineStatusUpdates();
            },
            fen => {
                this.#boardGui.loadFen(fen);
            },
            moves => {
                this.#moveTreeWidget.setMoves(moves);
                this.#renderAnalysisSummaryReportIfAvailable();
            },
            (botMove) => {
                this.#boardGui.registerOpponentMove(botMove, true, null);
                this.#moveTreeWidget.addMoveAtTheEnd(botMove);
                this.#updateButtonsEnabled();
                this.#enablePlayerMoveIfPermitted();
            },
            () => this.#gameEndedCallback(true),
            () => this.#gameEndedCallback(false)
        );

        // set up listeners
        this.#boardGui.addAfterMoveListener((move) => {
            this.#moveTreeWidget.addMoveAtTheEnd(move);
            this.#boardGui.disablePlayerMove();
            this.#controller.playMove(move);
        });
        this.#moveTreeWidget.addClickedNodeListener(() => this.#handleNavigationEvent());
        this.#moveTreeWidget.addNavigationListener(() => this.#handleNavigationEvent());
        this.#moveTreeWidget.metadataFetcher = () => this.#controller.buildPgnMetadata();

        this.#resignButton.addEventListener('click', (e) => {
            if (isInfoBoxButtonEnabled(e)) {
                this.#handleClickedResignButton();
            }
        });

        this.#cancelButton.addEventListener('click', (e) => {
            if (isInfoBoxButtonEnabled(e)) {
                this.#handleClickedCancelButton();
            }
        });

        this.#analyzeButtons.forEach((button) => {
            button.addEventListener('click', (e) => {
                if (isAppButtonEnabled(e)) {
                    let gameId = new GameId(GameType.PVB, this.#controller.gameId);
                    window.location.href = gameId.analysisUrl;
                }
            });

            addToolTip(button, ANALYZE_BUTTON_TOOLTIP_DISABLED);
        });

        // modals
        UI.preloadModals(
            Modals.GAME_WIN,
            Modals.GAME_LOSS,
            Modals.CONFIRMATION
        );
    }

    #handleClickedResignButton() {
        let span = document.createElement('span');
        span.innerText = 'Are you sure you want to resign?';
        let yesCallback = () => {
            this.#controller.resign(() => {
                this.#boardGui.disablePlayerMove();
                this.#updateOutcomeLabel();
                this.#updateButtonsEnabled();
            });
        };
        let yesButtonText = 'resign';
        let noCallback = () => UI.hideModal(null);
        let noButtonText = 'no';
        UI.showConfirmationModal(span, yesCallback, yesButtonText, noCallback, noButtonText);
    }

    #handleClickedCancelButton() {
        const span = document.createElement('span');
        span.innerText = 'Are you sure you want to cancel this game?';
        const yesCallback = () => {
            this.#controller.cancel(() => {
                this.#boardGui.disablePlayerMove();
                this.#updateOutcomeLabel();
                this.#updateButtonsEnabled();
            });
        };
        const yesButtonText = 'yes';
        const noCallback = () => UI.hideModal(null);
        const noButtonText = 'no';
        UI.showConfirmationModal(span, yesCallback, yesButtonText, noCallback, noButtonText);
    }

    /**
     * @param isUserVictory {boolean}
     */
    #gameEndedCallback(isUserVictory) {
        let modalName = isUserVictory ? Modals.GAME_WIN : Modals.GAME_LOSS;
        UI.showModalByName(modalName, () => {
            document
                .getElementById('ok-button')
                .addEventListener('click', () => UI.hideModal(null));
        });
        this.#boardGui.disablePlayerMove();
        this.#updateOutcomeLabel();
        this.#updateButtonsEnabled();
    }

    #handleNavigationEvent() {
        if (this.#moveTreeWidget.isLastMoveSelected()) {
            this.#enablePlayerMoveIfPermitted();
        } else {
            this.#boardGui.disablePlayerMove();
        }
    }

    #enablePlayerMoveIfPermitted() {
        if (this.#controller.userCanPlay()) {
            this.#boardGui.enablePlayerMove();
        }
    }

    #updateOutcomeLabel() {
        let label = '--';
        let victoryType = '';
        switch (this.#controller.gameStatus()) {
            case GameEventType.CANCELED:
                label = 'Canceled';
                break;
            case GameEventType.RESIGNED:
            case GameEventType.AUTO_RESIGNED:
                victoryType = ' (resignation)';
                break;
            case GameEventType.CHECKMATED:
                victoryType = ' (checkmate)';
                break;
            case GameEventType.STALEMATED:
                victoryType = ' (stalemate)';
                break;
            default:
                break;
        }

        switch (this.#controller.userOutcome()) {
            case UserOutcome.WIN:
                label = 'Human victory' + victoryType;
                break;
            case UserOutcome.LOSS:
                label = 'Bot victory' + victoryType;
                break;
            case UserOutcome.DRAW:
                label = 'Draw';
                break;
        }

        this.#infoOutcomeLabel.innerText = label;
    }

    #updateButtonsEnabled() {
        if (isStatusFinished(this.#controller.gameStatus()) && !this.#controller.isManchu()) {
            this.#analyzeButtons.forEach((button) => {
                button.classList.remove('app-buttons-disabled');
                addToolTip(button, ANALYZE_BUTTON_TOOLTIP_ENABLED);
            });
        } else {
            const tooltip = this.#controller.isManchu()
                ? 'Analysis is not supported for Manchu variant games'
                : ANALYZE_BUTTON_TOOLTIP_DISABLED;
            this.#analyzeButtons.forEach((button) => {
                button.classList.add('app-buttons-disabled');
                addToolTip(button, tooltip);
            });
        }

        if (this.#controller.shouldShowActionButtons()) {
            setInfoBoxButtonEnabled(this.#resignButton, this.#controller.canResign());
            setInfoBoxButtonEnabled(this.#cancelButton, this.#controller.canCancel());

            document.getElementById('game-actions-buttons-info-box').style.display = 'block';
        } else {
            document.getElementById('game-actions-buttons-info-box').style.display = 'none';
        }
    }

    /**
     * @param gameId {string}
     * @param botGameDto {BotGameDto}
     */
    #startSpectatorSessionIfNeeded(gameId, botGameDto) {
        function isSpectator() {
            const userId = userIdOrNull();
            return (userId == null || userId !== botGameDto.userId) && !botGameDto.isAnonymous;
        }

        if (botGameDto.isInProgress() && isSpectator()) {
            new BotGameSpectatorWebSocketSession(
                gameId,
                botGameDto.moveIndex,
                this.#boardGui,
                this.#moveTreeWidget,
                (newStatus, outcome) => {
                    this.#controller.updateStatus(newStatus);
                    this.#controller.updateOutcome(outcome);
                    this.#updateOutcomeLabel();
                }
            );
        }
    }

    #setupIsOnlineStatusUpdates() {
        const playerUserId = this.#controller.userId();
        const userId = userIdOrNull();

        if (userId === playerUserId) {
            this.#isOnlineIndicator.classList.add(IS_ONLINE_CSS_CLASS);
        } else {
            setIntervalNoDelay(() => {
                if (playerUserId != null) {
                    fetchAreOnline([playerUserId], (isOnlineUserIds) => {
                        if (isOnlineUserIds.includes(playerUserId)) {
                            this.#isOnlineIndicator.classList.add(IS_ONLINE_CSS_CLASS);
                        } else {
                            this.#isOnlineIndicator.classList.remove(IS_ONLINE_CSS_CLASS);
                        }
                    })
                }
            }, 3_000);
        }
    }

    #renderAnalysisSummaryReportIfAvailable() {
        if (isStatusFinished(this.#controller.gameStatus())) {
            renderAnalysisSummaryReportGeneric(
                new GameId(GameType.PVB, this.#controller.gameId),
                this.#moveTreeWidget.getMainBranchNodes(),
                this.#controller.startFen(),
                this.#moveTreeWidget
            );
        }
    }

}

window.onload = () => new PlayerVsBotPage();
