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

class MyBotGamesPage extends InfiniteScrollPage {

    #myGameItems = document.getElementById('my-game-items');
    #noGameMessage = document.getElementById('no-game-played-message');

    constructor() {
        super();
        this.fetchItems();
    }

    baseUrl() {
        return '/api/botgame/list-user-games';
    }

    deserializeJsonEntry(jsonEntry) {
        return new BotGameEntryDto(jsonEntry);
    }

    /**
     * @param entry {BotGameEntryDto}
     */
    extractToken(entry) {
        return entry.lastUpdated.toString();
    }

    showNoItem(value) {
        this.#noGameMessage.style.display = value ? 'block' : 'none';
    }

    /**
     * @param entries {BotGameEntryDto[]}
     * @returns {void}
     */
    addEntries(entries) {
        function buildBotIcon() {
            const puzzleImg = document.createElement('img');
            puzzleImg.className = 'icon';
            puzzleImg.src = '/images/icons/bot-icon.png';
            puzzleImg.alt = 'Bot';
            return wrapInDiv(puzzleImg);
        }

        /**
         * @param entry {BotGameEntryDto}
         * @returns {HTMLDivElement}
         */
        function buildCustomFenIcon(entry) {
            const puzzleImg = document.createElement('img');
            puzzleImg.className = 'icon';
            puzzleImg.src = '/images/icons/color-palette.png';
            puzzleImg.alt = 'This game was played with a custom start position';
            puzzleImg.style.opacity = '75%';

            const div = wrapInDiv(puzzleImg);
            div.id = `custom-start-fen-${entry.gameId}`;
            addToolTip(div, 'This game was played with a custom start position');
            return div;
        }

        entries.forEach(entry => {
            // structure
            const leftPane = document.createElement('div');
            leftPane.className = 'left-pane';

            const variantPane = document.createElement('div');
            variantPane.className = 'variant-pane';

            const middlePane = document.createElement('div');
            middlePane.className = 'middle-pane';

            const customFenIndicatorPane = document.createElement('div');
            customFenIndicatorPane.className = 'indicator-pane';

            const preAnalysisIndicatorPane = document.createElement('div');
            preAnalysisIndicatorPane.className = 'indicator-pane';

            const outcomeIndicatorPane = document.createElement('div');
            outcomeIndicatorPane.className = 'indicator-pane';

            const rightPane = document.createElement('div');
            rightPane.className = 'right-pane';

            const item = document.createElement('a');
            item.className = 'my-game-item';
            item.setAttribute('href', entry.gameUrl);

            item.append(
                variantPane,
                leftPane,
                middlePane,
                customFenIndicatorPane,
                preAnalysisIndicatorPane,
                outcomeIndicatorPane,
                rightPane
            );

            this.#myGameItems.append(item);
            addMiniboardDiv(item, entry.gameId, entry.currentFen, entry.color);

            // left pane
            leftPane.append(buildBotIcon());

            // variant pane
            variantPane.append(buildVariantCell(entry.variant));

            // middle pane
            const opponentDiv = document.createElement('div');
            opponentDiv.className = 'opponent';
            opponentDiv.innerText = `${entry.formattedEngine} (${entry.depth})`;
            const middlePaneItems = [
                opponentDiv,
                wrapInDiv(buildColorSpan(entry.color)),
            ];
            middlePane.append(...middlePaneItems);

            if (entry.hasCustomStartFen) {
                customFenIndicatorPane.append(buildCustomFenIcon(entry));
            }
            if (entry.isPreAnalyzed) {
                preAnalysisIndicatorPane.append(buildPreAnalyzedIcon(entry.gameId));
            }

            // outcome indicator pane
            const outcomeDiv = buildUserOutcomeDiv(entry);
            if (outcomeDiv != null) {
                outcomeIndicatorPane.append(outcomeDiv);
            }

            // right pane
            const statusDiv = document.createElement('div');
            statusDiv.classList.add('game-status');
            statusDiv.innerText = entry.formattedStatus;

            const lastUpdatedDiv = document.createElement('div');
            lastUpdatedDiv.className = 'last-modified';
            lastUpdatedDiv.id = 'last-updated-' + entry.gameId;
            setRelativeTimeAndToolTip(lastUpdatedDiv, entry.lastUpdated);

            const moveDiv = document.createElement('div');
            moveDiv.className = 'move-index';
            moveDiv.innerText = `move ${entry.fullMoveIndex}`;

            rightPane.append(
                statusDiv,
                lastUpdatedDiv,
                moveDiv
            );
        });
    }

    #renderList(entries, tbody) {
        // let tbody = emptyTable(this.#table);
        // this.#showNoGameMessage(this.#entries.length === 0);
        entries.forEach(entry => {
            let row = tbody.insertRow();
            row.id = 'game-' + entry.gameId;

            let engineCell = row.insertCell();
            engineCell.classList.add('clickable');
            engineCell.append(buildLink(entry.gameUrl, entry.formattedEngine));

            let depthCell = row.insertCell();
            depthCell.classList.add('clickable');
            depthCell.innerText = entry.depth.toString();

            let customFenCell = row.insertCell();
            customFenCell.classList.add('clickable');
            if (entry.hasCustomStartFen) {
                customFenCell.innerText = 'custom';
            } else {
                customFenCell.innerText = 'standard';
            }

            let colorCell = row.insertCell();
            colorCell.classList.add('clickable');
            colorCell.append(buildColorSpan(entry.color));

            let statusCell = row.insertCell();
            statusCell.classList.add('clickable', 'long-label-cell');
            let status = entry.formattedStatus;
            if (status != null) {
                statusCell.innerText = status;
            }

            let outcomeCell = row.insertCell();
            outcomeCell.classList.add('clickable');
            outcomeCell.innerText = entry.formattedOutcome;

            let moveCell = row.insertCell();
            moveCell.classList.add('clickable');
            moveCell.innerText = entry.fullMoveIndex.toString();

            let createdCell = row.insertCell();
            createdCell.id = 'created-' + entry.gameId;
            createdCell.classList.add('clickable', 'date-cell');
            setRelativeTimeAndToolTip(createdCell, entry.created);

            let lastUpdatedCell = row.insertCell();
            lastUpdatedCell.id = 'last-updated-' + entry.gameId;
            lastUpdatedCell.classList.add('clickable', 'date-cell');
            setRelativeToInitialTimeAndToolTip(lastUpdatedCell, entry.created, entry.lastUpdated);
        });

        // addMouseoverListenersForClickableCells(this.#table);
        // addClickListenerForClickableCells(this.#table, (row, _) => {
        //     let gameId = row.id.substring('game-'.length);
        //     window.location.href = '/playbot?id=' + gameId;
        // });
    }

}

window.onload = () => new MyBotGamesPage();
