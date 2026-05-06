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

const MIN_LETTERS_TO_TRIGGER_AUTOCOMPLETE = 2;

class Suggestion {

    constructor(id, name) {
        this.id = id;
        this.name = name;
    }

    static parse(json) {
        const suggestions = [];
        for (let entry of json.entries) {
            suggestions.push(new Suggestion(entry.id, entry.name));
        }
        return suggestions;
    }

}

/**
 * Input text with {@class Suggestion} autocompletion
 */
class AutocompleteSearchField {

    #inputField;

    /**
     * The div element that contains the suggestion elements and that show up when the user types something in the input field
     */
    #suggestionBox;

    /**
     * The id of the clicked element in the suggestion box
     */
    #selectedId;

    /**
     * The prefix of the id of the suggestion elements (informative only)
     */
    #idPrefix;

    constructor(inputId, idPrefix) {
        this.#idPrefix = idPrefix;
        this.#inputField = document.getElementById(inputId);
        this.#suggestionBox = this.#inputField.parentElement.parentElement.getElementsByClassName('suggestions-box-content')[0];
        this.#selectedId = null;

        this.#inputField.addEventListener('keyup', e => {
            this.#selectedId = null;
            let value = e.target.value;
            if (value.length >= MIN_LETTERS_TO_TRIGGER_AUTOCOMPLETE) {
                this.#fetchSuggestions(value);
            } else {
                this.#clearBox();
            }
        });
    }

    /**
     * @return {string}
     */
    get inputFieldValue() {
        return this.#inputField.value;
    }

    /**
     * @returns {string}
     */
    get inputFieldId() {
        return this.#inputField.id;
    }

    url() {
        throw new Error('Abstract');
    }

    getSelectedId() {
        return this.#selectedId;
    }

    #showBox() {
        this.#suggestionBox.style.visibility = 'visible';
    }

    hideBox() {
        this.#suggestionBox.style.visibility = 'hidden';
    }

    clear() {
        this.#inputField.value = '';
        this.#clearBox();
        this.#selectedId = null;
    }

    #clearBox() {
        this.#suggestionBox.innerHTML = '';
    }

    #fetchSuggestions(value) {
        const url = `${this.url()}?contains=${value}`;
        getAndHandle(url, json => this.#loadSuggestionsToBox(Suggestion.parse(json)));
    }

    #loadSuggestionsToBox(suggestions) {
        this.#clearBox();
        if (suggestions.length > 0) {
            for (let suggestion of suggestions) {
                const suggestionElement = buildDivWithTextAndClass(suggestion.name, 'suggestion-element');
                suggestionElement.id = `${this.#idPrefix}_${suggestion.id}`;
                suggestionElement.addEventListener('click', e => {
                    this.#inputField.value = suggestion.name;
                    this.hideBox();
                    this.#selectedId = e.target.id.split('_')[1];
                });
                this.#suggestionBox.append(suggestionElement);
            }
            this.#showBox();
        } else {
            this.hideBox();
        }
    }

}

class PlayerSearchAutocompleteSearchField extends AutocompleteSearchField {

    constructor() {
        super('player-search', 'player');
    }

    url() {
        return '/api/database/autocomplete/players'
    }

}

class EventSearchAutocompleteSearchField extends AutocompleteSearchField {

    constructor() {
        super('event-search', 'event');
    }

    url() {
        return '/api/database/autocomplete/events'
    }

}
