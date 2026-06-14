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
 * Opening explorer (opening repository · move tree · board) shown on a database player profile
 * for players that have pre-calculated opening data. Unlike the analysis board, the opening
 * repertoire differs depending on the color the player played, hence the
 * "All / Plays red / Plays black" color filter.
 *
 * Moves are played on the board (or by clicking an explorer row) to walk one step deeper into
 * the repertoire; the move tree below the opening widget keeps track of the variations and lets
 * the user navigate them. The "Reset" button clears the tree and returns to the start position.
 */
class DatabasePlayerOpenings {

    #playerId;

    // null means "all" (both colors aggregated); "Plays red" is the default
    #color = Color.RED;

    #resetButton = document.getElementById('player-openings-reset-button');

    #boardGui = createWebappBoardGui({elementId: 'player-openings-board-container'});

    #moveTreeWidget = new MoveTreeWidget({
        containerId: 'move-tree-container',
        isContextualMenuEnabled: false,
        isLoadingAnimationEnabled: false
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

        // settings (piece style, flip board, etc.)
        new SettingsGui(this.#boardGui, this.#moveTreeWidget);

        // connect board to the move tree so navigation (clicks, arrow keys) drives the board
        this.#moveTreeWidget.boardWidget = this.#boardGui;

        // each move played walks one step deeper into the repertoire
        this.#boardGui.addAfterMoveListener((move) => {
            this.#moveTreeWidget.addToTree([move]);
            this.#updateWidgets();
        });

        // selecting a node in the move tree updates the explorer for that position
        this.#moveTreeWidget.addClickedNodeListener(() => this.#updateWidgets());
        this.#moveTreeWidget.addNavigationListener(() => this.#updateWidgets());

        // color filter
        document.querySelectorAll('input[name="player-openings-color"]').forEach((radio) => {
            radio.addEventListener('change', (e) => {
                this.#color = e.target.value === 'ALL' ? null : e.target.value;
                this.#updateWidgets();
            });
        });

        // reset button: back to the start position and re-start the explorer
        this.#resetButton.addEventListener('click', () => {
            if (!this.#resetButton.classList.contains('app-buttons-disabled')) {
                this.#reset();
            }
        });

        // init
        this.#reset();
    }

    #reset() {
        this.#moveTreeWidget.clear();
        this.#boardGui.loadFen(DEFAULT_START_FEN, true);
        this.#boardGui.enablePlayerMove();
        this.#updateWidgets();
    }

    #updateWidgets() {
        this.#boardGui.enablePlayerMove();
        this.#updateResetButtonState();
        this.#openingRepositoryWidget.fetchOpeningsNextMoves(this.#moveTreeWidget.getMovesUpToSelection());
    }

    // the reset button is greyed out until at least one move has been played
    #updateResetButtonState() {
        if (this.#moveTreeWidget.getMovesUpToSelection().length === 0) {
            this.#resetButton.classList.add('app-buttons-disabled');
        } else {
            this.#resetButton.classList.remove('app-buttons-disabled');
        }
    }

}
