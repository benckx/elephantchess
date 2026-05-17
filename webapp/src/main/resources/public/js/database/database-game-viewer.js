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
                // players-info, game-status-info, game-date-info and game-event-info are
                // rendered server-side (so that crawlers / no-JS users see meaningful content,
                // including Chinese names).

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
                    DEFAULT_START_FEN,
                    this.#moveTreeWidget
                );
            });
        } else {
            window.open('/', '_self');
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
