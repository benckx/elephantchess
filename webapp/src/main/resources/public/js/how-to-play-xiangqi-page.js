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

class XiangqiAboutPage extends BasePage {

    #boardGuiGeneral = createWebappBoardGui({elementId: 'board-container-general-about'});
    #boardGuiAdvisor = createWebappBoardGui({elementId: 'board-container-advisor-about'});
    #boardGuiElephant = createWebappBoardGui({elementId: 'board-container-elephant-about'});
    #boardGuiHorse = createWebappBoardGui({elementId: 'board-container-horse-about'});
    #boardGuiChariot = createWebappBoardGui({elementId: 'board-container-chariot-about'});
    #boardGuiCannon = createWebappBoardGui({elementId: 'board-container-cannon-about'});
    #boardGuiSoldier = createWebappBoardGui({elementId: 'board-container-soldier-about'});
    #boardGuiManchuChariot = createWebappBoardGui({elementId: 'board-container-manchu-chariot-about'});
    #settingsGui;

    constructor() {
        super();
        this.#resetGeneralBoard();
        this.#resetAdvisorBoard();
        this.#resetElephantBoard();
        this.#resetHorseBoard();
        this.#resetChariotBoard();
        this.#resetCannonBoard();
        this.#resetSoldierBoard();
        this.#resetManchuChariotBoard();

        this.#initResetButton('reset-general-board-button', () => this.#resetGeneralBoard());
        this.#initResetButton('reset-advisor-board-button', () => this.#resetAdvisorBoard());
        this.#initResetButton('reset-elephant-board-button', () => this.#resetElephantBoard());
        this.#initResetButton('reset-horse-board-button', () => this.#resetHorseBoard());
        this.#initResetButton('reset-chariot-board-button', () => this.#resetChariotBoard());
        this.#initResetButton('reset-cannon-board-button', () => this.#resetCannonBoard());
        this.#initResetButton('reset-soldier-board-button', () => this.#resetSoldierBoard());
        this.#initResetButton('reset-manchu-chariot-board-button', () => this.#resetManchuChariotBoard());

        this.#settingsGui = new SettingsGui(this.#boardGuiGeneral, null, false, true);
        this.#settingsGui.addBoardGui(this.#boardGuiAdvisor);
        this.#settingsGui.addBoardGui(this.#boardGuiElephant);
        this.#settingsGui.addBoardGui(this.#boardGuiHorse);
        this.#settingsGui.addBoardGui(this.#boardGuiChariot);
        this.#settingsGui.addBoardGui(this.#boardGuiCannon);
        this.#settingsGui.addBoardGui(this.#boardGuiSoldier);
        this.#settingsGui.addBoardGui(this.#boardGuiManchuChariot);
    }

    #initResetButton(elementId, callback) {
        document.getElementById(elementId).addEventListener('click', callback);
    }

    #resetBoard(boardGui, pieceChar, piecePosition, blockerPositions = []) {
        const board = new Board();
        if (pieceChar !== 'K') {
            board.addPieceAt('K', new Position(3, 1), false);
        }
        if (pieceChar !== 'k') {
            board.addPieceAt('k', new Position(5, 8), false);
        }
        board.addPieceAt(pieceChar, piecePosition, false);
        blockerPositions.forEach(position => board.addPieceAt('p', position, false));
        boardGui.loadFen(board.outputFen(), true);
    }

    #resetGeneralBoard() {
        this.#resetBoard(
            this.#boardGuiGeneral,
            'K',
            new Position(4, 1),
            [new Position(3, 2), new Position(5, 2)]
        );
    }

    #resetAdvisorBoard() {
        this.#resetBoard(
            this.#boardGuiAdvisor,
            'A',
            new Position(4, 1),
            [new Position(3, 2), new Position(5, 2), new Position(4, 2)]
        );
    }

    #resetElephantBoard() {
        this.#resetBoard(
            this.#boardGuiElephant,
            'B',
            new Position(4, 2),
            [new Position(3, 3), new Position(5, 3)]
        );
    }

    #resetHorseBoard() {
        this.#resetBoard(
            this.#boardGuiHorse,
            'N',
            new Position(4, 4),
            [new Position(5, 4), new Position(4, 5), new Position(4, 3)]
        );
    }

    #resetChariotBoard() {
        this.#resetBoard(
            this.#boardGuiChariot,
            'R',
            new Position(4, 4),
            [new Position(4, 6), new Position(6, 4), new Position(2, 4)]
        );
    }

    #resetCannonBoard() {
        this.#resetBoard(
            this.#boardGuiCannon,
            'C',
            new Position(4, 3),
            [new Position(4, 4), new Position(4, 7), new Position(6, 3)]
        );
    }

    #resetSoldierBoard() {
        this.#resetBoard(
            this.#boardGuiSoldier,
            'P',
            new Position(4, 5),
            [new Position(3, 5), new Position(5, 5), new Position(4, 6)]
        );
    }

    #resetManchuChariotBoard() {
        this.#resetBoard(
            this.#boardGuiManchuChariot,
            'M',
            new Position(4, 4),
            [new Position(4, 7), new Position(6, 4), new Position(2, 4)]
        );
    }

}

window.onload = () => new XiangqiAboutPage();
