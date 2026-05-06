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

class DatabasePlayerEdit extends BasePage {

    constructor() {
        super();
        this.playerName = extractPlayerNameFromUrl();
        this.currentPlayerId = document.querySelector('body').dataset.playerId;
        this.#updateViewProfileLink();
        this.#loadCurrentEdit();
        this.#loadEditHistory();
        this.#setupEventHandlers();
    }

    #updateViewProfileLink() {
        const profileLink = document.getElementById('view-player-profile-link');
        if (profileLink) {
            profileLink.href = `/database/player/${encodePlayerNameForUrl(this.playerName)}`;
        }
    }

    #loadCurrentEdit() {
        const url = `/api/database/player/current-edit?playerId=${this.currentPlayerId}`;
        getAndHandle(url, (json) => {
            console.log('current edit data:', json);
            this.#renderEditData(new DatabasePlayerProfileDto(json));
        });
    }

    #loadEditHistory() {
        const url = `/api/database/info/player/edit-history-by-player-id?playerId=${this.currentPlayerId}`;
        getAndHandle(url, (json) => {
            console.log('edit history data:', json);
            const versionHistory = new DatabasePlayerVersionHistoryDto(json);
            const historyContainer = document.getElementById('version-history-container');
            renderPlayerProfileVersionHistory(versionHistory.versionHistory, historyContainer);
        });
    }

    /**
     * @param playerData {DatabasePlayerProfileDto}
     */
    #renderEditData(playerData) {
        // hide loading message and show form
        document.getElementById('loading-message').style.display = 'none';
        document.getElementById('player-edit-form').style.display = 'block';

        // populate form fields
        document.getElementById('canonical-name').value = playerData.canonicalName;
        document.getElementById('chinese-name').value = playerData.chineseName || '';
        document.getElementById('gender').value = playerData.gender || '';
        document.getElementById('profile-text').value = playerData.profileText || '';

        // populate sources (sorted by index ascending)
        const sourcesContainer = document.getElementById('sources-container');
        sourcesContainer.innerHTML = ''; // Clear existing sources

        if (playerData.sources.length === 0) {
            // Add an empty source entry to remind users to add at least one source
            this.#addSourceToUI(null);
        } else {
            playerData.sources
                .sort((a, b) => a.index - b.index)
                .forEach(source => {
                    this.#addSourceToUI(source);
                });
        }
    }

    #setupEventHandlers() {
        // add source button
        document.getElementById('add-source-btn').addEventListener('click', () => {
            this.#addSourceToUI(null);
        });

        // save changes button
        document
            .getElementById('save-changes-btn')
            .addEventListener('click', () => {
                this.#savePlayerData();
            });

        // cancel button
        document
            .getElementById('cancel-btn')
            .addEventListener('click', () => {
                window.location.reload();
            });
    }

    #addSourceToUI(source) {
        const sourcesContainer = document.getElementById('sources-container');
        const sourceItem = document.createElement('div');
        sourceItem.className = 'source-item';

        const index = source ? source.index : sourcesContainer.querySelectorAll('.source-item').length + 1;
        const url = source ? source.url : '';
        const title = source ? source.title : '';

        const viewButtonHtml = url ? `<input type="button" class="app-buttons-blue visit-source-btn" value="visit" />` : '<div></div>';

        sourceItem.innerHTML = `
            <input type="number" name="source-index[]" value="${index}" placeholder="Index" min="1" readonly />
            <div style="grid-column: 2 / 3;">
                <input type="text" name="source-title[]" value="${title}" placeholder="Title" class="source-title" style="width: 100%; margin-bottom: 0.25rem;" />
                <input type="url" name="source-url[]" value="${url}" placeholder="URL" class="source-url" style="width: 100%;" />
            </div>
            <input type="button" class="app-buttons-red remove-source-btn" value="remove" />
            ${viewButtonHtml}
        `;

        // Add event listener for the remove button
        const removeBtn = sourceItem.querySelector('.remove-source-btn');
        removeBtn.addEventListener('click', () => {
            sourceItem.remove();
            this.#reindexSources();
        });

        // Add event listener for the visit button
        const visitBtn = sourceItem.querySelector('.visit-source-btn');
        if (visitBtn) {
            visitBtn.addEventListener('click', () => {
                window.open(url, '_blank', 'noopener,noreferrer');
            });
        }

        sourcesContainer.appendChild(sourceItem);
    }

    #reindexSources() {
        const sourcesContainer = document.getElementById('sources-container');
        const sourceItems = sourcesContainer.querySelectorAll('.source-item');

        sourceItems.forEach((item, index) => {
            const indexInput = item.querySelector('input[name="source-index[]"]');
            indexInput.value = index + 1;
        });
    }

    #savePlayerData() {
        const form = document.getElementById('edit-form');
        const formData = new FormData(form);

        // Get basic fields
        const playerId = this.currentPlayerId; // We need to store this when loading
        const canonicalName = formData.get('canonicalName');
        const chineseName = formData.get('chineseName') || null;
        const gender = formData.get('gender') || null;
        const profileText = formData.get('profileText') || null;
        const editComment = formData.get('editComment') || null;

        // collect sources
        const sourceIndices = formData.getAll('source-index[]');
        const sourceTitles = formData.getAll('source-title[]');
        const sourceUrls = formData.getAll('source-url[]');

        const sources = sourceIndices.map((index, i) => ({
            index: parseInt(index),
            title: sourceTitles[i],
            url: sourceUrls[i]
        }));

        // build request body matching DatabasePlayerEditInfo structure
        const requestBody = {
            playerId: playerId,
            canonicalName: canonicalName,
            chineseName: chineseName,
            gender: gender,
            profileText: profileText,
            editComment: editComment,
            sources: sources
        };

        const url = `/api/database/player/edit`;
        postAndHandle(url, requestBody, (response) => {
            console.log('Player data saved successfully:', response);
            UI.pushInfoNotification('Player profile updated successfully');
            // reload the page to show updated data
            setTimeout(() => {
                window.location.reload();
            }, 1_500);
        });
    }

}

window.onload = () => new DatabasePlayerEdit();
