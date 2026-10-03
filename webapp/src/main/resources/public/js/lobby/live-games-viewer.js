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

class LiveGamesViewer {

    #client = new LobbyClient();
    #settingsGui;

    #pvpGameItemDivs = getElementsByClassNameArray('latest-pvp-game-thumb');
    #pvbGameItemDivs = getElementsByClassNameArray('latest-pvb-game-thumb');

    /**
     * @type {GameThumb[]}
     */
    #pvpThumbs = [];

    /**
     * @type {GameThumb[]}
     */
    #pvbThumbs = [];

    /**
     * @type {LiveGamesWebSocketSession}
     */
    #wsSession;

    /**
     * @param settingsGui {SettingsGui}
     */
    constructor(settingsGui) {
        this.#settingsGui = settingsGui;
        this.#wsSession = new LiveGamesWebSocketSession((updates) => this.#applyUpdates(updates));
        this.#initThumbs();

        // periodically reload the latest games to discover new ones (the individual
        // move updates are pushed in real time over the WebSocket)
        setInterval(() => {
            this.#completeRefreshIfNeeded();
        }, 60_000);
    }

    #initThumbs() {
        this.#pvpThumbs = this.#pvpGameItemDivs.map((div, i) =>
            this.#createThumb(div, `last-pvp-game-board-${i}`));

        this.#pvbThumbs = this.#pvbGameItemDivs.map((div, i) =>
            this.#createThumb(div, `last-pvb-game-board-${i}`));

        for (let i = 2; i < this.#pvpGameItemDivs.length; i++) {
            this.#pvpGameItemDivs[i].classList.add('only-desktop-flex');
        }

        for (let i = 2; i < this.#pvbGameItemDivs.length; i++) {
            this.#pvbGameItemDivs[i].classList.add('only-desktop-flex');
        }

        this.#loadLatestPvpGames();
        this.#loadLatestPvbGames();
    }

    /**
     * @param div {HTMLDivElement}
     * @param boardElementId {string}
     * @returns {GameThumb}
     */
    #createThumb(div, boardElementId) {
        const boardGui = createWebappBoardGui({
            elementId: boardElementId,
            showCoordinates: false,
            mini: true,
            playSounds: false,
        });
        this.#settingsGui.addBoardGui(boardGui);
        return new GameThumb(div, boardGui);
    }

    #loadLatestPvpGames() {
        this.#client.listLastPvpGames(this.#pvpThumbs.length, (gameItemsDto) => {
            for (let i = 0; i < gameItemsDto.length; i++) {
                this.#pvpThumbs[i].render(gameItemsDto[i], 'lobby');
            }
            this.#updateSubscription();
        });
    }

    #loadLatestPvbGames() {
        this.#client.listLastPvbGames(this.#pvbThumbs.length, (gameItemsDto) => {
            for (let i = 0; i < gameItemsDto.length; i++) {
                this.#pvbThumbs[i].render(gameItemsDto[i], 'lobby');
            }
            this.#updateSubscription();
        });
    }

    /**
     * @returns {GameThumb[]}
     */
    #allThumbs() {
        return this.#pvpThumbs.concat(this.#pvbThumbs);
    }

    /**
     * Declare to the server which games we are watching. The server pushes
     * individual move/status updates for those games over the WebSocket.
     */
    #updateSubscription() {
        const gameIds = this.#allThumbs()
            .filter((thumb) => this.#shouldRefresh(thumb))
            .map((thumb) => thumb.metadata.gameId);

        this.#wsSession.subscribe(gameIds);
    }

    /**
     * Apply the updates pushed over the WebSocket to the matching thumbs.
     *
     * @param updates {Array<{gameId: GameId, status: string, fen: string, lastUpdated: number, moveIndex: number|null, newMoves: string[]}>}
     */
    #applyUpdates(updates) {
        const thumbs = this.#allThumbs().filter((t) => t.metadata != null);
        updates.forEach((update) => {
            const thumb = thumbs.find((t) => t.metadata.gameId.toString() === update.gameId.toString());
            if (thumb != null) {
                thumb.refresh(update);
            }
        });
    }

    /**
     * Reload the latest games (and re-subscribe), unless we're already watching
     * mostly live games, to avoid interrupting their animations.
     */
    #completeRefreshIfNeeded() {
        const thumbs = this.#allThumbs().filter((t) => t.metadata != null);
        const liveGames = thumbs.filter((t) => t.metadata.isLive()).length;
        const mustSkipCompleteRefresh = thumbs.length > 0 && liveGames / thumbs.length > 0.5;

        if (!mustSkipCompleteRefresh) {
            this.#loadLatestPvpGames();
            this.#loadLatestPvbGames();
        }
    }

    /**
     * Whether the given thumb represents a game that should be watched for updates.
     *
     * @param thumb {GameThumb}
     * @returns {boolean}
     */
    #shouldRefresh(thumb) {
        if (thumb.metadata == null) {
            return false;
        }

        // Bot games stay in the CREATED status while in progress (there's no
        // JOINED transition since the bot has no joining concept), so we must
        // treat CREATED as "in progress" for PVB games.
        const type = thumb.metadata.gameId.type;
        if (type === GameType.PVP) {
            return isStatusInProgress(thumb.metadata.status);
        }
        if (type === GameType.PVB) {
            return thumb.metadata.isLive();
        }
        return false;
    }

}
