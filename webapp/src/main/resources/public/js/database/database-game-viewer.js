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

class DatabaseGameViewerPage extends BasePage {

    /**
     * @type {GameDataClient|null}
     */
    #gameDataClient;

    /**
     * @type {GameMetadataDto|null}
     */
    #gameMetadata;

    /**
     * @type {HTMLDivElement}
     */
    #playersInfo = document.getElementById('players-info');

    /**
     * @type {HTMLDivElement}
     */
    #gameStatusInfo = document.getElementById('game-status-info');

    /**
     * @type {HTMLDivElement}
     */
    #gameDateInfo = document.getElementById('game-date-info');

    /**
     * @type {HTMLDivElement}
     */
    #gameEventInfo = document.getElementById('game-event-info');

    #boardGui = createWebappBoardGui();

    /**
     * @type {MoveTreeWidget}
     */
    #moveTreeWidget = new MoveTreeWidget({containerId: 'move-tree-container'});

    constructor() {
        super();

        // widgets
        this.#boardGui.disablePlayerMove();
        this.#moveTreeWidget.addNavigationPanel({
            containerId: 'mobile-navigation-panel',
            isDownloadButtonEnabled: true
        });
        this.#moveTreeWidget.addNavigationPanel({
            containerId: 'move-history-navigation-panel',
            isDownloadButtonEnabled: true
        });
        this.#moveTreeWidget.boardWidget = this.#boardGui;
        this.#moveTreeWidget.metadataFetcher = () => this.#buildPgnMetadata();
        new SettingsGui(this.#boardGui, this.#moveTreeWidget);

        // data
        this.#loadGameData();
    }

    #loadGameData() {
        const idParam = getQueryParam('id');
        const orientationParam = getQueryParam('orientation');

        if (idParam != null) {
            let gameId = new GameId(GameType.DB, idParam);
            this.#gameDataClient = new GameDataClient(gameId);
            this.#gameDataClient.fetchMetadata(metadata => {
                // display metadata
                this.#gameMetadata = metadata;
                this.#boardGui.loadFen(metadata.finalFen);
                if (orientationParam != null) {
                    this.#boardGui.flipToColor(orientationParam);
                }
                this.#playersInfo.append(this.#playerInfo(metadata));
                this.#gameStatusInfo.innerText = this.#formatOutcome(metadata);
                if (metadata.lastUpdated != null) {
                    this.#gameDateInfo.innerText = formatTimestampDefaultDateFormatNoTime(metadata.lastUpdated);
                } else {
                    this.#gameDateInfo.innerText = '<unknown date>';
                }

                if (metadata.eventId != null && metadata.eventName != null) {
                    this.#gameEventInfo.innerHTML = '';
                    this.#gameEventInfo.append(
                        buildLink(
                            `/database/event?id=${metadata.eventId}`,
                            metadata.eventName
                        )
                    );
                } else if (metadata.eventName != null) {
                    this.#gameEventInfo.innerText = metadata.eventName;
                } else {
                    this.#gameEventInfo.style.display = 'none';
                }

                ['analyze-button-left-side', 'analyze-button-right-side']
                    .forEach((id) => {
                        document.getElementById(id)
                            .addEventListener('click', () => {
                                window.open(gameId.analysisUrl, '_self');
                            });
                    });
            });

            this.#gameDataClient.fetchMoves(moves => {
                this.#moveTreeWidget.setMoves(moves);
                renderAnalysisSummaryReportGeneric(
                    gameId,
                    this.#moveTreeWidget.getMainBranchNodes(),
                );
            });
        } else {
            window.open('/', '_self');
        }
    }

    /**
     * @param metadata {GameMetadataDto}
     * @return {HTMLDivElement}
     */
    #playerInfo(metadata) {
        const red = buildSimpleSpan('<unknown>');
        const black = buildSimpleSpan('<unknown>');
        if (metadata.redPlayerName != null && metadata.redPlayerName !== '') {
            red.innerHTML = '';
            red.append(
                buildLink(`/database/player/${encodePlayerNameForUrl(metadata.redPlayerName)}`, metadata.redPlayerName)
            );
        }
        if (metadata.blackPlayerName != null && metadata.blackPlayerName !== '') {
            black.innerHTML = '';
            black.append(
                buildLink(`/database/player/${encodePlayerNameForUrl(metadata.blackPlayerName)}`, metadata.blackPlayerName)
            );
        }

        const div = document.createElement('div');
        div.append(red);
        div.append(buildSimpleSpan(' vs. '));
        div.append(black);
        return div;
    }

    /**
     * @param metadata {GameMetadataDto}
     * @returns {string}
     */
    #formatOutcome(metadata) {
        switch (metadata.outcome) {
            case Outcome.RED_WINS:
                if (metadata.redPlayerName != null && metadata.redPlayerName !== '') {
                    return `${metadata.redPlayerName} victory (Red)`;
                } else {
                    return 'Red wins';
                }
            case Outcome.BLACK_WINS:
                if (metadata.blackPlayerName != null && metadata.blackPlayerName !== '') {
                    return `${metadata.blackPlayerName} victory (Black)`;
                } else {
                    return 'Black wins';
                }
            case Outcome.DRAW:
                return 'Draw';
            default:
                return '--';
        }
    }

    /**
     * @return {Map<string, string>}
     */
    #buildPgnMetadata() {
        let metadata = new Map();

        // site metadata
        metadata.set('Site', window.location.href);

        // game metadata
        if (this.#gameMetadata != null) {
            this
                .#gameMetadata
                .buildPgnMetadata()
                .forEach((value, key) => metadata.set(key, value));
        }

        return metadata;
    }

}

window.onload = () => new DatabaseGameViewerPage();
