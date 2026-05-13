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

class UserGamesBrowsePage extends BrowseGamesPage {
    #username;

    /**
     * @param username {string}
     */
    constructor(username) {
        super('pvp');
        this.#username = username;
    }

    baseUrl() {
        return '/api/game-data/list-latest-pvp-games-by-user';
    }

    additionalParameters() {
        const params = super.additionalParameters();
        params.set('username', this.#username);
        params.set('distinctByUsers', 'false');
        return params;
    }
}

window.onload = () => {
    const username = new URLSearchParams(window.location.search).get('username');
    if (username) {
        new UserGamesBrowsePage(username);
    } else {
        new BrowseGamesPage('pvp');
    }
};
