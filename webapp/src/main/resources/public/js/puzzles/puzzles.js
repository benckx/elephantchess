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

const FeedbackBoxes = Object.freeze({
    PLAYING_RED: 'playing-red-feedback-box',
    PLAYING_BLACK: 'playing-black-feedback-box',
    NO_SOLUTION: 'no-solution-feedback-box',
    MATED_BY_PUZZLE: 'mated-by-puzzle-feedback-box',
    SOLVED: 'puzzle-solved-feedback-box',
    SOLUTION_REVEALED: 'solution-revealed-feedback-box'
});

const USER_RATING_DELTA_VALUE_BOX_NEGATIVE_CLASS = 'user-rating-delta-value-box-negative';
const USER_RATING_DELTA_VALUE_BOX_POSITIVE_CLASS = 'user-rating-delta-value-box-positive';

// FIXME: kinda weird we adding and removing after moveListeners between 2 puzzles
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

    #debugMode = false;
    #newRatingToShow = null;

    #skipPuzzleButton = document.getElementById('skip-puzzle-button');
    #revealSolutionButton = document.getElementById('reveal-solution-button');

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
    #feedbackBoxes = getElementsByClassNameArray('feedback-info-box-table');

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
        new SettingsGui(this.#boardGui, this.#moveTreeWidget);

        // add listeners
        this.#skipPuzzleButton.addEventListener('click', () => this.#puzzlesController.skip());
        this.#revealSolutionButton.addEventListener('click', (e) => {
            if (isInfoBoxButtonEnabled(e)) {
                this.#revealSolution();
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
        this.#puzzleState = puzzleState;

        // re-init GUI elements
        this.#boardGui.flipToColor(this.#puzzleState.playerColor);
        this.#boardGui.loadFen(this.#puzzleState.startFen);
        this.#boardGui.enablePlayerMove();
        this.#boardGui.addAfterMoveListener((submittedMove) => {
            this.#puzzlesController.submitSolution(submittedMove);
        });

        this.#moveTreeWidget.resetFlaggedAsRevealed();
        this.#moveTreeWidget.setMoves(this.#puzzleState.moves);
        this.#moveTreeWidget.addClickedNodeListener(() => this.#handleNavigationEvent());
        this.#moveTreeWidget.addNavigationListener(() => this.#handleNavigationEvent());

        // update UI info
        this.#puzzleIdLabel.textContent = '#' + this.#puzzleState.id;
        this.#puzzleDisabledIcon.style.display = this.#puzzleState.enabled === false ? 'inline' : 'none';
        this.#attempts.textContent = this.#puzzleState.attempts.toString();

        switch (this.#puzzleState.playerColor) {
            case Color.RED:
                this.#showFeedbackBox(FeedbackBoxes.PLAYING_RED);
                break;
            case Color.BLACK:
                this.#showFeedbackBox(FeedbackBoxes.PLAYING_BLACK);
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
            this.#boardGui.enablePlayerMove();
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
        this.#showFeedbackBox(FeedbackBoxes.MATED_BY_PUZZLE);
        this.#displayVoteButtons(true);
        this.#hideDebugHighlight();
    }

    #renderNoSolutionFound(submittedMove) {
        // update move history
        this.#moveTreeWidget.addSubmittedMove(submittedMove);
        this.#moveTreeWidget.renderSubmittedMovesAsErroneous();

        // update UI feedback info
        this.#allocatedPliesFeedbackLabel.innerText = this.#puzzleState.allocatedNumberOfMoves.toString();
        this.#showFeedbackBox(FeedbackBoxes.NO_SOLUTION);
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
            this.#showFeedbackBox(FeedbackBoxes.SOLVED);
            this.#displayVoteButtons(true);

            // update UI controls
            setInfoBoxButtonEnabled(this.#revealSolutionButton, false);
            this.#revealPuzzleMetadata();

            this.#boardGui.clearAllAfterMovesListeners();
            this.#boardGui.updateHighlightedChecks();

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
            this.#showFeedbackBox(FeedbackBoxes.SOLUTION_REVEALED);
            this.#displayVoteButtons(true);
        } else {
            console.warn('solution already revealed or puzzle solved')
        }
    }

    #showFeedbackBox(id) {
        this.#feedbackBoxes.forEach(box => {
            box.classList.remove('feedback-info-box-table-rendered');
        });

        document.getElementById(id).classList.add('feedback-info-box-table-rendered');
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
