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

/**
 * Encode player name for URL by replacing spaces with underscores
 * @param {string} playerName - The player name to encode
 * @returns {string} The encoded player name
 */
function encodePlayerNameForUrl(playerName) {
    return playerName.replace(/ /g, '_');
}

/**
 * Decode player name from URL by replacing underscores with spaces
 * @param {string} encodedName - The encoded player name
 * @returns {string} The decoded player name
 */
function decodePlayerNameFromUrl(encodedName) {
    return encodedName.replace(/_/g, ' ');
}

/**
 * Extract player name from URL path like /database/player/{playerName}/...
 * @returns {string} The decoded player name
 */
// TODO: should be replaced by data attributes (like we did for data-player-id)
function extractPlayerNameFromUrl() {
    const pathParts = window.location.pathname.split('/');
    const playerIndex = pathParts.indexOf('player');
    if (playerIndex !== -1 && playerIndex + 1 < pathParts.length) {
        return decodePlayerNameFromUrl(pathParts[playerIndex + 1]);
    }
    throw new Error('Player name not found in URL');
}

/**
 * Display version history table for a player
 * @param {PlayerProfileVersionHistoryEntryDto[]} versionHistory - The version history entries
 * @param {HTMLElement} container - The container element to render into
 */
function renderPlayerProfileVersionHistory(versionHistory, container) {
    container.innerHTML = '';

    if (versionHistory.length === 0) {
        container.innerHTML = '<p>No edit history yet.</p>';
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
            <th>version</th>
            <th>editor</th>
            <th>date</th>
            <th>canonical name</th>
            <th>chinese name</th>
            <th>gender</th>
            <th>comment</th>
            <th>actions</th>
        </tr>
    `;
    table.appendChild(thead);

    // create body
    const tbody = document.createElement('tbody');
    versionHistory.forEach(version => {
        const slug = encodePlayerNameForUrl(version.canonicalName);
        const lowerSlug = slug.toLowerCase().replaceAll('_', '-');

        const row = tbody.insertRow();

        if (!version.enabled) {
            row.className = 'disabled-version-row';
        }

        const enabledCell = row.insertCell();
        enabledCell.appendChild(buildSimpleSpan(version.enabled ? 'ok' : 'disabled'));

        const versionCell = row.insertCell();
        versionCell.textContent = version.versionIndex.toString();

        const editorCell = row.insertCell();
        editorCell.appendChild(buildUsernameSpan(version.editorUserId, version.editorUsername, UserType.AUTHENTICATED));

        const dateCell = row.insertCell();
        dateCell.textContent = formatTimestampDefaultDateFormat(version.versionTime);

        const canonicalNameCell = row.insertCell();
        canonicalNameCell.appendChild(buildLink(`/database/player/${slug}`, version.canonicalName));

        const chineseNameCell = row.insertCell();
        chineseNameCell.textContent = version.chineseName || '--';
        chineseNameCell.style.fontStyle = version.chineseName ? 'normal' : 'italic';

        const genderCell = row.insertCell();
        genderCell.textContent = version.gender === 'M' ? 'Male' : version.gender === 'F' ? 'Female' : '--';
        genderCell.style.fontStyle = version.gender ? 'normal' : 'italic';

        const commentSpan = buildSimpleSpan(version.comment);
        commentSpan.id = `comment-${lowerSlug}-${version.versionIndex}-${randomId()}`;
        row.insertCell().append(cropAndAddToolTip(commentSpan, 60));

        const actionsCell = row.insertCell();
        actionsCell.appendChild(buildLink(`/database/player/${slug}?version=${version.versionIndex}`, 'view'));

        // add diff link if version >= 1 (compare with previous version)
        if (version.versionIndex >= 1) {
            actionsCell.appendChild(document.createTextNode(' | '));
            actionsCell.appendChild(buildLink(`/database/player/${slug}/edit-diff?version=${version.versionIndex}`, 'diff'));
        }
    });
    table.appendChild(tbody);

    container.appendChild(table);
}
