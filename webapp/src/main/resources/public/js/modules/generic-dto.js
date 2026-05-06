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

class UserDto {

    #userType;
    #userId;
    #username;

    /**
     * @param {Object} json
     * @param {string} json.userType
     * @param {string} json.userId
     * @param {string} json.username
     */
    constructor(json) {
        this.#userType = json.userType;
        this.#userId = json.userId;
        this.#username = json.username;
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
    get userId() {
        return this.#userId;
    }

    /**
     * @returns {string}
     */
    get username() {
        return this.#username;
    }

    toString() {
        return `UserDto(userType=${this.#userType}, userId=${this.#userId}, username=${this.#username})`;
    }
}
