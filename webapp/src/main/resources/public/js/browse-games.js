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

const DEFAULT_DISPLAY = '';
const FIRST_ROW_SIZE = 3;
const PRE_RENDERED_THUMBS_COUNT = 6;

class BrowseGamesPage extends InfiniteScrollPage {

    #gamesContainer;
    #noGamesMessage = document.getElementById('no-games-message');

    /**
     * @type {GameThumb[]}
     */
    #initThumbs = [];

    /**
     * @type {HTMLDivElement[]}
     */
    #initThumbDivs = [];

    #renderedCount = 0;

    /**
     * Number of pre-rendered game thumb divs present in the DOM.
     * Derived from the template (e.g. `{{game_thumb}}[[iterations:N; ...]]`).
     * @type {number}
     */
    #initElementsCount = 0;

    /**
     * @type {string}
     */
    #gameType;

    #playerNameHighlight = null;

    /**
     * @param gameType {'pvp'|'pvb'|'db'}
     * @param playerNameHighlight {string|null}
     */
    constructor(gameType, playerNameHighlight = null) {
        super();
        this.#gameType = gameType;
        this.#playerNameHighlight = playerNameHighlight;
        this.#gamesContainer = document.getElementById(`${gameType}-games-container`);

        // initialize board GUIs for pre-rendered game thumbs
        this.#initThumbDivs = getElementsByClassNameArray(`${gameType}-game-thumb`);
        this.#initElementsCount = this.#initThumbDivs.length;
        this.#initThumbDivs.forEach((thumbDiv, i) => {
            const boardId = `last-${gameType}-game-board-${i}`;
            const boardElement = document.getElementById(boardId);

            if (!boardElement) {
                console.error(`Board element not found: ${boardId}`);
                return;
            }

            const options = {
                elementId: boardId,
                showCoordinates: false,
                mini: true,
            };

            this.#initThumbs.push(new GameThumb(thumbDiv, createWebappBoardGui(options)));
        });

        this.fetchItems();
    }

    shouldFetchNextPage() {
        const maybeElement = getLastElementOfClassName(`${this.#gameType}-game-thumb`);
        return maybeElement !== null && isInViewport(maybeElement);
    }

    baseUrl() {
        return `/api/game-data/list-latest-${this.#gameType}-games`;
    }

    deserializeJsonEntry(jsonEntry) {
        return new GameMetadataDto(jsonEntry);
    }

    /**
     * @param entry {GameMetadataDto}
     */
    extractToken(entry) {
        return entry.lastUpdated.toString();
    }

    /**
     * @param value {boolean}
     */
    showNoItem(value) {
        this.#noGamesMessage.style.display = value ? 'block' : 'none';
        if (value) {
            this.#setSecondThumbRowVisibility(false);
        }
    }

    /**
     * @returns {Map<string, any>}
     */
    additionalParameters() {
        const params = new Map();
        params.set('limit', '12');
        params.set('distinctByUsers', 'false');
        return params;
    }

    /**
     * @param entries {GameMetadataDto[]}
     */
    addEntries(entries) {
        const isInitialBatch = this.#renderedCount === 0;
        entries.forEach((entry) => {
            if (this.#renderedCount < this.#initElementsCount) {
                this.#initThumbDivs[this.#renderedCount].style.display = DEFAULT_DISPLAY;
                // Use pre-rendered thumbs for first batch
                this.#initThumbs[this.#renderedCount].render(
                    entry,
                    `browse_${this.#gameType}`,
                    this.#playerNameHighlight
                );
                this.#renderedCount++;
            } else {
                // Create new thumbs for subsequent batches (infinite scroll)
                this.#createNewGameThumb(entry);
                this.#renderedCount++;
            }
        });

        if (isInitialBatch) {
            this.#setSecondThumbRowVisibility(this.#renderedCount > FIRST_ROW_SIZE);
        }
    }

    /**
     * @param gameMetadataDto {GameMetadataDto}
     */
    #createNewGameThumb(gameMetadataDto) {
        const template = this.#initThumbs[this.#initThumbs.length - 1];
        const boardId = `${this.#gameType}-game-board-${this.#renderedCount}`;

        const gameThumb = template.cloneInto(this.#gamesContainer, boardId);
        gameThumb.render(
            gameMetadataDto,
            `browse_${this.#gameType}`,
            this.#playerNameHighlight
        );
    }

    /**
     * @param show {boolean}
     */
    #setSecondThumbRowVisibility(show) {
        for (let i = FIRST_ROW_SIZE; i < Math.min(PRE_RENDERED_THUMBS_COUNT, this.#initThumbDivs.length); i++) {
            this.#initThumbDivs[i].style.display = show ? DEFAULT_DISPLAY : 'none';
        }
    }

}
