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

class MyDbSearchesPage extends InfiniteScrollPage {

    /**
     * @type {HTMLDivElement}
     */
    #itemsDiv = document.getElementById('my-search-items');

    /**
     * @type {HTMLDivElement}
     */
    #noSearchesMessage = document.getElementById('no-searches-message');

    constructor() {
        super();
        this.fetchItems();
    }

    baseUrl() {
        return '/api/database/list-user-searches';
    }

    deserializeJsonEntry(jsonEntry) {
        return new MyDbSearchEntryDto(jsonEntry);
    }

    /**
     * @param entry {MyDbSearchEntryDto}
     */
    extractToken(entry) {
        return entry.updateTime.toString();
    }

    showNoItem(value) {
        this.#noSearchesMessage.style.display = value ? 'block' : 'none';
    }

    /**
     * @param entries {MyDbSearchEntryDto[]}
     */
    addEntries(entries) {
        entries.forEach(entry => {
            const leftPane = buildDivWithClass('left-pane');
            const middlePane = buildDivWithClass('middle-pane');
            const rightPane = buildDivWithClass('right-pane');

            const item = document.createElement('a');
            item.className = 'my-game-item';
            item.setAttribute('href', this.#buildRepeatSearchUrl(entry));

            item.append(leftPane, middlePane, rightPane);
            this.#itemsDiv.append(item);

            // left pane: database icon
            const dbIcon = document.createElement('img');
            dbIcon.className = 'icon';
            dbIcon.src = '/images/icons/data-search.png';
            dbIcon.alt = 'DB Search';
            leftPane.append(wrapInDiv(dbIcon));

            // middle pane: search summary
            const summaryDiv = document.createElement('div');
            summaryDiv.className = 'default-text';
            summaryDiv.append(this.#buildSearchSummaryElement(entry));
            middlePane.append(summaryDiv);

            let numberOfResultsStr;
            if (entry.numberOfResults >= 20) {
                numberOfResultsStr = '20+ results';
            } else if (entry.numberOfResults > 0) {
                numberOfResultsStr = `${entry.numberOfResults} result${entry.numberOfResults > 1 ? 's' : ''}`;
            } else {
                numberOfResultsStr = 'No results';
            }

            const resultsDiv = document.createElement('div');
            resultsDiv.className = 'game-status';
            resultsDiv.innerText = numberOfResultsStr;
            middlePane.append(resultsDiv);

            // right pane: last used time
            const lastUsedDiv = document.createElement('div');
            lastUsedDiv.className = 'last-modified';
            lastUsedDiv.id = `last-used-${entry.queryId}`;
            setRelativeTimeAndToolTip(lastUsedDiv, entry.updateTime);
            rightPane.append(lastUsedDiv);

            // mini board overview when a FEN was searched
            if (entry.fen !== null && entry.fen.trim().length > 0) {
                const orientation = entry.playerColor === 'BLACK' ? Color.BLACK : Color.RED;
                const fen = entry.fen.trim();
                // Stored FENs may be abridged (board layout only); complete them so loadFen accepts them.
                const fullFen = fen.includes(' ') ? fen : `${fen} w - - 0 1`;
                addMiniboardDiv(item, entry.queryId, fullFen, orientation);
            }
        });
    }

    /**
     * Builds the URL to repeat the given search on the database search page.
     * @param entry {MyDbSearchEntryDto}
     * @returns {string}
     */
    #buildRepeatSearchUrl(entry) {
        const params = new URLSearchParams();
        if (entry.playerName !== null) {
            // strip any bracketed content (e.g. Chinese name suffix) added for display
            const sanitizedPlayerName = entry.playerName.replace(/\s*\([^)]*\)\s*/g, ' ').trim();
            if (sanitizedPlayerName.length > 0) {
                params.set('playerName', sanitizedPlayerName);
            }
        }
        if (entry.playerColor !== null) {
            params.set('playerColor', entry.playerColor);
        }
        if (entry.eventName !== null && entry.eventName.trim().length > 0) {
            params.set('eventName', entry.eventName);
        }
        if (entry.searchStart !== null) {
            params.set('dateStart', entry.searchStart);
        }
        if (entry.searchEnd !== null) {
            params.set('dateEnd', entry.searchEnd);
        }
        if (entry.fen !== null) {
            params.set('fen', entry.fen);
        }
        const queryString = params.toString();
        return `/database/search${queryString ? '?' + queryString : ''}`;
    }

    /**
     * Builds a human-readable summary of the search parameters as a DOM element.
     * The player name is rendered with a color class when a player color is set.
     * @param entry {MyDbSearchEntryDto}
     * @returns {HTMLSpanElement}
     */
    #buildSearchSummaryElement(entry) {
        const container = document.createElement('span');
        const parts = [];

        if (entry.playerName !== null && entry.playerName.trim().length > 0) {
            const playerSpan = document.createElement('span');
            playerSpan.innerText = entry.playerName;
            if (entry.playerColor === Color.RED) {
                playerSpan.classList.add('red-color');
            } else if (entry.playerColor === Color.BLACK) {
                playerSpan.classList.add('black-color');
            }
            parts.push(playerSpan);
        }
        if (entry.eventName !== null && entry.eventName.trim().length > 0) {
            parts.push(entry.eventName);
        }
        if (entry.searchStart !== null || entry.searchEnd !== null) {
            const start = entry.searchStart ?? '...';
            const end = entry.searchEnd ?? '...';
            parts.push(`${start} → ${end}`);
        }
        if (entry.fen !== null && entry.fen.trim().length > 0) {
            parts.push(`FEN: ${entry.fen}`);
        }

        if (parts.length === 0) {
            container.innerText = 'All games';
            return container;
        }

        parts.forEach((part, index) => {
            if (index > 0) {
                container.append(document.createTextNode(', '));
            }
            container.append(part);
        });
        return container;
    }

}

window.onload = () => new MyDbSearchesPage();
