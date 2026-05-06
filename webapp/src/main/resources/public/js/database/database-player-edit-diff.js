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

class DatabasePlayerEditDiff extends BasePage {

    /**
     * @type {string}
     */
    #playerId;

    constructor() {
        super();
        this.playerName = extractPlayerNameFromUrl();
        this.#playerId = document.querySelector('body').dataset.playerId;
        this.#initializeDiff(
            'chinese-name-from',
            'chinese-name-to',
            'chinese-name-diff-output',
            'chinese-name-no-diff'
        );
        this.#initializeDiff(
            'gender-from',
            'gender-to',
            'gender-diff-output',
            'gender-no-diff'
        );
        this.#initializeDiff(
            'profile-description-from',
            'profile-description-to',
            'profile-description-diff-output',
            'profile-description-no-diff'
        );
        this.#initializeDiff(
            'profile-sources-from',
            'profile-sources-to',
            'profile-sources-diff-output',
            'profile-sources-no-diff'
        );
        this.#loadEditHistory();
    }

    #initializeDiff(fromDivId, toDivId, outputDivId, noDiffDivId) {
        // Extract content from template tags to preserve HTML while preventing unclosed tags from affecting page layout
        const fromTemplate = document.getElementById(fromDivId).querySelector('template');
        const toTemplate = document.getElementById(toDivId).querySelector('template');

        const fromHtml = fromTemplate ? fromTemplate.innerHTML.trim() : '';
        const toHtml = toTemplate ? toTemplate.innerHTML.trim() : '';

        const diff = Diff.diffWords(fromHtml, toHtml);
        let html = '';
        let hasChanges = false;

        diff.forEach((part) => {
            // don't escape HTML - we want to preserve formatting like <i>, <b>, etc.
            const text = part.value;
            if (part.added) {
                html += `<span class="diff-added">${text}</span>`;
                hasChanges = true;
            } else if (part.removed) {
                html += `<span class="diff-removed">${text}</span>`;
                hasChanges = true;
            } else {
                html += `<span class="diff-unchanged">${text}</span>`;
            }
        });

        if (hasChanges) {
            const outputDiv = document.getElementById(outputDivId);
            outputDiv.innerHTML = html;
            outputDiv.style.display = 'block';
        } else {
            const noDiffDiv = document.getElementById(noDiffDivId);
            if (noDiffDiv) {
                noDiffDiv.style.display = 'block';
            }
        }
    }

    #loadEditHistory() {
        const url = `/api/database/info/player/edit-history-by-player-id?playerId=${this.#playerId}`;

        getAndHandle(url, (json) => {
            const history = new DatabasePlayerVersionHistoryDto(json);

            // display version history
            const historyContainer = document.getElementById('version-history-container');
            renderPlayerProfileVersionHistory(history.versionHistory, historyContainer);

            // update link to player profile
            const playerProfileLink = document.getElementById('player-profile-link');
            if (playerProfileLink) {
                const slug = encodePlayerNameForUrl(this.playerName);
                playerProfileLink.href = `/database/player/${slug}`;
            }
        });
    }

}

window.onload = () => new DatabasePlayerEditDiff();
