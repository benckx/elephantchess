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

class SevenKingdomsRulesPage extends BasePage {

    #boardGuiSetup = new BoardGui7k({containerId: 'board-container-setup'});
    #boardGuiGeneral = new BoardGui7k({containerId: 'board-container-general'});
    #boardGuiChancellor = new BoardGui7k({containerId: 'board-container-chancellor'});
    #boardGuiDiplomat = new BoardGui7k({containerId: 'board-container-diplomat'});
    #boardGuiCannon = new BoardGui7k({containerId: 'board-container-cannon'});
    #boardGuiGoBetween = new BoardGui7k({containerId: 'board-container-go-between'});
    #boardGuiArcher = new BoardGui7k({containerId: 'board-container-archer'});
    #boardGuiCrossbowman = new BoardGui7k({containerId: 'board-container-crossbowman'});
    #boardGuiKnight = new BoardGui7k({containerId: 'board-container-knight'});
    #boardGuiDagger = new BoardGui7k({containerId: 'board-container-dagger'});
    #boardGuiSword = new BoardGui7k({containerId: 'board-container-sword'});

    #prays = [
        new Position7k(4, 4),
        new Position7k(6, 14),
        new Position7k(6, 17),
        new Position7k(14, 14),
        new Position7k(14, 9)
    ];

    constructor() {
        super();
        this.#boardGuiSetup.loadFen(DEFAULT_START_FEN_7K);
        this.#boardGuiSetup.addPostMoveListener((_) => {
            const colorToPlay = this.#boardGuiSetup.colorToPlay;
            if (colorToPlay != null) {
                document.getElementById('turn-color-name').innerText = formatEnumValue(colorToPlay.colorName);

                const indicator = document.getElementById('turn-color-indicator');
                indicator.className = indicatorClass();
                indicator.classList.add(indicatorClass(colorToPlay));
            }
        })

        this.#allPieceMoveBoards().forEach(board => {
            board.initColors = [ColorTypes.WHITE, ColorTypes.BLACK];
        });
        this.#resetToBaseBoard(this.#boardGuiGeneral, PieceTypes.GENERAL);
        this.#resetToBaseBoard(this.#boardGuiChancellor, PieceTypes.CHANCELLOR);
        this.#resetToBaseBoard(this.#boardGuiDiplomat, PieceTypes.DIPLOMAT);
        this.#resetToBaseBoard(this.#boardGuiCannon, PieceTypes.CANNON);
        this.#resetToBaseBoard(this.#boardGuiGoBetween, PieceTypes.GO_BETWEEN);
        this.#resetToBaseBoard(this.#boardGuiArcher, PieceTypes.ARCHER);
        this.#resetToBaseBoard(this.#boardGuiCrossbowman, PieceTypes.CROSSBOWMAN);
        this.#resetKnightBoard();
        this.#resetToBaseBoard(this.#boardGuiDagger, PieceTypes.DAGGER_SOLDIER);
        this.#resetToBaseBoard(this.#boardGuiSword, PieceTypes.SWORDSMAN);
        new BoardGui7k({containerId: 'board-container-emperor'});

        document.getElementById('reset-general-board-button').addEventListener('click', () => {
            this.#resetToBaseBoard(this.#boardGuiGeneral, PieceTypes.GENERAL);
        });
        document.getElementById('reset-chancellor-board-button').addEventListener('click', () => {
            this.#resetToBaseBoard(this.#boardGuiChancellor, PieceTypes.CHANCELLOR);
        });
        document.getElementById('reset-diplomat-board-button').addEventListener('click', () => {
            this.#resetToBaseBoard(this.#boardGuiDiplomat, PieceTypes.DIPLOMAT);
        });
        document.getElementById('reset-cannon-board-button').addEventListener('click', () => {
            this.#resetToBaseBoard(this.#boardGuiCannon, PieceTypes.CANNON);
        });
        document.getElementById('reset-go-between-board-button').addEventListener('click', () => {
            this.#resetToBaseBoard(this.#boardGuiGoBetween, PieceTypes.GO_BETWEEN);
        });
        document.getElementById('reset-archer-board-button').addEventListener('click', () => {
            this.#resetToBaseBoard(this.#boardGuiArcher, PieceTypes.ARCHER);
        });
        document.getElementById('reset-crossbowman-board-button').addEventListener('click', () => {
            this.#resetToBaseBoard(this.#boardGuiCrossbowman, PieceTypes.CROSSBOWMAN);
        });
        document.getElementById('reset-knight-board-button').addEventListener('click', () => {
            this.#resetKnightBoard();
        });
        document.getElementById('reset-dagger-board-button').addEventListener('click', () => {
            this.#resetToBaseBoard(this.#boardGuiDagger, PieceTypes.DAGGER_SOLDIER);
        });
        document.getElementById('reset-sword-board-button').addEventListener('click', () => {
            this.#resetToBaseBoard(this.#boardGuiSword, PieceTypes.SWORDSMAN);
        });
    }

    /**
     * All boards beside the main one at the top of the page and the Emperor one at the end of the list.
     *
     * @returns {BoardGui7k[]}
     */
    #allPieceMoveBoards() {
        return [
            this.#boardGuiGeneral,
            this.#boardGuiChancellor,
            this.#boardGuiDiplomat,
            this.#boardGuiCannon,
            this.#boardGuiGoBetween,
            this.#boardGuiArcher,
            this.#boardGuiCrossbowman,
            this.#boardGuiKnight,
            this.#boardGuiDagger,
            this.#boardGuiSword
        ];
    }

    /**
     * @param board {BoardGui7k}
     * @param pieceType {AbstractPieceType7k}
     */
    #resetToBaseBoard(board, pieceType) {
        /**
         * @param positions {Position7k[]}
         * @returns {PieceAtPosition7k[]}
         */
        function mapToSwordsmen(positions) {
            return positions.map(position => {
                const piece = new Piece7k(ColorTypes.BLACK, PieceTypes.SWORDSMAN)
                return new PieceAtPosition7k(piece, position);
            });
        }

        board.clear();
        board.setPiece(
            new PieceAtPosition7k(
                new Piece7k(ColorTypes.WHITE, pieceType),
                new Position7k(6, 6)
            )
        );

        mapToSwordsmen(this.#prays).forEach(pieceAtPosition => {
            board.setPiece(pieceAtPosition);
        });
    }

    #resetKnightBoard() {
        this.#resetToBaseBoard(this.#boardGuiKnight, PieceTypes.KNIGHT);
        const daggerPieceType = new Piece7k(ColorTypes.BLACK, PieceTypes.DAGGER_SOLDIER);

        [
            new Position7k(4, 9),
            new Position7k(7, 6),
            new Position7k(9, 2)
        ]
            .map((position) => new PieceAtPosition7k(daggerPieceType, position))
            .forEach((pieceAtPosition) => {
                this.#boardGuiKnight.setPiece(pieceAtPosition);
            });
    }

}

window.onload = () => new SevenKingdomsRulesPage();
