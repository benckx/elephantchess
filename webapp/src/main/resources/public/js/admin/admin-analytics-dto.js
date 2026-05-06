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

class TimeSeriesDto {

    #name;
    #values = [];

    constructor(json) {
        this.#name = json.name;
        for (let value of json.values) {
            this.#values.push(Number(value));
        }
    }

    get name() {
        return this.#name;
    }

    /**
     * @returns {Number[]}
     */
    get values() {
        return this.#values;
    }

}

class MultipleTimeSeriesDto {

    #periods;
    #timeSeries = [];

    constructor(json) {
        this.#periods = json.periods;
        for (let timeSeries of json.timeSeries) {
            this.#timeSeries.push(new TimeSeriesDto(timeSeries));
        }
    }

    /**
     * @returns {string[]}
     */
    getAllPeriods() {
        return this.#periods;
    }

    /**
     * @returns {string[]}
     */
    getAllNames() {
        return this.#timeSeries.map(it => it.name);
    }

    /**
     * @param period {string}
     * @returns {number[]}
     */
    valuesForPeriod(period) {
        let i = this.#periods.length - this.#periods.indexOf(period) - 1;
        return this.#timeSeries.map(it => it.values[i]);
    }

    /**
     * Get values at a specific index without reverse logic (for charts)
     * @param index {number}
     * @returns {number[]}
     */
    getValuesAtIndex(index) {
        return this.#timeSeries.map(it => it.values[index]);
    }

}
