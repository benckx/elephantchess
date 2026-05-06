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

function formatDate(date) {
    if (date == null) {
        return '--';
    } else {
        return formatDayToShortDateFormat(date);
    }
}

class AdminSearchQueriesPage extends BasePage {

    #searchQueriesTable = document.getElementById('list-search-queries');

    constructor() {
        super();
        getAndHandle(`${ADMIN_URL_PREFIX}/list-latest-search-queries`, json => {
            const entries = [];
            for (let i = 0; i < json.entries.length; i++) {
                entries.push(new ReferenceGameSearchQueryDto(json.entries[i]));
            }

            this.#renderEntries(entries);
        });
    }

    /**
     * @param entries {ReferenceGameSearchQueryDto[]}
     */
    #renderEntries(entries) {
        const tbody = this.#searchQueriesTable.querySelector('tbody');
        tbody.innerHTML = '';

        entries.forEach(entry => {
            const row = tbody.insertRow()

            // user
            const userCell = row.insertCell();
            userCell.append(buildUsernameSpan(entry.userId, entry.username, entry.userType));

            // start date
            const startCell = row.insertCell();
            startCell.innerText = formatDate(entry.searchStart);

            // end date
            const endCell = row.insertCell();
            endCell.innerText = formatDate(entry.searchEnd)

            // player
            const playerCell = row.insertCell();
            playerCell.textContent = entry.playerName || '--';

            // player color
            const playerColorCell = row.insertCell();
            playerColorCell.textContent = entry.playerColor || '--';

            // event
            const eventCell = row.insertCell();
            eventCell.textContent = cropText(entry.eventName || '--', 30);

            // fen
            const fenCell = row.insertCell();
            fenCell.textContent = cropText(entry.fen || '--', 30);

            // query time
            const queryTimeCell = row.insertCell();
            queryTimeCell.innerText = formatTimestampDefaultDateFormat(entry.queryTime);

            // offset
            const offsetCell = row.insertCell();
            offsetCell.textContent = entry.offset?.toString() || '--';

            // number of results
            const numberOfResultsCell = row.insertCell();
            numberOfResultsCell.textContent = entry.numberOfResults.toString();
        });
    }
}

window.onload = () => new AdminSearchQueriesPage();
