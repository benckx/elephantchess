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

function getEventIdFromBody() {
    return document.body.dataset.eventId;
}

function getRoundFromUrl() {
    const urlParams = new URLSearchParams(window.location.search);
    const round = urlParams.get('round');
    return round ? parseInt(round, 10) : null;
}

class BrowseDbEventGamesPage extends BrowseGamesPage {

    constructor() {
        super('db', null);
        this.offset = 0;
    }

    baseUrl() {
        return `/api/game-data/list-db-event-games`;
    }

    /**
     * @returns {Map<string, any>}
     */
    additionalParameters() {
        const params = new Map();
        params.set('limit', '12');
        params.set('eventId', getEventIdFromBody());
        const round = getRoundFromUrl();
        if (round !== null) {
            params.set('round', round.toString());
        }
        if (this.offset && this.offset > 0) {
            params.set('continuation', this.offset.toString());
        }
        return params;
    }

    /**
     * @param entry {GameMetadataDto}
     */
    extractToken(entry) {
        return (entry.paginationOffset + 1).toString();
    }

}

window.onload = () => new BrowseDbEventGamesPage();
