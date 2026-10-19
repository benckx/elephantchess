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

class ApexChartWidget {

    /**
     * @type {HTMLElement}
     */
    #container;
    #chartOptions = {};
    #shouldRender = false;
    #chart = null;

    /**
     * @param containerId {string}
     */
    constructor(containerId) {
        this.#container = document.getElementById(containerId);
    }

    set chartOptions(value) {
        // Disable zoom by default for all charts
        if (!value.chart) {
            value.chart = {};
        }
        value.chart.zoom = {enabled: false};
        this.#chartOptions = value;
    }

    enableRender() {
        this.#shouldRender = true;
    }

    /**
     * @return {boolean}
     */
    render() {
        if (this.#shouldRender) {
            this.destroy();
            // noinspection JSUnresolvedFunction
            this.#chart = new ApexCharts(this.#container, this.#chartOptions);
            this.#chart.render();
            return true;
        } else {
            return false;
        }
    }

    destroy() {
        if (this.#chart != null) {
            this.#chart.destroy();
            this.#chart = null;
        }
    }

}
