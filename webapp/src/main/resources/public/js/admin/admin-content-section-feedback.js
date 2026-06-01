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

class AdminContentSectionFeedbackPage extends BasePage {

    #table = document.getElementById('content-section-feedback-table');

    constructor() {
        super();

        getAndHandle(`${ADMIN_URL_PREFIX}/content-section-feedback`, json => {
            const tbody = emptyTable(this.#table);
            json.entries.forEach(entry => {
                const row = tbody.insertRow();
                row.insertCell().append(buildUsernameSpan(entry.userId, entry.username, entry.userType));
                row.insertCell().innerText = entry.pageId;
                row.insertCell().innerText = entry.sectionId;
                row.insertCell().innerText = entry.upVoted ? 'up' : 'down';
                row.insertCell().innerText = entry.feedback;
                row.insertCell().innerText = formatTimestampDefaultDateFormat(entry.creationTime);
                row.insertCell().innerText = formatTimestampDefaultDateFormat(entry.updateTime);
            });
        });
    }
}

window.onload = () => new AdminContentSectionFeedbackPage();
