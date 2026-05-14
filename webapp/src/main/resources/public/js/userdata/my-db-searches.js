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
            item.setAttribute('href', entry.repeatSearchUrl);

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
            summaryDiv.innerText = entry.searchSummary;
            middlePane.append(summaryDiv);

            let numberOfResults = 0;
            if (entry.numberOfResults > 0) {
                numberOfResults = entry.numberOfResults;
            }
            if (entry.offset > 0) {
                numberOfResults += entry.offset;
            }

            const resultsDiv = document.createElement('div');
            resultsDiv.className = 'game-status';
            resultsDiv.innerText = `${numberOfResults} result${numberOfResults > 1 ? 's' : ''}`;
            middlePane.append(resultsDiv);

            // right pane: last used time
            const lastUsedDiv = document.createElement('div');
            lastUsedDiv.className = 'last-modified';
            lastUsedDiv.id = `last-used-${entry.queryId}`;
            setRelativeTimeAndToolTip(lastUsedDiv, entry.updateTime);
            rightPane.append(lastUsedDiv);
        });
    }

}

window.onload = () => new MyDbSearchesPage();
