/*
 * Copyright (C) 2023-2026 Encelade SRL
 * Copyright (C) 2023-2026 elephantchess.io
 * Copyright (C) 2023-2026 Benoît Vleminckx (benckx)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

class AdminMoveAnnotationFailuresPage extends BasePage {
    #table = document.getElementById('annotation-failures-table');
    #summary = document.getElementById('annotation-failures-summary');

    constructor() {
        super();
        document.getElementById('refresh-annotation-failures').addEventListener('click', () => this.#fetchGames());
        this.#fetchGames();
    }

    #fetchGames() {
        const summary = this.#summary;
        const handler = new class extends SimpleResponseHandler {
            handle(response) {
                if (!response.ok) {
                    summary.innerText = 'Could not load annotation failures.';
                }
                super.handle(response);
            }

            handleCatchError(error) {
                summary.innerText = 'Could not load annotation failures.';
                UI.pushErrorNotification('Could not load annotation failures. Please retry.');
                super.handleCatchError(error);
            }
        }(json => this.#render(json.entries));
        getAndHandleWith(`${ADMIN_URL_PREFIX}/move-annotation-failures`, handler);
    }

    #render(entries) {
        const tbody = emptyTable(this.#table);
        this.#summary.innerText = entries.length === 0 ? 'No recorded move annotation failures.' : `${entries.length} games`;
        entries.forEach(entry => {
            const row = tbody.insertRow();
            const url = gameIdToPageLink(entry.gameId);
            row.insertCell().innerText = entry.gameId.type;
            row.insertCell().append(buildLink(url, entry.gameId.id));
            row.insertCell().innerText = entry.analysisStatus ?? 'Game missing';
            row.insertCell().innerText = formatNumber(entry.exceptionCount);
            row.insertCell().innerText = formatTimestampDefaultDateFormat(entry.lastExceptionTime);
            const actions = row.insertCell();
            if (entry.analysisStatus != null && entry.analysisStatus !== AnalysisStatus.NOT_STARTED) {
                const button = document.createElement('button');
                button.type = 'button';
                button.className = 'app-buttons';
                button.innerText = 'Clean analysis';
                button.disabled = entry.analysisStatus === AnalysisStatus.STARTED;
                if (button.disabled) {
                    button.title = 'Analysis is running. Refresh after it finishes.';
                }
                button.addEventListener('click', () => this.#clean(entry.gameId, button));
                actions.append(button);
            }
        });
    }

    #clean(gameId, button) {
        if (!window.confirm(`Delete pre-analysis data for ${gameId.type}-${gameId.id} and reset to NOT_STARTED?`)) {
            return;
        }
        button.disabled = true;
        const handler = new class extends SimpleResponseHandler {
            handle(response) {
                if (!response.ok) {
                    button.disabled = false;
                }
                if (response.status === 412 || response.status === 404) {
                    response.text().then(text => UI.pushErrorNotification(text));
                    return;
                }
                super.handle(response);
            }

            handleCatchError(error) {
                button.disabled = false;
                UI.pushErrorNotification('Could not clean analysis. Please retry.');
                super.handleCatchError(error);
            }
        }(json => {
            UI.pushInfoNotification('Analysis cleaned');
            this.#render(json.entries);
        });
        postAndHandleWith(`${ADMIN_URL_PREFIX}/move-annotation-failures/clean`, gameId, handler);
    }
}

window.onload = () => new AdminMoveAnnotationFailuresPage();
