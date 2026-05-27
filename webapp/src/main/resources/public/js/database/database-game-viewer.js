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
        const ds = document.body.dataset;
        const idParam = ds.gameId;
        const orientationParam = ds.orientation;

        if (idParam == null || idParam === '') {
            window.open('/', '_self');
            return;
        }

        const gameId = new GameId(GameType.DB, idParam);
        this.#gameDataClient = new GameDataClient(gameId);

        // players-info, game-date-info, game-event-info and the page title / meta
        // description are rendered server-side. The board's final FEN, game id and
        // orientation are all read from body data-* attributes, so we don't need to
        // fetch metadata dynamically — we only need to fetch the move list.
        const finalFen = ds.finalFen;
        if (finalFen != null && finalFen !== '') {
            this.#boardGui.loadFen(finalFen);
        }
        if (orientationParam != null && orientationParam !== '') {
            this.#boardGui.flipToColor(orientationParam);
        }

        ['analyze-button-left-side', 'analyze-button-right-side']
            .forEach((id) => {
                document.getElementById(id)
                    .addEventListener('click', () => {
                        window.open(gameId.analysisUrl, '_self');
                    });
            });

        this.#gameDataClient.fetchMoves(moves => {
            this.#moveTreeWidget.setMoves(moves);
            renderAnalysisSummaryReportGeneric(gameId, this.#moveTreeWidget);
        });
    }

    /**
     * Build the PGN metadata Map from the body data-* attributes (populated
     * server-side). Avoids the extra metadata HTTP fetch.
     *
     * @return {Map<string, string>}
     */
    #buildPgnMetadata() {
        const metadata = new Map();
        metadata.set('Site', window.location.href);

        const ds = document.body.dataset;
        if (ds.pgnRedPlayer) metadata.set('White', ds.pgnRedPlayer);
        if (ds.pgnBlackPlayer) metadata.set('Black', ds.pgnBlackPlayer);
        if (ds.pgnEvent) metadata.set('Event', ds.pgnEvent);
        if (ds.pgnDate) metadata.set('Date', ds.pgnDate);
        if (ds.pgnResult) metadata.set('Result', ds.pgnResult);
        metadata.set('Variant', 'Xiangqi');

        return metadata;
    }

}

window.onload = () => new DatabaseGameViewerPage();
