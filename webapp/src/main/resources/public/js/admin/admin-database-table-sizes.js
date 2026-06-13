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

class AdminDatabaseTableSizesPage extends BasePage {

    /**
     * @type {HTMLTableElement}
     */
    #tableSizesTable = document.getElementById('table-sizes');

    /**
     * @type {HTMLSpanElement}
     */
    #totalTablesSpan = document.getElementById('total-tables');

    /**
     * @type {HTMLSpanElement}
     */
    #openingPreCalculationDataSizeCell = document.getElementById('opening-pre-calculation-data-size');

    /**
     * @type {HTMLSpanElement}
     */
    #openingPreCalculationIndexSizeCell = document.getElementById('opening-pre-calculation-index-size');

    /**
     * @type {HTMLSpanElement}
     */
    #openingPreCalculationTotalSizeCell = document.getElementById('opening-pre-calculation-total-size');

    constructor() {
        super();
        this.#fetchTableSizes();
    }

    #fetchTableSizes() {
        getAndHandle(ADMIN_URL_PREFIX + '/database-table-sizes', json => {
            this.#renderTableSizes(json);
            this.#renderOpeningPreCalculationSize(json);
            this.#renderChart(json);
        });
    }

    /**
     * Renders a bar chart of the top 10 largest tables
     * @param json {object}
     */
    #renderChart(json) {
        const entries = json.entries || [];
        const chart = new DatabaseTableSizesChart('table-sizes-chart', entries);
        chart.render();
    }

    /**
     * Renders the combined size of the opening_pre_calculation tables, split into data and index.
     * @param json {object}
     */
    #renderOpeningPreCalculationSize(json) {
        const entries = json.entries || [];
        const openingEntries = entries
            .filter(entry => entry.tableName.startsWith('opening_pre_calculation'));

        const dataBytes = openingEntries.reduce((sum, entry) => sum + entry.tableBytes, 0);
        const indexBytes = openingEntries.reduce((sum, entry) => sum + entry.indexBytes, 0);
        const totalBytes = openingEntries.reduce((sum, entry) => sum + entry.totalBytes, 0);

        this.#openingPreCalculationDataSizeCell.innerText = formatBytes(dataBytes);
        this.#openingPreCalculationIndexSizeCell.innerText = formatBytes(indexBytes);
        this.#openingPreCalculationTotalSizeCell.innerText = formatBytes(totalBytes);
    }

    /**
     * @param json {object}
     */
    #renderTableSizes(json) {
        const entries = json.entries || [];
        const tbody = this.#tableSizesTable.getElementsByTagName('tbody')[0];

        // clear existing rows
        emptyTable(this.#tableSizesTable);

        // update number of tables
        this.#totalTablesSpan.innerText = entries.length.toString();

        // add rows for each table
        entries.forEach(entry => {
            const row = tbody.insertRow();

            // Table name
            const nameCell = row.insertCell();
            nameCell.innerText = entry.tableName;
            nameCell.style.fontWeight = '600';

            // row estimate
            const rowsCell = row.insertCell();
            rowsCell.className = 'numeric-cell';
            rowsCell.innerText = formatNumber(entry.rowEstimate);

            // table size
            const tableSizeCell = row.insertCell();
            tableSizeCell.className = 'numeric-cell';
            tableSizeCell.innerText = entry.tableSize;

            // index size
            const indexSizeCell = row.insertCell();
            indexSizeCell.className = 'numeric-cell';
            indexSizeCell.innerText = entry.indexSize;

            // total size
            const totalSizeCell = row.insertCell();
            totalSizeCell.className = 'numeric-cell size-cell';
            totalSizeCell.innerText = entry.totalSize;
        });
    }

}

window.onload = () => new AdminDatabaseTableSizesPage();
