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

class AnalysisEntryDto {

    #json;

    constructor(json) {
        this.#json = json;
    }

    get analysisId() {
        return this.#json.analysisId;
    }

    get url() {
        return '/analysis?id=' + this.analysisId;
    }

    get name() {
        return this.#json.name;
    }

    /**
     * @return {Number}
     */
    get currentVersion() {
        return Number(this.#json.currentVersion);
    }

    get created() {
        return this.#json.created;
    }

    get lastUpdated() {
        return this.#json.lastUpdated;
    }

    get versions() {
        return this.#json.versions;
    }

    get rowId() {
        return 'analysis-row-' + this.analysisId;
    }

    get gameType() {
        return this.#json.gameType;
    }

    /**
     * @returns {string|null}
     */
    get selectedNodeFen() {
        return this.#json.selectedNodeFen;
    }

    /**
     * @returns {number}
     */
    get numberOfAnnotations() {
        return Number(this.#json.numberOfAnnotations);
    }

    /**
     * @returns {number}
     */
    get numberOfVariations() {
        return Number(this.#json.numberOfVariations);
    }

}
