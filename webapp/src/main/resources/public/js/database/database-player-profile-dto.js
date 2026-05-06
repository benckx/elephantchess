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

class DatabasePlayerProfileDto {

    /** @type {string} */
    playerId;

    /** @type {string} */
    canonicalName;

    /** @type {string|null} */
    chineseName;

    /** @type {string|null} */
    gender;

    /** @type {string|null} */
    profileText;

    /** @type {DatabasePlayerProfileSourceDto[]} */
    sources;

    constructor(json) {
        this.playerId = json.playerId;
        this.canonicalName = json.canonicalName;
        this.chineseName = json.chineseName;
        this.gender = json.gender;
        this.profileText = json.profileText;
        this.sources = json.sources.map(s => new DatabasePlayerProfileSourceDto(s));
    }

}

class DatabasePlayerVersionHistoryDto {

    /**
     *  @type {PlayerProfileVersionHistoryEntryDto[]}
     */
    versionHistory;

    constructor(json) {
        this.versionHistory = json.versionHistory.map(v => new PlayerProfileVersionHistoryEntryDto(v));
    }

}

class PlayerProfileVersionHistoryEntryDto {

    /** @type {number} */
    versionIndex;

    /** @type {string} */
    editorUserId;

    /** @type {string} */
    editorUsername;

    /** @type {number} */
    versionTime;

    /** @type {string|null} */
    comment;

    /** @type {string} */
    canonicalName;

    /** @type {string|null} */
    chineseName;

    /** @type {string|null} */
    gender;

    /** @type {boolean} */
    enabled;

    constructor(json) {
        this.versionIndex = json.versionIndex;
        this.editorUserId = json.editorUserId;
        this.editorUsername = json.editorUsername;
        this.versionTime = json.versionTime;
        this.comment = json.comment;
        this.canonicalName = json.canonicalName;
        this.chineseName = json.chineseName;
        this.gender = json.gender;
        this.enabled = json.enabled;
    }

}

class DatabasePlayerProfileSourceDto {

    /** @type {number} */
    index;

    /** @type {string} */
    url;

    /** @type {string} */
    title;

    constructor(json) {
        this.index = json.index;
        this.url = json.url;
        this.title = json.title;
    }

}
