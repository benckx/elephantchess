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

const API_GAME_DATA = '/api/game-data';

function formatPlayerName(name) {
    if (name == null || name === 'null' || name === '') {
        return '<unknown>';
    } else {
        return name;
    }
}

/**
 * @param gameId {GameId}
 * @return {string}
 */
function gameIdToPageLink(gameId) {
    switch (gameId.type) {
        case GameType.PVP:
            return `/game?id=${gameId.id}`;
        case GameType.PVB:
            return `/playbot?id=${gameId.id}`;
        case GameType.DB:
            return `/database/game?id=${gameId.id}`;
        default:
            throw new Error('Invalid game type ' + gameId.type);
    }
}

class GameId {

    /**
     * @param type {string}
     */
    #type;

    /**
     * @param id {string}
     */
    #id;

    constructor(gameType, id) {
        validateGameType(gameType);
        this.#type = gameType;
        this.#id = id;
    }

    /**
     * @return {string}
     */
    get type() {
        return this.#type;
    }

    /**
     * @return {string}
     */
    get id() {
        return this.#id;
    }

    get urlParams() {
        return [`gameType=${this.#type}`, `gameId=${this.#id}`].join('&');
    }

    get analysisUrl() {
        return `/analysis?${this.urlParams}`;
    }

    toString() {
        return this.#type + '-' + this.#id;
    }

    /**
     * Assumes the object has a 'gameId' field in 2 parts
     *
     * @param json {object}
     * @return {GameId}
     */
    static fromJsonParent(json) {
        return new GameId(json.gameId.type, json.gameId.id)
    }

}

class GameMetadataDto {

    #gameId;
    #redPlayerName;
    #redPlayerId;
    #redPlayerRating;
    #redUserType;
    #isRedOnline;
    #blackPlayerName;
    #blackPlayerId;
    #blackPlayerRating;
    #blackUserType;
    #isBlackOnline;
    #userColor;
    #eventId;
    #eventName;
    #startFen;
    #finalFen;
    #status;
    #outcome;
    #analysisStatus;
    #engine;
    #depth;
    #lastUpdated;
    #paginationOffset;
    #variant;

    constructor(json) {
        this.#gameId = GameId.fromJsonParent(json);
        this.#redPlayerName = json.redPlayerName;
        this.#redPlayerId = json.redPlayerId;
        this.#redPlayerRating = json.redPlayerRating;
        this.#redUserType = json.redUserType;
        this.#isRedOnline = json.isRedOnline;
        this.#blackPlayerName = json.blackPlayerName;
        this.#blackPlayerId = json.blackPlayerId;
        this.#blackPlayerRating = json.blackPlayerRating;
        this.#blackUserType = json.blackUserType;
        this.#isBlackOnline = json.isBlackOnline;
        this.#userColor = json.userColor;
        this.#eventId = json.eventId;
        this.#eventName = json.eventName;
        this.#startFen = json.startFen;
        this.#finalFen = json.finalFen;
        this.#status = json.status;
        this.#outcome = json.outcome;
        this.#analysisStatus = json.analysisStatus;
        this.#engine = json.engine;
        this.#depth = json.depth;
        if (json.lastUpdated != null) {
            this.#lastUpdated = Number(json.lastUpdated);
        } else {
            this.#lastUpdated = null;
        }
        this.#paginationOffset = json.paginationOffset;
        this.#variant = json.variant ?? Variant.XIANGQI;
    }

    /**
     * @return {GameId}
     */
    get gameId() {
        return this.#gameId;
    }

    /**
     * @return {string|null}
     */
    get redPlayerName() {
        return this.#redPlayerName;
    }

    /**
     * @return {string|null}
     */
    get redPlayerId() {
        return this.#redPlayerId;
    }

    /**
     * @return {number|null}
     */
    get redPlayerRating() {
        return this.#redPlayerRating;
    }

    /**
     * @return {string|null}
     */
    get redUserType() {
        return this.#redUserType;
    }

    /**
     * @return {boolean|null}
     */
    get isRedOnline() {
        return this.#isRedOnline;
    }

    /**
     * @return {string|null}
     */
    get blackPlayerName() {
        return this.#blackPlayerName;
    }

    /**
     * @return {string|null}
     */
    get blackPlayerId() {
        return this.#blackPlayerId;
    }

    /**
     * @return {number|null}
     */
    get blackPlayerRating() {
        return this.#blackPlayerRating;
    }

    /**
     * @return {string|null}
     */
    get blackUserType() {
        return this.#blackUserType;
    }

    /**
     * @return {boolean|null}
     */
    get isBlackOnline() {
        return this.#isBlackOnline;
    }

    /**
     * @return {string|null}
     */
    get userColor() {
        return this.#userColor;
    }

    /**
     *  @return {string|null}
     */
    get eventId() {
        return this.#eventId;
    }

    /**
     *  @return {string|null}
     */
    get eventName() {
        return this.#eventName;
    }

    /**
     * @returns {string|null}
     */
    get startFen() {
        return this.#startFen;
    }

    /**
     * @return {string}
     */
    get finalFen() {
        return this.#finalFen;
    }

    /**
     * @return {string|null}
     */
    get outcome() {
        return this.#outcome;
    }

    /**
     * @return {string}
     */
    get analysisStatus() {
        return this.#analysisStatus;
    }

    /**
     * @return {string|null}
     */
    get status() {
        return this.#status;
    }

    /**
     * @return {string|null}
     */
    get engine() {
        return this.#engine;
    }

    /**
     * @return {number|null}
     */
    get depth() {
        return this.#depth;
    }

    /**
     * @returns {number|null}
     */
    get lastUpdated() {
        return this.#lastUpdated;
    }

    /**
     * @returns {number|null}
     */
    get paginationOffset() {
        return this.#paginationOffset;
    }

    /**
     * @return {string}
     */
    get variant() {
        return this.#variant;
    }

    isLive() {
        // in PvB games, "CREATED" is ongoing
        // in PvP games, "CREATED" is not joined yet
        // but in this case we're only showing game with move > 4
        return (isStatusInProgress(this.#status) || this.#status === GameEventType.CREATED) &&
            this.#isRedOnline &&
            this.#isBlackOnline &&
            (dayjs.utc().valueOf() - this.lastUpdated) <= 180_000;
    }

    toStringPlayerNames() {
        const playersNames = [];
        const isPvB = this.#gameId.type === GameType.PVB && this.#engine != null && this.#depth != null;

        if (this.#redPlayerName != null && this.#redPlayerName !== '') {
            playersNames.push(formatPlayerName(this.#redPlayerName));
        } else if (this.#blackPlayerName != null && this.#blackPlayerName !== '' && !isPvB) {
            playersNames.push('<unknown>');
        }

        if (this.#blackPlayerName != null && this.#blackPlayerName !== '') {
            playersNames.push(formatPlayerName(this.#blackPlayerName));
        } else if (this.#redPlayerName != null && this.#redPlayerName !== '' && !isPvB) {
            playersNames.push('<unknown>');
        }

        if (isPvB) {
            playersNames.push(formatEngineName(this.#engine) + ' (' + this.#depth + ')');
        }

        return playersNames.join(' vs. ');
    }

    /**
     * @return {null|string}
     */
    toStringOutcome() {
        switch (this.#outcome) {
            case Outcome.RED_WINS:
                let redPlayerName = '';
                if (this.#gameId.type === GameType.PVB && this.#redPlayerId == null && this.#engine != null) {
                    redPlayerName = formatEngineName(this.#engine);
                } else {
                    redPlayerName = formatPlayerName(this.#redPlayerName)
                }
                return redPlayerName + ' victory (Red)';
            case Outcome.BLACK_WINS:
                let blackPlayerName = '';
                if (this.#gameId.type === GameType.PVB && this.#blackPlayerId == null && this.#engine != null) {
                    blackPlayerName = formatEngineName(this.#engine);
                } else {
                    blackPlayerName = formatPlayerName(this.#blackPlayerName)
                }
                return blackPlayerName + ' victory (Black)';
            case Outcome.DRAW:
                return 'Draw';
            default:
                return null;
        }
    }

    toString() {
        let lines = [];
        lines.push(this.toStringPlayerNames());
        lines.push(this.toStringOutcome());
        if (this.#lastUpdated != null) {
            lines.push(formatTimestampToShortDateFormat(this.#lastUpdated));
        }
        return lines.filter(line => line != null).join('\n');
    }

    /**
     * @return {Map<string, string>}
     */
    buildPgnMetadata() {
        let metadata = new Map();
        let outcome = formatOutcome(this.#outcome).replaceAll(' ', '');
        metadata.set('Result', outcome);

        if (this.#redPlayerName != null) {
            metadata.set('White', this.#redPlayerName);
        }

        if (this.#blackPlayerName != null) {
            metadata.set('Black', this.#blackPlayerName);
        }

        if (this.#gameId.type === GameType.PVB && this.#engine != null) {
            if (this.#redPlayerName != null) {
                metadata.set('Black', formatEngineName(this.#engine) + ' (depth ' + this.#depth + ')');
            }
            if (this.#blackPlayerName != null) {
                metadata.set('White', formatEngineName(this.#engine) + ' (depth ' + this.#depth + ')');
            }
        }

        metadata.set('Variant', this.#variant === Variant.MANCHU ? 'Manchu' : 'Xiangqi');

        // TODO: add date, tournament, etc.

        return metadata;
    }

}


class StartGameAnalysisResponseDto {

    #status;
    #hasStarted;

    constructor(json) {
        this.#status = json.status;
        this.#hasStarted = json.hasStarted;
    }

    /**
     * @return {string}
     */
    get status() {
        return this.#status;
    }

    /**
     * @return {boolean}
     */
    get hasStarted() {
        return this.#hasStarted;
    }

}

class AnalysisProgressStatusDto {

    #status;
    #progress;

    constructor(json) {
        this.#status = json.status;
        this.#progress = json.progress;
    }

    /**
     * @return {string}
     */
    get status() {
        return this.#status;
    }

    /**
     * @return {number}
     */
    get progress() {
        return this.#progress;
    }

}

class GameMoveAnnotationDto {

    #moveIndex;
    #annotation;
    #cpl;
    #engineCp;
    #actualMoveCp;

    /**
     * @param json {object}
     */
    constructor(json) {
        this.#moveIndex = json.moveIndex;
        this.#annotation = json.annotation;
        this.#cpl = json.cpl;
        this.#engineCp = json.engineCp;
        this.#actualMoveCp = json.actualMoveCp;
    }

    /**
     * @return {number}
     */
    get moveIndex() {
        return this.#moveIndex;
    }

    /**
     * @return {string}
     */
    get annotation() {
        return this.#annotation;
    }

    /**
     * @return {number}
     */
    get cpl() {
        return this.#cpl;
    }

    /**
     * @return {number}
     */
    get engineCp() {
        return this.#engineCp;
    }

    /**
     * @return {number}
     */
    get actualMoveCp() {
        return this.#actualMoveCp;
    }

}

class GameAnalysisResponseDto {

    #entries;
    #moveAnnotations;

    /**
     * @param json {object}
     */
    constructor(json) {
        this.#entries = json.entries.map(jsonEntry => new InfoLineResult(jsonEntry));
        this.#moveAnnotations = (json.moveAnnotations ?? []).map(jsonEntry => new GameMoveAnnotationDto(jsonEntry));
    }

    /**
     * @return {InfoLineResult[]}
     */
    get entries() {
        return this.#entries;
    }

    /**
     * @return {GameMoveAnnotationDto[]}
     */
    get moveAnnotations() {
        return this.#moveAnnotations;
    }

}

class GameDataClient {

    #gameId;

    /**
     * @param gameId {GameId}
     */
    constructor(gameId) {
        this.#gameId = gameId;
    }

    /**
     * @param cb {function(GameMetadataDto)}
     */
    fetchMetadata(cb) {
        const url = `${API_GAME_DATA}/game-metadata?${this.#urlParams()}`
        getAndHandle(url, json => cb(new GameMetadataDto(json)));
    }

    /**
     * @param cb {function(HalfMove[])}
     */
    fetchMoves(cb) {
        const url = `${API_GAME_DATA}/game-moves?${this.#urlParams()}`
        getAndHandle(url, json => cb(json.moves.map(uci => HalfMove.parseUci(uci))));
    }

    /**
     * @param cb {function(StartGameAnalysisResponseDto)}
     */
    startAnalysis(cb) {
        const url = `${API_GAME_DATA}/start-game-analysis?${this.#urlParams()}`
        getAndHandle(url, json => cb(new StartGameAnalysisResponseDto(json)));
    }

    /**
     * @param cb {function(AnalysisProgressStatusDto)}
     */
    fetchAnalysisStatus(cb) {
        const url = `${API_GAME_DATA}/game-analysis-status?${this.#urlParams()}`
        getAndHandle(url, json => cb(new AnalysisProgressStatusDto(json)));
    }

    /**
     * @param cb {function(GameAnalysisResponseDto)}
     */
    fetchAnalysisData(cb) {
        const url = `${API_GAME_DATA}/game-analysis-data?${this.#urlParams()}`;
        getAndHandle(url, json => cb(new GameAnalysisResponseDto(json)));
    }

    #urlParams() {
        return this.#gameId.urlParams;
    }

}
