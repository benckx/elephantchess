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

class AdminPreAnalysisPage extends BasePage {

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
        this.#fetchPreAnalyzedReferenceGamesPerYear();
    }

    #fetchLatestMoveAnalysisByGame() {
        getAndHandle(`${ADMIN_URL_PREFIX}/list-latest-move-analysis-by-game`, json => {
            let entries = [];
            json.entries.map(jsonEntry => entries.push(new MoveAnalysisByGame(jsonEntry)));
            this.#renderLatestMoveAnalysisByGame(entries);
        });
    }

    /**
     * @param entries {MoveAnalysisByGame[]}
     */
    #renderLatestMoveAnalysisByGame(entries) {
        const tbody = emptyTable(this.#latestAnalyzedGamesTable);
        entries.forEach(entry => {
            const url = gameIdToPageLink(entry.gameId);
            const row = tbody.insertRow();
            row.insertCell().append(buildLink(url, entry.gameId.type));
            row.insertCell().append(buildLink(url, entry.gameId.id));
            row.insertCell().innerText = entry.firstFormatted;
            row.insertCell().innerText = entry.lastFormatted;
            row.insertCell().innerText = entry.totalAnalyzedMoves.toString();
            row.insertCell().innerText = entry.analysisStatus;
        });
    }

    #fetchPreAnalyzedReferenceGamesPerYear() {
        const statusColumns = [
            AnalysisStatus.NOT_STARTED,
            AnalysisStatus.PARTIALLY_COMPLETED,
            AnalysisStatus.COMPLETED,
            AnalysisStatus.CANCELLED,
            AnalysisStatus.STARTED
        ];

        getAndHandle(`${ADMIN_URL_PREFIX}/pre-analyzed-reference-games-per-year`, json => {
            const tbody = emptyTable(this.#perYearTable);
            const entries = StatusPerYearEntryDto.parse(json);
            const minYear = Math.min(...entries.map(it => it.year));
            const maxYear = Math.max(...entries.map(it => it.year));
            for (let year = maxYear; year > minYear; year--) {
                let row = tbody.insertRow();
                row.insertCell().innerText = year.toString();
                let completed = 0;
                for (let i = 0; i < statusColumns.length; i++) {
                    const cell = row.insertCell();
                    const status = statusColumns[i];
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

            for (let i = 0; i < statusColumns.length; i++) {
                const cell = totalRow.insertCell();
                const status = statusColumns[i];

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
}

window.onload = () => new AdminPreAnalysisPage();
