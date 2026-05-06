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

class MyDatabaseEditsPage extends BasePage {

    #container = document.getElementById('my-edits-container');
    #noEditsMessage = document.getElementById('no-edits-message');

    constructor() {
        super();
        this.#fetchAndRenderEdits();
    }

    #fetchAndRenderEdits() {
        getAndHandle('/api/database/list-user-edits', (data) => {
            this.#renderEdits(data.entries);
        });
    }

    /**
     * Render the list of edits as a table
     * @param {Array} entries - Array of edit entries
     */
    #renderEdits(entries) {
        this.#container.innerHTML = '';

        if (entries.length === 0) {
            this.#container.style.display = 'none';
            this.#noEditsMessage.style.display = 'block';
            return;
        }

        const table = document.createElement('table');
        table.classList.add('standard-data-table', 'edit-history-table');
        table.style.width = '100%';

        // create header
        const thead = document.createElement('thead');
        thead.innerHTML = `
            <tr>
                <th>status</th>
                <th>canonical name</th>
                <th>chinese name</th>
                <th>version</th>
                <th>latest editor</th>
                <th>latest edit</th>
                <th>comment</th>
                <th>actions</th>
            </tr>
        `;
        table.appendChild(thead);

        // create body
        const tbody = document.createElement('tbody');
        entries.forEach(entry => {
            const slug = encodePlayerNameForUrl(entry.playerCanonicalName);
            const lowerSlug = slug.toLowerCase().replaceAll('_', '-');

            const row = tbody.insertRow();
            if (!entry.enabled) {
                row.classList.add('disabled-version-row');
            }

            const enabledCell = row.insertCell();
            enabledCell.appendChild(buildSimpleSpan(entry.enabled ? 'ok' : 'disabled'));

            const canonicalNameCell = row.insertCell();
            canonicalNameCell.appendChild(buildLink(`/database/player/${slug}`, entry.playerCanonicalName));

            const chineseNameCell = row.insertCell();
            chineseNameCell.textContent = entry.playerChineseName || '--';
            chineseNameCell.style.fontStyle = entry.playerChineseName ? 'normal' : 'italic';

            const versionCell = row.insertCell();
            versionCell.textContent = entry.version.toString();

            const editorCell = row.insertCell();
            editorCell.appendChild(buildUsernameSpan(entry.latestEditorId, entry.latestEditorUsername, UserType.AUTHENTICATED));

            const dateCell = row.insertCell();
            dateCell.textContent = formatTimestampDefaultDateFormat(entry.latestEditTimestamp);

            const commentSpan = buildSimpleSpan(entry.latestComment);
            commentSpan.id = `comment-${lowerSlug}-${entry.version}-${randomId()}`;
            row.insertCell().append(cropAndAddToolTip(commentSpan, 50));

            const actionsCell = row.insertCell();
            actionsCell.appendChild(buildLink(`/database/player/${slug}?version=${entry.version}`, 'view'));

            // add diff link if version >= 1
            if (entry.version >= 1) {
                actionsCell.appendChild(document.createTextNode(' | '));
                actionsCell.appendChild(buildLink(`/database/player/${slug}/edit-diff?version=${entry.version}`, 'diff'));
            }

            // add edit history link
            actionsCell.appendChild(document.createTextNode(' | '));
            actionsCell.appendChild(buildLink(`/database/player/${slug}/edit-history`, 'history'));

        });
        table.appendChild(tbody);

        this.#container.appendChild(table);
    }

}

window.onload = () => new MyDatabaseEditsPage();
