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

class PuzzlesController {

    #puzzlesClient = new PuzzlesClient(puzzleState => this.#loadState(puzzleState));

    /**
     * @type {string[]}
     */
    #selectedCategories = [];

    /**
     * @type {PuzzleState}
     */
    #puzzleState;

    #isEngineModeEnabled = false;
    #readyToMoveToNextPuzzle = false;

    /**
     * @type {function(PuzzleState)}
     */
    #loadedPuzzleCb;

    /**
     * @type {function(number, number)}
     */
    #outcomeRatingCb;

    /**
     * @type {function(HalfMove, string)}
     */
    #solvedCb;

    /**
     * @type {function(HalfMove, HalfMove)}
     */
    #playerMatedCb;

    /**
     * @type {function(HalfMove)}
     */
    #noSolutionCb;

    /**
     * @type {function(HalfMove, HalfMove)}
     */
    #nextOpponentMoveCb;

    /**
     * @param loadedPuzzleCb {function(PuzzleState)}
     * @param outcomeRatingCb {function(number, number)}
     * @param solvedCb {function(HalfMove, string)}
     * @param playerMatedCb {function(HalfMove, HalfMove)}
     * @param noSolutionCb {function(HalfMove)}
     * @param nextOpponentMoveCb {function(HalfMove, HalfMove)}
     */
    constructor(loadedPuzzleCb, outcomeRatingCb, solvedCb, playerMatedCb, noSolutionCb, nextOpponentMoveCb) {
        this.#loadedPuzzleCb = loadedPuzzleCb;
        this.#outcomeRatingCb = outcomeRatingCb;
        this.#solvedCb = solvedCb;
        this.#playerMatedCb = playerMatedCb;
        this.#noSolutionCb = noSolutionCb;
        this.#nextOpponentMoveCb = nextOpponentMoveCb;

        // process query parameters
        const categories = getAllQueryParam('category');
        if (categories != null && categories.length > 0) {
            this.#selectedCategories = categories;
        }

        const id = getQueryParam('id');
        if (id != null) {
            this.#puzzlesClient.fetchById(id, this.#selectedCategories);
        } else {
            this.#puzzlesClient.fetchCurrent(this.#selectedCategories);
        }

        document.addEventListener('keydown', (e) => {
            switch (e.key) {
                case 'Enter':
                    if (this.#readyToMoveToNextPuzzle) {
                        this.fetchNext();
                    }
                    break;
            }
        });
    }

    /**
     * @return {boolean}
     */
    shouldRevealMetadata() {
        return this.#selectedCategories.length > 0 || this.#puzzleState.categories.includes('MATE_IN_5');
    }

    fetchNext() {
        if (this.#puzzleState == null || !this.#puzzleState.hasOutcome()) {
            console.warn('Can not fetch new puzzle as long as current puzzle is not finished');
        } else {
            this.#puzzlesClient.fetchNext(this.#selectedCategories);
        }
    }

    /**
     * @param puzzleState {PuzzleState}
     */
    #loadState(puzzleState) {
        this.#puzzleState = puzzleState;
        this.#reInit();
        let url = 'puzzles?id=' + this.#puzzleState.id;
        if (this.#selectedCategories.length > 0) {
            url = addCategoriesParamsToUrl(url, this.#selectedCategories);
        }
        updateUrl(url);
        this.#loadedPuzzleCb(this.#puzzleState);
    }

    #reInit() {
        this.#isEngineModeEnabled = false;
        this.#isEngineModeEnabled = false;
    }

    // only exists for debug
    isEngineMode() {
        return this.#isEngineModeEnabled;
    }

    #enableEngineMode() {
        if (this.#isEngineModeEnabled) {
            console.warn('engine mode already enabled');
        } else {
            this.#isEngineModeEnabled = true;
        }
    }

    #respondWithEngine(submittedMove) {
        this.#puzzlesClient.fetchBestMoveAnd(this.#puzzleState.outputFen(), engineMove => {
            this.#puzzleState.addPlayedMove(engineMove);
            if (this.#puzzleState.isMated(this.#puzzleState.playerColor)) {
                this.#processOutcome(PuzzleOutcome.FAILED);
                this.#playerMatedCb(submittedMove, engineMove);
            } else {
                this.#nextOpponentMoveCb(submittedMove, engineMove);
            }
        });
    }

    /**
     * @param submittedMove {HalfMove}
     */
    submitSolution(submittedMove) {
        this.#puzzleState.addPlayedMove(submittedMove);

        const opponentColor = this.#puzzleState.opponentColor;
        const isCheckmate = this.#puzzleState.isCheckmate(opponentColor);
        const isStalemate = this.#puzzleState.isStalemate(opponentColor);

        if (isCheckmate || isStalemate) {
            let victoryType;
            if (isCheckmate) {
                victoryType = 'checkmate';
            } else if (isStalemate) {
                victoryType = 'stalemate';
            } else {
                victoryType = 'mate';
            }
            this.#solvedCb(submittedMove, victoryType);
            this.#processOutcome(PuzzleOutcome.SOLVED);
        } else if (this.#puzzleState.isOutOfMoves()) {
            this.#noSolutionCb(submittedMove);
            this.#processOutcome(PuzzleOutcome.FAILED);
        } else {
            if (!this.#isEngineModeEnabled) {
                if (this.#puzzleState.isMoveCorrect(submittedMove)) {
                    // player has played pre-recorded move
                    let nextOpponentMove = this.#puzzleState.nextOpponentMove;
                    this.#puzzleState.addPlayedMove(nextOpponentMove);
                    this.#puzzleState.incrementPreRecordedSolution();
                    // add interval to make it look like the opponent is thinking
                    setTimeout(() => this.#nextOpponentMoveCb(submittedMove, nextOpponentMove), 400);
                } else {
                    this.#enableEngineMode();
                    this.#respondWithEngine(submittedMove);
                }
            } else {
                this.#respondWithEngine(submittedMove);
            }
        }
    }

    skip() {
        this.#processOutcome(PuzzleOutcome.SKIPPED);
        this.fetchNext();
    }

    reveal() {
        this.#processOutcome(PuzzleOutcome.SKIPPED);
    }

    #processOutcome(outcome) {
        if (!this.#puzzleState.hasOutcome()) {
            this.#puzzleState.outcome = outcome;
            this.#readyToMoveToNextPuzzle = true;
            this.#puzzlesClient.postOutcome(
                this.#puzzleState.id,
                outcome,
                !this.#isEngineModeEnabled,
                this.#selectedCategories.length > 0,
                (rating) => {
                    if (rating.isDefined() && isUserIdentified()) {
                        this.#outcomeRatingCb(rating.oldRating, rating.newRating);
                    }
                })
        }
    }

    /**
     * @param upVoted {boolean}
     * @param cb {function(boolean)}
     */
    vote(upVoted, cb) {
        this.#puzzlesClient.postVote(this.#puzzleState.id, upVoted, (success) => {
            cb(success);
        });
    }

    /**
     * @param cb {function(Number)}
     */
    fetchUserRating(cb) {
        const user = new User();
        this.#puzzlesClient.fetchUserRating(user.userId, cb);
    }

    /**
     * @param cb {function(GameMetadataDto)}
     */
    fetchOriginalGameMetadata(cb) {
        this.#puzzlesClient.fetchOriginalGameMetadata(this.#puzzleState.id, cb);
    }

}
