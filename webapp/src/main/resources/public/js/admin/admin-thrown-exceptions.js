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

class ThrownExceptionDto {

    #exceptionTime;
    #httpCode;
    #exceptionClass;
    #exceptionMessage;

    constructor(json) {
        this.#exceptionTime = json.exceptionTime;
        this.#httpCode = json.httpCode;
        this.#exceptionClass = json.exceptionClass;
        this.#exceptionMessage = json.exceptionMessage;
    }

    /**
     * @returns {number}
     */
    get exceptionTime() {
        return this.#exceptionTime;
    }

    /**
     * @returns {number}
     */
    get httpCode() {
        return this.#httpCode;
    }

    /**
     * @returns {string}
     */
    get exceptionClass() {
        return this.#exceptionClass;
    }

    /**
     * @returns {string}
     */
    get exceptionMessage() {
        return this.#exceptionMessage;
    }
}

class AdminThrownExceptions extends BasePage {

    #table = document.getElementById('thrown-exceptions-table');

    constructor() {
        super();
        getAndHandle(`${ADMIN_URL_PREFIX}/list-latest-thrown-exceptions`, json => {
            const entries = [];
            for (let i = 0; i < json.entries.length; i++) {
                entries.push(new ThrownExceptionDto(json.entries[i]));
            }

            this.#renderEntries(entries);
        });
    }

    /**
     * @param entries {ThrownExceptionDto[]}
     */
    #renderEntries(entries) {
        const tbody = this.#table.querySelector('tbody');
        tbody.innerHTML = '';

        entries.forEach(entry => {
            const row = tbody.insertRow();
            renderExceptionRow(entry, row, {showFullyQualifiedClassName: true});
        });
    }
}

window.onload = () => new AdminThrownExceptions();
