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

class AdminOnlineUsersStatsPage extends BasePage {

    constructor() {
        super();
        this.#fetchOnlineUsersStatistics();
    }

    #fetchOnlineUsersStatistics() {
        getAndHandle(`${ADMIN_URL_PREFIX}/online-users-stats-by-hour`, json => {
            if (json && json.entries) {
                new OnlineUsersHourlyChart('online-users-hourly-chart', json).render();
            } else {
                console.error('Invalid data for hourly chart:', json);
            }
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/online-users-stats-by-day-of-week`, json => {
            if (json && json.entries) {
                new OnlineUsersDayOfWeekChart('online-users-day-of-week-chart', json).render();
            } else {
                console.error('Invalid data for day of week chart:', json);
            }
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/online-users-stats-by-day`, json => {
            if (json && json.entries) {
                new OnlineUsersDailyChart('online-users-daily-chart', json).render();
            } else {
                console.error('Invalid data for daily chart:', json);
            }
        });

        getAndHandle(`${ADMIN_URL_PREFIX}/online-users-stats-by-month?months=18`, json => {
            if (json && json.entries) {
                new OnlineUsersMonthlyChart('online-users-monthly-chart', json).render();
            } else {
                console.error('Invalid data for monthly chart:', json);
            }
        });
    }

}

window.onload = () => new AdminOnlineUsersStatsPage();
