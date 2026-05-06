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

const API_ANALYSIS = '/api/analysis';

class SaveAnalysisResponseDto {

    #analysisId;
    #version;
    #lastUpdated;

    constructor(json) {
        this.#analysisId = json.analysisId;
        this.#version = Number(json.version);
        this.#lastUpdated = Number(json.lastUpdated);
    }

    /**
     * @return {string}
     */
    get analysisId() {
        return this.#analysisId;
    }

    /**
     * @return {number}
     */
    get version() {
        return this.#version;
    }

    /**
     * Last updated timestamp in milliseconds
     *
     * @return {number}
     */
    get lastUpdated() {
        return this.#lastUpdated;
    }

}

class AnalysisBoardClient {

    /**
     * @param analysisId {string}
     * @param version {number|null}
     * @param callback {function(AnalysisDto)}
     */
    fetchAnalysis(analysisId, version, callback) {
        let url = `${API_ANALYSIS}/get?analysisId=${analysisId}`;
        if (version != null) {
            url += '&version=' + version;
        }

        getAndHandle(url, analysisJson => {
            callback(new AnalysisDto(analysisJson));
        });
    }

    /**
     * @param analysisId {string}
     * @param callback {function(InfoLineResult[])}
     */
    fetchAnalysisEngineDataCache(analysisId, callback) {
        let url = `${API_ANALYSIS}/engine-data?analysisId=${analysisId}`;
        getAndHandle(url, json => {
            let entries = json.entries.map(jsonEntry => new InfoLineResult(jsonEntry));
            callback(entries);
        });
    }

    /**
     * @param analysisId {string}
     * @param name {string}
     * @param callback {function(SaveAnalysisResponseDto)}
     */
    renameAnalysis(analysisId, name, callback) {
        let body = {
            analysisId: analysisId, name: name
        }

        postAndHandle(`${API_ANALYSIS}/rename`, body, (json) => {
            callback(new SaveAnalysisResponseDto(json));
        });
    }

    /**
     * @param analysisId {string}
     * @param startFen {string}
     * @param callback {function(SaveAnalysisResponseDto)}
     */
    updateStartFen(analysisId, startFen, callback) {
        let body = {
            analysisId: analysisId,
            startFen: startFen
        }

        postAndHandle(`${API_ANALYSIS}/update-start-fen`, body, (json) => {
            callback(new SaveAnalysisResponseDto(json));
        });
    }

    /**
     * @param analysisId {string|null}
     * @param name {string}
     * @param gameId {GameId|null}
     * @param nodes {MoveTreeNodeDto[]}
     * @param selectedNodeId {string|null}
     * @param openedBranchIds {string[]}
     * @param engineCache {Map<string, InfoLineResult>}
     * @param startFen {string|null}
     * @param callback {function(SaveAnalysisResponseDto)}
     */
    saveAnalysis(
        analysisId,
        name,
        gameId,
        nodes,
        selectedNodeId,
        openedBranchIds,
        engineCache,
        startFen,
        callback) {

        let gameIdLiteral = null
        if (gameId != null) {
            gameIdLiteral = {
                type: gameId.type,
                id: gameId.id
            };
        }

        const body = {
            analysisId: analysisId,
            name: name,
            nodes: nodes.map(nodeDto => nodeDto.toLiteral()),
            gameId: gameIdLiteral,
            engineCache: this.#cacheToLiteral(engineCache),
            selectedNodeId: selectedNodeId,
            startFen: startFen,
            openBranchIds: openedBranchIds
        }

        postAndHandle(`${API_ANALYSIS}/save`, body, (response) => {
            callback(new SaveAnalysisResponseDto(response));
        });
    }

    /**
     * @param engineCache Map<string, InfoLineResult>
     */
    #cacheToLiteral(engineCache) {
        let result = [];
        Array.from(engineCache).forEach(entry => {
            let fenKey = entry[0];
            let pv = entry[1];
            result.push(pv.toLiteral(fenKey));
        });

        return result;
    }

}
