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

const USER_INFO_API = `/api/user/info`;
const PUZZLE_USER_STATS_API = `${USER_INFO_API}/puzzles/stats`;

class UserProfileClient {

    /**
     * {string}
     */
    #userId;

    /**
     * @param userId {string}
     */
    constructor(userId) {
        this.#userId = userId;
    }

    fetchGameRatings(cb) {
        this.#validateUserId();
        const url = `${USER_INFO_API}/game-ratings?userId=${this.#userId}`;
        getAndHandle(url, json => {
            cb(new GameStatsDto(json));
        });
    }

    /**
     * @param cb {function(PuzzleSummaryStatsDto)}
     */
    fetchPuzzleStatsSummary(cb) {
        this.#validateUserId();
        const url = `${PUZZLE_USER_STATS_API}/summary/${this.#userId}`;
        getAndHandle(url, json => cb(new PuzzleSummaryStatsDto(json)));
    }

    /**
     * @param cb {function(PuzzleRatingHistoryDto)}
     */
    fetchPuzzleRatingHistory(cb) {
        this.#validateUserId();
        const url = `${PUZZLE_USER_STATS_API}/rating/${this.#userId}`;
        getAndHandle(url, json => cb(new PuzzleRatingHistoryDto(json)));
    }

    /**
     * @param cb {function(PuzzleNumbersHistoryDto)}
     */
    fetchPuzzleDailyNumbers(cb) {
        this.#validateUserId();
        const url = `${PUZZLE_USER_STATS_API}/numbers/${this.#userId}`;
        getAndHandle(url, json => cb(new PuzzleNumbersHistoryDto(json)));
    }

    /**
     * @param cb {function(boolean)}
     */
    fetchIsOnline(cb) {
        this.#validateUserId();
        fetchAreOnline([this.#userId], onlineUserIds => {
            cb(onlineUserIds.includes(this.#userId));
        });
    }

    /**
     * Prevent calling endpoint if userId is not set.
     */
    #validateUserId() {
        if (this.#userId == null) {
            throw new Error('userId is undefined, not calling API');
        }
    }

}
