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

/**
 * Opening triptych (opening repository · board · move tree) shown on a database player
 * profile for players that have pre-calculated opening data. Unlike the analysis board,
 * the opening repertoire differs depending on the color the player played, hence the
 * "All / Plays red / Plays black" color filter.
 */
class DatabasePlayerOpeningsTriptych {

    #playerId;

    // null means "all" (both colors aggregated)
    #color = null;

    #boardGui = createWebappBoardGui({ elementId: 'player-openings-board-container' });

    #moveTreeWidget = new MoveTreeWidget({
        containerId: 'player-openings-move-tree-container',
        ...moveTreeResizeCookiePersistence('database-player-openings', 'player-openings-move-tree-container')
    });

    #openingRepositoryWidget = new OpeningRepositoryWidget(this.#boardGui, {
        url: '/api/database/player/openings/next-moves-info',
        buildBody: (movesAsUci) => ({
            'moves': movesAsUci,
            'playerId': this.#playerId,
            'color': this.#color
        })
    });

    constructor(playerId) {
        this.#playerId = playerId;

        // widgets
        this.#moveTreeWidget.boardWidget = this.#boardGui;
        this.#moveTreeWidget.addNavigationPanel({ containerId: 'player-openings-navigation-panel' });
        new SettingsGui(this.#boardGui, this.#moveTreeWidget);

        // listeners
        this.#boardGui.addAfterMoveListener((move) => {
            this.#moveTreeWidget.addToTree([move]);
            this.#updateWidgets(this.#moveTreeWidget.getMovesUpToSelection());
        });
        this.#moveTreeWidget.addClickedNodeListener(() => this.#handleNodeSelected());
        this.#moveTreeWidget.addNavigationListener(() => this.#handleNodeSelected());

        // color filter
        document.querySelectorAll('input[name="player-openings-color"]').forEach((radio) => {
            radio.addEventListener('change', (e) => {
                this.#color = e.target.value === 'ALL' ? null : e.target.value;
                this.#updateWidgets(this.#moveTreeWidget.getMovesUpToSelection());
            });
        });

        // init
        this.#boardGui.loadFen(DEFAULT_START_FEN);
        this.#updateWidgets(this.#moveTreeWidget.getMovesUpToSelection());
    }

    #handleNodeSelected() {
        if (this.#moveTreeWidget.selectedNode != null) {
            this.#boardGui.enablePlayerMove();
            this.#updateWidgets(this.#moveTreeWidget.getMovesUpToSelection());
        } else {
            // can not branch off from before the first move
            this.#boardGui.disablePlayerMove();
            this.#updateWidgets([]);
        }
    }

    /**
     * @param movesUpToSelection {HalfMove[]} including the selected one
     */
    #updateWidgets(movesUpToSelection) {
        this.#openingRepositoryWidget.fetchOpeningsNextMoves(movesUpToSelection);
    }

}
