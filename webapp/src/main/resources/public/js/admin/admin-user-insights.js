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

class AdminInsightsUserAutocompleteSearchField extends AutocompleteSearchField {

    constructor() {
        super('insights-user-search', 'insights-user');
    }

    url() {
        return `${ADMIN_URL_PREFIX}/user-insights/autocomplete`;
    }
}

class AdminUserInsightsPage extends BasePage {

    #searchField = new AdminInsightsUserAutocompleteSearchField();
    #searchButton = document.getElementById('admin-user-insights-search-button');

    constructor() {
        super();

        document
            .getElementsByTagName('html')
            .item(0)
            .addEventListener('click', () => this.#searchField.hideBox());

        this.#searchButton.addEventListener('click', () => this.#search());
        document.getElementById('insights-user-search').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                this.#search();
            }
        });
    }

    #search() {
        const selectedId = this.#searchField.getSelectedId();
        const typedValue = this.#searchField.inputFieldValue.trim();
        const identifier = selectedId != null ? selectedId : typedValue;
        if (identifier !== '') {
            window.location.href = `/admin/user/${encodeURIComponent(identifier)}`;
        }
    }
}

window.onload = () => new AdminUserInsightsPage();
