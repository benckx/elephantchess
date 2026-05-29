/*
 * Copyright (C) 2026  Encelade SRL
 * Copyright (C) 2026  elephantchess.io
 * Copyright (C) 2026  Benoît Vleminckx (benckx)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see <https://www.gnu.org/licenses/>.
 */

// Small DOM helpers and constants, inlined here so this file is usable on its
// own (only requires xiangqi.js, which provides the `Color` enum). Previously
// imported from utils.js / ui.js. For helpers that may also be defined by
// those modules when loaded as part of the full webapp, we only define them
// if the host page hasn't already loaded those modules, to avoid clobbering
// existing definitions.
if (typeof buildImg === 'undefined') {
    // eslint-disable-next-line no-var
    var buildImg = function (src, className = null) {
        const img = document.createElement('img');
        img.src = src;
        if (className != null) img.className = className;
        return img;
    };
}

if (typeof htmlCollectionToArray === 'undefined') {
    // eslint-disable-next-line no-var
    var htmlCollectionToArray = function (htmlCollection) {
        const arr = [];
        for (let i = 0; i < htmlCollection.length; i++) {
            arr.push(htmlCollection[i]);
        }
        return arr;
    };
}

if (typeof buildDivWithClass === 'undefined') {
    // eslint-disable-next-line no-var
    var buildDivWithClass = function (className) {
        const div = document.createElement('div');
        div.className = className;
        return div;
    };
}

// Mapping from FEN piece char to piece image file name. Lives here (rather
// than in ui.js) so that board-gui.js can be used standalone.
const pieceImageNames = new Map();
pieceImageNames.set('K', 'red_general.png');
pieceImageNames.set('k', 'black_general.png');
pieceImageNames.set('A', 'red_advisor.png');
pieceImageNames.set('a', 'black_advisor.png');
pieceImageNames.set('B', 'red_elephant.png');
pieceImageNames.set('b', 'black_elephant.png');
pieceImageNames.set('N', 'red_horse.png');
pieceImageNames.set('n', 'black_horse.png');
pieceImageNames.set('C', 'red_cannon.png');
pieceImageNames.set('c', 'black_cannon.png');
pieceImageNames.set('R', 'red_chariot.png');
pieceImageNames.set('r', 'black_chariot.png');
pieceImageNames.set('P', 'red_soldier.png');
pieceImageNames.set('p', 'black_soldier.png');
// Manchu super-chariot (combines chariot + horse + cannon powers), red only, displayed as chariot
pieceImageNames.set('M', 'red_chariot.png');

const diagonalDescending = '<svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none"><line x1="0" y1="0" x2="100" y2="100" vector-effect="non-scaling-stroke" stroke="black" /></svg>';
const diagonalRising = '<svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none"><line x1="0" y1="100" x2="100" y2="0" vector-effect="non-scaling-stroke" stroke="black"/></svg>';

const diagonalDescendingMini = '<svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none"><line x1="0" y1="0" x2="100" y2="100" stroke="black" /></svg>';
const diagonalRisingMini = '<svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none"><line x1="0" y1="100" x2="100" y2="0" stroke="black"/></svg>';

const PRIMARY_ARROW_COLOR = 'rgb(27, 181, 29)';
const SECONDARY_ARROW_COLOR = 'rgb(62, 56, 219)';

const ARROW_MARKERS = `
<defs>
    <marker id='head-primary' orient="auto" markerWidth='3' markerHeight='4' refX='0.1' refY='2'>
      <path d='M0,0 V4 L2,2 Z' fill="${PRIMARY_ARROW_COLOR}" />
    </marker>
        <marker id='head-secondary' orient="auto" markerWidth='3' markerHeight='4' refX='0.1' refY='2'>
      <path d='M0,0 V4 L2,2 Z' fill="${SECONDARY_ARROW_COLOR}" />
    </marker>
</defs>`;

const bottomRightCrosshairs = [
    new Position(0, 8), new Position(6, 8),
    new Position(1, 7), new Position(3, 7), new Position(5, 7), new Position(7, 7),
    new Position(1, 4), new Position(3, 4), new Position(5, 4), new Position(7, 4),
    new Position(0, 3), new Position(6, 3),
];

const bottomLeftCrosshairs = [
    new Position(1, 8), new Position(7, 8),
    new Position(0, 7), new Position(2, 7), new Position(4, 7), new Position(6, 7),
    new Position(0, 4), new Position(2, 4), new Position(4, 4), new Position(6, 4),
    new Position(1, 3), new Position(7, 3),
];

const topRightCrosshairs = [
    new Position(0, 7), new Position(6, 7),
    new Position(1, 6), new Position(3, 6), new Position(5, 6), new Position(7, 6),
    new Position(1, 3), new Position(3, 3), new Position(5, 3), new Position(7, 3),
    new Position(0, 2), new Position(6, 2),
];

const topLeftCrosshairs = [
    new Position(1, 7), new Position(7, 7),
    new Position(0, 6), new Position(2, 6), new Position(4, 6), new Position(6, 6),
    new Position(0, 3), new Position(2, 3), new Position(4, 3), new Position(6, 3),
    new Position(1, 2), new Position(7, 2)
];

const EngineArrowType = Object.freeze({
    PRIMARY: 'PRIMARY',
    SECONDARY: 'SECONDARY'
});

/**
 * Orientation of the board coordinate labels.
 * Use `null` (not part of this enum) to hide the coordinates entirely.
 */
const CoordinatesOrientation = Object.freeze({
    WXF: 'WXF',
    ALGEBRAIC: 'ALGEBRAIC',
});

// Chinese numerals for files 1..9, used to label files in WXF mode when the
// {@link FileNumbersStyle} setting calls for Chinese numerals on that side.
const CHINESE_FILE_DIGITS = ['一', '二', '三', '四', '五', '六', '七', '八', '九'];

/**
 * How file numbers are rendered around the board in WXF mode. Only affects the
 * file-number labels; the algebraic orientation (a..i letters) is unaffected.
 */
const FileNumbersStyle = Object.freeze({
    /** Arabic numerals (1..9) on both sides of the board. */
    ARABIC_BOTH: 'ARABIC_BOTH',
    /** Chinese numerals (一..九) on both sides of the board. */
    CHINESE_BOTH: 'CHINESE_BOTH',
    /** Chinese numerals on red's side; Arabic numerals on black's side (default). */
    CHINESE_RED_ONLY: 'CHINESE_RED_ONLY',
    /** Chinese numerals on black's side; Arabic numerals on red's side. */
    CHINESE_BLACK_ONLY: 'CHINESE_BLACK_ONLY',
    /** Chinese numerals on the bottom (lower) side of the screen; Arabic on the top side. */
    CHINESE_LOWER_ONLY: 'CHINESE_LOWER_ONLY',
    /** Chinese numerals on the top side of the screen; Arabic on the bottom side. */
    CHINESE_TOP_ONLY: 'CHINESE_TOP_ONLY',
    DEFAULT: 'CHINESE_RED_ONLY',
});

/**
 * Visual style of the board pieces. Used to pick the corresponding image folder
 * (under `${assetsBaseUrl}/images/pieces/<style-lowercased>/`).
 */
const PieceStyleSetting = Object.freeze({
    TRADITIONAL: 'TRADITIONAL',
    ROMANIZED_ROUNDED: 'ROMANIZED_ROUNDED',
    DEFAULT: 'TRADITIONAL',
});

/**
 * @typedef {Object} BoardGuiOptions
 * @property {string}      [elementId]              - id of the container element
 * @property {boolean}     [showCoordinates]        - whether to reserve space for file/rank coordinates
 * @property {string|null} [coordinatesOrientation] - one of {@link CoordinatesOrientation} or `null` to
 *                                                    hide the labels (space is still reserved when
 *                                                    `showCoordinates` is true). The caller is in
 *                                                    charge of resolving any user preference (e.g. cookies).
 * @property {boolean}     [mini]                   - whether this is a mini (thumb) board
 * @property {boolean}     [forceRenderChecks]      - render checks even on mini boards
 * @property {boolean}     [svg]                    - enable the SVG overlay (used for engine arrows)
 * @property {boolean}     [playSounds]             - whether board sounds are enabled
 * @property {string}      [assetsBaseUrl]          - base URL prepended to every static asset path
 *                                                    (images, audio). Default: `https://elephantchess.io`.
 *                                                    Pass an empty string to use relative paths (e.g. when
 *                                                    serving the assets from the current host on localhost).
 * @property {string}      [pieceStyle]             - one of {@link PieceStyleSetting}; selects the piece
 *                                                    image folder.
 * @property {boolean}     [colorblindFriendlyBlackPieces] - if true, black piece images get an invert
 *                                                    CSS filter for improved contrast.
 * @property {boolean}     [flipOpponentPieces]     - if true, opponent piece images are rotated
 *                                                    180° to simulate the OTB appearance.
 * @property {string}      [fileNumbersStyle]       - one of {@link FileNumbersStyle}; selects how
 *                                                    file numbers are rendered in WXF mode.
 */

/** @type {Readonly<Required<BoardGuiOptions>>} */
const DEFAULT_BOARD_GUI_OPTIONS = Object.freeze({
    elementId: 'board-container',
    showCoordinates: true,
    coordinatesOrientation: 'WXF',
    mini: false,
    forceRenderChecks: false,
    svg: false,
    playSounds: true,
    assetsBaseUrl: 'https://cdn.elephantchess.io/static',
    pieceStyle: PieceStyleSetting.DEFAULT,
    colorblindFriendlyBlackPieces: false,
    flipOpponentPieces: false,
    fileNumbersStyle: FileNumbersStyle.DEFAULT,
});

class BoardGui {

    #board = new Board();
    #boardContainer;
    #currentShowingLegalMovesFor = null;
    #selectedPiecePosition = null;
    #flippedRed = true; // if true, oriented toward the RED player
    #afterMoveListeners = [];
    #afterDrawPositionsListeners = [];
    #afterFlipListeners = [];

    /** @type {HTMLAudioElement} */
    #clickSound;

    // when false, player won't be able to make a move
    #isPlayerMoveEnabled = true;

    /**
     * @type {Readonly<Required<BoardGuiOptions>>}
     */
    #options;

    /**
     * @type {HalfMove|null}
     */
    #primaryEngineArrow = null;

    /**
     * @type {HalfMove|null}
     */
    #secondaryEngineArrow = null;

    #isSafari = false;

    /**
     * @param {BoardGuiOptions} [options]
     */
    constructor(options = {}) {
        this.#isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
        this.#options = Object.freeze({...DEFAULT_BOARD_GUI_OPTIONS, ...options});

        this.#clickSound = new Audio(`${this.#options.assetsBaseUrl}/audio/rclick-13693.mp3`);

        this.#boardContainer = document.getElementById(this.#options.elementId);
        this.#renderColorblindFriendlyBlackPiecesSetting(this.#options.colorblindFriendlyBlackPieces);
        this.#renderFlipOpponentPiecesSetting(this.#options.flipOpponentPieces);
        this.#drawBoard();
        this.#drawPieces(); // FIXME: useful?

        const boardGui = this;

        document
            .getElementsByTagName('html')
            .item(0)
            .addEventListener('click', (e) => {
                if (!boardGui.#boardContainer.contains(e.target)) {
                    boardGui.#hideAllPiecePlaceHolders();
                }
            });

        if (this.#options.svg) {
            window.onresize = function () {
                boardGui.#renderSvg();
            };
        }

        this.#forceSafariLayoutRefresh();
    }

    /**
     * Draw the position described by {@code fen}.
     *
     * When {@code animate} is true, the transition from the currently displayed
     * position to the target position is animated: unchanged pieces are left in
     * place, pieces of the same type are paired between the current and target
     * positions and slide from their source square to their destination square,
     * leftover current pieces fade out, and target pieces fade in - all
     * simultaneously. This works for any diff size (not just a single move),
     * similar to what lichess does.
     *
     * @param fen {string}
     * @param animate {boolean}
     */
    loadFen(fen, animate = false) {
        if (this.isInPlaceHolderMode()) {
            console.warn('Can not load FEN when in placeholder mode');
            return;
        }

        if (animate) {
            this.#drawPositionAnimated(fen);
        } else {
            this.#drawPositionNoAnimation(fen);
        }
    }

    #drawPositionNoAnimation(targetFen) {
        this.#hideAllPiecePlaceHolders();
        this.#hideHighlightedLastMove();
        this.#unDrawAllPieces();
        this.#board.loadFen(targetFen);
        this.#drawPieces();
        this.updateHighlightedChecks();
        this.#resetDraggableCursors();
        this.#forceSafariLayoutRefresh();
    }

    /**
     * Compute the target position (via a temporary board so we don't mutate the
     * current {@link Board} model before we're ready) and diff it against the
     * currently displayed pieces to build the animation plan.
     *
     * @param targetFen {string}
     */
    #drawPositionAnimated(targetFen) {
        this.#hideAllPiecePlaceHolders();
        this.#hideHighlightedLastMove();

        // compute the target piece layout via a temp board (no DOM side-effects,
        // and no mutation of the real model yet)
        const targetBoard = new Board();
        targetBoard.loadFen(targetFen);
        const targetPieces = targetBoard.listPiecePositions();

        this.#animatePiecesTo(targetPieces, 200, () => {
            // commit the new board state in the model
            this.#board.loadFen(targetFen);
            this.updateHighlightedChecks();
            this.#resetDraggableCursors();
            this.#afterDrawPositionsListeners.forEach(listener => listener());
        });
    }

    outputFen() {
        return this.#board.outputFen();
    }

    /**
     * Returns a copy of the underlying (non-GUI) {@link Board}, useful when
     * callers need to inspect/query the board model without touching the DOM.
     *
     * A copy is returned rather than the internal instance to preserve
     * encapsulation (so callers can't mutate the BoardGui's state directly).
     *
     * @return {Board}
     */
    get board() {
        return this.#board.copy();
    }

    /**
     * Change which color is to play next (doesn't alter the pieces on the board).
     *
     * @param color {string}
     */
    forceColorToPlay(color) {
        this.#board.forceColorToPlay(color);
    }

    get isPlayerMoveEnabled() {
        return this.#isPlayerMoveEnabled;
    }

    set isPlayerMoveEnabled(value) {
        if (value) {
            this.enablePlayerMove();
        } else {
            this.disablePlayerMove();
        }
    }

    disablePlayerMove() {
        this.#isPlayerMoveEnabled = false;
        this.#hideAllPiecePlaceHolders();
        this.#hideAllDraggableCursors();
    }

    enablePlayerMove() {
        this.#isPlayerMoveEnabled = true;
        this.#resetDraggableCursors();
    }

    /**
     * @param listener {function}
     */
    addAfterMoveListener(listener) {
        this.#afterMoveListeners.push(listener);
    }

    /**
     * @param listener {function}
     */
    addAfterDrawPositionsListener(listener) {
        this.#afterDrawPositionsListeners.push(listener);
    }

    /**
     * @param listener {function(string)}
     */
    addAfterFlipListener(listener) {
        this.#afterFlipListeners.push(listener);
    }

    clearAllAfterMovesListeners() {
        this.#afterMoveListeners = [];
    }

    /**
     * @param move {HalfMove}
     * @param highLastMove {boolean} if true, the last move will be highlighted with a green dashed circle
     * @param afterMoveCallback {function}
     */
    registerOpponentMove(move, highLastMove, afterMoveCallback) {
        this.#hideHighlightedLastMove();
        this.#animateMoveViaDiff(move, () => {
            if (highLastMove) {
                this.highlightLastMove(move);
            }
            this.updateHighlightedChecks();
            this.#resetDraggableCursors();
            if (this.#options.playSounds) {
                this.#clickSound
                    .play()
                    .catch(e => {
                        // ignored, spam error in console in dev
                    });
            }
            if (afterMoveCallback != null) {
                afterMoveCallback()
            }
        });
    }

    /**
     * Animate the DOM pieces from the currently displayed position (as
     * described by {@link #board}) to {@code targetPieces}. Does NOT mutate the
     * underlying board model; the caller is expected to update the model in
     * {@code onDone}.
     *
     * Diff strategy (mirrors lichess/chessground):
     *  - pieces of the same pieceChar on the same square: leave image alone
     *  - current-board misplacements and target-board misplacements of the
     *    same pieceChar: paired greedily by shortest distance and animated
     *    with a CSS transform transition
     *  - unpaired leftover current-board misplacements: faded out
     *  - unpaired leftover target-board misplacements: faded in
     *
     * @param targetPieces {PieceAtPosition[]}
     * @param durationMs {number}
     * @param onDone {function}
     */
    #animatePiecesTo(targetPieces, durationMs, onDone) {
        const currentPieces = this.#board.listPiecePositions();

        const posKey = p => `${p.toUci()}`;
        const currentMap = new Map(currentPieces.map(pp => [posKey(pp.position), pp]));
        const targetMap = new Map(targetPieces.map(pp => [posKey(pp.position), pp]));

        // pieces currently drawn on the board that don't match the target
        // position (either the target expects a different piece on that
        // square, or no piece at all). Each will be either animated to a
        // square in targetBoardMisplacements or faded out.
        /** @type {PieceAtPosition[]} */
        const currentBoardMisplacements = [];
        for (const [positionKey, currentPp] of currentMap) {
            const targetPp = targetMap.get(positionKey);
            if (targetPp && targetPp.piece.pieceChar === currentPp.piece.pieceChar) {
                // same piece on same square: leave image alone
                targetMap.delete(positionKey);
            } else {
                currentBoardMisplacements.push(currentPp);
            }
        }

        // pieces required by the target position that aren't already correctly
        // drawn on the current board (the square is empty or holds a different
        // piece). Each will be either the destination of an animated move from
        // currentBoardMisplacements or faded in as a new image.
        /** @type {PieceAtPosition[]} */
        const targetBoardMisplacements = Array.from(targetMap.values());

        // pair movers greedily by closest same-pieceChar distance
        const calculateDistance = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
        const candidatePairs = [];
        currentBoardMisplacements.forEach((c, i) => {
            targetBoardMisplacements.forEach((t, j) => {
                if (t.piece.pieceChar === c.piece.pieceChar) {
                    candidatePairs.push({i, j, distance: calculateDistance(c.position, t.position)});
                }
            });
        });
        candidatePairs.sort((a, b) => a.distance - b.distance);

        // indices into currentBoardMisplacements / targetBoardMisplacements that
        // have already been claimed by an animated move, so we don't pair the
        // same piece twice while walking the distance-sorted candidate list.
        /** @type {Set<number>} */
        const usedCurrent = new Set();
        /** @type {Set<number>} */
        const usedTarget = new Set();
        const animatedMoves = [];
        for (const {i, j} of candidatePairs) {
            if (!usedCurrent.has(i) && !usedTarget.has(j)) {
                usedCurrent.add(i);
                usedTarget.add(j);
                animatedMoves.push({
                    from: currentBoardMisplacements[i].position,
                    to: targetBoardMisplacements[j].position,
                    pieceChar: currentBoardMisplacements[i].piece.pieceChar,
                });
            }
        }
        const toRemove = currentBoardMisplacements.filter((_, i) => !usedCurrent.has(i));
        const toAdd = targetBoardMisplacements.filter((_, j) => !usedTarget.has(j));

        // nothing to animate: bail out early
        if (animatedMoves.length === 0 && toRemove.length === 0 && toAdd.length === 0) {
            onDone();
            return;
        }

        const transitionStr = `transform ${durationMs}ms ease, opacity ${durationMs}ms ease`;
        const getSquareRect = pos => document
            .getElementById(this.#positionToElementId('square', pos))
            .getBoundingClientRect();

        const cleanups = [];

        // animate movers: translate the source-square image toward the target square
        animatedMoves.forEach(animatedMove => {
            const img = document.getElementById(this.#positionToElementId('image', animatedMove.from));
            if (img == null) return;
            const fromRect = getSquareRect(animatedMove.from);
            const toRect = getSquareRect(animatedMove.to);
            const dx = toRect.left - fromRect.left;
            const dy = toRect.top - fromRect.top;

            // rename id so it doesn't conflict with drawPieceAt on the target square
            img.id = `animating-${Math.random().toString(36).slice(2)}`;
            img.style.transition = transitionStr;
            // must stay above .crosshair-square (z-index: 80) and resting
            // pieces (z-index: 100) so the moving piece isn't painted under
            // crosshairs of neighboring squares it flies over
            img.style.zIndex = '110';
            img.style.pointerEvents = 'none';

            // trigger on next frame so the transition actually runs
            requestAnimationFrame(() => {
                img.style.transform = `translate(${dx}px, ${dy}px)`;
            });

            cleanups.push(() => img.remove());
        });

        // fade out leftover current pieces (captures / removals)
        toRemove.forEach(rm => {
            const img = document.getElementById(this.#positionToElementId('image', rm.position));
            if (img == null) return;
            img.id = `fading-${Math.random().toString(36).slice(2)}`;
            img.style.transition = transitionStr;
            img.style.pointerEvents = 'none';
            requestAnimationFrame(() => {
                img.style.opacity = '0';
            });
            cleanups.push(() => img.remove());
        });

        // fade in leftover target pieces (newly placed on a previously empty square)
        const addedImages = [];
        toAdd.forEach(add => {
            this.#drawPieceAt(add.piece.pieceChar, add.position);
            const img = document.getElementById(this.#positionToElementId('image', add.position));
            if (img != null) {
                img.style.opacity = '0';
                img.style.transition = transitionStr;
                addedImages.push(img);
                requestAnimationFrame(() => {
                    img.style.opacity = '1';
                });
            }
        });

        setTimeout(() => {
            // drop transient animation images
            cleanups.forEach(c => c());

            // draw the final image at each move's target square
            animatedMoves.forEach(mv => {
                const existing = document.getElementById(this.#positionToElementId('image', mv.to));
                if (existing != null) existing.remove();
                this.#drawPieceAt(mv.pieceChar, mv.to);
            });

            // clear transient styles on faded-in images
            addedImages.forEach(img => {
                img.style.transition = '';
                img.style.opacity = '';
            });

            onDone();
            this.#forceSafariLayoutRefresh();
        }, durationMs + 20);
    }

    /**
     * @param move {HalfMove}
     * @param animate {boolean}
     */
    registerMoveIfLegal(move, animate = true) {
        if (!this.#board.isLegalMove(move)) {
            console.log(move + ' is not a legal move');
            return;
        }

        const afterCommit = () => {
            this.updateHighlightedChecks();
            this.#resetDraggableCursors();
            for (let i = 0; i < this.#afterMoveListeners.length; i++) {
                this.#afterMoveListeners[i](move);
            }
        };

        if (animate) {
            // the diff-based animator will commit the model via its onDone
            this.#animateMoveViaDiff(move, afterCommit);
        } else {
            const piece = this.#board.getPieceAt(move.from);
            this.#board.registerMove(move);
            this.#drawMove(piece.pieceChar, move);
            afterCommit();
        }
    }

    /**
     * Animate a single move by delegating to the generic diff-based animator
     * {@link #animatePiecesTo}. The target position is computed on a copy of
     * the current board so the real model is only mutated once the animation
     * completes. This replaces the old xiangqi-shape-aware setInterval based
     * {@code #animateMove}.
     *
     * @param move {HalfMove}
     * @param onDone {function}
     */
    #animateMoveViaDiff(move, onDone) {
        const targetBoard = this.#board.copy();
        targetBoard.registerMove(move);
        const targetPieces = targetBoard.listPiecePositions();
        this.#animatePiecesTo(targetPieces, 200, () => {
            this.#board.registerMove(move);
            onDone();
        });
    }

    /**
     *
     * @param pieceChar {string}
     * @param position {Position}
     * @param enforcePlacementRules {boolean}
     */
    addPieceAt(pieceChar, position, enforcePlacementRules) {
        this.#board.addPieceAt(pieceChar, position, enforcePlacementRules);
        this.#unDrawPiece(position);
        this.#drawPieceAt(pieceChar, position);
    }

    /**
     * @param position {Position}
     */
    removePieceFrom(position) {
        this.#board.removePieceFrom(position);
        this.#unDrawPiece(position);
    }

    // FIXME: feels like this could be private? encapsulated?
    updateHighlightedChecks() {
        this.#hideAllCheckHighlights();

        if (!this.#options.mini || this.#options.forceRenderChecks) {
            const redGeneral = this.#board.findGeneral(Color.RED);
            const blackGeneral = this.#board.findGeneral(Color.BLACK);

            // generals may be absent (e.g. when flip() is called before any
            // FEN has been loaded, on an empty board); nothing to highlight.
            if (redGeneral == null || blackGeneral == null) {
                return;
            }

            if (this.#board.isInCheck(Color.RED)) {
                if (this.#board.isCheckmate(Color.RED)) {
                    this.disablePlayerMove();
                    this.#hideHighlightedLastMove();
                    this.#highlightCheckMate(redGeneral.position);
                } else {
                    this.#highlightCheck(redGeneral.position);
                }
            }

            if (this.#board.isInCheck(Color.BLACK)) {
                if (this.#board.isCheckmate(Color.BLACK)) {
                    this.disablePlayerMove();
                    this.#hideHighlightedLastMove();
                    this.#highlightCheckMate(blackGeneral.position);
                } else {
                    this.#highlightCheck(blackGeneral.position);
                }
            }
        }
    }

    /**
     * @param move {HalfMove}
     */
    highlightLastMove(move) {
        this.#highlightWithClass(move.from, 'highlighted-last-move');
        this.#highlightWithClass(move.to, 'highlighted-last-move');
    }

    #hideAllCheckHighlights() {
        Position
            .getAll()
            .map(position => this.#locateLegalMovePlaceHolderAt(position))
            .map(placeHolder => placeHolder.classList)
            .forEach(classList => classList.remove('highlighted-check', 'highlighted-checkmate'));
    }

    #hideHighlightedLastMove() {
        Position
            .getAll()
            .map(position => this.#locateLegalMovePlaceHolderAt(position))
            .map(placeHolder => placeHolder.classList)
            .forEach(classList => classList.remove('highlighted-last-move'));
    }

    /**
     * @param position {Position}
     */
    #highlightCheck(position) {
        this.#highlightWithClass(position, 'highlighted-check');
    }

    /**
     * @param position {Position}
     */
    #highlightCheckMate(position) {
        this.#highlightWithClass(position, 'highlighted-checkmate');
    }

    /**
     * @param position {Position}
     * @param className {string}
     */
    #highlightWithClass(position, className) {
        this.#locateLegalMovePlaceHolderAt(position).classList.add(className);
    }

    /**
     * @returns {boolean}
     */
    isInPlaceHolderMode() {
        return this.#boardContainer.classList.contains('board-container-placeholder');
    }

    enablePlaceholderMode() {
        this.#boardContainer.classList.add('board-container-placeholder');
    }

    disablePlaceholderMode() {
        this.#boardContainer.classList.remove('board-container-placeholder');
    }

    /**
     * Clear the SVG layer, which atm only render analytics arrows.
     */
    clearSvg() {
        if (this.#options.svg) {
            const svg = document.getElementById('board-svg');
            svg.innerHTML = '';
            svg.innerHTML += ARROW_MARKERS;
        }
    }

    /**
     * @param move {HalfMove}
     * @param type {string}
     */
    addEngineArrow(move, type) {
        if (this.#options.svg) {
            switch (type) {
                case EngineArrowType.PRIMARY:
                    this.#primaryEngineArrow = move;
                    break;
                case EngineArrowType.SECONDARY:
                    this.#secondaryEngineArrow = move;
                    break;
            }

            this.#renderSvg();
        }
    }

    #renderSvg() {
        if (this.#options.svg) {
            this.clearSvg();
            const svg = document.getElementById('board-svg');
            if (this.#primaryEngineArrow != null) {
                svg.append(...this.#buildMoveArrow(this.#primaryEngineArrow, EngineArrowType.PRIMARY));
            }
            if (this.#secondaryEngineArrow != null) {
                svg.append(...this.#buildMoveArrow(this.#secondaryEngineArrow, EngineArrowType.SECONDARY));
            }
        }
    }

    /**
     * @param move {HalfMove}
     * @param type {string}
     * @returns {SVGGeometryElement[]}
     */
    #buildMoveArrow(move, type) {
        function isSmallMove(move) {
            return (move.isVertical() && Math.abs(move.to.y - move.from.y) <= 1) ||
                (move.isHorizontal() && Math.abs(move.to.x - move.from.x) <= 1)
        }

        function isKnightMove(move) {
            const dx = Math.abs(move.to.x - move.from.x);
            const dy = Math.abs(move.to.y - move.from.y);
            return (dx === 1 && dy === 2) || (dx === 2 && dy === 1);
        }

        const boardBounds = this.#boardContainer.getBoundingClientRect();

        const square1 = document.getElementById(this.#positionToElementId('square', move.from));
        const square2 = document.getElementById(this.#positionToElementId('square', move.to));
        const bound1 = square1.getBoundingClientRect();
        const bound2 = square2.getBoundingClientRect();

        const x1 = (bound1.left + bound1.width / 2) - boardBounds.left;
        const y1 = (bound1.top + bound1.height / 2) - boardBounds.top;
        const x2 = (bound2.left + bound2.width / 2) - boardBounds.left;
        const y2 = (bound2.top + bound2.height / 2) - boardBounds.top;

        // stroke width scales with the square size so the arrow looks consistent across viewports
        // (a fixed thick stroke is proportionally too fat on smaller boards, making the elbow look off-centered)
        const strokeWidth = Math.min(16, bound1.width * 0.16);

        let pathD, midpointX, midpointY;

        if (isKnightMove(move)) {
            // draw an elbowed arrow matching the horse's actual movement:
            //   segment 1: orthogonal (straight line, 1 square) — the horse's "leg"
            //   segment 2: diagonal (1 square) — the horse's diagonal step
            const sourceReduction = bound1.width * 0.40;
            // the arrow-head marker (path "M0,0 V4 L2,2", refX=0.1) tips at 1.9 marker units past the
            // path endpoint, and markerUnits defaults to strokeWidth — so to make the tip land exactly
            // on the destination intersection, we shorten the destination end by 1.9 * strokeWidth.
            const destReduction = strokeWidth * 1.9;
            const dxPx = x2 - x1;
            const dyPx = y2 - y1;

            // Returns the point shortened by `r` pixels along the line from (px,py) toward (tx,ty)
            function shortenToward(px, py, tx, ty, r) {
                const dx = tx - px;
                const dy = ty - py;
                const d = Math.sqrt(dx * dx + dy * dy);
                return [px + (dx / d) * r, py + (dy / d) * r];
            }

            let x1Prime, y1Prime, x2Prime, y2Prime, elbowX, elbowY;

            const dyBoardSquares = Math.abs(move.to.y - move.from.y);

            if (dyBoardSquares === 2) {
                // vertical-dominant: horse moves 2 squares vertically then 1 diagonally
                // elbow is exactly 1 square-height above/below the source, derived from the actual pixel displacement
                elbowX = x1;
                elbowY = y1 + dyPx / 2;

                [x1Prime, y1Prime] = shortenToward(x1, y1, elbowX, elbowY, sourceReduction);
                [x2Prime, y2Prime] = shortenToward(x2, y2, elbowX, elbowY, destReduction);
            } else {
                // horizontal-dominant: horse moves 2 squares horizontally then 1 diagonally
                // elbow is exactly 1 square-width left/right of the source, derived from the actual pixel displacement
                elbowX = x1 + dxPx / 2;
                elbowY = y1;

                [x1Prime, y1Prime] = shortenToward(x1, y1, elbowX, elbowY, sourceReduction);
                [x2Prime, y2Prime] = shortenToward(x2, y2, elbowX, elbowY, destReduction);
            }

            pathD = `M${Math.round(x1Prime)},${Math.round(y1Prime)} L${Math.round(elbowX)},${Math.round(elbowY)} L${Math.round(x2Prime)},${Math.round(y2Prime)}`;
            midpointX = elbowX;
            midpointY = elbowY;
        } else {
            const dist = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

            // we reduce the distance, otherwise the arrow goes from the middle of a square to the other (with the arrow tip on top)
            let reduction;

            // we can not reduce by as much when the arrow is already very small
            // otherwise, the direction of the arrow flips and points the wrong way
            if (isSmallMove(move)) {
                // we remove 40% a square length
                reduction = bound1.width * 0.40;
            } else {
                // we remove 50% a square length
                reduction = bound1.width * 0.50;
            }

            const reducedDist = dist - reduction;
            const ratio = reducedDist / dist;

            const x1Prime = x2 + ratio * (x1 - x2);
            const y1Prime = y2 + ratio * (y1 - y2);
            const x2Prime = x1 + ratio * (x2 - x1);
            const y2Prime = y1 + ratio * (y2 - y1);

            pathD = `M${Math.round(x1Prime)},${Math.round(y1Prime)} L${Math.round(x2Prime)},${Math.round(y2Prime)}`;
            midpointX = (x1Prime + x2Prime) / 2;
            midpointY = (y1Prime + y2Prime) / 2;
        }

        const arrowPath = document.createElementNS("http://www.w3.org/2000/svg", "path");
        arrowPath.setAttribute('d', pathD);
        arrowPath.style.strokeWidth = `${strokeWidth}px`;
        arrowPath.style.fill = 'none';
        arrowPath.style.strokeLinejoin = 'round';

        switch (type) {
            case EngineArrowType.PRIMARY:
                arrowPath.style.stroke = PRIMARY_ARROW_COLOR;
                arrowPath.style.markerEnd = 'url(#head-primary)';
                break;
            case EngineArrowType.SECONDARY:
                arrowPath.style.stroke = SECONDARY_ARROW_COLOR;
                arrowPath.style.markerEnd = 'url(#head-secondary)';
                break;
        }

        // draw number circle
        const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
        circle.setAttribute('r', (bound1.width * 0.20).toString());
        circle.setAttribute('cx', midpointX.toString());
        circle.setAttribute('cy', midpointY.toString());
        circle.setAttribute('fill', 'white');
        circle.setAttribute('stroke-width', (bound1.width * 0.04).toString());

        switch (type) {
            case EngineArrowType.PRIMARY:
                circle.setAttribute('stroke', PRIMARY_ARROW_COLOR);
                break;
            case EngineArrowType.SECONDARY:
                circle.setAttribute('stroke', SECONDARY_ARROW_COLOR);
                break;
        }

        // draw number text
        const numberLabel = document.createElementNS("http://www.w3.org/2000/svg", "text");
        numberLabel.setAttribute('x', midpointX.toString());
        numberLabel.setAttribute('y', midpointY.toString());
        numberLabel.setAttribute('text-anchor', 'middle');
        numberLabel.setAttribute('dominant-baseline', 'middle');
        numberLabel.setAttribute('font-size', (bound1.width * 0.25).toString() + 'px');
        numberLabel.setAttribute('font-weight', 'bold');

        switch (type) {
            case EngineArrowType.PRIMARY:
                numberLabel.setAttribute('fill', PRIMARY_ARROW_COLOR);
                numberLabel.innerHTML = '1';
                break;
            case EngineArrowType.SECONDARY:
                numberLabel.setAttribute('fill', SECONDARY_ARROW_COLOR);
                numberLabel.innerHTML = '2';
                break;
        }

        return [arrowPath, circle, numberLabel];
    }

    #handlePieceHolderClickEvent(e, position) {
        let onlyClickedSquareAndNoImage = true;

        let images = document.getElementsByClassName('piece-image');
        for (let i = 0; i < images.length; i++) {
            if (images[i].contains(e.target)) {
                onlyClickedSquareAndNoImage = false;
                break;
            }
        }

        if (onlyClickedSquareAndNoImage) {
            this.#clickedOnSquare(position);
        }
    }

    #clickedOnPiece(position) {
        if (this.#isPlayerMoveEnabled) {
            let board = this.#board;
            let selected = this.#selectedPiecePosition;

            if (selected == null && board.isAllowedToPlayPieceAt(position)) {
                // user is selecting the piece
                this.#showSelectedPositionAndLegalMovesPlaceHolders(position);
            } else if (Position.areEquals(selected, position)) {
                // if the piece user just clicked on is the same as the one he had selected before,
                // then user is un-selecting the piece
                this.#hideAllPiecePlaceHolders();
            } else if (selected != null && board.containOppositeColors(selected, position)) {
                // user is capturing the piece he clicked on
                this.registerMoveIfLegal(new HalfMove(selected, position));
                this.#hideAllPiecePlaceHolders();
            } else if (selected != null && board.containSameColors(selected, position) && board.isAllowedToPlayPieceAt(position)) {
                // user is selecting another piece
                this.#showSelectedPositionAndLegalMovesPlaceHolders(position);
            }
        }
    }

    #clickedOnSquare(position) {
        if (this.#isPlayerMoveEnabled) {
            if (this.#selectedPiecePosition != null) {
                this.registerMoveIfLegal(new HalfMove(this.#selectedPiecePosition, position));
                this.#hideAllPiecePlaceHolders();
            }
        }
    }

    /**
     * @param pieceChar {string}
     * @param move {HalfMove}
     */
    #drawMove(pieceChar, move) {
        this.#unDrawPiece(move.from);
        this.#unDrawPiece(move.to);
        this.#drawPieceAt(pieceChar, move.to);
    }

    #drawBoard() {
        /**
         * @param positionType {string} - one of 'bottom-right', 'bottom-left', 'top-right', 'top-left'
         * @param drawingPosition {Position}
         * @param positions {Position[]}
         * @returns {HTMLDivElement|null}
         */
        function drawCrosshairForPositionType(positionType, drawingPosition, positions) {
            const crosshairToDraw = positions.find(crosshair => Position.areEquals(crosshair, drawingPosition));
            if (crosshairToDraw != null) {
                const crosshair = document.createElement('div');
                crosshair.classList.add('crosshair-square', `crosshair-square-${positionType}`);
                return crosshair;
            } else {
                return null;
            }
        }

        /**
         * @param drawingPosition {Position}
         * @returns {HTMLDivElement[]}
         */
        function drawCrosshairs(drawingPosition) {
            const crosshairTypes = [
                {type: 'bottom-right', positions: bottomRightCrosshairs},
                {type: 'bottom-left', positions: bottomLeftCrosshairs},
                {type: 'top-right', positions: topRightCrosshairs},
                {type: 'top-left', positions: topLeftCrosshairs}
            ];

            return crosshairTypes
                .map(({type, positions}) => drawCrosshairForPositionType(type, drawingPosition, positions))
                .filter(crosshair => crosshair !== null);
        }

        for (let y = BOARD_HEIGHT - 1; y >= 0; y--) {
            // draw rows
            let row = document.createElement('div');
            row.className = 'rows';

            for (let x = 0; x < BOARD_WIDTH; x++) {
                let renderPosition;
                if (this.#flippedRed) {
                    renderPosition = new Position(x, y);
                } else {
                    renderPosition = new Position(BOARD_WIDTH - x - 1, BOARD_HEIGHT - y - 1);
                }

                const pieceHolder = document.createElement('div');
                pieceHolder.id = this.#positionToElementId('square', renderPosition);
                pieceHolder.className = 'piece-holder';
                pieceHolder.addEventListener('click', (e) => {
                    this.#handlePieceHolderClickEvent(e, renderPosition)
                });

                // drag and drop listeners
                pieceHolder.addEventListener('dragover', (e) => e.preventDefault());
                pieceHolder.addEventListener('dragenter', (e) => e.preventDefault());
                pieceHolder.addEventListener('drop', (e) => {
                    this.#handlePieceHolderDropEvent(e, renderPosition);
                });

                const legalMovePlaceHolder = document.createElement('div');
                legalMovePlaceHolder.id = this.#positionToElementId('legal_move', renderPosition);
                pieceHolder.appendChild(legalMovePlaceHolder);

                if (y === 5) {
                    if (x === 0) {
                        const river = buildDivWithClass('large-river');

                        if (!this.#options.mini) {
                            const riverOfTheChuContainer = buildDivWithClass('river-of-the-chu');
                            riverOfTheChuContainer.append(buildImg(`${this.#options.assetsBaseUrl}/images/river-of-the-chu-brown.png`));

                            const borderOfTheHanContainer = buildDivWithClass('border-of-the-han');
                            borderOfTheHanContainer.append(buildImg(`${this.#options.assetsBaseUrl}/images/border-of-the-han-brown.png`));

                            river.append(
                                riverOfTheChuContainer,
                                borderOfTheHanContainer
                            );
                        }

                        pieceHolder.append(river);
                    }
                } else {
                    // draw row of visible squares
                    if (x < BOARD_WIDTH - 1 && y > 0) {
                        const visibleSquare = buildDivWithClass('visible-square');
                        pieceHolder.appendChild(visibleSquare);

                        if ((x === 3 && y === 2) || (x === 4 && y === 1) || (x === 3 && y === 9) || (x === 4 && y === 8)) {
                            if (this.#options.mini) {
                                visibleSquare.innerHTML = diagonalDescendingMini;
                            } else {
                                visibleSquare.innerHTML = diagonalDescending;
                            }
                        }
                        if ((x === 3 && y === 1) || (x === 4 && y === 2) || (x === 3 && y === 8) || (x === 4 && y === 9)) {
                            if (this.#options.mini) {
                                visibleSquare.innerHTML = diagonalRisingMini;
                            } else {
                                visibleSquare.innerHTML = diagonalRising;
                            }
                        }

                        if (x === BOARD_WIDTH - 2) {
                            visibleSquare.classList.add('visible-square-last-file');
                        }

                        // draw crosshairs
                        drawCrosshairs(new Position(x, y))
                            .forEach(element => visibleSquare.append(element));
                    }
                }
                row.appendChild(pieceHolder);
            }
            this.#boardContainer.appendChild(row);
        }

        // add bottom border to last row of visible squares
        if (this.#flippedRed) {
            for (let x = 0; x < BOARD_WIDTH - 1; x++) {
                const position = new Position(x, 1);
                const square = document.getElementById(this.#positionToElementId('square', position));
                square.getElementsByClassName('visible-square')[0].classList.add('visible-square-last-row');
            }
        } else {
            for (let x = BOARD_WIDTH - 1; x > 0; x--) {
                const position = new Position(x, BOARD_HEIGHT - 2);
                const square = document.getElementById(this.#positionToElementId('square', position));
                square.getElementsByClassName('visible-square')[0].classList.add('visible-square-last-row');
            }
        }

        this.#drawCoordinates();

        // TODO: hacky
        if (this.#isSafari) {
            let rows = document.getElementsByClassName('rows');
            for (let i = 0; i < rows.length; i++) {
                rows[i].classList.add('safari-rows');
            }

            document.getElementById(this.#options.elementId).classList.add('safari-board-container');
        }

        if (this.#options.svg) {
            const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
            svg.id = 'board-svg';
            this.#boardContainer.append(svg);
        }

        this.#forceSafariLayoutRefresh();
    }

    // https://stackoverflow.com/questions/9628507/how-can-i-tell-google-translate-to-not-translate-a-section-of-a-website
    #drawCoordinates() {
        function buildFileDiv(label, cssClass, visible) {
            let span = document.createElement('span');
            span.classList.add('coordinates-labels', 'notranslate');
            span.innerHTML = label;
            if (visible) {
                span.style.visibility = 'visible';
            } else {
                span.style.visibility = 'hidden';
            }

            let div = document.createElement('div');
            div.className = cssClass;
            div.appendChild(span);
            return div;
        }

        if (this.#options.showCoordinates) {
            const orientation = this.#options.coordinatesOrientation;
            const isSettingEnabled = orientation !== null;
            // when the user has disabled coordinates we still reserve the space (hidden labels),
            // so we must pick an arbitrary orientation for the (invisible) labels:
            const isWfxOriented = orientation !== CoordinatesOrientation.ALGEBRAIC;

            const fileNumbersStyle = this.#options.fileNumbersStyle;
            // For a given side ('red' | 'black') and its screen position ('top' | 'bottom'),
            // should we render Chinese numerals?
            const isChineseOnSide = (side, position) => {
                switch (fileNumbersStyle) {
                    case FileNumbersStyle.ARABIC_BOTH:
                        return false;
                    case FileNumbersStyle.CHINESE_BOTH:
                        return true;
                    case FileNumbersStyle.CHINESE_BLACK_ONLY:
                        return side === 'black';
                    case FileNumbersStyle.CHINESE_LOWER_ONLY:
                        return position === 'bottom';
                    case FileNumbersStyle.CHINESE_TOP_ONLY:
                        return position === 'top';
                    case FileNumbersStyle.CHINESE_RED_ONLY:
                    default:
                        return side === 'red';
                }
            };

            // draw top file coordinates
            if (isWfxOriented) {
                let topCoordinatesY = BOARD_HEIGHT - 1;
                if (!this.#flippedRed) {
                    topCoordinatesY = 0;
                }
                // top row shows black's side when red is at the bottom, and red's side when flipped
                const topSide = this.#flippedRed ? 'black' : 'red';
                const topChinese = isChineseOnSide(topSide, 'top');

                for (let x = 0; x < BOARD_WIDTH; x++) {
                    // actual text
                    let label;
                    if (this.#flippedRed) {
                        // top row: file 1 is on the right (black perspective)
                        label = topChinese ? CHINESE_FILE_DIGITS[x] : (x + 1).toString();
                    } else {
                        // top row: file 1 is on the left (board flipped, red on top)
                        label = topChinese
                            ? CHINESE_FILE_DIGITS[BOARD_WIDTH - x - 1]
                            : (BOARD_WIDTH - x).toString();
                    }

                    let div = buildFileDiv(label, 'file-coordinates-top', isSettingEnabled);
                    let squareId = this.#positionToElementId('square', new Position(x, topCoordinatesY));
                    document.getElementById(squareId).appendChild(div);
                }
            }

            // draw bottom file coordinates
            let bottomCoordinatesY = 0;
            if (!this.#flippedRed) {
                bottomCoordinatesY = BOARD_HEIGHT - 1;
            }
            // bottom row shows red's side when red is at the bottom, and black's side when flipped
            const bottomSide = this.#flippedRed ? 'red' : 'black';
            const bottomChinese = isChineseOnSide(bottomSide, 'bottom');
            for (let x = 0; x < BOARD_WIDTH; x++) {
                // actual text
                let label;
                if (isWfxOriented) {
                    if (this.#flippedRed) {
                        // bottom row: file 1 is on the left (red perspective)
                        label = bottomChinese
                            ? CHINESE_FILE_DIGITS[BOARD_WIDTH - x - 1]
                            : (BOARD_WIDTH - x).toString();
                    } else {
                        // bottom row: file 1 is on the right (board flipped, black at bottom)
                        label = bottomChinese ? CHINESE_FILE_DIGITS[x] : (x + 1).toString();
                    }
                } else {
                    label = UCI_LETTER[x];
                }

                let div = buildFileDiv(label, 'file-coordinates-bottom', isSettingEnabled);
                let squareId = this.#positionToElementId('square', new Position(x, bottomCoordinatesY));
                document.getElementById(squareId).appendChild(div);
            }

            // draw right-side row coordinates
            if (!isWfxOriented) {
                let rightCoordinatesX = BOARD_WIDTH - 1;
                if (!this.#flippedRed) {
                    rightCoordinatesX = 0;
                }
                for (let y = 0; y < BOARD_HEIGHT; y++) {
                    let span = document.createElement('span');
                    span.classList.add('coordinates-labels', 'notranslate');
                    span.innerHTML = (y + 1).toString();
                    if (isSettingEnabled) {
                        span.style.visibility = 'visible';
                    } else {
                        span.style.visibility = 'hidden';
                    }

                    let div = document.createElement('div');
                    div.className = 'rows-coordinates-right';
                    div.appendChild(span);
                    let squareId = this.#positionToElementId('square', new Position(rightCoordinatesX, y));
                    document.getElementById(squareId).appendChild(div);
                }
            }
        }
    }

    #drawPieces() {
        this.#board
            .listPiecePositions()
            .forEach(piecePosition => this.#drawPiece(piecePosition));
    }

    /**
     * Resolves the URL of the image for a given piece char, using this board's
     * configured `assetsBaseUrl` and `pieceStyle` options. Exposed so external
     * widgets that share this board's visual identity (e.g. the position
     * editor's piece palette) can use the exact same image set.
     *
     * @param pieceChar {string}
     * @return {string}
     */
    getPieceImageSource(pieceChar) {
        const style = this.#options.pieceStyle.toLowerCase();
        return `${this.#options.assetsBaseUrl}/images/pieces/${style}/${pieceImageNames.get(pieceChar)}`;
    }

    /**
     * @param piecePosition {PieceAtPosition}
     */
    #drawPiece(piecePosition) {
        this.#drawPieceAt(piecePosition.piece.pieceChar, piecePosition.position);
    }

    /**
     * @param pieceChar {string}
     * @param position {Position}
     */
    #drawPieceAt(pieceChar, position) {
        const square = document.getElementById(this.#positionToElementId('square', position));
        const img = document.createElement('img');
        img.id = this.#positionToElementId('image', position);
        img.className = 'piece-image';
        if (isBlackPiece(pieceChar)) {
            img.classList.add('piece-image-black');
        } else if (isRedPiece(pieceChar)) {
            img.classList.add('piece-image-red');
        }
        img.setAttribute('src', this.getPieceImageSource(pieceChar));
        img.addEventListener('click', () => this.#clickedOnPiece(position));
        img.addEventListener('dragstart', (e) => this.#dragStart(e, position));
        img.addEventListener('dragend', () => this.#dragEnd());
        square.prepend(img);
    }

    /**
     * Forces Safari to recalculate layout and positioning of board elements.
     *
     * Safari has known issues with layout calculations, particularly with CSS transforms,
     * absolute positioning, and element positioning after DOM updates. This method applies
     * various techniques to force Safari to trigger reflows and recalculate element positions.
     *
     * The method uses multiple approaches:
     * - Hardware acceleration triggers via translateZ(0) transforms
     * - Forced reflows by accessing offsetHeight
     * - Position property manipulation on squares and piece images
     *
     * Called automatically after board state changes like moves, flips, and piece placement
     * to ensure visual elements are correctly positioned in Safari.
     *
     * @private
     */
    #forceSafariLayoutRefresh() {
        if (this.#isSafari) {
            setTimeout(() => {
                const container = this.#boardContainer;

                // force container reflow
                container.style.transform = 'translateZ(0)';
                container.offsetHeight;
                container.style.transform = '';

                // force reflow on all squares
                container
                    .querySelectorAll('.piece-holder')
                    .forEach(square => {
                        square.style.position = 'relative';
                        square.offsetHeight;
                        square.style.position = '';
                    });

                // force reflow on all piece images
                container
                    .querySelectorAll('.piece-image')
                    .forEach(piece => {
                        piece.style.transform = 'translateZ(0)';
                        piece.offsetHeight;
                        piece.style.transform = '';
                    });
            }, 0);
        }
    }

    /**
     * @param e {DragEvent}
     * @param position {Position}
     */
    #dragStart(e, position) {
        console.log('drag start');

        if (this.isPlayerMoveEnabled && this.#board.getColorAt(position) === this.#board.getColorToPlay()) {
            e.dataTransfer.setData('text/plain', position.toUci());
            this.#showSelectedPositionAndLegalMovesPlaceHolders(position);
        } else {
            e.preventDefault();
        }
    }

    #dragEnd() {
        console.log('drag end');

        this.#hideAllPiecePlaceHolders();
    }

    /**
     * @param e {DragEvent}
     * @param to {Position}
     */
    #handlePieceHolderDropEvent(e, to) {
        const uci = e.dataTransfer.getData('text/plain');
        const from = Position.parseUci(uci);
        const move = new HalfMove(from, to);
        this.registerMoveIfLegal(move, false);
        this.#hideAllPiecePlaceHolders();
    }

    #unDrawAllPieces() {
        Position.getAll().forEach(position => this.#unDrawPiece(position));
    }

    /**
     * @param position {Position}
     */
    #unDrawPiece(position) {
        const square = document.getElementById(this.#positionToElementId('square', position));
        // NB: getElementsByClassName returns a live HTMLCollection; convert to a
        // static array so removing elements doesn't skip siblings.
        htmlCollectionToArray(square.getElementsByClassName('piece-image'))
            .forEach(img => square.removeChild(img));
    }

    #hideAllPiecePlaceHolders() {
        Position.getAll().forEach(position => {
            // in case when the board is a mini board overview on an infinite scroll page
            // the placeholder can be null if the miniboard has been discarded
            // which would throw a bunch of errors in the console if we don't check for nullability
            const placeHolder = this.#locateLegalMovePlaceHolderAt(position);
            if (placeHolder != null) {
                placeHolder.classList.remove(
                    'legal-move-place-holder',
                    'selected-piece',
                    'possible-capture',
                    'highlighted-last-move'
                );
            }
        });

        this.#selectedPiecePosition = null;
        this.#currentShowingLegalMovesFor = null;
    }

    #showSelectedPositionAndLegalMovesPlaceHolders(position) {
        // remove previous placeholders
        this.#hideAllPiecePlaceHolders();

        // show selected piece
        this.#locateLegalMovePlaceHolderAt(position).classList.add('selected-piece');
        this.#selectedPiecePosition = position;

        // show legal moves and possible captures
        this.#showLegalMovesPlaceHolders(position);
    }

    /**
     * @param position {Position}
     */
    #showLegalMovesPlaceHolders(position) {
        this
            .#board
            .listLegalMovesFrom(position)
            .map(move => move.to)
            .forEach(targetPosition => {
                let placeHolder = this.#locateLegalMovePlaceHolderAt(targetPosition);
                if (this.#board.containOppositeColors(position, targetPosition)) {
                    placeHolder.classList.add('possible-capture');
                } else {
                    placeHolder.classList.add('legal-move-place-holder');
                }
            });

        this.#currentShowingLegalMovesFor = position;
    }

    highlightDebugMove(move, color) {
        let from = this.#locateLegalMovePlaceHolderAt(move.from);
        let to = this.#locateLegalMovePlaceHolderAt(move.to);
        from.classList.add('highlighted-debug');
        from.style.backgroundColor = color;
        to.classList.add('highlighted-debug');
        to.style.backgroundColor = color;
    }

    hideAllDebugHighlight() {
        Position.getAll().forEach(position => {
            let element = this.#locateLegalMovePlaceHolderAt(position);
            element.classList.remove('highlighted-debug');
            element.style.backgroundColor = null;
        });
    }

    highlightDynamicMove(move) {
        this.#hideAllPiecePlaceHolders();
        let from = this.#locateLegalMovePlaceHolderAt(move.from);
        let to = this.#locateLegalMovePlaceHolderAt(move.to);
        from.classList.add('highlighted-move');
        to.classList.add('highlighted-move');
    }

    hideAllHighlightedDynamicMoves() {
        Position.getAll().forEach(position => {
            let element = this.#locateLegalMovePlaceHolderAt(position);
            element.classList.remove('highlighted-move');
        });
    }

    clearBoard() {
        this.#unDrawAllPieces();
        this.#hideAllPiecePlaceHolders();
        this.#board.clearBoard();
    }

    /**
     * @param color {string|null}
     */
    flipToColor(color) {
        if (color != null && ((this.#flippedRed && color === Color.BLACK) || (!this.#flippedRed && color === Color.RED))) {
            this.flip();
        }
    }

    /**
     * Returns the color currently shown at the bottom of the board.
     *
     * @return {string}
     */
    get bottomColor() {
        return this.#flippedRed ? Color.RED : Color.BLACK;
    }

    flip() {
        this.#boardContainer.innerHTML = '';
        this.#flippedRed = !this.#flippedRed;
        this.#drawBoard();
        this.#drawPieces();
        this.updateHighlightedChecks();
        this.#resetDraggableCursors();
        this.#renderFlipOpponentPiecesSetting(this.#options.flipOpponentPieces);

        const newColor = this.#flippedRed ? Color.RED : Color.BLACK;
        this.#afterFlipListeners.forEach(listener => listener(newColor));
        this.#forceSafariLayoutRefresh();
    }

    /**
     *
     * @param moveFormat {string}
     */
    updateMoveFormat(moveFormat) {
        // easy solution: complete redraw (TODO: can more subtle)
        this.#boardContainer.innerHTML = '';
        this.#drawBoard();
        this.#drawPieces();
    }

    reRenderPieces() {
        this.#unDrawAllPieces();
        this.#drawPieces();
    }

    /**
     * Update the piece style and re-render the pieces. Needed because the
     * options object is otherwise frozen and the piece style can be changed at
     * runtime by the user (via the settings menu).
     *
     * @param pieceStyle {string} one of {@link PieceStyleSetting}
     */
    updatePieceStyle(pieceStyle) {
        if (this.#options.pieceStyle === pieceStyle) {
            return;
        }
        this.#options = Object.freeze({...this.#options, pieceStyle});
        this.reRenderPieces();
    }

    /**
     * @param enabled {boolean}
     */
    setColorblindFriendlyBlackPiecesEnabled(enabled) {
        if (this.#options.colorblindFriendlyBlackPieces === enabled) {
            return;
        }
        this.#options = Object.freeze({...this.#options, colorblindFriendlyBlackPieces: enabled});
        this.#renderColorblindFriendlyBlackPiecesSetting(enabled);
    }

    /**
     * @param enabled {boolean}
     */
    setFlipOpponentPiecesEnabled(enabled) {
        if (this.#options.flipOpponentPieces === enabled) {
            return;
        }
        this.#options = Object.freeze({...this.#options, flipOpponentPieces: enabled});
        this.#renderFlipOpponentPiecesSetting(enabled);
    }

    /**
     * @param playSoundsEnabled {boolean}
     */
    updatePlaySounds(playSoundsEnabled) {
        this.#options = Object.freeze({...this.#options, playSounds: playSoundsEnabled});
    }

    /**
     * Change which numeral system is used for file coordinates in WXF mode.
     *
     * @param fileNumbersStyle {string} one of {@link FileNumbersStyle}
     */
    setFileNumbersStyle(fileNumbersStyle) {
        if (this.#options.fileNumbersStyle === fileNumbersStyle) {
            return;
        }
        this.#options = Object.freeze({...this.#options, fileNumbersStyle});
        this.#redrawCoordinates();
    }

    /**
     * Change the orientation (WXF numerals vs algebraic letters) of the board coordinates.
     *
     * @param coordinatesOrientation {string|null} one of {@link CoordinatesOrientation} or
     *                                             `null` to keep the labels hidden
     */
    setCoordinatesOrientation(coordinatesOrientation) {
        if (this.#options.coordinatesOrientation === coordinatesOrientation) {
            return;
        }
        this.#options = Object.freeze({...this.#options, coordinatesOrientation});
        this.#redrawCoordinates();
    }

    #redrawCoordinates() {
        // remove existing file-coordinate (top + bottom) and right-side rank labels
        // (rank labels only exist in algebraic mode but the selector is harmless if absent)
        document.querySelectorAll(`#${this.#options.elementId} .file-coordinates-top,
                                   #${this.#options.elementId} .file-coordinates-bottom,
                                   #${this.#options.elementId} .rows-coordinates-right`)
            .forEach(el => el.remove());
        this.#drawCoordinates();
    }

    /**
     * @return {boolean}
     */
    toggleShowCoordinates() {
        let areVisible = this.#areCoordinatesVisible();
        this.#renderShowCoordinatesSetting(!areVisible);
        return !areVisible;
    }

    /**
     * @param show {boolean}
     */
    #renderShowCoordinatesSetting(show) {
        let labels = document.getElementsByClassName('coordinates-labels');
        for (let label of labels) {
            label.style.visibility = show ? 'visible' : 'hidden';
        }
    }

    /**
     * @param enabled {boolean}
     */
    #renderColorblindFriendlyBlackPiecesSetting(enabled) {
        this.#boardContainer.classList.toggle('colorblind-friendly-black-pieces', enabled);
    }

    /**
     * @param enabled {boolean}
     */
    #renderFlipOpponentPiecesSetting(enabled) {
        this.#boardContainer.classList.remove('flip-opponent-pieces-red', 'flip-opponent-pieces-black');

        if (enabled) {
            if (!this.#flippedRed) {
                this.#boardContainer.classList.add('flip-opponent-pieces-red');
            } else {
                this.#boardContainer.classList.add('flip-opponent-pieces-black');
            }
        }
    }

    #areCoordinatesVisible() {
        let labels = document.getElementsByClassName('coordinates-labels');
        for (let label of labels) {
            if (label.style.visibility === 'hidden') {
                return false;
            }
        }
        return true;
    }

    #hideAllDraggableCursors() {
        this.#board
            .listPiecePositions()
            .forEach(pieceAtPosition => {
                const imageId = this.#positionToElementId('image', pieceAtPosition.position);
                const image = document.getElementById(imageId);
                // image may be momentarily absent while an animation is in
                // flight (source-square images are renamed to 'animating-xxx'
                // until the animation commits). In that case there's nothing
                // to update here; the animation's onDone will re-run cursor
                // bookkeeping via #resetDraggableCursors().
                if (image != null) {
                    image.classList.remove('piece-image-can-move');
                }
            });
    }

    #resetDraggableCursors() {
        const isMated = () => {
            try {
                return this.#board.isMated();
            } catch (e) {
                // FIXME: happens when re-loading a game where it's your turn to play and enablePlayerMove is called
                console.warn('error while checking if mated');
                return false;
            }
        }

        this.#hideAllDraggableCursors();

        if (this.isPlayerMoveEnabled && !isMated()) {
            const colorToPlay = this.#board.getColorToPlay();

            this.#board
                .listPiecePositions()
                .filter(piecePosition => colorToPlay === piecePosition.pieceColor)
                .map(piecePosition => piecePosition.position)
                .filter(position => this.#board.listLegalMovesFrom(position).length > 0)
                .forEach(position => {
                    const imageId = this.#positionToElementId('image', position);
                    const image = document.getElementById(imageId);
                    // see #hideAllDraggableCursors: image may be absent
                    // mid-animation (ID temporarily renamed).
                    if (image != null) {
                        image.classList.add('piece-image-can-move');
                    }
                });
        }
    }

    /**
     * @param position {Position}
     * @returns {HTMLElement}
     */
    #locateLegalMovePlaceHolderAt(position) {
        return document.getElementById(this.#positionToElementId('legal_move', position));
    }

    /**
     * @param position {Position}
     * @param prefix {string}
     * @returns {string}
     */
    #positionToElementId(prefix, position) {
        return boardPositionToElementId(this.#options.elementId, prefix.toLowerCase(), position);
    }

}

/**
 *
 * @param boardId {string}
 * @param prefix {string}
 * @param position {Position}
 */
function boardPositionToElementId(boardId, prefix, position) {
    return `${boardId}-${prefix.toLowerCase()}-${position.x}-${position.y}`;
}

/**
 * @param elementId {string}
 * @returns {Position}
 */
function parsePositionFromElementId(elementId) {
    const split = elementId.split('-');
    const x = Number(split[split.length - 2]);
    const y = Number(split[split.length - 1]);
    return new Position(x, y);
}

/**
 * Adds a miniature board that appears on hover for an element.
 * Uses {@link createWebappBoardGui} so piece images are served from the
 * correct base URL (local server in dev, CDN in production).
 *
 * @param element {HTMLElement} - The element to attach hover listeners to
 * @param gameId {string} - Unique identifier for this miniboard
 * @param fen {string} - FEN string representing the board position
 * @param playerColor {string} - Color to flip the board to
 * @param lazy {boolean} - If true, only create the board on first mouseenter (default: false)
 * @returns {HTMLElement|null} - The created miniboard div (null if lazy and not yet created)
 */
function addMiniboardDiv(element, gameId, fen, playerColor, lazy = false) {
    const LEFT_MARGIN = 12;
    const MINI_BOARD_HEIGHT = 256 / 0.9;

    const miniBoardId = `mini-board-overview-${gameId}`;
    let miniBoardDiv = null;

    function createBoard() {
        if (miniBoardDiv) return;

        miniBoardDiv = document.createElement('div');
        miniBoardDiv.id = miniBoardId;
        miniBoardDiv.classList.add(
            'board-container',
            'mini-board-container',
            'mini-board-overview'
        );

        document.body.appendChild(miniBoardDiv);

        const boardGui = createWebappBoardGui({
            elementId: miniBoardId,
            showCoordinates: false,
            mini: true,
            forceRenderChecks: true,
        });
        boardGui.loadFen(fen);
        boardGui.flipToColor(playerColor);
        boardGui.updateHighlightedChecks();
    }

    // Create board immediately if not lazy
    if (!lazy) {
        createBoard();
    }

    // listeners
    function showMiniboard() {
        // Create board on first hover if lazy
        if (!miniBoardDiv) {
            createBoard();
        }

        const gameItemRect = element.getBoundingClientRect();
        const left = gameItemRect.right + LEFT_MARGIN + window.scrollX;
        const top = gameItemRect.top + window.scrollY + (gameItemRect.height / 2) - (MINI_BOARD_HEIGHT / 2);
        miniBoardDiv.style.top = `${top}px`;
        miniBoardDiv.style.left = `${left}px`;
        miniBoardDiv.style.display = 'block';
    }

    function hideMiniboard() {
        if (miniBoardDiv) {
            miniBoardDiv.style.display = 'none';
        }
    }

    element.addEventListener('mouseenter', showMiniboard);
    element.addEventListener('mouseleave', hideMiniboard);

    return miniBoardDiv;
}
