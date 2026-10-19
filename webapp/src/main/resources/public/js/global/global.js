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

class GlobalStatsPage extends BasePage {

    constructor() {
        super();
        this.#fetchPlayerVsPlayerLeaderboard();
        this.#fetchLeaderboardLast12Months();
        this.#fetchLeaderboard();
        this.#fetchGlobalPuzzleStats();
        this.#fetchGlobalGameStats();
        this.#fetchGlobalUsersStats();
        this.#fetchGlobalAppData();
    }

    #fetchPlayerVsPlayerLeaderboard() {
        getAndHandle('/api/global/pvp-leaderboard', json => {
            const allEntries = PlayerVsPlayerLeaderboardEntryDto.parseJson(json);
            this.#renderPlayerVsPlayerLeaderboard(allEntries, TimeControlEnum.BULLET, 'bullet-leaderboard');
            this.#renderPlayerVsPlayerLeaderboard(allEntries, TimeControlEnum.BLITZ, 'blitz-leaderboard');
            this.#renderPlayerVsPlayerLeaderboard(allEntries, TimeControlEnum.RAPID, 'rapid-leaderboard');
            this.#renderPlayerVsPlayerLeaderboard(allEntries, TimeControlEnum.CLASSICAL, 'classical-leaderboard');
            this.#renderPlayerVsPlayerLeaderboard(allEntries, TimeControlEnum.CORRESPONDENCE, 'correspondence-leaderboard');
        });
    }

    /**
     * @param allEntries {PlayerVsPlayerLeaderboardEntryDto[]}
     * @param category {string}
     * @param elementId {string}
     */
    #renderPlayerVsPlayerLeaderboard(allEntries, category, elementId) {
        const table = document.getElementById(elementId);

        allEntries
            .filter((entry) => entry.timeControlCategory === category)
            .forEach((entry, i) => {
                const row = table.insertRow();

                const rankCell = row.insertCell();
                rankCell.className = 'rank-cell';
                rankCell.innerText = (i + 1).toString();

                const userDiv = document.createElement('div');
                userDiv.className = 'center-content-div';

                const nameDiv = buildUserLinkDiv(entry.username);
                nameDiv.classList.add('username-sub-panel');

                if (entry.countryCode != null) {
                    const flagDiv = document.createElement('div');
                    flagDiv.classList.add('username-sub-panel');
                    flagDiv.append(buildFlagIconImg(entry.countryCode));
                    userDiv.append(flagDiv);
                }
                userDiv.append(nameDiv);

                const userCell = row.insertCell();
                userCell.className = 'user-cell';
                userCell.append(userDiv);

                const ratingCell = row.insertCell();
                ratingCell.className = 'small-cell';
                ratingCell.innerText = formatNumber(entry.rating);

                const totalPlayedCell = row.insertCell();
                totalPlayedCell.className = 'small-cell';
                totalPlayedCell.innerText = formatNumber(entry.totalPlayed);

                const lastPlayedCell = row.insertCell();
                lastPlayedCell.className = 'date-cell';
                lastPlayedCell.innerText = formatTimestampToShortDate(entry.lastPlayed);
            });
    }

    #fetchLeaderboardLast12Months() {
        getAndHandle('/api/user/info/puzzles/stats/leaderboard-last-12-months', json => {
            this.#renderPuzzleLeaderboard(LeaderboardEntryDto.parseJson(json), 'leaderboard-last-12-months');
        });
    }

    #fetchLeaderboard() {
        getAndHandle('/api/user/info/puzzles/stats/leaderboard', json => {
            this.#renderPuzzleLeaderboard(LeaderboardEntryDto.parseJson(json), 'leaderboard');
        });
    }

    /**
     * @param entries {LeaderboardEntryDto[]}
     * @param elementId {string}
     */
    #renderPuzzleLeaderboard(entries, elementId) {
        const table = document.getElementById(elementId);
        entries.forEach((entry, i) => {
            const row = table.insertRow();

            const rankCell = row.insertCell();
            rankCell.className = 'rank-cell';
            rankCell.innerText = (i + 1).toString();

            const userDiv = document.createElement('div');
            userDiv.className = 'center-content-div';

            const nameDiv = buildUserLinkDiv(entry.username);
            nameDiv.classList.add('username-sub-panel');

            if (entry.countryCode != null) {
                const flagDiv = document.createElement('div');
                flagDiv.className = 'username-sub-panel';
                flagDiv.append(buildFlagIconImg(entry.countryCode));
                userDiv.append(flagDiv);
            }
            userDiv.append(nameDiv);

            const userCell = row.insertCell();
            userCell.className = 'user-cell';
            userCell.append(userDiv);

            const ratingCell = row.insertCell();
            ratingCell.className = 'small-cell';
            ratingCell.innerText = formatNumber(entry.rating);

            const maxRatingCell = row.insertCell();
            maxRatingCell.className = 'small-cell';
            maxRatingCell.innerText = formatNumber(entry.maxRating);

            const totalPlayedCell = row.insertCell();
            totalPlayedCell.className = 'small-cell';
            totalPlayedCell.innerText = formatNumber(entry.totalPlayed);

            const lastPlayedCell = row.insertCell();
            lastPlayedCell.className = 'date-cell';
            lastPlayedCell.innerText = entry.formattedLastPlayed;

            const successCell = row.insertCell();
            successCell.className = 'success-cell';

            const successIndicator = new SuccessIndicator(entry.solvedRate, entry.failedRate);
            successCell.append(successIndicator.render());
        });
    }

    #fetchGlobalGameStats() {
        const url = '/api/global/game-stats';
        getAndHandle(url, json => {
            const totalGames = document.getElementById('total-games');
            const totalInAppGames = document.getElementById('total-in-app-games');
            const totalManchuGames = document.getElementById('total-manchu-games');
            const totalMoves = document.getElementById('total-moves');
            const totalInAppMoves = document.getElementById('total-in-app-moves');

            totalGames.innerText = formatNumberWithSuffix(json.totalGames);
            totalInAppGames.innerText = formatNumberWithSuffix(json.totalInAppGames);
            totalManchuGames.innerText = formatNumberWithSuffix(json.totalManchuGames);
            totalMoves.innerText = formatNumberWithSuffix(Math.floor(json.totalMoves / 2));
            totalInAppMoves.innerText = formatNumberWithSuffix(Math.floor(json.totalInAppMoves / 2));
        });
    }

    #fetchGlobalUsersStats() {
        const url = '/api/user/info/global';
        getAndHandle(url, json => {
            const totalUsers = document.getElementById('total-users');
            const recentlyActiveUsers = document.getElementById('recently-active-users');
            const onlineUsers = document.getElementById('online-users');
            totalUsers.innerText = formatNumberWithSuffix(json.totalUsers);
            recentlyActiveUsers.innerText = formatNumberWithSuffix(json.recentlyActiveUsers);
            onlineUsers.innerText = formatNumberWithSuffix(json.onlineUsers);
        });
    }

    #fetchGlobalPuzzleStats() {
        const url = '/api/puzzle/stats/global';
        getAndHandle(url, json => {
            const totalPuzzles = document.getElementById('total-puzzles');
            const totalPlayed = document.getElementById('total-puzzles-played');
            const playedAtLeast10x = document.getElementById('puzzles-played-at-least-10x');
            const playedAtLeast20x = document.getElementById('puzzles-played-at-least-20x');

            totalPuzzles.innerText = formatNumberWithSuffix(json.totalPuzzles);
            totalPlayed.innerText = formatNumberWithSuffix(json.totalPuzzlesPlayed);
            playedAtLeast10x.innerText = displayPercentage(json.puzzlesPlayedRatio10x, 1);
            playedAtLeast20x.innerText = displayPercentage(json.puzzlesPlayedRatio20x, 1);
        });
    }

    #fetchGlobalAppData() {
        const url = '/api/global/app-data';
        getAndHandle(url, json => {
            const lastDeploy = document.getElementById('last-deploy');
            if (json.lastDeploy == null) {
                lastDeploy.innerText = 'unknown';
            } else {
                setRelativeTimeShorthandAndToolTip(lastDeploy, Number(json.lastDeploy));
            }
        });
    }

}

window.onload = () => new GlobalStatsPage();
