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

class BrowsePlayerVsPlayerOfUserPage extends BrowseGamesPage {

    /**
     * @param username {string}
     */
    constructor(username) {
        super('pvp');
        // Use a regular property (not a private field) so it is set before any
        // base-class call to additionalParameters() that happens during super().
        // Note: super() runs before subclass field initializers, so private
        // fields declared on the subclass are not yet available there.
        this.username = username;
    }

    baseUrl() {
        return '/api/game-data/list-latest-pvp-games-by-user';
    }

    additionalParameters() {
        const params = super.additionalParameters();
        params.set('username', this.username ?? document.body.dataset.username);
        return params;
    }
}
