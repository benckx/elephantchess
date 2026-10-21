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

// TODO: pass a callback instead of the widgets directly
class ImportMovesHandler extends ModalHandler {

    /**
     * @param boardGui {BoardGui}
     * @param moveTreeWidget {MoveTreeWidget}
     * @param onImported {function(): void}
     */
    constructor(boardGui, moveTreeWidget, onImported = () => {}) {
        super();

        let area = document.getElementById('moves-to-import-text-area');
        let button = document.getElementById('import-button');

        button.addEventListener('click', () => {
            if (area.value.trim().length === 0) {
                UI.pushErrorNotification('Please enter some moves to import', 3_000);
            } else {
                try {
                    // TODO: process annotations
                    const entries = parseToMoves(area.value.trim());
                    const moves = entries.map(entry => entry.move);
                    boardGui.loadFen(calculateFen(moves), false);
                    moveTreeWidget.setMoves(moves);
                    onImported();
                    UI.hideModal(null);
                } catch (error) {
                    console.error(error);
                    UI.pushErrorNotification(error.message, 5_000);
                }
            }
        });
    }

}
