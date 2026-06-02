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

    #totalRefresh = 0;

    /**
     * @param settingsGui {SettingsGui}
     */
    constructor(settingsGui) {
        this.#settingsGui = settingsGui;
        this.#initThumbs();

        setInterval(() => {
            this.#refreshGames();
        }, 1_000);
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
        });
    }

    #loadLatestPvbGames() {
        this.#client.listLastPvbGames(this.#pvbThumbs.length, (gameItemsDto) => {
            for (let i = 0; i < gameItemsDto.length; i++) {
                this.#pvbThumbs[i].render(gameItemsDto[i], 'lobby');
            }
        });
    }

    /**
     * @returns {GameThumb[]}
     */
    #allThumbs() {
        return this.#pvpThumbs.concat(this.#pvbThumbs);
    }

    #refreshGames() {
        const thumbs = this.#allThumbs().filter((t) => t.metadata != null);

        if (thumbs.length === 0) {
            return;
        }

        const gameIdsToUpdate = thumbs
            .filter((thumb) => this.#shouldRefresh(thumb))
            .map((thumb) => thumb.metadata.gameId);

        // do a complete refresh/re-initialization of the thumbs every 1 min,
        // expect if we're already watching live games
        const doCompleteRefreshIfNeeded = (mustSkipCompleteRefresh) => {
            if (this.#totalRefresh % 60 === 0 && !mustSkipCompleteRefresh) {
                this.#loadLatestPvpGames();
                this.#loadLatestPvbGames();
            }

            this.#totalRefresh++;
        };

        if (gameIdsToUpdate.length > 0) {
            let totalGames = 0;
            let liveGames = 0;

            const moveIndexes = Object.fromEntries(
                thumbs
                    .filter((t) => t.currentMoveIndex != null)
                    .map((t) => [t.metadata.gameId.id, t.currentMoveIndex])
            );

            this.#client.fetchLatestGamesUpdate(gameIdsToUpdate, moveIndexes, (updates) => {
                updates.forEach((update) => {
                    const thumb = thumbs.find((t) => t.metadata.gameId.toString() === update.gameId.toString());
                    if (thumb != null) {
                        thumb.refresh(update);
                        totalGames++;
                        if (this.#isUpdateLive(thumb, update)) {
                            liveGames++;
                        }
                    }
                });

                const mustSkipCompleteRefresh = totalGames > 0 && liveGames / totalGames > 0.5;
                doCompleteRefreshIfNeeded(mustSkipCompleteRefresh);
            });
        } else {
            doCompleteRefreshIfNeeded(false);
        }
    }

    /**
     * Whether the given thumb represents a game that should be polled for updates.
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

    /**
     * Mirrors {@link GameMetadataDto#isLive} but uses the freshly fetched
     * status / lastUpdated from an update payload (lastUpdated must be < 1 min).
     *
     * @param thumb {GameThumb}
     * @param update {{status: string, lastUpdated: number}}
     * @returns {boolean}
     */
    #isUpdateLive(thumb, update) {
        if (thumb.metadata == null) {
            return false;
        }
        const isPvb = thumb.metadata.gameId.type === GameType.PVB;
        const inProgress = isStatusInProgress(update.status) || (isPvb && update.status === GameEventType.CREATED);
        return inProgress && (new Date().getTime() - update.lastUpdated) <= 60_000;
    }

}
