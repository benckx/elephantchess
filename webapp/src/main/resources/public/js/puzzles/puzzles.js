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

const PuzzleStateBoxes = Object.freeze({
    PLAYING_RED: 'playing-red-state',
    PLAYING_BLACK: 'playing-black-state',
    NO_SOLUTION: 'no-solution-state',
    MATED_BY_PUZZLE: 'mated-by-puzzle-state',
    SOLVED: 'puzzle-solved-state',
    SOLUTION_REVEALED: 'solution-revealed-state'
});

const USER_RATING_DELTA_VALUE_BOX_NEGATIVE_CLASS = 'user-rating-delta-value-box-negative';
const USER_RATING_DELTA_VALUE_BOX_POSITIVE_CLASS = 'user-rating-delta-value-box-positive';

class PuzzlesPage extends BasePage {

    /**
     * @type {PuzzlesController}
     */
    #puzzlesController;

    /**
     * @type {PuzzleState}
     */
    #puzzleState;

    /**
     * @type {BoardGui}
     */
    #boardGui = createWebappBoardGui();

    /**
     * @type {PuzzleMoveTreeWidget}
     */
    #moveTreeWidget = new PuzzleMoveTreeWidget({containerId: 'move-tree-container'});

    #debugMode = document.body.dataset.debugMode === 'true';
    #newRatingToShow = null;

    /**
     * In memory only (reset on page reload)
     */
    #skipConfirmationEnabled = true;

    /**
     * In memory only (reset on page reload)
     */
    #revealConfirmationEnabled = true;

    #nextPuzzleButton = document.getElementById('next-puzzle-button');
    #nextPuzzleButtonLabel = document.getElementById('next-puzzle-button-label');
    #revealSolutionButton = document.getElementById('reveal-solution-button');
    #tryAgainButton = document.getElementById('try-again-button');

    #puzzleIdLabel = document.getElementById('puzzle-id');
    #puzzleDisabledIcon = document.getElementById('puzzle-disabled-icon');
    #attempts = document.getElementById('number-of-attempts');
    #puzzleCategoriesLabel = document.getElementById('puzzle-categories');
    #puzzleRatingLabel = document.getElementById('puzzle-rating');
    #gameInfoLabel = document.getElementById('game-info');

    #allocatedPliesFeedbackLabel = document.getElementById('allocated-plies');
    #victoryTypeFeedbackLabel = document.getElementById('victory-type');

    #userRatingInfoBox = document.getElementById('user-rating-info-box');
    #userRatingValueBox = document.getElementById('user-rating-value-box');
    #userRatingDeltaValueBox = document.getElementById('user-rating-delta-value-box');

    /**
     * @type {HTMLElement[]}
     */
    #stateBoxes = getElementsByClassNameArray('puzzle-state-info-box-table');

    #voteContainer = document.getElementById("vote-up-down-container");
    #voteThumbsUp = document.getElementById("vote-thumbs-up");
    #voteThumbsDown = document.getElementById("vote-thumbs-down");

    #renderDelay = 100;

    constructor() {
        super();

        this.#puzzlesController = new PuzzlesController(
            (puzzleState) => {
                this.#loadedPuzzleCb(puzzleState);
            },
            (oldRating, newRating) => {
                this.#updateUserRating(oldRating, newRating);
            },
            (submittedMove, victoryType) => {
                this.#renderSolved(submittedMove, victoryType);
            },
            (submittedMove, nextOpponentMove) => {
                this.#renderPlayerMated(submittedMove, nextOpponentMove);
            },
            (submittedMove) => {
                this.#renderNoSolutionFound(submittedMove);
            },
            (submittedMove, nextOpponentMove) => {
                this.#renderNextOpponentMove(submittedMove, nextOpponentMove)
            }
        );

        // fetch rating if identified
        if (isUserIdentified()) {
            this.#puzzlesController.fetchUserRating(rating => {
                this.#userRatingInfoBox.style.display = 'block';
                this.#userRatingValueBox.innerText = rating.toString();
            });
        }

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
        this.#moveTreeWidget.addClickedNodeListener(() => this.#handleNavigationEvent());
        this.#moveTreeWidget.addNavigationListener(() => this.#handleNavigationEvent());
        new SettingsGui(this.#boardGui, this.#moveTreeWidget);

        // add listeners
        this.#nextPuzzleButton.addEventListener('click', () => this.#handleNextPuzzleButtonClicked());
        this.#revealSolutionButton.addEventListener('click', (e) => {
            if (isInfoBoxButtonEnabled(e)) {
                this.#handleRevealSolutionButtonClicked();
            }
        });
        this.#tryAgainButton.addEventListener('click', (e) => {
            if (isInfoBoxButtonEnabled(e)) {
                this.#puzzlesController.retry();
            }
        });

        this.#puzzleIdLabel.addEventListener('click', () => {
            copyTextToClipboardAndNotify(
                getFullHost() + '/puzzles?id=' + this.#puzzleState.id,
                'Puzzle link copied to clipboard!'
            );
        });

        getElementsByClassNameArray('next-puzzle-link').forEach(link => {
            link.addEventListener('click', () => this.#puzzlesController.fetchNext());
        });

        this.#voteThumbsUp.addEventListener('click', () => this.#handleVote(true));
        this.#voteThumbsDown.addEventListener('click', () => this.#handleVote(false));
    }

    /**
     * @param puzzleState {PuzzleState}
     */
    #loadedPuzzleCb(puzzleState) {
        // animate pieces only when transitioning from a previous puzzle (skip / next)
        const animate = this.#puzzleState != null;
        this.#puzzleState = puzzleState;

        // re-init GUI elements
        this.#boardGui.flipToColor(this.#puzzleState.playerColor);
        this.#boardGui.loadFen(this.#puzzleState.startFen, animate);
        this.#boardGui.enablePlayerMove();
        this.#boardGui.clearAllAfterMovesListeners();
        this.#boardGui.addAfterMoveListener((submittedMove) => {
            setInfoBoxButtonEnabled(this.#tryAgainButton, false);
            this.#puzzlesController.submitSolution(submittedMove);
        });

        this.#moveTreeWidget.resetFlaggedAsRevealed();
        this.#moveTreeWidget.clear();
        this.#moveTreeWidget.setMoves(this.#puzzleState.moves);

        // update UI info
        this.#puzzleIdLabel.textContent = '#' + this.#puzzleState.id;
        this.#puzzleDisabledIcon.style.display = this.#puzzleState.enabled === false ? 'inline' : 'none';
        this.#attempts.textContent = this.#puzzleState.attempts.toString();

        switch (this.#puzzleState.playerColor) {
            case Color.RED:
                this.#showPuzzleStateBox(PuzzleStateBoxes.PLAYING_RED);
                break;
            case Color.BLACK:
                this.#showPuzzleStateBox(PuzzleStateBoxes.PLAYING_BLACK);
                break;
            default:
                throw new Error('unknown player color: ' + this.#puzzleState.playerColor);
        }

        if (this.#puzzlesController.shouldRevealMetadata()) {
            this.#revealPuzzleMetadata();
        } else {
            this.#hidePuzzleMetadata();
        }

        setInfoBoxButtonEnabled(this.#revealSolutionButton, true);
        setInfoBoxButtonEnabled(this.#tryAgainButton, this.#puzzleState.hasPlayedMoves());
        this.#renderNextPuzzleButtonAsSkipPuzzle();
        this.#displayVoteButtons(false);

        // update rating
        this.#userRatingDeltaValueBox.style.visibility = 'hidden';
        if (this.#newRatingToShow != null) {
            this.#userRatingValueBox.innerText = this.#newRatingToShow;
        }

        // debug
        this.#highlightNextMoveDebug();

        // TODO: hide notification if any
        //  (it's weird "thanks for feedback" is still there when the next puzzle has loaded)
    }

    #handleNavigationEvent() {
        if (!this.#puzzleState.hasOutcome() && this.#moveTreeWidget.isLastMoveSelected()) {
            this.#boardGui.enablePlayerMove();
        } else {
            this.#boardGui.disablePlayerMove();
        }
    }

    /**
     * @param oldRating {number}
     * @param newRating {number}
     */
    #updateUserRating(oldRating, newRating) {
        if (oldRating != null && newRating != null) {
            let delta = newRating - oldRating;
            this.#userRatingDeltaValueBox.classList.remove(USER_RATING_DELTA_VALUE_BOX_POSITIVE_CLASS, USER_RATING_DELTA_VALUE_BOX_NEGATIVE_CLASS);
            if (delta > 0) {
                this.#userRatingDeltaValueBox.classList.add(USER_RATING_DELTA_VALUE_BOX_POSITIVE_CLASS);
                this.#userRatingDeltaValueBox.innerText = '+' + delta.toString();
            } else if (delta < 0) {
                this.#userRatingDeltaValueBox.classList.add(USER_RATING_DELTA_VALUE_BOX_NEGATIVE_CLASS);
                this.#userRatingDeltaValueBox.innerText = delta.toString();
            } else {
                this.#userRatingDeltaValueBox.innerText = 'n/a';
            }
            this.#userRatingDeltaValueBox.style.visibility = 'visible';
            this.#newRatingToShow = newRating;
        }
    }

    #revealPuzzleMetadata() {
        this.#puzzleCategoriesLabel.textContent = this.#puzzleState.formattedCategories.join(', ');
        this.#puzzleCategoriesLabel.classList.remove('hidden-values');

        this.#puzzleRatingLabel.textContent = this.#puzzleState.rating.toString();
        this.#puzzleRatingLabel.classList.remove('hidden-values');

        this.#puzzlesController.fetchOriginalGameMetadata(gameMetadata => {
            const dbGameUrl = `${gameIdToPageLink(gameMetadata.gameId)}&orientation=${this.#puzzleState.playerColor}`;
            const dbGameLinkDiv = wrapInDiv(buildLink(dbGameUrl, gameMetadata.toStringPlayerNames(), '_blank'));
            this.#gameInfoLabel.innerHTML = '';
            this.#gameInfoLabel.append(dbGameLinkDiv);
            if (gameMetadata.lastUpdated != null) {
                this.#gameInfoLabel.append(
                    buildSimpleTextDiv(formatTimestampToShortDateFormat(gameMetadata.lastUpdated))
                );
            }
            this.#gameInfoLabel.classList.remove('hidden-values');
        });
    }

    #hidePuzzleMetadata() {
        this.#puzzleCategoriesLabel.innerText = 'hidden';
        this.#puzzleCategoriesLabel.classList.add('hidden-values');

        this.#puzzleRatingLabel.innerText = 'hidden';
        this.#puzzleRatingLabel.classList.add('hidden-values');

        this.#gameInfoLabel.innerText = 'hidden';
        this.#gameInfoLabel.classList.add('hidden-values');
    }

    /**
     * @param submittedMove {HalfMove}
     * @param nextOpponentMove {HalfMove}
     */
    #renderNextOpponentMove(submittedMove, nextOpponentMove) {
        this.#moveTreeWidget.addSubmittedMove(submittedMove);
        this.#moveTreeWidget.addSubmittedMove(nextOpponentMove);
        this.#boardGui.registerOpponentMove(nextOpponentMove, true, () => {
            this.#moveTreeWidget.selectLastNode();
            this.#handleNavigationEvent();
            setInfoBoxButtonEnabled(this.#tryAgainButton, true);
            this.#highlightNextMoveDebug();
        });
    }

    /**
     * @param submittedMove {HalfMove}
     * @param nextOpponentMove {HalfMove}
     */
    #renderPlayerMated(submittedMove, nextOpponentMove) {
        this.#renderNextOpponentMove(submittedMove, nextOpponentMove);
        this.#moveTreeWidget.renderSubmittedMovesAsErroneous();
        this.#showPuzzleStateBox(PuzzleStateBoxes.MATED_BY_PUZZLE);
        this.#renderNextPuzzleButtonAsNextPuzzle();
        this.#displayVoteButtons(true);
        this.#hideDebugHighlight();
    }

    #renderNoSolutionFound(submittedMove) {
        setInfoBoxButtonEnabled(this.#tryAgainButton, true);
        // update move history
        this.#moveTreeWidget.addSubmittedMove(submittedMove);
        this.#moveTreeWidget.renderSubmittedMovesAsErroneous();

        // update UI feedback info
        this.#allocatedPliesFeedbackLabel.innerText = this.#puzzleState.allocatedNumberOfMoves.toString();
        this.#showPuzzleStateBox(PuzzleStateBoxes.NO_SOLUTION);
        this.#renderNextPuzzleButtonAsNextPuzzle();
        this.#displayVoteButtons(true);

        // update UI controls
        this.#revealPuzzleMetadata();

        // debug
        this.#hideDebugHighlight();
    }

    #renderSolved(submittedMove, victoryType) {
        // we need to do this async or the move animation on the board lags a bit
        // not really clear why though
        setTimeout(() => {
            // update move history
            this.#moveTreeWidget.addSubmittedMove(submittedMove);
            this.#moveTreeWidget.selectMoveAt(this.#puzzleState.moveHistoryIndex);
            this.#moveTreeWidget.renderSubmittedMovesAsSolution();

            // update UI feedback info
            this.#victoryTypeFeedbackLabel.innerText = victoryType;
            this.#showPuzzleStateBox(PuzzleStateBoxes.SOLVED);
            this.#renderNextPuzzleButtonAsNextPuzzle();
            this.#displayVoteButtons(true);

            // update UI controls
            setInfoBoxButtonEnabled(this.#revealSolutionButton, false);
            this.#revealPuzzleMetadata();

            this.#boardGui.clearAllAfterMovesListeners();
            this.#boardGui.updateHighlightedChecks();
            setInfoBoxButtonEnabled(this.#tryAgainButton, true);

            // debug
            this.#hideDebugHighlight();
        }, this.#renderDelay);
    }

    #revealSolution() {
        if (!this.#moveTreeWidget.isSolutionRevealed() && !this.#puzzleState.isSolved()) {
            // process the puzzle outcome
            this.#puzzlesController.reveal();

            // update UI
            this.#boardGui.disablePlayerMove();
            this.#moveTreeWidget.flagAsRevealed();
            setInfoBoxButtonEnabled(this.#revealSolutionButton, false);
            this.#revealPuzzleMetadata();
            this.#hideDebugHighlight();

            // render history
            this.#boardGui.loadFen(this.#puzzleState.startFen);
            this.#moveTreeWidget.setMoves(this.#puzzleState.allMoves);
            this.#moveTreeWidget.selectMoveAt(this.#puzzleState.moves.length - 1);
            this.#moveTreeWidget.renderLastMovesAsSolution(this.#puzzleState.preRecordedSolutionMoves.length);

            // update feedback box
            this.#showPuzzleStateBox(PuzzleStateBoxes.SOLUTION_REVEALED);
            this.#renderNextPuzzleButtonAsNextPuzzle();
            this.#displayVoteButtons(true);
        } else {
            console.warn('solution already revealed or puzzle solved')
        }
    }

    #handleNextPuzzleButtonClicked() {
        if (this.#puzzleState == null) {
            return;
        }
        if (this.#puzzleState.hasOutcome() || this.#puzzlesController.hasRecordedOutcome() || !this.#skipConfirmationEnabled) {
            this.#puzzlesController.skip();
        } else {
            this.#showRatingLossConfirmationModal(
                'puzzle-skip-confirmation',
                () => this.#skipConfirmationEnabled = false,
                () => this.#puzzlesController.skip()
            );
        }
    }

    #handleRevealSolutionButtonClicked() {
        if (this.#puzzleState == null) {
            return;
        }
        if (this.#puzzleState.hasOutcome() || this.#puzzlesController.hasRecordedOutcome() || !this.#revealConfirmationEnabled) {
            this.#revealSolution();
        } else {
            this.#showRatingLossConfirmationModal(
                'puzzle-reveal-confirmation',
                () => this.#revealConfirmationEnabled = false,
                () => this.#revealSolution()
            );
        }
    }

    /**
     * @param modalName {string} name of the modal to display
     * @param disableConfirmationCb {function} called when the user ticked "Do not show again"
     * @param confirmedCb {function}
     */
    #showRatingLossConfirmationModal(modalName, disableConfirmationCb, confirmedCb) {
        UI.showModalByName(modalName, () => {
            const checkbox = document.getElementById('puzzle-confirmation-do-not-show-again');

            // "confirm" button listener
            document
                .getElementById('puzzle-confirmation-yes-button')
                .addEventListener('click', () => {
                    if (checkbox.checked) {
                        disableConfirmationCb();
                    }
                    UI.hideModal(null);
                    confirmedCb();
                });

            // "cancel" button listener
            document
                .getElementById('puzzle-confirmation-no-button')
                .addEventListener('click', () => {
                    UI.hideModal(null);
                });
        });
    }

    #renderNextPuzzleButtonAsSkipPuzzle() {
        this.#nextPuzzleButtonLabel.innerText = 'Skip Puzzle';
    }

    /**
     * When the puzzle is over, the button only moves to the next puzzle (no penalty)
     */
    #renderNextPuzzleButtonAsNextPuzzle() {
        this.#nextPuzzleButtonLabel.innerText = 'Next Puzzle';
    }

    #showPuzzleStateBox(id) {
        this.#stateBoxes.forEach(box => {
            box.classList.remove('puzzle-state-info-box-table-rendered');
        });

        document.getElementById(id).classList.add('puzzle-state-info-box-table-rendered');
    }

    /**
     * @param value {boolean}
     */
    #displayVoteButtons(value) {
        this.#reInitVoteButtons();
        if (value) {
            this.#voteContainer.style.visibility = 'visible';
        } else {
            this.#voteContainer.style.visibility = 'hidden';
        }
    }

    /**
     * @param upVoted {boolean}
     */
    #handleVote(upVoted) {
        if (isUserAuthenticated()) {
            this.#puzzlesController.vote(upVoted, (registered) => {
                if (registered) {
                    this.#reInitVoteButtons();
                    if (upVoted) {
                        this.#voteThumbsUp.classList.add('voted-for');
                        this.#voteThumbsDown.classList.add('voted-against');
                    } else {
                        this.#voteThumbsUp.classList.add('voted-against');
                        this.#voteThumbsDown.classList.add('voted-for');
                    }
                    UI.pushInfoNotification('Thank you for you feedback!');
                } else {
                    UI.pushErrorNotification('Votes can only be submitted within 10 minutes of completing the puzzle.');
                }
            });
        } else {
            showSignUpModal();
        }
    }

    #reInitVoteButtons() {
        this.#voteThumbsUp.classList.remove('voted-for', 'voted-against');
        this.#voteThumbsDown.classList.remove('voted-for', 'voted-against');
    }

    /**
     * Ensure debugMode is always disabled in production
     */
    #isDebugModeEnabled() {
        return this.#debugMode && isUsingDevPort();
    }

    #highlightNextMoveDebug() {
        if (this.#isDebugModeEnabled()) {
            this.#hideDebugHighlight();
            if (!this.#puzzlesController.isEngineMode()) {
                this.#boardGui.highlightDebugMove(this.#puzzleState.currentSolutionMove, 'green');
                if (this.#puzzleState.hasNextOpponentMove()) {
                    this.#boardGui.highlightDebugMove(this.#puzzleState.nextOpponentMove, 'yellow');
                }
            }
        }
    }

    #hideDebugHighlight() {
        this.#boardGui.hideAllDebugHighlight();
    }

}

window.onload = () => new PuzzlesPage();
