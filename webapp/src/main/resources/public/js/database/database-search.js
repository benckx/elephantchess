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

const DATE_FORMAT = 'yyyy-mm-dd';
const API_DATABASE = '/api/database';

class DatabaseSearchPage extends InfiniteScrollPage {

    #totalGamesSpan = document.getElementById('total-games');
    #playerSearchField = new PlayerSearchAutocompleteSearchField();
    #eventSearchField = new EventSearchAutocompleteSearchField();
    #dateRangePicker = new DateRangePicker(document.getElementById('date-range-search'), {format: DATE_FORMAT});
    #fenSearchField = document.getElementById('fen-search');

    #searchButton = document.getElementById('search-button');
    #clearButton = document.getElementById('clear-button');

    #resultContainer = document.getElementById('search-results');
    #noResultsMessage = document.getElementById('no-results-message'); // TODO

    #yearToDateRangeSelector = document.getElementById('range-selector-ytd');
    #last12MonthsRangeSelector = document.getElementById('range-selector-12m');
    #monthToDateRangeSelector = document.getElementById('range-selector-mtd');
    #last30DaysRangeSelector = document.getElementById('range-selector-30d');

    #featuredPlayersList = document.getElementById('featured-players-list');
    #randomizeFeaturedPlayersButton = document.getElementById('randomize-featured-players-button');

    // Search parameters
    #currentPlayerName = null;
    #currentPlayerIds = [];
    #currentEventName = null;
    #currentEventIds = [];
    #currentInterval = null;
    #currentFen = null;

    constructor() {
        super();

        this.#fetchTotalGames();
        this.#fetchFeaturedPlayers();

        this.#randomizeFeaturedPlayersButton.addEventListener('click', (e) => {
            e.preventDefault();
            this.#fetchFeaturedPlayers();
        });

        document
            .getElementsByTagName('html')
            .item(0)
            .addEventListener('click', () => this.#closeAllSuggestionBoxes());

        this.#enablePlayerColorRadioButtons(false);

        this.#searchButton.addEventListener('click', () => {
            this.#setButtonEnabled(false);
            this.#clearResults();

            this.#currentPlayerName = this.#playerSearchField.inputFieldValue;
            this.#currentPlayerIds = this.#playerSearchField.getSelectedId() != null ? [this.#playerSearchField.getSelectedId()] : [];
            this.#currentEventName = this.#eventSearchField.inputFieldValue;
            this.#currentEventIds = this.#eventSearchField.getSelectedId() != null ? [this.#eventSearchField.getSelectedId()] : [];
            this.#currentInterval = this.#dateRangePicker.getDates(DATE_FORMAT);
            this.#currentFen = this.#fenSearchField.value.trim() !== '' ? this.#fenSearchField.value.trim() : null;
            this.fetchItems();
        });

        this.#clearButton.addEventListener('click', () => {
            this.#playerSearchField.clear();
            this.#eventSearchField.clear();
            getElementsByClassNameArray('datepicker-field').forEach(field => field.value = '');
            this.#dateRangePicker = new DateRangePicker(document.getElementById('date-range-search'), {format: DATE_FORMAT});
            this.#fenSearchField.value = '';
            this.#clearResults();
            this.#enablePlayerColorRadioButtons(false);
        });

        this.#yearToDateRangeSelector.addEventListener('click', () => {
            const firstDayOfYear = new Date();
            firstDayOfYear.setMonth(0);
            firstDayOfYear.setDate(1);
            this.#dateRangePicker.setDates(firstDayOfYear, new Date());
        });

        this.#last12MonthsRangeSelector.addEventListener('click', () => {
            const twelveMonthsAgo = new Date();
            twelveMonthsAgo.setFullYear(twelveMonthsAgo.getFullYear() - 1);
            this.#dateRangePicker.setDates(twelveMonthsAgo, new Date());
        });

        this.#monthToDateRangeSelector.addEventListener('click', () => {
            const firstDayOfMonth = new Date();
            firstDayOfMonth.setDate(1);
            this.#dateRangePicker.setDates(firstDayOfMonth, new Date());
        });

        this.#last30DaysRangeSelector.addEventListener('click', () => {
            const thirtyDaysAgo = new Date();
            thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
            this.#dateRangePicker.setDates(thirtyDaysAgo, new Date());
        });

        document
            .getElementById(this.#playerSearchField.inputFieldId)
            .addEventListener('input', e => {
                const isEmpty = e.target.value.trim() === '';
                console.log('player field input isEmpty=', isEmpty);
                this.#enablePlayerColorRadioButtons(!isEmpty);
            });

        addToolTip(
            document.getElementById('player-color-red-input'),
            'When searching for a specific player, filter games where that player played as Red.'
        );
        addToolTip(
            document.getElementById('player-color-red-label'),
            'When searching for a specific player, filter games where that player played as Red.'
        );
        addToolTip(
            document.getElementById('player-color-black-input'),
            'When searching for a specific player, filter games where that player played as Black.'
        );
        addToolTip(
            document.getElementById('player-color-black-label'),
            'When searching for a specific player, filter games where that player played as Black.'
        );
        addToolTip(
            document.getElementById('player-color-any-input'),
            'When searching for a specific player, select this to not filter by playing color.'
        );
        addToolTip(
            document.getElementById('player-color-any-label'),
            'When searching for a specific player, select this to not filter by playing color.'
        );

        this.#initFromUrlParams();
    }

    /**
     * Pre-fills the search form from URL query parameters and auto-triggers the search if any params are present.
     * This allows linking directly to a search from e.g. "My DB Searches".
     */
    #initFromUrlParams() {
        const params = new URLSearchParams(window.location.search);
        const playerName = params.get('playerName');
        const playerColor = params.get('playerColor');
        const eventName = params.get('eventName');
        const dateStart = params.get('dateStart');
        const dateEnd = params.get('dateEnd');
        const fen = params.get('fen');

        if (!playerName && !eventName && !dateStart && !dateEnd && !fen) {
            return;
        }

        if (playerName) {
            this.#playerSearchField.setInputFieldValue(playerName);
            this.#enablePlayerColorRadioButtons(true);
        }
        if (playerColor) {
            const validColors = ['red', 'black', 'both'];
            const normalizedColor = playerColor.toLowerCase();
            if (validColors.includes(normalizedColor)) {
                const radioInput = document.querySelector(`input[name="player-color"][value="${normalizedColor}"]`);
                if (radioInput) {
                    radioInput.checked = true;
                }
            }
        }
        if (eventName) {
            this.#eventSearchField.setInputFieldValue(eventName);
        }
        if (dateStart || dateEnd) {
            const datePattern = /^\d{4}-\d{2}-\d{2}$/;
            const startDate = (dateStart && datePattern.test(dateStart)) ? new Date(dateStart) : null;
            const endDate = (dateEnd && datePattern.test(dateEnd)) ? new Date(dateEnd) : null;
            const startValid = startDate && !isNaN(startDate.getTime());
            const endValid = endDate && !isNaN(endDate.getTime());
            if (startValid && endValid) {
                this.#dateRangePicker.setDates(startDate, endDate);
            } else if (startValid) {
                this.#dateRangePicker.setDates(startDate, null);
            } else if (endValid) {
                this.#dateRangePicker.setDates(null, endDate);
            }
        }
        if (fen) {
            this.#fenSearchField.value = fen;
        }

        // auto-trigger the search
        this.#setButtonEnabled(false);
        this.#currentPlayerName = playerName;
        this.#currentPlayerIds = [];
        this.#currentEventName = eventName;
        this.#currentEventIds = [];
        this.#currentInterval = this.#dateRangePicker.getDates(DATE_FORMAT);
        this.#currentFen = fen;
        this.fetchItems();
    }

    /**
     * @returns {string}
     */
    baseUrl() {
        return `${API_DATABASE}/search`;
    }

    /**
     * @param jsonEntry {Object}
     * @returns {GameMetadataDto}
     */
    deserializeJsonEntry(jsonEntry) {
        return new GameMetadataDto(jsonEntry);
    }

    /**
     * @param entry {GameMetadataDto}
     * @return {string}
     */
    extractToken(entry) {
        return (entry.paginationOffset + 1).toString();
    }

    /**
     * @param value {boolean}
     */
    showNoItem(value) {
        if (this.#noResultsMessage) {
            this.#noResultsMessage.style.display = value ? 'block' : 'none';
        }
    }

    /**
     * @param entries {GameMetadataDto[]}
     */
    addEntries(entries) {
        entries.forEach(entry => {
            const gameItem = this.#createGameItem(entry);
            this.#resultContainer.appendChild(gameItem);
        });
        this.#setButtonEnabled(true);
    }

    /**
     * @returns {Map<string, any>}
     */
    additionalParameters() {
        const params = new Map();
        if (this.#currentPlayerName != null) {
            params.set('playerName', this.#currentPlayerName);
        }
        if (this.#currentPlayerIds.length > 0) {
            params.set('playerIds', this.#currentPlayerIds.join(','));
        }
        const selectedPlayerColor = document.querySelector('input[name="player-color"]:checked')?.value;
        if (selectedPlayerColor && selectedPlayerColor !== 'both') {
            params.set('playerColor', selectedPlayerColor.toUpperCase());
        }
        if (this.#currentEventName != null) {
            params.set('eventName', this.#currentEventName);
        }
        if (this.#currentEventIds.length > 0) {
            params.set('eventIds', this.#currentEventIds.join(','));
        }
        if (this.#currentInterval != null && this.#currentInterval.length === 2) {
            if (this.#currentInterval[0] != null) {
                params.set('dateStart', this.#currentInterval[0]);
            }
            if (this.#currentInterval[1] != null) {
                params.set('dateEnd', this.#currentInterval[1]);
            }
        }
        if (this.#currentFen != null) {
            params.set('fen', this.#currentFen);
        }
        return params;
    }

    fetchItemsErrorCb(responseText) {
        super.fetchItemsErrorCb(responseText);
        this.#setButtonEnabled(true);
    }

    /**
     * @param entry {GameMetadataDto}
     * @returns {HTMLElement}
     */
    #createGameItem(entry) {
        const gameItem = document.createElement('a');
        gameItem.className = 'my-game-item';
        gameItem.href = gameIdToPageLink(entry.gameId);

        const leftPane = buildDivWithClass('left-pane');
        const middlePane = buildDivWithClass('middle-pane');
        const outcomeIndicatorPane = buildDivWithClass('indicator-pane');
        const rightPane = buildDivWithClass('right-pane');
        gameItem.append(
            leftPane,
            middlePane,
            outcomeIndicatorPane,
            rightPane
        );

        // left pane
        const databaseIcon = document.createElement('img');
        databaseIcon.className = 'time-control-icons';
        databaseIcon.src = '/images/icons/data-search.png';
        databaseIcon.alt = 'Database Game';
        leftPane.append(wrapInDiv(databaseIcon));

        // player names
        const playersDiv = buildDivWithClass('player-names');
        playersDiv.append(buildDivWithTextAndClass(formatPlayerName(entry.redPlayerName), 'red-player-name'));
        playersDiv.append(buildDivWithTextAndClass(formatPlayerName(entry.blackPlayerName), 'black-player-name'));
        middlePane.append(playersDiv);

        // event name
        if (entry.eventName != null) {
            middlePane.append(buildDivWithTextAndClass(entry.eventName, 'event-name'));
        }

        // outcome indicator pane
        if (entry.outcome) {
            const outcomeDiv = buildDivWithClass('outcome-label-holder');

            // use the Outcome enum for proper type checking
            switch (entry.outcome) {
                case Outcome.RED_WINS:
                    outcomeDiv.textContent = 'RED';
                    outcomeDiv.classList.add('outcome-label-red-wins');
                    break;
                case Outcome.BLACK_WINS:
                    outcomeDiv.textContent = 'BLACK';
                    outcomeDiv.classList.add('outcome-label-black-wins');
                    break;
                case Outcome.DRAW:
                    outcomeDiv.textContent = 'DRAW';
                    outcomeDiv.classList.add('outcome-label-draw');
                    break;
                default:
                    outcomeDiv.textContent = '??';
                    break;
            }

            outcomeIndicatorPane.append(outcomeDiv);
        }

        // right pane
        if (entry.lastUpdated != null) {
            rightPane.append(
                buildDivWithTextAndClass(
                    formatTimestampToShortDateFormat(entry.lastUpdated),
                    'game-date'
                )
            );
        } else {
            rightPane.append(
                buildDivWithTextAndClass('--', 'game-date')
            );
        }

        // miniboard
        if (entry.finalFen != null) {
            addMiniboardDiv(gameItem, entry.gameId.id, entry.finalFen, Color.RED);
        }

        return gameItem;
    }

    #clearResults() {
        // clear existing results
        this.#resultContainer.innerHTML = '';
        this.resetPagination();

        // clear current search parameters
        this.#currentPlayerName = null;
        this.#currentPlayerIds = [];
        this.#currentEventName = null;
        this.#currentEventIds = [];
        this.#currentInterval = null;
        this.#currentFen = null;

        // remove all the existing mini-boards overview from the DOM
        getElementsByClassNameArray('mini-board-overview')
            .forEach(miniboardDiv => miniboardDiv.remove());
    }

    #fetchTotalGames() {
        getAndHandle(`${API_DATABASE}/info/count-all-games`, json => {
            this.#totalGamesSpan.innerText = formatNumberWithSuffix(json.count, 0);
        });
    }

    #fetchFeaturedPlayers() {
        getAndHandle(`${API_DATABASE}/info/list-featured-players`, json => {
            this.#featuredPlayersList.innerHTML = '';
            if (json.entries && json.entries.length > 0) {
                json.entries.forEach(entry => {
                    this.#featuredPlayersList.appendChild(
                        buildAnchorWithClass(
                            `/database/player/${entry.slug}?medium=database-featured-player`,
                            entry.displayName,
                            'featured-player-link'
                        )
                    );
                });
            } else {
                this.#featuredPlayersList.textContent = 'No featured players available.';
            }
        });
    }

    #closeAllSuggestionBoxes() {
        this.#playerSearchField.hideBox();
        this.#eventSearchField.hideBox();
    }

    // TODO: move to some place to be re-usable (ui.js)
    #setButtonEnabled(value) {
        if (value) {
            this.#searchButton.classList.remove('app-buttons-disabled');
        } else {
            this.#searchButton.classList.add('app-buttons-disabled');
        }
    }

    /**
     * @param value {boolean}
     */
    #enablePlayerColorRadioButtons(value) {
        document
            .querySelectorAll('input[name="player-color"]')
            .forEach(radio => {
                radio.disabled = !value;
                if (!value) {
                    // reset to "both" when disabling
                    if (radio.value === 'both') {
                        radio.checked = true;
                    }
                }
            });

        const playerColorOptions = document.getElementById('player-color-options');
        if (value) {
            playerColorOptions
                .classList
                .remove('player-color-options-disabled');
        } else {
            playerColorOptions
                .classList
                .add('player-color-options-disabled');
        }
    }

}

window.onload = () => new DatabaseSearchPage();
