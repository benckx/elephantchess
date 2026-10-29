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

class MoveHistoryDropDownMenuWidget extends DropDownMenu {

    /**
     * @param id {string}
     * @param boardGui {BoardGui}
     * @param moveTreeWidget {MoveTreeWidget}
     * @param getCurrentStartFenCb {function(): string}
     * @param selectedFenCb {function(string)} callback to be called when a fen is selected
     */
    constructor(id, boardGui, moveTreeWidget, getCurrentStartFenCb, selectedFenCb) {
        super(id);

        const parentDivId = 'move-history-drop-down-menu-3-dots-button';
        const iconId = 'move-history-drop-down-menu-img';

        // render icon
        const iconImg = document.createElement('img');
        iconImg.src = `${ICON_PATH}/dots.png`;
        iconImg.id = iconId;

        // render button div
        const buttonDiv = document.createElement('div');
        buttonDiv.id = parentDivId;
        buttonDiv.append(iconImg);
        buttonDiv.addEventListener('click', () => this.toggle());
        document.getElementsByClassName('flex-triptych-right-container')[0].append(buttonDiv);

        // callbacks
        this.addSimpleItem('Import moves', () => {
            UI.openWithConfirmation(
                !moveTreeWidget.isEmpty(),
                'Importing moves will erase the current game history. Continue?',
                'import',
                () => UI.showModalByName(Modals.IMPORT_MOVES, () => new ImportMovesHandler(boardGui, moveTreeWidget))
            );
        });

        this.addSimpleItem('Edit start position', () => {
            UI.openWithConfirmation(
                !moveTreeWidget.isEmpty(),
                'Editing the start position will erase the current game history. Continue?',
                'continue',
                () => UI.showModalByName(Modals.POSITION_EDITOR, () => new PositionEditorHandler(getCurrentStartFenCb, selectedFenCb))
            );
        });

        // register
        DropDownMenuManager.getInstance().registerDropDownMenu(this, [iconId], parentDivId);

        // add tool tip
        // addToolTip(buttonDiv, 'More Options');

        // pre-load position editor
        setTimeout(() => UI.preloadModal(Modals.POSITION_EDITOR), 2_000);
    }

}
