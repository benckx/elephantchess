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

class AnalysisDto {

    /**
     * @type {string}
     */
    #analysisId;

    /**
     * @type {number}
     */
    #version;

    /**
     * @type {string}
     */
    #name;

    /**
     * @type {string}
     */
    #userId;

    /**
     * @type {string}
     */
    #username;

    #isOwner = false;

    /**
     * @type {number}
     */
    #lastUpdated;

    /**
     * @type {GameId|null}
     */
    #gameId;

    /**
     * @type {string|null}
     */
    #startFen;

    /**
     * @type {GameMetadataDto|null}
     */
    #gameMetadata;

    /**
     * @type {string|null}
     */
    #selectedNodeId;

    /**
     * @type {string[]}
     */
    #openedBranchIds;

    /**
     * @type {MoveTreeNodeDto[]}
     */
    #nodes= [];

    constructor(json) {
        this.#analysisId = json.analysisId;
        this.#version = Number(json.version);
        this.#name = json.name;
        this.#userId = json.userId;
        this.#username = json.username;
        if (User.isAuthenticated()) {
            let user = new User();
            this.#isOwner = user.userId === this.#userId;
        }
        this.#lastUpdated = json.lastUpdated;
        if (json.gameId != null && json.gameId.type != null && json.gameId.id != null) {
            this.#gameId = new GameId(json.gameId.type, json.gameId.id);
        }
        this.#startFen = json.startFen;
        this.#selectedNodeId = json.selectedNodeId;
        this.#openedBranchIds = json.openBranchIds;
        this.#deserializeNodes(json.nodes);
    }

    #deserializeNodes(jsonNodes) {
        for (let i = 0; i < jsonNodes.length; i++) {
            let jsonNode = jsonNodes[i];
            let nodeDto =
                new MoveTreeNodeDto(
                    jsonNode.id,
                    jsonNode.move,
                    jsonNode.level,
                    jsonNode.previous,
                    jsonNode.next,
                    jsonNode.childNodes,
                    jsonNode.annotation
                );

            this.#nodes.push(nodeDto);
        }
    }

    get analysisId() {
        return this.#analysisId;
    }

    get version() {
        return this.#version;
    }

    get name() {
        return this.#name;
    }

    get userId() {
        return this.#userId;
    }

    get username() {
        return this.#username;
    }

    get isOwner() {
        return this.#isOwner;
    }

    get lastUpdated() {
        return this.#lastUpdated;
    }

    get gameId() {
        return this.#gameId;
    }

    get startFen() {
        return this.#startFen;
    }

    /**
     * @return {GameMetadataDto|null}
     */
    get gameMetadata() {
        return this.#gameMetadata;
    }

    /**
     * @param gameMetadata {GameMetadataDto}
     */
    set gameMetadata(gameMetadata) {
        this.#gameMetadata = gameMetadata;
    }

    /**
     * @return {MoveTreeNodeDto[]}
     */
    get nodes() {
        return this.#nodes;
    }

    get selectedNodeId() {
        return this.#selectedNodeId;
    }

    get openedBranchIds() {
        return this.#openedBranchIds;
    }

    hasGameId() {
        return this.#gameId != null;
    }

}
