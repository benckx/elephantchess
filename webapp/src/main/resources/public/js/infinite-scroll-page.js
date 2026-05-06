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

function buildUserOutcomeDiv(entry) {
    if (entry.userOutcome !== null) {
        const outcomeLabelHolder = document.createElement('div');
        outcomeLabelHolder.className = 'outcome-label-holder';

        let text = '';
        switch (entry.userOutcome) {
            case UserOutcome.WIN:
                text = 'win';
                outcomeLabelHolder.classList.add('outcome-label-win');
                break;
            case UserOutcome.LOSS:
                text = 'loss';
                outcomeLabelHolder.classList.add('outcome-label-loss');
                break;
            case UserOutcome.DRAW:
                text = 'draw';
                break;
            default:
        }

        outcomeLabelHolder.append(buildSimpleTextDiv(text));
        return outcomeLabelHolder;
    } else {
        return null;
    }
}

class InfiniteScrollPage extends BasePage {

    #isLoading = false;
    #lastPageFound = false;
    #continuation = null;

    #isLoadingMessage = document.getElementById('is-loading-message');
    #endOfPaginationMessage = document.getElementById('end-of-pagination-message');

    constructor() {
        super();
        const me = this;
        let lastScrollTop = 0;

        document.body.style.minHeight = '150vh';
        window.addEventListener('scroll', () => {
            const st = window.scrollY || document.documentElement.scrollTop;
            if (st > lastScrollTop) {
                if (!me.#isLoading && !this.#lastPageFound && me.shouldFetchNextPage()) {
                    me.fetchItems();
                }
            }
            lastScrollTop = st <= 0 ? 0 : st; // For Mobile or negative scrolling
        }, false);
    }

    /**
     * Override this to define when to fetch the next page (e.g., last row in viewport)
     * @returns {boolean}
     */
    shouldFetchNextPage() {
        const maybeElement = getLastElementOfClassName('my-game-item');
        return maybeElement !== null && isInViewport(maybeElement);
    }

    /**
     * @returns {string}
     */
    baseUrl() {
        throw new Error('baseUrl() must be implemented in subclass');
    }

    /**
     * @param jsonEntry {Object}
     * @returns {*}
     */
    deserializeJsonEntry(jsonEntry) {
        throw new Error('deserializeJsonEntry() must be implemented in subclass');
    }

    /**
     * @param entry {*}
     * @return {string}
     */
    extractToken(entry) {
        throw new Error('extractToken() must be implemented in subclass');
    }

    /**
     * @param value {boolean}
     * @returns {void}
     */
    showNoItem(value) {
        throw new Error('showNoItem() must be implemented in subclass');
    }

    /**
     * @param entries {*[]}
     * @returns {void}
     */
    addEntries(entries) {
        throw new Error('addEntries() must be implemented in subclass');
    }

    /**
     * @returns {Map<string, any>}
     */
    additionalParameters() {
        return new Map();
    }

    resetPagination() {
        this.#continuation = null;
        this.#lastPageFound = false;
        this.showNoItem(false);
        this.#updateIsLoading(false);
        this.#updateEndOfPaginationMessage(false);

        getElementsByClassNameArray('mini-board-overview')
            .forEach(e => e.remove());
    }

    fetchItems() {
        // building url
        const params = this.additionalParameters();
        if (this.#continuation != null) {
            params.set('continuation', this.#continuation);
        }
        const url = this.baseUrl() + paramMapToQueryString(params);

        // fetching
        this.#updateIsLoading(true);

        const handler = new ResponseHandlerWithError(
            (json) => {
                const entries = [];
                json.entries.forEach(entry => entries.push(this.deserializeJsonEntry(entry)));
                this.addEntries(entries);
                if (this.#continuation == null && entries.length === 0) {
                    this.showNoItem(true);
                }
                this.#continuation = entries.length > 0 ? this.extractToken(entries[entries.length - 1]) : null;
                this.#lastPageFound = entries.length === 0;
                this.#updateIsLoading(false);
                this.#updateEndOfPaginationMessage(this.#lastPageFound);
            },
            (responseText) => this.fetchItemsErrorCb(responseText)
        );

        getAndHandleWith(url, handler);
    }

    /**
     * @param responseText {string}
     */
    fetchItemsErrorCb(responseText) {
        let errors;
        try {
            const json = JSON.parse(responseText);
            if (Array.isArray(json.errors)) {
                errors = json.errors;
            }
        } catch (e) {
            // Not JSON, treat as plain text
        }
        const message = errors || responseText;
        UI.showValidationErrors(message, 3_000);
        this.#updateIsLoading(false);
    }

    /**
     * @param value {boolean}
     */
    #updateIsLoading(value) {
        this.#isLoading = value;
        if (this.#isLoadingMessage != null) {
            this.#isLoadingMessage.style.display = value ? 'block' : 'none';
        }
    }

    #updateEndOfPaginationMessage(value) {
        if (this.#endOfPaginationMessage != null) {
            this.#endOfPaginationMessage.style.display = value ? 'block' : 'none';
        }
    }

}
