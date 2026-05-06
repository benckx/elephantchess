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

const MAX_CHARS_IN_CELL = 16;

class UserSessionDto {

    #userId;
    #userType;
    #username;
    #os;
    #agentName;
    #countryCode;
    #countryName;
    #region;
    #city;
    #remoteAddress;
    #created;
    #updated;

    constructor(jsonEntry) {
        this.#userId = jsonEntry.userId;
        this.#userType = jsonEntry.userType;
        this.#username = jsonEntry.username;
        this.#os = jsonEntry.os;
        this.#agentName = jsonEntry.agentName;
        this.#countryCode = jsonEntry.countryCode;
        this.#countryName = jsonEntry.countryName;
        this.#region = jsonEntry.region;
        this.#city = jsonEntry.city;
        this.#remoteAddress = jsonEntry.remoteAddress;
        this.#created = jsonEntry.created;
        this.#updated = jsonEntry.updated;
    }

    /**
     * @returns {string}
     */
    get userId() {
        return this.#userId;
    }

    /**
     * @returns {string}
     */
    get userType() {
        return this.#userType;
    }

    /**
     * @returns {string}
     */
    get username() {
        return this.#username;
    }

    get isAnonymous() {
        return this.#userId == null;
    }

    get operatingSystemName() {
        return this.#os;
    }

    get agentName() {
        return this.#agentName;
    }

    get country() {
        return this.#countryName;
    }

    get region() {
        return this.#region;
    }

    get city() {
        return this.#city;
    }

    get remoteAddress() {
        return this.#remoteAddress;
    }

    get formattedCreated() {
        return formatTimestampToDateTime(this.#created);
    }

    get formattedLastUpdated() {
        return formatTimestampToDateTime(this.#updated);
    }

    static parseEntries(json) {
        let entries = [];
        json.entries.forEach(jsonEntry => {
            entries.push(new UserSessionDto(jsonEntry));
        });
        return entries;
    }

}

class AdminUserSessionsPage extends BasePage {

    #userSessionsTable = document.getElementById('user-sessions');

    constructor() {
        super();
        this.#fetchUserSessions();
    }

    #fetchUserSessions() {
        getAndHandle(ADMIN_URL_PREFIX + '/list-user-sessions', json => {
            const entries = UserSessionDto.parseEntries(json);
            this.#render(entries, emptyTable(this.#userSessionsTable));
        });
    }

    /**
     * @param entries {UserSessionDto[]}
     * @param tbody {HTMLTableSectionElement}
     */
    #render(entries, tbody) {
        entries.forEach(entry => {
            const row = tbody.insertRow();

            const userCell = row.insertCell();
            userCell.className = 'label-cell';

            if (entry.isAnonymous) {
                userCell.innerText = '--';
            } else {
                userCell.append(
                    buildUsernameSpan(
                        entry.userId,
                        entry.username,
                        userTypeFromName(entry.username)
                    )
                );
            }

            row.insertCell().innerText = entry.operatingSystemName;
            row.insertCell().innerText = entry.agentName;

            const countryCell = row.insertCell();
            countryCell.innerText = cropText(entry.country, MAX_CHARS_IN_CELL);
            if (entry.country != null && entry.country.length > MAX_CHARS_IN_CELL) {
                countryCell.id = randomId();
                addToolTip(countryCell, entry.country);
            }

            const regionCell = row.insertCell();
            regionCell.innerText = cropText(entry.region, MAX_CHARS_IN_CELL);
            if (entry.region != null && entry.region.length > MAX_CHARS_IN_CELL) {
                regionCell.id = randomId();
                addToolTip(regionCell, entry.region);
            }

            const cityCell = row.insertCell();
            cityCell.innerText = cropText(entry.city, MAX_CHARS_IN_CELL);
            if (entry.city != null && entry.city.length > MAX_CHARS_IN_CELL) {
                cityCell.id = randomId();
                addToolTip(cityCell, entry.city);
            }

            row.insertCell().innerText = entry.remoteAddress;
            row.insertCell().innerText = entry.formattedCreated;
            row.insertCell().innerText = entry.formattedLastUpdated;
        });
    }

}

window.onload = () => new AdminUserSessionsPage();
