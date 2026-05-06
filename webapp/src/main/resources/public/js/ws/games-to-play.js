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

class GamesToPlayUpdateDto {

    #gamesToJoin;
    #turnToPlayGames;
    #totalOnline;

    constructor(json) {
        this.#gamesToJoin = json.gamesToJoin.map(game => new GameToPlayDto(game));
        this.#turnToPlayGames = json.turnToPlayGames.map(game => new GameToPlayDto(game));
        this.#totalOnline = Number(json.totalOnline);
    }

    /**
     * @returns {GameToPlayDto[]}
     */
    get gamesToJoin() {
        return this.#gamesToJoin;
    }

    /**
     * @returns {Set<string>}
     */
    get gameIdsToJoinSet() {
        return new Set(this.#gamesToJoin.map(game => game.gameId));
    }

    /**
     * @returns {GameToPlayDto[]}
     */
    get turnToPlayGames() {
        return this.#turnToPlayGames;
    }

    /**
     * @returns {Set<string>}
     */
    get turnToPlayGameIdsSet() {
        return new Set(this.#turnToPlayGames.map(game => game.gameId));
    }

    /**
     * @returns {number}
     */
    get totalOnline() {
        return this.#totalOnline;
    }

}

class GameToPlayDto {

    #gameId;
    #isRated;
    #opponentUserId;
    #opponentUserType;
    #opponentUsername;
    #opponentColor;
    #opponentRating;
    #isOpponentOnline;
    #timeControlCategory;
    #timeControl;
    #allowGuests;
    #lastUpdated;

    constructor(json) {
        this.#gameId = json.gameId;
        this.#isRated = json.isRated;
        this.#opponentUserId = json.opponentUserId;
        this.#opponentUserType = json.opponentUserType;
        this.#opponentUsername = json.opponentUsername;
        this.#opponentColor = json.opponentColor;
        this.#opponentRating = Number(json.opponentRating);
        this.#isOpponentOnline = json.isOpponentOnline;
        this.#timeControlCategory = json.timeControlCategory;
        this.#timeControl = TimeControl.fromJson(json);
        this.#allowGuests = json.allowGuests;
        this.#lastUpdated = Number(json.lastUpdated);
    }

    /**
     * @returns {string}
     */
    get gameId() {
        return this.#gameId;
    }

    /**
     * @returns {boolean}
     */
    get isRated() {
        return this.#isRated;
    }

    /**
     * @returns {string}
     */
    get opponentUserId() {
        return this.#opponentUserId;
    }

    /**
     * @returns {string}
     */
    get opponentUserType() {
        return this.#opponentUserType;
    }

    /**
     * @returns {string}
     */
    get opponentUsername() {
        return this.#opponentUsername;
    }

    /**
     * @returns {string}
     */
    get opponentColor() {
        return this.#opponentColor;
    }

    /**
     * @returns {number}
     */
    get opponentRating() {
        return this.#opponentRating;
    }

    /**
     * @returns {boolean}
     */
    get isOpponentOnline() {
        return this.#isOpponentOnline
    }

    /**
     * @returns {string}
     */
    get timeControlCategory() {
        return this.#timeControlCategory;
    }

    /**
     * @return {TimeControl|null}
     */
    get timeControl() {
        return this.#timeControl;
    }

    /**
     * @returns {boolean}
     */
    get allowGuests() {
        return this.#allowGuests;
    }

    /**
     * @returns {number}
     */
    get lastUpdated() {
        return this.#lastUpdated;
    }

    get isCorrespondenceGame() {
        return this.#timeControlCategory === TimeControlEnum.CORRESPONDENCE;
    }

}

/**
 * Move online at the top of the list.
 *
 * @param e1 {GameToPlayDto}
 * @param e2 {GameToPlayDto}
 * @return {number}
 */
function sortByOnline(e1, e2) {
    if (e1.isOpponentOnline && !e2.isOpponentOnline) {
        return -1;
    } else if (!e1.isOpponentOnline && e2.isOpponentOnline) {
        return +1;
    } else {
        return 0
    }
}

class GamesToPlayWebSocketSession {

    /**
     * @param gamesToJoinListeners {function(GamesToPlayUpdateDto)[]}
     * @param turnToPlayGamesListeners {function(GamesToPlayUpdateDto)[]}
     */
    constructor(gamesToJoinListeners, turnToPlayGamesListeners) {
        /**
         * @param listeners {function(GamesToPlayUpdateDto)[]}
         * @param arg {GamesToPlayUpdateDto}
         */
        function safeCallListeners(listeners, arg) {
            listeners.forEach(listener => {
                try {
                    listener(arg);
                } catch (e) {
                    console.warn('error during listener', e);
                }
            });
        }

        let lastUpdate = null;

        openReconnectingWebSocket({
            endpoint: 'pvp/games-to-play',
            logLabel: 'games-to-play',
            buildParams: () => {
                const compositeUserId = compositeUserIdOrNull();
                if (compositeUserId == null) {
                    console.warn('not authenticated, cannot listen for games to play');
                    return null;
                }
                return new Map([
                    ['userType', compositeUserId.userType],
                    ['userId', compositeUserId.userId]
                ]);
            },
            onOpen: () => {
                // reset state on (re)connect: next message will be treated as the first update
                lastUpdate = null;
            },
            onMessage: (e) => {
                const json = JSON.parse(e.data);

                if (lastUpdate == null) {
                    // force call the listeners on first update
                    lastUpdate = new GamesToPlayUpdateDto(json);
                    safeCallListeners(turnToPlayGamesListeners, lastUpdate);
                    safeCallListeners(gamesToJoinListeners, lastUpdate);
                } else {
                    // on subsequent updates, only call the listeners if the data has changed
                    const newUpdate = new GamesToPlayUpdateDto(json);
                    if (lastUpdate.totalOnline !== newUpdate.totalOnline || !eqSet(lastUpdate.gameIdsToJoinSet, newUpdate.gameIdsToJoinSet)) {
                        safeCallListeners(gamesToJoinListeners, newUpdate);
                    }
                    if (!eqSet(lastUpdate.turnToPlayGameIdsSet, newUpdate.turnToPlayGameIdsSet)) {
                        safeCallListeners(turnToPlayGamesListeners, newUpdate);
                    }
                    lastUpdate = newUpdate;
                }
            }
        });
    }

}
