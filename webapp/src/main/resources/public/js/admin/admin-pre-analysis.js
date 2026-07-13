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

const STATUS_COLUMNS = [
    AnalysisStatus.NOT_STARTED,
    AnalysisStatus.PARTIALLY_COMPLETED,
    AnalysisStatus.COMPLETED,
    AnalysisStatus.CANCELLED,
    AnalysisStatus.STARTED
];

const GAME_TYPE_ROWS = [
    GameType.DB,
    GameType.PVB,
    GameType.PVP
];

class AdminPreAnalysisPage extends BasePage {

    /**
     * @type {HTMLTableElement}
     */
    #byTypeTable = document.getElementById('pre-analysis-by-type-table');

    /**
     * @type {HTMLTableElement}
     */
    #perYearTable = document.getElementById('pre-analysis-per-year-table');

    /**
     * @type {HTMLTableElement}
     */
    #latestAnalyzedGamesTable = document.getElementById('latest-analyzed-games');

    constructor() {
        super();
        this.#fetchLatestMoveAnalysisByGame();
        this.#fetchPreAnalysisStatusByGameType();
        this.#fetchPreAnalyzedReferenceGamesPerYear();
    }

    #fetchLatestMoveAnalysisByGame() {
        getAndHandle(`${ADMIN_URL_PREFIX}/list-latest-move-analysis-by-game`, json => {
            let entries = [];
            json.entries.map(jsonEntry => entries.push(new MoveAnalysisByGame(jsonEntry)));
            this.#renderMoveAnalysisByGame(this.#latestAnalyzedGamesTable, entries);
        });
    }

    /**
     * @param table {HTMLTableElement}
     * @param entries {MoveAnalysisByGame[]}
     */
    #renderMoveAnalysisByGame(table, entries) {
        const tbody = emptyTable(table);
        entries.forEach(entry => {
            const url = gameIdToPageLink(entry.gameId);
            const row = tbody.insertRow();
            row.insertCell().append(buildLink(url, entry.gameId.type));
            row.insertCell().append(buildLink(url, entry.gameId.id));
            row.insertCell().innerText = entry.firstFormatted;
            row.insertCell().innerText = entry.lastFormatted;
            row.insertCell().innerText = entry.totalAnalyzedMoves.toString();
            row.insertCell().innerText = entry.analysisStatus;
            row.insertCell().innerText = entry.analyzedFromBatch ? '✓' : '';
        });
    }

    #fetchPreAnalyzedReferenceGamesPerYear() {
        getAndHandle(`${ADMIN_URL_PREFIX}/pre-analyzed-reference-games-per-year`, json => {
            const tbody = emptyTable(this.#perYearTable);
            const entries = StatusPerYearEntryDto.parse(json);
            const minYear = Math.min(...entries.map(it => it.year));
            const maxYear = Math.max(...entries.map(it => it.year));
            for (let year = maxYear; year > minYear; year--) {
                let row = tbody.insertRow();
                row.insertCell().innerText = year.toString();
                let completed = 0;
                for (let i = 0; i < STATUS_COLUMNS.length; i++) {
                    const cell = row.insertCell();
                    const status = STATUS_COLUMNS[i];
                    const entry = entries.find(it => it.year === year && it.status === status);
                    if (entry != null) {
                        if (status === AnalysisStatus.COMPLETED) {
                            completed = entry.count;
                        }
                        cell.innerText = formatNumber(entry.count);
                    } else {
                        cell.innerText = '--';
                    }
                }

                const yearTotal = entries
                    .filter(it => it.year === year)
                    .map(it => it.count)
                    .reduce((a, b) => a + b, 0);

                row.insertCell().innerText = formatNumber(yearTotal);
                row.insertCell().innerText = displayPercentage(completed / yearTotal);
            }

            const totalRow = tbody.insertRow();
            totalRow.insertCell().innerText = 'total';

            for (let i = 0; i < STATUS_COLUMNS.length; i++) {
                const cell = totalRow.insertCell();
                const status = STATUS_COLUMNS[i];

                const statusTotal = entries
                    .filter(it => it.status === status)
                    .map(it => it.count)
                    .reduce((a, b) => a + b, 0);

                cell.innerText = formatNumber(statusTotal);
            }

            const totalCompleted = entries
                .filter(it => it.status === AnalysisStatus.COMPLETED)
                .map(it => it.count)
                .reduce((a, b) => a + b, 0);

            const totalGames = entries
                .map(it => it.count)
                .reduce((a, b) => a + b, 0);

            totalRow.insertCell().innerText = formatNumber(totalGames);
            totalRow.insertCell().innerText = displayPercentage(totalCompleted / totalGames);
        });
    }

    #fetchPreAnalysisStatusByGameType() {
        getAndHandle(`${ADMIN_URL_PREFIX}/pre-analysis-status-by-game-type`, json => {
            const tbody = emptyTable(this.#byTypeTable);
            const entries = StatusByGameTypeEntryDto.parse(json);

            GAME_TYPE_ROWS.forEach(gameType => {
                const row = tbody.insertRow();
                row.insertCell().innerText = gameType;
                let completed = 0;
                let total = 0;

                STATUS_COLUMNS.forEach(status => {
                    const entry = entries.find(it => it.gameType === gameType && it.status === status);
                    const count = entry?.count || 0;
                    if (status === AnalysisStatus.COMPLETED) {
                        completed = count;
                    }
                    total += count;
                    row.insertCell().innerText = formatNumber(count);
                });

                row.insertCell().innerText = formatNumber(total);
                row.insertCell().innerText = total > 0 ? displayPercentage(completed / total) : '--';
            });

            const totalRow = tbody.insertRow();
            totalRow.insertCell().innerText = 'total';

            let totalCompleted = 0;
            let totalGames = 0;

            STATUS_COLUMNS.forEach(status => {
                const statusTotal = entries
                    .filter(it => it.status === status)
                    .map(it => it.count)
                    .reduce((a, b) => a + b, 0);

                if (status === AnalysisStatus.COMPLETED) {
                    totalCompleted = statusTotal;
                }
                totalGames += statusTotal;
                totalRow.insertCell().innerText = formatNumber(statusTotal);
            });

            totalRow.insertCell().innerText = formatNumber(totalGames);
            totalRow.insertCell().innerText = totalGames > 0 ? displayPercentage(totalCompleted / totalGames) : '--';
        });
    }
}

window.onload = () => new AdminPreAnalysisPage();
