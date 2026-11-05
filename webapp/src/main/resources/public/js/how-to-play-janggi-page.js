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

class JanggiAboutPage extends BasePage {

    #boardGuiGeneral = this.#createBoardGui('board-container-general-about');
    #boardGuiAdvisor = this.#createBoardGui('board-container-advisor-about');
    #boardGuiChariot = this.#createBoardGui('board-container-chariot-about');
    #boardGuiCannon = this.#createBoardGui('board-container-cannon-about');
    #boardGuiHorse = this.#createBoardGui('board-container-horse-about');
    #boardGuiElephant = this.#createBoardGui('board-container-elephant-about');
    #boardGuiSoldier = this.#createBoardGui('board-container-soldier-about');
    #boardGuiCompleteSetup = this.#createBoardGui('board-container-complete-setup-about');

    /** @type {SettingsGui} */
    #settingsGui;

    constructor() {
        super();
        this.#resetGeneralBoard();
        this.#resetAdvisorBoard();
        this.#resetChariotBoard();
        this.#resetCannonBoard();
        this.#resetHorseBoard();
        this.#resetElephantBoard();
        this.#resetSoldierBoard();
        this.#resetCompleteSetupBoard();

        this.#initResetButton('reset-general-board-button', () => this.#resetGeneralBoard());
        this.#initResetButton('reset-advisor-board-button', () => this.#resetAdvisorBoard());
        this.#initResetButton('reset-chariot-board-button', () => this.#resetChariotBoard());
        this.#initResetButton('reset-cannon-board-button', () => this.#resetCannonBoard());
        this.#initResetButton('reset-horse-board-button', () => this.#resetHorseBoard());
        this.#initResetButton('reset-elephant-board-button', () => this.#resetElephantBoard());
        this.#initResetButton('reset-soldier-board-button', () => this.#resetSoldierBoard());
        this.#initResetButton('reset-complete-setup-board-button', () => this.#resetCompleteSetupBoard());

        const boardGuis = [
            this.#boardGuiGeneral,
            this.#boardGuiAdvisor,
            this.#boardGuiChariot,
            this.#boardGuiCannon,
            this.#boardGuiHorse,
            this.#boardGuiElephant,
            this.#boardGuiSoldier,
            this.#boardGuiCompleteSetup,
        ];
        this.#settingsGui = new SettingsGui(boardGuis[0], null, false, true);
        boardGuis.slice(1).forEach(boardGui => this.#settingsGui.addBoardGui(boardGui));
    }

    /**
     * @param {string} elementId
     * @returns {BoardGui}
     */
    #createBoardGui(elementId) {
        return createWebappBoardGui({
            elementId,
            variant: Variant.JANGGI,
            pieceStyle: PieceStyleSetting.ROMANIZED_ROUNDED,
        });
    }

    /**
     * @param {string} elementId
     * @param {function(): void} callback
     * @returns {void}
     */
    #initResetButton(elementId, callback) {
        document.getElementById(elementId).addEventListener('click', callback);
    }

    /**
     * @param {BoardGui} boardGui
     * @param {string} pieceChar
     * @param {Position} piecePosition
     * @param {{pieceChar: string, position: Position}[]} [blockerPieces=[]]
     * @returns {void}
     */
    #resetBoard(boardGui, pieceChar, piecePosition, blockerPieces = []) {
        const board = new JanggiBoard();
        if (pieceChar !== 'K') {
            board.addPieceAt('K', new Position(4, 1), false);
        }
        board.addPieceAt('k', new Position(3, 8), false);
        board.addPieceAt(pieceChar, piecePosition, false);
        blockerPieces.forEach(({pieceChar: blockerChar, position}) => {
            board.addPieceAt(blockerChar, position, false);
        });
        boardGui.loadFen(board.outputFen(), true);
    }

    #resetGeneralBoard() {
        this.#resetBoard(this.#boardGuiGeneral, 'K', new Position(4, 1));
    }

    #resetAdvisorBoard() {
        this.#resetBoard(this.#boardGuiAdvisor, 'A', new Position(3, 0));
    }

    #resetChariotBoard() {
        this.#resetBoard(
            this.#boardGuiChariot,
            'R',
            new Position(1, 4),
            [
                {pieceChar: 'p', position: new Position(1, 6)},
                {pieceChar: 'p', position: new Position(6, 4)},
            ]
        );
    }

    #resetCannonBoard() {
        this.#resetBoard(
            this.#boardGuiCannon,
            'C',
            new Position(1, 4),
            [
                {pieceChar: 'P', position: new Position(1, 5)},
                {pieceChar: 'p', position: new Position(1, 7)},
                {pieceChar: 'c', position: new Position(3, 4)},
                {pieceChar: 'p', position: new Position(5, 4)},
            ]
        );
    }

    #resetHorseBoard() {
        this.#resetBoard(
            this.#boardGuiHorse,
            'N',
            new Position(3, 4),
            [{pieceChar: 'p', position: new Position(3, 5)}]
        );
    }

    #resetElephantBoard() {
        this.#resetBoard(this.#boardGuiElephant, 'B', new Position(3, 4));
    }

    #resetSoldierBoard() {
        this.#resetBoard(this.#boardGuiSoldier, 'P', new Position(4, 7));
    }

    #resetCompleteSetupBoard() {
        this.#boardGuiCompleteSetup.loadFen(JANGGI_START_FEN, true);
    }

}

window.onload = () => new JanggiAboutPage();
