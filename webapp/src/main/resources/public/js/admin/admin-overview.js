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

function formatCounters(json) {
    const nbrOfAuthenticated = json.entries.filter(entry => entry.userType === UserType.AUTHENTICATED).length;
    const nbrOfGuests = json.entries.filter(entry => entry.userType === UserType.GUEST).length;
    const total = nbrOfAuthenticated + nbrOfGuests;
    return `${nbrOfAuthenticated} authenticated, ${nbrOfGuests} guests, total ${total}`;
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

    #fetchOnlineUsers() {
        // right now
        getAndHandle(ADMIN_URL_PREFIX + '/online-users', json => {
            this.#onlineUsersDiv.innerHTML = '';
            this.#onlineUsersCounterDiv.innerText = formatCounters(json);

            this
                .#renderOnlineUsersResponse(json, true)
                .forEach((userLinkDiv) => {
                    this.#onlineUsersDiv.append(userLinkDiv);
                });
        });
    }

    #fetchRecentlyOnlineUsers(div, counter, hours) {
        getAndHandle(ADMIN_URL_PREFIX + '/online-within-hours?hours=' + hours, json => {
            div.innerHTML = '';
            counter.innerText = formatCounters(json);

            this
                .#renderOnlineUsersResponse(json, false)
                .forEach((userLinkDiv) => {
                    div.append(userLinkDiv);
                });
        });
    }

    /**
     *
     * @return {HTMLDivElement[]}
     */
    #renderOnlineUsersResponse(json, showGuests) {
        return json
            .entries
            .sort((a, b) => a.userType.localeCompare(b.userType))
            .map(entry => {
                let element;
                if (entry.userType === UserType.GUEST) {
                    if (showGuests) {
                        element = buildUsernameSpan(entry.id, entry.username, entry.userType);
                    } else {
                        element = document.createElement('span');
                    }
                } else {
                    element = buildUsernameSpan(entry.id, entry.username, entry.userType);
                }

                element.classList.add('user-link');
                return element;
            })
            .filter(element => element.innerText.length > 0);
    }

    #fetchLatestExceptions() {
        getAndHandle(`${ADMIN_URL_PREFIX}/list-latest-thrown-exceptions?limit=10&codeFilter=5xx`, json => {
            const tbody = emptyTable(this.#latestExceptionsTable);

            json.entries.forEach(entry => {
                const row = tbody.insertRow();
                renderExceptionRow(entry, row, {showFullyQualifiedClassName: false});
            });
        });
    }

    #fetchLatestActivity() {
        getAndHandle(`${ADMIN_URL_PREFIX}/latest-game-activity/latest-pvp`, json => {
            this.#setActivityTimestamp('latest-pvp-timestamp', json.latestPvpActivity);
            this.#setActivityTimestamp('latest-pvp3-timestamp', json.latestPvp3Activity);
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/latest-game-activity/latest-pvb`, json => {
            this.#setActivityTimestamp('latest-pvb-timestamp', json.latestPvbActivity);
            this.#setActivityTimestamp('latest-pvb3-timestamp', json.latestPvb3Activity);
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
    }

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

    #setLiveGamesCount(elementId, count) {
        const element = document.getElementById(elementId);
        element.classList.remove('activity-recent', 'activity-hours', 'activity-days');
        element.innerText = count;

        if (count > 0) {
            element.classList.add('activity-recent');
        }
    }

    #fetchHourlyPageViews() {
        getAndHandle(`${ADMIN_URL_PREFIX}/hourly-page-views`, json => {
            new HourlyPageViewsChart('hourly-page-views-chart', json).render();
        });
    }

}

window.onload = () => new AdminOverviewPage();
