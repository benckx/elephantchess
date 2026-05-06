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

const API_PUZZLE = '/api/puzzle';

class PuzzlesClient {

    /**
     * @type {function(PuzzleState)}
     */
    #loadedPuzzleStateCb;

    /**
     * @param loadedPuzzleStateCb {function(PuzzleState)}
     */
    constructor(loadedPuzzleStateCb) {
        this.#loadedPuzzleStateCb = loadedPuzzleStateCb;
    }

    /**
     * @param id {string}
     * @param categories {string[]}
     */
    fetchById(id, categories = []) {
        let url = `${API_PUZZLE}/get?id=${id}`;
        url = addCategoriesParamsToUrl(url, categories);
        this.#fetchAndLoad(url);
    }

    /**
     * @param categories {string[]}
     */
    fetchCurrent(categories = []) {
        let url = `${API_PUZZLE}/current`;
        url = addCategoriesParamsToUrl(url, categories);
        this.#fetchAndLoad(url);
    }

    /**
     * @param categories {string[]}
     */
    fetchNext(categories = []) {
        let url = `${API_PUZZLE}/next`;
        url = addCategoriesParamsToUrl(url, categories);
        this.#fetchAndLoad(url);
    }

    /**
     * @param userId {string}
     * @param cb {function(Number)}
     */
    fetchUserRating(userId, cb) {
        const url = `/api/user/info/puzzles/rating/${userId}`;
        getAndHandle(url, json => cb(Number(json.rating)));
    }

    /**
     *
     * @param puzzleId {string}
     * @param outcome {string}
     * @param usedPreRecordedSolution {boolean}
     * @param visibleCategories {boolean}
     * @param cb {function(PuzzleRatingUpdateDto)}
     */
    postOutcome(puzzleId, outcome, usedPreRecordedSolution, visibleCategories, cb) {
        const url = API_PUZZLE + '/outcome';
        const body = {
            'puzzleId': puzzleId,
            'outcome': outcome,
            'usedPreRecordedSolution': usedPreRecordedSolution,
            'visibleCategories': visibleCategories
        };
        postAndHandle(url, body, json => {
            cb(new PuzzleRatingUpdateDto(json));
        });
    }

    /**
     * @param puzzleId {string}
     * @param upVoted {boolean}
     * @param cb {function(boolean)}
     */
    postVote(puzzleId, upVoted, cb) {
        const url = `${API_PUZZLE}/vote`;
        const body = {'puzzleId': puzzleId, 'upVoted': upVoted};
        postAndHandle(url, body, json => cb(json.registered));
    }

    /**
     * @param fen {string}
     * @param cb {function(HalfMove)}
     */
    fetchBestMoveAnd(fen, cb) {
        const url = `${API_PUZZLE}/best-move`
        const body = {'fen': fen};
        postAndHandle(url, body, json => cb(HalfMove.parseUci(json.uci)));
    }

    /**
     * @param puzzleId {string}
     * @param cb {function(GameMetadataDto)}
     */
    fetchOriginalGameMetadata(puzzleId, cb) {
        postAndHandle(`${API_PUZZLE}/original-games-metadata`, {puzzleIds: [puzzleId]}, json => {
            if (json.entries.length === 1) {
                cb(new GameMetadataDto(json.entries[0].gameMetadata));
            }
        });
    }

    /**
     * @param url {string}
     */
    #fetchAndLoad(url) {
        getAndHandle(url, json => {
            this.#loadedPuzzleStateCb(new PuzzleState(json));
        });
    }

}
