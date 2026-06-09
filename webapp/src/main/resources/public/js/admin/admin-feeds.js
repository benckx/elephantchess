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

class AdminFeedsPage extends BasePage {

    #gamesTable = document.getElementById('list-games');
    #manchuGamesTable = document.getElementById('list-manchu-games');
    #botGamesTable = document.getElementById('list-bot-games');
    #manchuBotGamesTable = document.getElementById('list-manchu-bot-games');
    #lastPlayedPuzzlesTable = document.getElementById('last-played-puzzles');
    #usersAnalysisTable = document.getElementById('users-analysis');

    constructor() {
        super();
        if (this.#gamesTable != null) {
            this.#fetchGames('/list-games', this.#gamesTable);
        }
        if (this.#manchuGamesTable != null) {
            this.#fetchGames('/list-manchu-games', this.#manchuGamesTable);
        }
        if (this.#botGamesTable != null) {
            this.#fetchBotGames();
        }
        if (this.#manchuBotGamesTable != null) {
            this.#fetchBotGamesGeneric('/list-manchu-bot-games', this.#manchuBotGamesTable);
        }
        if (this.#lastPlayedPuzzlesTable != null) {
            this.#fetchLastPlayedPuzzles(
                `${ADMIN_URL_PREFIX}/last-played-puzzles`,
                this.#lastPlayedPuzzlesTable,
                true
            );
        }
        if (this.#usersAnalysisTable != null) {
            this.#fetchUsersAnalysis();
        }
    }

    /**
     * @param endpoint {string}
     * @param tableElement {HTMLTableElement}
     */
    #fetchGames(endpoint, tableElement) {
        getAndHandle(ADMIN_URL_PREFIX + endpoint, json => {
            const tbody = emptyTable(tableElement);
            GameAnalyticsDto.parseEntries(json).forEach(entry => {
                const row = tbody.insertRow();

                if (!entry.isLegit()) {
                    row.classList.add('not-legit-game');
                }

                const variantCell = row.insertCell();
                variantCell.innerText = entry.variant === Variant.MANCHU ? '统' : '象';

                const gameIdCell = row.insertCell();
                gameIdCell.append(entry.buildGameAnchor());

                const winnerSpan = document.createElement('span');
                winnerSpan.innerHTML = HTML_WHITE_SPACE + '[v]';

                const inviterCell = row.insertCell();
                inviterCell.classList.add('crop-text-ellipsis');

                inviterCell.append(
                    buildUsernameSpan(
                        entry.inviterUserId,
                        entry.inviterUsername,
                        userTypeFromName(entry.inviterUsername)
                    )
                );

                if (entry.isInviterWinner()) {
                    inviterCell.append(winnerSpan);
                }

                const inviteeCell = row.insertCell();
                inviteeCell.classList.add('crop-text-ellipsis');
                if (entry.inviteeUsername != null) {
                    inviteeCell.append(
                        buildUsernameSpan(
                            entry.inviteeUserId,
                            entry.inviteeUsername,
                            userTypeFromName(entry.inviteeUsername)
                        )
                    );

                    if (entry.isInviteeWinner()) {
                        inviteeCell.append(winnerSpan);
                    }
                } else {
                    inviteeCell.innerText = '--';
                }

                const ratingModelCell = row.insertCell();
                ratingModelCell.innerText = entry.ratingMode

                const allowGuestsCell = row.insertCell();
                allowGuestsCell.innerText = entry.allowGuests ? '[v]' : '[ ]';

                const alwaysVisibleCell = row.insertCell();
                alwaysVisibleCell.innerText = entry.alwaysVisibleInLobby ? '[v]' : '[ ]';

                const isPrivateInviteCell = row.insertCell();
                isPrivateInviteCell.innerText = entry.isPrivateInvite ? '[v]' : '[ ]';

                const timeControlCell = row.insertCell();
                timeControlCell.classList.add('crop-text-ellipsis');
                timeControlCell.innerText = entry.formattedTimeControl;

                row.insertCell().innerText = entry.formattedStatus;
                row.insertCell().innerText = entry.index.toString();
                row.insertCell().innerText = entry.formattedCreated;
                row.insertCell().innerText = entry.formattedLastUpdated;
                row.insertCell().innerText = entry.formattedSourceType;
            });
        });
    }

    #fetchBotGames() {
        this.#fetchBotGamesGeneric('/list-bot-games', this.#botGamesTable);
    }

    /**
     * @param endpoint {string}
     * @param tableElement {HTMLTableElement}
     */
    #fetchBotGamesGeneric(endpoint, tableElement) {
        getAndHandle(ADMIN_URL_PREFIX + endpoint, json => {
            const tbody = emptyTable(tableElement);
            BotGameAnalyticsDto.parseEntries(json).forEach(entry => {
                const row = tbody.insertRow();

                if (!entry.isLegit()) {
                    row.classList.add('not-legit-game');
                }

                const variantCell = row.insertCell();
                variantCell.className = 'label-cell';
                variantCell.innerText = entry.variant === Variant.MANCHU ? '统' : '象';

                const gameIdCell = row.insertCell();
                gameIdCell.className = 'label-cell';
                gameIdCell.append(entry.buildGameAnchor());

                if (!entry.isAnonymous()) {
                    const userCell = row.insertCell();
                    userCell.className = 'label-cell';
                    userCell.append(
                        buildUsernameSpan(
                            entry.userId,
                            entry.username,
                            entry.userType
                        )
                    );
                }

                const engineCell = row.insertCell();
                engineCell.className = 'label-cell';
                engineCell.innerText = formatEngineName(entry.engine);

                const depthCell = row.insertCell();
                depthCell.className = 'value-cell';
                depthCell.innerText = entry.depth.toString();

                const customFenCell = row.insertCell();
                if (entry.hasCustomStartFen) {
                    customFenCell.innerText = 'custom';
                } else {
                    customFenCell.innerText = 'standard';
                }

                const colorCell = row.insertCell();
                colorCell.className = 'label-cell';
                colorCell.append(buildColorSpan(entry.color));

                const statusCell = row.insertCell();
                statusCell.className = 'label-cell';
                statusCell.innerText = formatEnumValue(entry.status);

                const outcomeCell = row.insertCell();
                outcomeCell.className = 'label-cell';
                outcomeCell.innerText = formatEnumValue(gameOutcomeToUserOutcome(entry.color, entry.outcome));

                const indexCell = row.insertCell();
                indexCell.className = 'value-cell';
                indexCell.innerText = entry.index.toString();

                const createdCell = row.insertCell();
                createdCell.className = 'label-cell';
                createdCell.innerText = entry.formattedCreated;

                const lastUpdatedCell = row.insertCell();
                lastUpdatedCell.className = 'label-cell';
                lastUpdatedCell.innerText = entry.formattedLastUpdated;
            });
        });
    }

    /**
     * @param url {string}
     * @param tableElement {HTMLTableElement}
     * @param showUser {boolean}
     */
    #fetchLastPlayedPuzzles(url, tableElement, showUser) {
        getAndHandle(url, json => {
            const tbody = emptyTable(tableElement);

            json.entries.forEach(entry => {
                const row = tbody.insertRow();
                if (showUser) {
                    const userCell = row.insertCell();
                    userCell.className = 'label-cell';
                    userCell.append(
                        buildUsernameSpan(entry.userId, entry.username, entry.userType)
                    );
                }

                const puzzleCell = row.insertCell();
                puzzleCell.className = 'label-cell';
                puzzleCell.innerText = entry.puzzleId;

                const outcomeCell = row.insertCell();
                outcomeCell.className = 'label-cell';
                outcomeCell.innerText = capitalize(entry.outcome);

                const dateCell = row.insertCell();
                dateCell.className = 'label-cell';
                dateCell.innerText = formatTimestampToDateTime(entry.date);

                if (showUser) {
                    const voteCell = row.insertCell();
                    voteCell.className = 'label-cell';
                    if (entry.upVoted != null) {
                        voteCell.innerText = entry.upVoted;
                    } else {
                        voteCell.innerText = '--';
                    }
                }
            });
        });
    }

    #fetchUsersAnalysis() {
        getAndHandle(ADMIN_URL_PREFIX + '/last-users-analysis', json => {
            emptyTable(this.#usersAnalysisTable);
            const tbody = this.#usersAnalysisTable.getElementsByTagName('tbody')[0];

            json.entries.forEach(entry => {
                // html
                const tr = document.createElement('tr');
                const userCell = document.createElement('td');
                const analysisNameCell = document.createElement('td');
                const versionCell = document.createElement('td');
                const lastUpdatedCell = document.createElement('td');

                userCell.className = 'label-cell';
                analysisNameCell.className = 'long-label-cell';
                versionCell.className = 'value-cell';
                lastUpdatedCell.className = 'label-cell';

                tr.append(userCell, analysisNameCell, versionCell, lastUpdatedCell);
                tbody.append(tr);

                // content
                userCell.append(buildUserLinkDiv(entry.username));

                const analysisLink = document.createElement('a');
                analysisLink.href = '/analysis?id=' + entry.analysisId;
                analysisLink.innerText = entry.name;
                analysisNameCell.append(analysisLink);

                versionCell.innerText = entry.currentVersion;
                lastUpdatedCell.innerText = formatTimestampToDateTime(entry.lastUpdated);
            });
        });
    }

}

window.onload = () => new AdminFeedsPage();
