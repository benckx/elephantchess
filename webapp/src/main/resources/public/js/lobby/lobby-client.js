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

class LobbyClient {

    /**
     * @param cb {function(CurrentPuzzleDto)}
     */
    getCurrentPuzzle(cb) {
        getAndHandle('/api/puzzle/current', (json) => {
            cb(new CurrentPuzzleDto(json));
        });
    }

    joinGame(gameId, cb) {
        const body = {
            'gameId': gameId,
            'source': GameJoinSource.LOBBY
        };
        postAndHandle('/api/game/join', body, (json) => {
            // noinspection JSUnresolvedReference
            cb(json.inviteeColor);
        });
    }

    /**
     * @param limit {number}
     * @param cb {function(Array<GameMetadataDto>)}
     */
    listLastPvpGames(limit, cb) {
        getAndHandle('/api/game-data/list-latest-pvp-games?limit=' + limit, (json) => {
            const items = [];
            for (let i = 0; i < json.entries.length; i++) {
                items.push(new GameMetadataDto(json.entries[i]));
            }
            cb(items);
        });
    }

    /**
     * @param limit {number}
     * @param cb {function(Array<GameMetadataDto>)}
     */
    listLastPvbGames(limit, cb) {
        getAndHandle('/api/game-data/list-latest-pvb-games?limit=' + limit + '&excludeAutoResigned=true', (json) => {
            const items = [];
            for (let i = 0; i < json.entries.length; i++) {
                items.push(new GameMetadataDto(json.entries[i]));
            }
            cb(items);
        });
    }

    /**
     * @param gameIds {Array<GameId>}
     * @param cb {function(Array<{gameId: GameId, status: string, fen: string, lastUpdated: number, outcome: string|null, isRedOnline: boolean, isBlackOnline: boolean}>)}
     */
    fetchLatestGamesUpdate(gameIds, cb) {
        const body = {
            gameIds: gameIds.map((gameId) => ({type: gameId.type, id: gameId.id}))
        };
        postAndHandle('/api/lobby/latest-games-update', body, (json) => {
            const items = [];
            for (let i = 0; i < json.entries.length; i++) {
                const entry = json.entries[i];
                items.push({
                    gameId: new GameId(entry.gameId.type, entry.gameId.id),
                    status: entry.status,
                    fen: entry.fen,
                    lastUpdated: Number(entry.lastUpdated),
                    outcome: entry.outcome ?? null,
                    isRedOnline: entry.isRedOnline === true,
                    isBlackOnline: entry.isBlackOnline === true,
                });
            }
            cb(items);
        });
    }

    /**
     * @param cb {function([UpcomingEventDto])}
     */
    listUpcomingEvents(cb) {
        getAndHandle('/api/lobby/upcoming-events', (json) => {
            const items = [];
            for (let i = 0; i < json.events.length; i++) {
                items.push(new UpcomingEventDto(json.events[i]));
            }
            cb(items);
        });
    }

    /**
     * @param cb {function(GetSupportersResponse)}
     */
    listLatestTippers(cb) {
        getAndHandle('/api/lobby/list-latest-tippers', (json) => {
            cb(new GetSupportersResponse(json));
        });
    }

    /**
     * @param cb {function(GetSupportersResponse)}
     */
    listLatestRecurrentSupporters(cb) {
        getAndHandle('/api/lobby/list-latest-recurrent-supporters', (json) => {
            cb(new GetSupportersResponse(json));
        });
    }

}
