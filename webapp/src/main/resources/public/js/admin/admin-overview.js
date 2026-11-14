/*
 * Copyright (C) 2023-2026 Encelade SRL
 * Copyright (C) 2023-2026 elephantchess.io
 * Copyright (C) 2023-2026 Benoît Vleminckx (benckx)
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

function formatCounters(nbrOfAuthenticated, nbrOfGuests) {
    const total = nbrOfAuthenticated + nbrOfGuests;
    return `${formatNumber(nbrOfAuthenticated)} authenticated, ${formatNumber(nbrOfGuests)} guests, total ${formatNumber(total)}`;
}

class AdminOverviewPage extends BasePage {

    #onlineUsersDiv = document.getElementById('online-users');
    #onlineUsersCounterDiv = document.getElementById('online-users-counter');

    #onlineWithinHoursDiv24 = document.getElementById('online-within-hours-24');
    #onlineWithinHoursCounterDiv24 = document.getElementById('online-within-hours-counter-24');

    #latestExceptionsTable = document.getElementById('latest-exceptions');

    constructor() {
        super();
        setIntervalNoDelay(() => {
            this.#fetchOnlineUsers();
        }, 3_000);
        setIntervalNoDelay(() => {
            this.#fetchLatestActivity();
        }, 10_000);

        this.#fetchRecentlyOnlineUsers(
            this.#onlineWithinHoursDiv24,
            this.#onlineWithinHoursCounterDiv24,
            24
        );

        this.#fetchLatestExceptions();
        this.#fetchHourlyPageViews();
    }

    /**
     * Fetches currently online users and renders them with a summary counter.
     *
     * @return {void}
     */
    #fetchOnlineUsers() {
        // right now
        getAndHandle(ADMIN_URL_PREFIX + '/online-users', json => {
            this.#onlineUsersDiv.innerHTML = '';

            const nbrOfAuthenticated = json.entries.filter(entry => entry.userType === UserType.AUTHENTICATED).length;
            const nbrOfGuests = json.entries.filter(entry => entry.userType === UserType.GUEST).length;
            this.#onlineUsersCounterDiv.innerText = formatCounters(nbrOfAuthenticated, nbrOfGuests);

            this
                .#renderOnlineUsers(json.entries)
                .forEach((userLinkDiv) => {
                    this.#onlineUsersDiv.append(userLinkDiv);
                });
        });
    }

    /**
     * Fetches users seen online within the given number of hours and renders them.
     *
     * @param div {HTMLElement} - Container for the rendered user links.
     * @param counter {HTMLElement} - Element receiving the summary counter text.
     * @param hours {number} - Look-back window in hours.
     * @return {void}
     */
    #fetchRecentlyOnlineUsers(div, counter, hours) {
        getAndHandle(ADMIN_URL_PREFIX + '/online-within-hours?hours=' + hours, json => {
            div.innerHTML = '';
            counter.innerText = formatCounters(json.authenticatedUsers.length, json.guestCount);

            this
                .#renderAuthenticatedUsers(json.authenticatedUsers)
                .forEach((userLinkDiv) => {
                    div.append(userLinkDiv);
                });
        });
    }

    /**
     * Renders online users of any type (authenticated and guests).
     *
     * @param entries {Array<{id: string, username: string, userType: string}>} - Online user entries.
     * @return {HTMLElement[]}
     */
    #renderOnlineUsers(entries) {
        return entries
            .sort((a, b) => a.userType.localeCompare(b.userType))
            .map(entry => {
                const element = buildAdminUsernameSpan(entry.id, entry.username, entry.userType);
                element.classList.add('user-link');
                return element;
            });
    }

    /**
     * Renders authenticated users only (guests are represented by a count elsewhere).
     *
     * @param entries {Array<{id: string, username: string}>} - Authenticated user entries.
     * @return {HTMLElement[]}
     */
    #renderAuthenticatedUsers(entries) {
        return entries
            .map(entry => {
                const element = buildAdminUsernameSpan(entry.id, entry.username, UserType.AUTHENTICATED);
                element.classList.add('user-link');
                return element;
            });
    }

    /**
     * Fetches and renders the latest thrown exceptions into the exceptions table.
     *
     * @return {void}
     */
    #fetchLatestExceptions() {
        getAndHandle(`${ADMIN_URL_PREFIX}/list-latest-thrown-exceptions?limit=30`, json => {
            const tbody = emptyTable(this.#latestExceptionsTable);

            json.entries.forEach(entry => {
                const row = tbody.insertRow();
                renderExceptionRow(entry, row,
                    {
                        showFullyQualifiedClassName: false,
                        maxMessageLength: 100
                    }
                );
            });
        });
    }

    /**
     * Fetches the latest game, puzzle, analysis and user activity, and updates the trends.
     *
     * @return {void}
     */
    #fetchLatestActivity() {
        getAndHandle(`${ADMIN_URL_PREFIX}/latest-game-activity/latest-pvp`, json => {
            this.#setActivityTimestamp('latest-pvp-timestamp', json.latestPvpActivity);
            this.#setActivityTimestamp('latest-pvp3-timestamp', json.latestPvp3Activity);
            this.#setActivityTimestamp('latest-pvp-manchu3-timestamp', json.latestManchu3Activity);
            this.#setActivityTimestamp('latest-pvp-xiangqi3-timestamp', json.latestXiangqi3Activity);
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/latest-game-activity/latest-pvb`, json => {
            this.#setActivityTimestamp('latest-pvb-timestamp', json.latestPvbActivity);
            this.#setActivityTimestamp('latest-pvb3-timestamp', json.latestPvb3Activity);
            this.#setActivityTimestamp('latest-pvb-fairy3-timestamp', json.latestFairy3Activity);
            this.#setActivityTimestamp('latest-pvb-manchu3-timestamp', json.latestManchu3Activity);
            this.#setActivityTimestamp('latest-pvb-janggi3-timestamp', json.latestJanggi3Activity);
            this.#setActivityTimestamp('latest-pvb-xiangqi3-timestamp', json.latestXiangqi3Activity);
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/latest-game-activity/live-pvp`, json => {
            this.#setLiveGamesCount('live-pvp-games', json.livePvpGames);
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/latest-game-activity/live-pvb`, json => {
            this.#setLiveGamesCount('live-pvb-games', json.livePvbGames);
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/latest-puzzle-activity`, json => {
            this.#setActivityTimestamp('latest-puzzle-timestamp', json.latestPlayedPuzzle);
            this.#setActivityTimestamp('latest-puzzle-vote-timestamp', json.latestPuzzleVote);
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/latest-analysis-activity`, json => {
            this.#setActivityTimestamp('latest-move-analysis-timestamp', json.latestMoveAnalysis);
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/latest-new-users`, json => {
            this.#setActivityTimestamp('latest-new-guest-timestamp', json.latestNewGuest);
            this.#setActivityTimestamp('latest-new-authenticated-user-timestamp', json.latestNewAuthenticatedUser);
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/overview-trends`, json => {
            this.#setTrend(
                'trend-games-over-3-count',
                'trend-games-over-3-delta',
                json.gamesOver3.latest30Days,
                json.gamesOver3.previous30Days
            );
            this.#setTrend(
                'trend-puzzles-count',
                'trend-puzzles-delta',
                json.puzzles.latest30Days,
                json.puzzles.previous30Days
            );
        });
    }

    /**
     * Sets a relative-time label and recency CSS class for an activity timestamp.
     *
     * @param elementId {string} - Id of the target element.
     * @param timestamp {number|null} - Epoch milliseconds of the activity, or null if none.
     * @return {void}
     */
    #setActivityTimestamp(elementId, timestamp) {
        const element = document.getElementById(elementId);
        element.classList.remove('activity-recent', 'activity-hours', 'activity-days');

        if (timestamp === null) {
            element.innerText = 'n/a';
            return;
        }

        const elapsed = new Date().getTime() - timestamp;
        element.innerText = millisToRelativeTime(elapsed);

        if (elapsed < MS_PER_HOUR) {
            element.classList.add('activity-recent');
        } else if (elapsed < MS_PER_DAY) {
            element.classList.add('activity-hours');
        } else {
            element.classList.add('activity-days');
        }
    }

    /**
     * Displays a live games count and highlights it when games are in progress.
     *
     * @param elementId {string} - Id of the target element.
     * @param count {number} - Number of live games.
     * @return {void}
     */
    #setLiveGamesCount(elementId, count) {
        const element = document.getElementById(elementId);
        element.classList.remove('activity-recent', 'activity-hours', 'activity-days');
        element.innerText = formatNumber(count);

        if (count > 0) {
            element.classList.add('activity-recent');
        }
    }

    /**
     * Updates a trend's count and delta labels (absolute and percentage) with styling.
     *
     * @param countElementId {string} - Id of the element showing the latest count.
     * @param deltaElementId {string} - Id of the element showing the delta.
     * @param latest30Days {number} - Count over the latest 30 days.
     * @param previous30Days {number} - Count over the previous 30 days.
     * @return {void}
     */
    #setTrend(countElementId, deltaElementId, latest30Days, previous30Days) {
        const countElement = document.getElementById(countElementId);
        const deltaElement = document.getElementById(deltaElementId);
        countElement.innerText = formatNumber(latest30Days);

        deltaElement.classList.remove('trend-up', 'trend-down', 'trend-flat');

        const delta = latest30Days - previous30Days;
        if (delta === 0) {
            deltaElement.innerText = '(0 vs prev 30d)';
            deltaElement.classList.add('trend-flat');
            return;
        }

        const sign = delta > 0 ? '+' : '';
        if (previous30Days === 0) {
            deltaElement.innerText = `(${sign}${formatNumber(delta)} vs prev 30d)`;
        } else {
            const pct = ((delta / previous30Days) * 100).toFixed(1);
            deltaElement.innerText = `(${sign}${formatNumber(delta)}, ${sign}${pct}% vs prev 30d)`;
        }

        deltaElement.classList.add(delta > 0 ? 'trend-up' : 'trend-down');
    }

    /**
     * Fetches hourly page-view data and renders the chart.
     *
     * @return {void}
     */
    #fetchHourlyPageViews() {
        getAndHandle(`${ADMIN_URL_PREFIX}/hourly-page-views`, json => {
            new HourlyPageViewsChart('hourly-page-views-chart', json).render();
        });
    }

}

window.onload = () => new AdminOverviewPage();
