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

class DatabaseEventPage extends BasePage {

    constructor() {
        super();
        this.#initializeGameRows();
    }

    #initializeGameRows() {
        document
            .querySelectorAll('.game-row-hoverable')
            .forEach((row) => {
                const fen = row.getAttribute('data-fen');
                const gameId = row.getAttribute('data-game-id');

                if (fen && gameId) {
                    addMiniboardDiv(row, gameId, fen, Color.RED, true);
                    row.addEventListener('click', () => {
                        window.location.href = `/database/game?id=${gameId}`;
                    });
                }
            });
    }

}

window.onload = () => new DatabaseEventPage();
