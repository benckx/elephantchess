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

class AdminSockPuppetReportPage extends BasePage {

    #table = document.getElementById('sock-puppet-report-table');
    #summaryTotal = document.getElementById('sock-puppet-summary-total');
    #summaryUsers = document.getElementById('sock-puppet-summary-users');
    #summaryInviters = document.getElementById('sock-puppet-summary-inviters');
    #summaryInvitees = document.getElementById('sock-puppet-summary-invitees');

    constructor() {
        super();

        getAndHandle(`${ADMIN_URL_PREFIX}/sock-puppet-report`, json => {
            this.#summaryTotal.innerText = json.summary.totalDetections.toString();
            this.#summaryUsers.innerText = json.summary.distinctUsersInvolved.toString();
            this.#summaryInviters.innerText = json.summary.distinctInviters.toString();
            this.#summaryInvitees.innerText = json.summary.distinctInvitees.toString();

            const tbody = emptyTable(this.#table);
            json.entries.forEach(entry => {
                const row = tbody.insertRow();

                const gameCell = row.insertCell();
                const gameLink = document.createElement('a');
                gameLink.href = `/game?id=${entry.gameId}`;
                gameLink.innerText = entry.gameId;
                gameCell.append(gameLink);

                const inviterCell = row.insertCell();
                inviterCell.append(buildUsernameSpan(entry.inviterUserId, entry.inviterUsername, userTypeFromName(entry.inviterUsername)));

                const inviteeCell = row.insertCell();
                inviteeCell.append(buildUsernameSpan(entry.inviteeUserId, entry.inviteeUsername, userTypeFromName(entry.inviteeUsername)));

                row.insertCell().innerText = entry.sharedRemoteAddressCount.toString();
                row.insertCell().innerText = entry.sharedFingerprintCount.toString();
                row.insertCell().innerText = formatTimestampDefaultDateFormat(entry.gameCreated);
                row.insertCell().innerText = formatTimestampDefaultDateFormat(entry.lastScannedAt);
            });
        });
    }
}

window.onload = () => new AdminSockPuppetReportPage();
