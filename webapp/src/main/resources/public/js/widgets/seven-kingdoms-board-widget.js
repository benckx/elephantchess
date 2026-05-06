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

const SVG_DARK_STROKE = 'black';
const SVG_LIGHT_STROKE = '#d5d5d5';

const LINE_THROUGH_SVG_DARK =
    `<svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none">
        <line x1="0" y1="0" x2="100" y2="100" vector-effect="non-scaling-stroke" stroke="${SVG_DARK_STROKE}"></line>
        <line x1="0" y1="100" x2="100" y2="0" vector-effect="non-scaling-stroke" stroke="${SVG_DARK_STROKE}"></line>
     </svg>`;

const LINE_THROUGH_SVG_LIGHT =
    `<svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none">
        <line x1="0" y1="0" x2="100" y2="100" vector-effect="non-scaling-stroke" stroke="${SVG_LIGHT_STROKE}"></line>
        <line x1="0" y1="100" x2="100" y2="0" vector-effect="non-scaling-stroke" stroke="${SVG_LIGHT_STROKE}"></line>
     </svg>`;

const CssClass7k = Object.freeze({
    PIECE_CIRCLE: 'piece-circle',
    PIECE_LABEL: 'piece-label',
    PIECE_HOLDER: 'piece-holder',
    VISIBLE_SQUARE: 'visible-square',
    SELECTED_PIECE_HIGHLIGHT: 'selected-piece-highlight',
    LEGAL_MOVE_HIGHLIGHT: 'legal-move-highlight',
    POSSIBLE_CAPTURE_HIGHLIGHT: 'possible-capture-highlight',
    ALL_PLAYERS_MOVE_HIGHLIGHT: 'all-players-move-highlight',
});

const highlightCssClasses = [
    CssClass7k.SELECTED_PIECE_HIGHLIGHT,
    CssClass7k.POSSIBLE_CAPTURE_HIGHLIGHT,
    CssClass7k.LEGAL_MOVE_HIGHLIGHT,
    CssClass7k.ALL_PLAYERS_MOVE_HIGHLIGHT
];

const PIECE_7K_ICON_PATH = '/images/pieces/seven-kingdoms';
const EMPEROR_BG_COLOR = 'rgb(242, 239, 42)';
const MOUSE_OVER_TIMEOUT = 1_000;

const darkPieceIcons = new Map();
darkPieceIcons.set(PieceTypes.GENERAL, 'q-outline.png');
darkPieceIcons.set(PieceTypes.GO_BETWEEN, 'g-outline-2.png');
darkPieceIcons.set(PieceTypes.DIPLOMAT, 'b-outline-4.png');
darkPieceIcons.set(PieceTypes.CHANCELLOR, 'r-outline.png');
darkPieceIcons.set(PieceTypes.CANNON, 'c-outline.png');
darkPieceIcons.set(PieceTypes.ARCHER, 'h-outline-straight.png');
darkPieceIcons.set(PieceTypes.CROSSBOWMAN, 'w-outline-1-straight.png');
darkPieceIcons.set(PieceTypes.KNIGHT, 'n-outline.png');
darkPieceIcons.set(PieceTypes.DAGGER_SOLDIER, 'a-outline-2.png');
darkPieceIcons.set(PieceTypes.SWORDSMAN, 's-outline-2.png');

const lightPieceIcons = new Map();
lightPieceIcons.set(PieceTypes.GENERAL, 'q-outline-light.png');
lightPieceIcons.set(PieceTypes.GO_BETWEEN, 'g-outline-2-light.png');
lightPieceIcons.set(PieceTypes.DIPLOMAT, 'b-outline-4-light.png');
lightPieceIcons.set(PieceTypes.CHANCELLOR, 'r-outline-light.png');
lightPieceIcons.set(PieceTypes.CANNON, 'c-outline-light.png');
lightPieceIcons.set(PieceTypes.ARCHER, 'h-outline-straight-light.png');
lightPieceIcons.set(PieceTypes.CROSSBOWMAN, 'w-outline-1-straight-light.png');
lightPieceIcons.set(PieceTypes.KNIGHT, 'n-outline-light.png');
lightPieceIcons.set(PieceTypes.DAGGER_SOLDIER, 'a-outline-2-light.png');
lightPieceIcons.set(PieceTypes.SWORDSMAN, 's-outline-2-light.png');

/**
 * @param color {Color7k}
 */
function indicatorCrossSvg(color) {
    if (color.isLightText) {
        return LINE_THROUGH_SVG_LIGHT;
    } else {
        return LINE_THROUGH_SVG_DARK;
    }
}

/**
 * @param color {Color7k|null}
 * @returns {string}
 */
function indicatorClass(color = null) {
    if (color == null) {
        return `kingdom-color-indicator`;
    } else {
        return `kingdom-color-indicator-${color.colorName.toLowerCase()}`;
    }
}

class BoardGui7k {

    #board = new Board7k();
    #settingsManager = new SettingsManager7k();
    #boardContainer;

    /**
     * @type {PieceAtPosition7k|null}
     */
    #selectedPiece = null;

    #postMoveListeners = [];

    constructor(options) {
        if (options.containerId) {
            this.#boardContainer = document.getElementById(options.containerId);
        } else {
            throw new Error('containerId is required');
        }

        if (!this.#boardContainer == null) {
            throw new Error(`${options.containerId} was not found`);
        }

        this.#drawBoard();
    }

    /**
     * @returns {Color7k|null}
     */
    get colorToPlay() {
        return this.#board.colorToPlay;
    }

    render() {
        this.#unDrawAllPieces();
        this.#removeAllHighlights();
        this.#board.listAllPieces().forEach(pieceAtPosition => {
            this.#drawPiece(pieceAtPosition);
        });
        this.#drawEmperor();
    }

    /**
     * @param fen {string}
     */
    loadFen(fen) {
        this.#board.clear();
        this.#board.loadFen(fen);
        this.render();
    }

    /**
     * @return {string}
     */
    outputFen() {
        return this.#board.outputFen();
    }

    clear() {
        this.#unDrawAllPieces();
        this.#board.clear();
        this.#drawEmperor();
    }

    /**
     * @param colors {Color7k[]}
     */
    set initColors(colors) {
        this.#board.initColors = colors;
    }

    /**
     * @param pieceAtPosition {PieceAtPosition7k}
     */
    setPiece(pieceAtPosition) {
        this.#unDrawPieceAt(pieceAtPosition.position);
        this.#board.setPiece(pieceAtPosition);
        this.#drawPiece(pieceAtPosition);
    }

    /**
     * @param move {Move}
     */
    registerMoveIfLegal(move) {
        this.#removeAllHighlights();
        if (this.#board.isLegalMove(move)) {
            const piece = this.#board.pieceAt(move.from);
            const pieceAtPosition = new PieceAtPosition7k(piece, move.to);
            this.#board.registerMove(move);
            this.#unDrawPieceAt(move.from);
            this.#unDrawPieceAt(move.to);
            this.#drawPiece(pieceAtPosition);
            this.#postMoveListeners.forEach(listener => listener(move));
        }
    }

    /**
     * @param listener {function(Move)}
     */
    addPostMoveListener(listener) {
        this.#postMoveListeners.push(listener);
    }

    /**
     * @param position {Position7k}
     */
    #unDrawPieceAt(position) {
        this.#elementAt(CssClass7k.PIECE_CIRCLE, position)?.remove();
    }

    #unDrawAllPieces() {
        Position7k
            .listAll()
            .forEach(position => {
                this.#unDrawPieceAt(position);
            });
    }

    /**
     * @param pieceAtPosition {PieceAtPosition7k}
     */
    #drawPiece(pieceAtPosition) {
        let pieceCircle;

        switch (this.#settingsManager.pieceStyle7k) {
            case PieceStyleSetting7K.UCI_LETTER:
                pieceCircle = this.#drawPieceAt(
                    pieceAtPosition.backgroundColor,
                    pieceAtPosition.textColor,
                    pieceAtPosition.uci,
                    pieceAtPosition.position
                );
                break;
            case PieceStyleSetting7K.CHINESE_CHAR:
                pieceCircle = this.#drawPieceAt(
                    pieceAtPosition.backgroundColor,
                    pieceAtPosition.textColor,
                    pieceAtPosition.chinesePieceName,
                    pieceAtPosition.position
                );
                break;
            default:
                pieceCircle = this.#drawPieceAsIconAt(
                    pieceAtPosition.backgroundColor,
                    pieceAtPosition.piece,
                    pieceAtPosition.position
                );
                break;
        }

        let mouseOverTimeout = null;

        pieceCircle.addEventListener('click', () => {
            clearTimeout(mouseOverTimeout);
            this.#handleClickOnPieceEvent(pieceAtPosition);
        });

        pieceCircle.addEventListener('mouseover', () => {
            clearTimeout(mouseOverTimeout);
            mouseOverTimeout = setTimeout(() => {
                if (this.#selectedPiece == null) {
                    this.#renderHighlightsOfPlayersMoves(pieceAtPosition)
                }
            }, MOUSE_OVER_TIMEOUT);
        });

        pieceCircle.addEventListener('mouseout', () => {
            clearTimeout(mouseOverTimeout);
            if (this.#selectedPiece == null) {
                this.#removeAllHighlights();
            }
        });

        document
            .getElementsByTagName('html')
            .item(0)
            .addEventListener('click', (e) => {
                if (!this.#boardContainer.contains(e.target)) {
                    this.#selectedPiece = null;
                    this.#renderHighlightsForSelectedPiece();
                }
            });
    }

    #drawEmperor() {
        let label;
        switch (this.#settingsManager.pieceStyle7k) {
            case PieceStyleSetting7K.UCI_LETTER:
                label = 'E';
                break;
            case PieceStyleSetting7K.CHINESE_CHAR:
                label = '周';
                break;
            default:
                label = '';
                break;
        }

        const emperorCircle =
            this.#drawPieceAt(
                EMPEROR_BG_COLOR,
                '#000000',
                label,
                new Position7k(EMPEROR_X, EMPEROR_Y)
            );

        if (this.#settingsManager.pieceStyle7k === PieceStyleSetting7K.WESTERNIZED_ICONS) {
            getElementsByClassNameArray(CssClass7k.PIECE_LABEL).forEach((element) => {
                element.remove();
            });

            const img = document.createElement('img');
            img.src = `${PIECE_7K_ICON_PATH}/emperor.png`;
            emperorCircle.appendChild(img);
        }
    }

    /**
     * @param backgroundColor {string}
     * @param textColor {string}
     * @param label {string}
     * @param position {Position7k}
     * @returns {HTMLDivElement}
     */
    #drawPieceAt(backgroundColor, textColor, label, position) {
        const pieceCircle = this.#buildPositionDiv(CssClass7k.PIECE_CIRCLE, position);
        const pieceLabel = this.#buildPositionDiv(CssClass7k.PIECE_LABEL, position);

        pieceCircle.style.backgroundColor = backgroundColor;
        pieceLabel.innerText = label;
        pieceLabel.style.color = textColor;
        pieceCircle.appendChild(pieceLabel);

        this.#elementAt(CssClass7k.PIECE_HOLDER, position).appendChild(pieceCircle);
        return pieceCircle;
    }

    /**
     * @param backgroundColor {string}
     * @param piece {Piece7k}
     * @param position {Position7k}
     * @returns {HTMLDivElement}
     */
    #drawPieceAsIconAt(backgroundColor, piece, position) {
        const pieceCircle = this.#buildPositionDiv(CssClass7k.PIECE_CIRCLE, position);
        pieceCircle.style.backgroundColor = backgroundColor;

        let iconPath;
        if (piece.color.isLightText) {
            iconPath = `${PIECE_7K_ICON_PATH}/${lightPieceIcons.get(piece.abstractPieceType)}`;
        } else {
            iconPath = `${PIECE_7K_ICON_PATH}/${darkPieceIcons.get(piece.abstractPieceType)}`;
        }

        const img = document.createElement('img');
        img.src = iconPath;
        img.className = `${piece.color.colorName.toLowerCase()}-color`;
        pieceCircle.appendChild(img);

        this.#elementAt(CssClass7k.PIECE_HOLDER, position).appendChild(pieceCircle);
        return pieceCircle;
    }

    #drawBoard() {
        for (let y = 0; y < BOARD_SIZE_7K; y++) {
            const row = document.createElement('div');
            row.classList.add('rows');
            this.#boardContainer.appendChild(row);

            for (let x = 0; x < BOARD_SIZE_7K; x++) {
                // y is flipped because the board coordinates are oriented from down to up
                const position = new Position7k(x, BOARD_SIZE_7K - y - 1);
                const pieceHolder = this.#buildPositionDiv(CssClass7k.PIECE_HOLDER, position);
                row.appendChild(pieceHolder);

                if (x < BOARD_SIZE_7K - 1 && y < BOARD_SIZE_7K - 1) {
                    pieceHolder.appendChild(this.#buildPositionDiv(CssClass7k.VISIBLE_SQUARE, position));
                }

                pieceHolder.addEventListener('click', () => {
                    this.#handleClickOnSquareEvent(position);
                });
            }

            if (isSafari) {
                this.#boardContainer.classList.add('safari-board-container-7k');
            }
        }

        this.#drawEmperor();
    }

    /**
     * @param pieceAtPosition {PieceAtPosition7k}
     */
    #handleClickOnPieceEvent(pieceAtPosition) {
        if (this.#selectedPiece == null) {
            // user is selecting the piece
            if (pieceAtPosition.color === this.#board.colorToPlay) {
                this.#selectedPiece = pieceAtPosition;
                this.#renderHighlightsForSelectedPiece();
            }
        } else if (this.#selectedPiece.position.equalsTo(pieceAtPosition.position)) {
            // if the piece user just clicked on is the same as the one he had selected before,
            // then user is un-selecting the piece
            this.#selectedPiece = null;
            this.#removeAllHighlights();
        } else if (
            this.#selectedPiece != null &&
            this.#board.containDifferentColors(this.#selectedPiece.position, pieceAtPosition.position) &&
            !this.#board.hasGoBetweenAt(this.#selectedPiece.position)
        ) {
            // user is capturing the piece he clicked on
            this.registerMoveIfLegal(new Move(this.#selectedPiece.position, pieceAtPosition.position));
            this.#selectedPiece = null;
        } else if (
            this.#selectedPiece != null &&
            this.#board.containSameColors(this.#selectedPiece.position, pieceAtPosition.position)
        ) {
            // user is selecting another piece
            this.#selectedPiece = pieceAtPosition;
            this.#renderHighlightsForSelectedPiece();
        }
    }

    /**
     * @param position {Position7k}
     */
    #handleClickOnSquareEvent(position) {
        if (this.#selectedPiece != null && !this.#selectedPiece.position.equalsTo(position)) {
            this.registerMoveIfLegal(new Move(this.#selectedPiece.position, position));
            this.#selectedPiece = null;
        }
    }

    #renderHighlightsForSelectedPiece() {
        this.#removeAllHighlights();

        if (this.#selectedPiece != null) {
            // selected pieces
            this
                .#elementAt(CssClass7k.PIECE_HOLDER, this.#selectedPiece.position)
                .appendChild(this.#buildPositionDiv(CssClass7k.SELECTED_PIECE_HIGHLIGHT, this.#selectedPiece.position));

            // legal moves
            this.#renderHighlightsFromPosition(
                this.#selectedPiece.position,
                CssClass7k.LEGAL_MOVE_HIGHLIGHT
            );
        }
    }

    /**
     * @param pieceAtPosition {PieceAtPosition7k}
     */
    #renderHighlightsOfPlayersMoves(pieceAtPosition) {
        this.#renderHighlightsFromPosition(
            pieceAtPosition.position,
            CssClass7k.ALL_PLAYERS_MOVE_HIGHLIGHT
        );

        htmlCollectionToArray(document.getElementsByClassName(CssClass7k.ALL_PLAYERS_MOVE_HIGHLIGHT))
            .forEach(element => {
                element.style.backgroundColor = pieceAtPosition.backgroundColor;
            });
    }

    #renderHighlightsFromPosition(position, legalMoveCssClass) {
        this.#board.listLegalMovesFrom(position).forEach(move => {
            let cssClass;
            if (this.#board.containDifferentColors(move.from, move.to)) {
                cssClass = CssClass7k.POSSIBLE_CAPTURE_HIGHLIGHT;
            } else {
                cssClass = legalMoveCssClass;
            }

            this
                .#elementAt(CssClass7k.PIECE_HOLDER, move.to)
                .appendChild(this.#buildPositionDiv(cssClass, move.to));
        });
    }

    #removeAllHighlights() {
        highlightCssClasses.forEach(cssClass => {
            getElementsByClassNameArray(cssClass)
                .filter(element => element.id.startsWith(this.#boardContainer.id + '-'))
                .forEach(element => element.remove());
        });
    }

    /**
     * Create a positional div element
     * Whether the element has a positional id or not depends on the class name
     *
     * @param className {string}
     * @param position {Position7k}
     * @return {HTMLDivElement}
     */
    #buildPositionDiv(className, position) {
        const div = document.createElement('div');

        switch (className) {
            case CssClass7k.PIECE_HOLDER:
            case CssClass7k.PIECE_CIRCLE:
            case CssClass7k.PIECE_LABEL:
            case CssClass7k.SELECTED_PIECE_HIGHLIGHT:
            case CssClass7k.POSSIBLE_CAPTURE_HIGHLIGHT:
            case CssClass7k.LEGAL_MOVE_HIGHLIGHT:
            case CssClass7k.ALL_PLAYERS_MOVE_HIGHLIGHT:
                div.id = this.#positionToId(className, position);
                break;
            default:
                break;
        }

        div.className = className;
        return div;
    }

    /**
     * Locate a HTMLElement by its positional id
     *
     * @param prefix {string}
     * @param position {Position7k}
     * @returns {HTMLElement|null}
     */
    #elementAt(prefix, position) {
        return document.getElementById(this.#positionToId(prefix, position));
    }

    /**
     * Calculate the positional id, given the prefix and the position
     *
     * @param prefix {string}
     * @param position {Position7k}
     * @returns {string}
     */
    #positionToId(prefix, position) {
        return `${this.#boardContainer.id}-${prefix.toLowerCase()}-${position.x}-${position.y}`;
    }

}
