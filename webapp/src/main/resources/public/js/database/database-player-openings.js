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
 * Opening explorer (opening repository · board) shown on a database player profile for
 * players that have pre-calculated opening data. Unlike the analysis board, the opening
 * repertoire differs depending on the color the player played, hence the
 * "All / Plays red / Plays black" color filter.
 *
 * There is no move tree: moves are played on the board (or by clicking an explorer row) to
 * walk one step deeper into the repertoire, and the "Reset" button returns to the start
 * position and re-starts the explorer.
 */
class DatabasePlayerOpenings {

    #playerId;

    // null means "all" (both colors aggregated)
    #color = null;

    // moves played from the start position
    #moves = [];

    #boardGui = createWebappBoardGui({ elementId: 'player-openings-board-container' });

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
        new SettingsGui(this.#boardGui, null);

        // each move played walks one step deeper into the repertoire
        this.#boardGui.addAfterMoveListener((move) => {
            this.#moves.push(move);
            this.#updateWidgets();
        });

        // color filter
        document.querySelectorAll('input[name="player-openings-color"]').forEach((radio) => {
            radio.addEventListener('change', (e) => {
                this.#color = e.target.value === 'ALL' ? null : e.target.value;
                this.#updateWidgets();
            });
        });

        // reset button: back to the start position and re-start the explorer
        document.getElementById('player-openings-reset-button')
            .addEventListener('click', () => this.#reset());

        // init
        this.#reset();
    }

    #reset() {
        this.#moves = [];
        this.#boardGui.loadFen(DEFAULT_START_FEN);
        this.#boardGui.enablePlayerMove();
        this.#updateWidgets();
    }

    #updateWidgets() {
        this.#openingRepositoryWidget.fetchOpeningsNextMoves(this.#moves);
    }

}
