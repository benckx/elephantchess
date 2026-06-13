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

/**
 * WebSocket session feeding the lobby live games thumbnails.
 *
 * The client declares which games it watches (via {@link subscribe}); the server
 * tracks the last known move index per game and pushes back only the entries that
 * changed (new moves or a status change).
 */
class LiveGamesWebSocketSession {

    #socketHandle;
    #onUpdate;

    /**
     * @type {GameId[]}
     */
    #gameIds = [];

    /**
     * @param onUpdate {function(Array<{gameId: GameId, status: string, fen: string, lastUpdated: number, moveIndex: number|null, newMoves: string[]}>)}
     */
    constructor(onUpdate) {
        this.#onUpdate = onUpdate;

        this.#socketHandle = openReconnectingWebSocket({
            endpoint: 'lobby/live-games',
            logLabel: 'live-games',
            // available to everyone (including guests), no parameters required
            buildParams: () => new Map(),
            onOpen: () => {
                // (re)send the current subscription on every (re)connect
                this.#sendSubscription();
            },
            onMessage: (e) => {
                const json = JSON.parse(e.data);
                const items = json.entries.map((entry) => ({
                    gameId: new GameId(entry.gameId.type, entry.gameId.id),
                    status: entry.status,
                    fen: entry.fen,
                    lastUpdated: Number(entry.lastUpdated),
                    moveIndex: entry.moveIndex ?? null,
                    newMoves: entry.newMoves ?? [],
                }));
                this.#onUpdate(items);
            },
        });
    }

    /**
     * @param gameIds {GameId[]}
     */
    subscribe(gameIds) {
        this.#gameIds = gameIds;
        this.#sendSubscription();
    }

    #sendSubscription() {
        const socket = this.#socketHandle.getSocket();
        if (socket != null && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({
                gameIds: this.#gameIds.map((gameId) => ({type: gameId.type, id: gameId.id})),
            }));
        }
    }

}
