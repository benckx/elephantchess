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

class BrowsePlayerVsPlayerForUserPage extends BrowseGamesPage {

    /**
     * @param username {string}
     */
    constructor(username) {
        super('pvp');
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

// if "username" data attribute is present on the body,
// we are browsing PvP games of a single user
window.onload = () => {
    const username = document.body.dataset.username;
    if (username) {
        // browse PvP for a single user
        new BrowsePlayerVsPlayerForUserPage(username);
    } else {
        // general PvP browse
        new BrowseGamesPage('pvp');
    }
};
