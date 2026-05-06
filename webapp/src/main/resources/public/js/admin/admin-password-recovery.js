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

class PasswordRecoveryAttemptDto {

    #userId;
    #username;
    #email;
    #creationTime;
    #recoveryTime;
    #userCreation;

    constructor(json) {
        this.#userId = json.userId;
        this.#username = json.username;
        this.#email = json.email;
        this.#creationTime = json.creationTime;
        this.#recoveryTime = json.recoveryTime;
        this.#userCreation = json.userCreation;
    }

    /**
     * @returns {string|null}
     */
    get userId() {
        return this.#userId;
    }

    /**
     * @returns {string|null}
     */
    get username() {
        return this.#username;
    }

    /**
     * @returns {string}
     */
    get email() {
        return this.#email;
    }

    /**
     * @returns {number}
     */
    get creationTime() {
        return this.#creationTime;
    }

    /**
     * @returns {number|null}
     */
    get recoveryTime() {
        return this.#recoveryTime;
    }

    /**
     * @returns {number|null}
     */
    get userCreation() {
        return this.#userCreation;
    }
}

class AdminPasswordRecovery extends BasePage {

    #table = document.getElementById('password-recovery-attempts-table');

    constructor() {
        super();
        getAndHandle(`${ADMIN_URL_PREFIX}/list-latest-password-recovery-attempts`, json => {
            const entries = [];
            for (let i = 0; i < json.entries.length; i++) {
                entries.push(new PasswordRecoveryAttemptDto(json.entries[i]));
            }

            this.#renderEntries(entries);
        });
    }

    /**
     * @param entries {PasswordRecoveryAttemptDto[]}
     */
    #renderEntries(entries) {
        const tbody = this.#table.querySelector('tbody');
        tbody.innerHTML = '';

        entries.forEach(entry => {
            const row = tbody.insertRow()

            // user
            const userCell = row.insertCell();
            if (entry.userId != null && entry.username != null && entry.recoveryTime != null) {
                userCell.append(buildUsernameSpan(entry.userId, entry.username, UserType.AUTHENTICATED));
            } else {
                userCell.innerText = '--';
            }

            // email
            const emailCell = row.insertCell();
            emailCell.innerText = entry.email;

            // creation time
            const creationTimeCell = row.insertCell();
            creationTimeCell.innerText = formatTimestampDefaultDateFormat(entry.creationTime);

            // recovery time
            const recoveryTimeCell = row.insertCell();
            recoveryTimeCell.innerText = entry.recoveryTime != null ? formatTimestampDefaultDateFormat(entry.recoveryTime) : '--';

            // user creation time
            const userCreationCell = row.insertCell();
            userCreationCell.innerText = entry.userCreation != null ? formatTimestampDefaultDateFormat(entry.userCreation) : '--';
        });
    }
}

window.onload = () => new AdminPasswordRecovery();
