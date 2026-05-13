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

class GameAnalyticsDto {

    #gameId;
    #inviterUserId;
    #inviterUsername;
    #inviteeUserId;
    #inviteeUsername;
    #isRated;
    #allowGuests;
    #alwaysVisibleInLobby;
    #privateInvite;
    #timeControl;
    #status;
    #index;
    #isPreAnalyzed;
    #winnerUserId;
    #created;
    #lastUpdated;
    #sourceType;

    constructor(json) {
        this.#gameId = json.gameId;
        this.#inviterUserId = json.inviterUserId;
        this.#inviterUsername = json.inviterUsername;
        this.#inviteeUserId = json.inviteeUserId;
        this.#inviteeUsername = json.inviteeUsername;
        this.#isRated = json.isRated;
        this.#allowGuests = json.allowGuests;
        this.#alwaysVisibleInLobby = json.alwaysVisibleInLobby;
        this.#privateInvite = json.privateInvite;
        this.#timeControl = TimeControl.fromJson(json);
        this.#status = json.status;
        this.#index = json.index;
        this.#isPreAnalyzed = json.isPreAnalyzed;
        this.#winnerUserId = json.winnerUserId;
        this.#created = json.created;
        this.#lastUpdated = json.lastUpdated;
        this.#sourceType = json.sourceType;
    }

    /**
     * @return {string}
     */
    get gameId() {
        return this.#gameId;
    }

    get gameUrl() {
        return `/game?id=${this.gameId}`;
    }

    /**
     * @return {string}
     */
    get inviterUserId() {
        return this.#inviterUserId;
    }

    /**
     * @return {string}
     */
    get inviterUsername() {
        return this.#inviterUsername;
    }

    /**
     * @return {string}
     */
    get inviteeUserId() {
        return this.#inviteeUserId;
    }

    /**
     * @return {string}
     */
    get inviteeUsername() {
        return this.#inviteeUsername;
    }

    /**
     * @return {string}
     */
    get ratingMode() {
        return this.#isRated ? 'Rated' : 'Casual';
    }

    /**
     * @return {boolean}
     */
    get allowGuests() {
        return this.#allowGuests;
    }

    /**
     * @return {boolean}
     */
    get alwaysVisibleInLobby() {
        return this.#alwaysVisibleInLobby;
    }

    /**
     * @returns {boolean}
     */
    get isPrivateInvite() {
        return this.#privateInvite;
    }

    /**
     * @return {string}
     */
    get formattedTimeControl() {
        if (this.#timeControl === null) {
            return '--';
        } else {
            return this.#timeControl.printShort();
        }
    }

    /**
     * @return {string}
     */
    get status() {
        return this.#status;
    }

    get formattedStatus() {
        return formatEnumValue(this.status);
    }

    /**
     * @return {number}
     */
    get index() {
        return Number(this.#index);
    }

    /**
     * @return {boolean}
     */
    get isPreAnalyzed() {
        return this.#isPreAnalyzed;
    }

    /**
     * @return {string}
     */
    get formattedCreated() {
        return formatTimestampToDateTime(this.#created);
    }

    get formattedLastUpdated() {
        return formatTimestampToDateTime(this.#lastUpdated);
    }

    /**
     * @returns {string|null}
     */
    get formattedSourceType() {
        switch (this.#sourceType) {
            case 'DISCORD_NOTIFICATION':
                return 'discord';
            case 'LOBBY':
                return 'lobby';
            case 'MATCHED':
                return 'matched';
            case 'LINK':
                return 'link';
            default:
                return '--';
        }
    }

    /**
     * @return {boolean}
     */
    isInviterWinner() {
        return this.#winnerUserId === this.#inviterUserId;
    }

    /**
     * @return {boolean}
     */
    isInviteeWinner() {
        return this.#winnerUserId === this.#inviteeUserId;
    }

    /**
     * Legit in the sense that it looks like a "real" game
     * @return {boolean}
     */
    isLegit() {
        return this.#index > 6;
    }

    buildGameAnchor() {
        let anchor = document.createElement('a');
        anchor.href = this.gameUrl;
        anchor.innerText = this.gameId;
        return anchor;
    }

    /**
     * @return {GameAnalyticsDto[]}
     */
    static parseEntries(json) {
        let entries = [];
        for (let i = 0; i < json.entries.length; i++) {
            entries.push(new GameAnalyticsDto(json.entries[i]));
        }
        return entries;
    }

}

class BotGameAnalyticsDto {

    #gameId;
    #userId;
    #username;
    #userType;
    #color;
    #engine;
    #depth;
    #customStartFen;
    #status;
    #outcome;
    #index;
    #isPreAnalyzed;
    #created;
    #lastUpdated;

    constructor(json) {
        this.#gameId = json.gameId;
        this.#userId = json.userId;
        this.#username = json.username;
        this.#userType = json.userType;
        this.#color = json.color;
        this.#engine = json.engine;
        this.#depth = Number(json.depth);
        this.#customStartFen = json.customStartFen;
        this.#status = json.status;
        this.#outcome = json.outcome;
        this.#index = Number(json.index);
        this.#isPreAnalyzed = json.isPreAnalyzed;
        this.#created = Number(json.created);
        this.#lastUpdated = Number(json.lastUpdated);
    }

    /**
     * @return {string}
     */
    get gameId() {
        return this.#gameId;
    }

    /**
     * @return {string}
     */
    get gameUrl() {
        return `/playbot?id=${this.gameId}`;
    }

    /**
     * @return {string|null}
     */
    get userId() {
        return this.#userId;
    }

    /**
     * @return {string|null}
     */
    get username() {
        return this.#username;
    }

    /**
     * @return {string|null}
     */
    get userType() {
        return this.#userType;
    }

    /**
     * @return {string|null}
     */
    get color() {
        return this.#color;
    }

    /**
     * @return {string}
     */
    get engine() {
        return this.#engine;
    }

    /**
     * @return {number}
     */
    get depth() {
        return this.#depth;
    }

    /**
     * @return {boolean}
     */
    get hasCustomStartFen() {
        return this.#customStartFen;
    }

    /**
     * @return {string}
     */
    get status() {
        return this.#status;
    }

    /**
     * @return {string|null}
     */
    get outcome() {
        return this.#outcome;
    }

    /**
     * @return {number}
     */
    get index() {
        return this.#index;
    }

    /**
     * @return {boolean}
     */
    get isPreAnalyzed() {
        return this.#isPreAnalyzed;
    }

    /**
     * @return {number}
     */
    get created() {
        return this.#created;
    }

    /**
     * @return {number}
     */
    get lastUpdated() {
        return this.#lastUpdated;
    }

    /**
     * @return {string}
     */
    get formattedCreated() {
        return formatTimestampToDateTime(this.created);
    }

    /**
     * @return {string}
     */
    get formattedLastUpdated() {
        return formatTimestampToDateTime(this.lastUpdated);
    }

    /**
     * @return {boolean}
     */
    isAnonymous() {
        return this.#userId === null || this.#username === null;
    }

    /**
     * Legit in the sense that it looks like a "real" game
     * @return {boolean}
     */
    isLegit() {
        return this.#index > 3;
    }

    buildGameAnchor() {
        const anchor = document.createElement('a');
        anchor.href = this.gameUrl;
        anchor.innerText = this.gameId;
        return anchor;
    }

    /**
     * @return {BotGameAnalyticsDto[]}
     */
    static parseEntries(json) {
        const entries = [];
        for (let i = 0; i < json.entries.length; i++) {
            entries.push(new BotGameAnalyticsDto(json.entries[i]));
        }
        return entries;
    }

}
