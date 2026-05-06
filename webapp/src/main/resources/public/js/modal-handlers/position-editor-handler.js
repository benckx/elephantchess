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

const PositionEditorMode = Object.freeze({
    NONE: 'NONE',
    PIECE_SELECTED: 'PIECE_SELECTED',
    ERASER: 'ERASER'
});

/**
 * MIME type used when dragging a piece from the tools panel to the board.
 * Using a custom type allows us to distinguish this from regular board moves.
 */
const TOOL_PIECE_DRAG_MIME = 'application/x-xiangqi-tool-piece';

/**
 * MIME type used when dragging a piece that is already on the board from one
 * square to another. Holds the source position's UCI.
 */
const BOARD_PIECE_DRAG_MIME = 'application/x-xiangqi-board-piece-from';

/**
 * Checks whether placing a given piece at a given position would result in a valid
 * xiangqi position (king/advisor inside their palace, elephant on its own side,
 * pawns not on their own first two ranks).
 *
 * @param pieceChar {string}
 * @param position {Position}
 * @return {boolean}
 */
function isValidPiecePlacement(pieceChar, position) {
    const isRed = pieceChar === pieceChar.toUpperCase();
    const lower = pieceChar.toLowerCase();

    switch (lower) {
        case 'k':
        case 'a':
            return isRed ? position.isInRedPalace() : position.isInBlackPalace();
        case 'b':
            // elephant must stay on its own side (no crossing the river)
            return isRed ? position.y <= 4 : position.y >= 5;
        default:
            return true;
    }
}

class PositionEditorState {

    #mode = PositionEditorMode.NONE;

    /**
     *
     * @type {string|null}
     */
    #selectedPiece = null;

    /**
     * @return {string}
     */
    get mode() {
        return this.#mode;
    }

    /**
     * @return {string|null}
     */
    get selectedPiece() {
        if (this.#mode === PositionEditorMode.PIECE_SELECTED) {
            return this.#selectedPiece;
        } else {
            return null;
        }
    }

    /**
     *
     * @param pieceChar {string}
     */
    handlePieceClick(pieceChar) {
        if (!PIECES_CHARS.includes(pieceChar.toLowerCase())) {
            throw new Error('Invalid char: ' + pieceChar);
        }

        switch (this.#mode) {
            case PositionEditorMode.PIECE_SELECTED:
                if (this.#selectedPiece === pieceChar) {
                    this.#unselectPiece();
                } else {
                    this.#selectPiece(pieceChar);
                }
                break;
            default:
                this.#selectPiece(pieceChar);
        }
    }

    /**
     * Force-select a given piece (used by drag-and-drop initiation).
     * @param pieceChar {string}
     */
    forceSelectPiece(pieceChar) {
        if (!PIECES_CHARS.includes(pieceChar.toLowerCase())) {
            throw new Error('Invalid char: ' + pieceChar);
        }
        this.#selectPiece(pieceChar);
    }

    #selectPiece(pieceChar) {
        this.#selectedPiece = pieceChar;
        this.#mode = PositionEditorMode.PIECE_SELECTED;
    }

    #unselectPiece() {
        this.#selectedPiece = null;
        this.#mode = PositionEditorMode.NONE;
    }

    enableEraserMode() {
        this.#mode = PositionEditorMode.ERASER;
        this.#selectedPiece = null;
    }

    /**
     * Reset the state back to its initial (no tool / no piece selected) mode.
     */
    clear() {
        this.#mode = PositionEditorMode.NONE;
        this.#selectedPiece = null;
    }

}

class PositionEditorHandler extends ModalHandler {

    #state = new PositionEditorState();

    /**
     * @type {BoardGui}
     */
    #editorBoardGui;


    /**
     * @type {string[]}
     */
    #fenHistory = [];

    #toolsDiv = document.getElementById('tools-inner');

    #defaultStartPositionButton = document.getElementById('position-editor-default-start-position-button');
    #clearPositionButton = document.getElementById('position-editor-clear-board-button');
    #eraserButton = document.getElementById('position-editor-eraser-button');
    #undoButton = document.getElementById('position-editor-undo-button');

    #okButton = document.getElementById('ok-button');

    #redToPlayRadio = document.getElementById('position-editor-red-to-play');
    #blackToPlayRadio = document.getElementById('position-editor-black-to-play');

    /** @type {HTMLElement[]} */
    #toolPieceHolders = [];

    /**
     * @param getCurrentStartFenCb {function(): string}
     * @param selectedFenCb {function(string)} callback to be called when a fen is selected
     */
    constructor(getCurrentStartFenCb, selectedFenCb) {
        super();

        const editorBoardOptions = {
            elementId: 'editor-board-container',
            showCoordinates: false,
        };

        this.#editorBoardGui = createWebappBoardGui(editorBoardOptions);
        this.#editorBoardGui.disablePlayerMove();
        this.#editorBoardGui.loadFen(getCurrentStartFenCb());

        // initialize the "to play" radios from the loaded FEN
        this.#refreshToPlayRadios();
        this.#redToPlayRadio.addEventListener('change', () => {
            if (this.#redToPlayRadio.checked) {
                this.#applyBoardUpdate(board => board.forceColorToPlay(Color.RED));
            }
        });
        this.#blackToPlayRadio.addEventListener('change', () => {
            if (this.#blackToPlayRadio.checked) {
                this.#applyBoardUpdate(board => board.forceColorToPlay(Color.BLACK));
            }
        });

        const subPanels = this.#toolsDiv.getElementsByClassName('tools-sub-panel');
        const piecesPanel = subPanels.item(1);

        // render pieces in the tools box
        ['a', 'k', 'r', 'c', 'n', 'b', 'p'].forEach(lowerCasePieceChar => {
            [lowerCasePieceChar.toUpperCase(), lowerCasePieceChar.toLowerCase()].forEach(pieceChar => {
                const pieceHolderDiv = document.createElement('div');
                pieceHolderDiv.classList.add('tools-piece-holder', 'div-button-enabled');
                pieceHolderDiv.id = `tool-piece-holder-${pieceChar}`;
                pieceHolderDiv.setAttribute('draggable', 'true');

                const pieceImg = document.createElement('img');
                pieceImg.src = this.#editorBoardGui.getPieceImageSource(pieceChar);
                pieceImg.className = 'piece-image';
                // prevent the image from being dragged on its own; drag the holder div instead
                pieceImg.setAttribute('draggable', 'false');

                pieceHolderDiv.append(pieceImg);
                piecesPanel.append(pieceHolderDiv);
            });
        });

        // tools listeners
        this.#defaultStartPositionButton.addEventListener('click', () => {
            this.#applyBoardUpdate((board) => board.loadFen(DEFAULT_START_FEN));
            this.#state.clear();
            this.#refreshSelectionHighlight();
            this.#refreshToPlayRadios();
        });
        this.#clearPositionButton.addEventListener('click', () => {
            this.#applyBoardUpdate((board) => board.clearBoard());
            this.#state.clear();
            this.#refreshSelectionHighlight();
            this.#refreshToPlayRadios();
        });
        this.#eraserButton.addEventListener('click', () => {
            if (this.#state.mode === PositionEditorMode.ERASER) {
                this.#state.clear();
            } else {
                this.#state.enableEraserMode();
            }
            this.#refreshSelectionHighlight();
        });
        this.#undoButton.addEventListener('click', () => {
            if (isDivButtonEnabled(this.#undoButton) && this.#fenHistory.length > 0) {
                const lastFen = this.#fenHistory.pop();
                this.#editorBoardGui.loadFen(lastFen);
                if (this.#fenHistory.length === 0) {
                    disableDivButton(this.#undoButton);
                }
                // undoing should not keep the user in eraser / piece-selected mode
                this.#state.clear();
                this.#refreshSelectionHighlight();
                this.#refreshToPlayRadios();
            }
        });

        // listeners
        this.#okButton.addEventListener('click', () => {
            const fen = this.#editorBoardGui.outputFen();
            selectedFenCb(fen);
            UI.hideModal();
        });

        // pieces tools listeners
        getElementsByClassNameArray('tools-piece-holder')
            .forEach(toolPieceHolder => {
                const split = toolPieceHolder.id.split('-');
                const pieceChar = split[split.length - 1];

                this.#toolPieceHolders.push(toolPieceHolder);

                toolPieceHolder.addEventListener('click', () => {
                    this.#state.handlePieceClick(pieceChar);
                    this.#refreshSelectionHighlight();
                });

                // drag start from the tools panel
                toolPieceHolder.addEventListener('dragstart', (e) => {
                    this.#state.forceSelectPiece(pieceChar);
                    this.#refreshSelectionHighlight();
                    if (e.dataTransfer) {
                        e.dataTransfer.effectAllowed = 'copy';
                        e.dataTransfer.setData(TOOL_PIECE_DRAG_MIME, pieceChar);
                        // BoardGui also attaches a drop handler that reads 'text/plain'
                        // as a UCI move. Set it to a harmless self-move that won't match
                        // any legal move, just in case our capture-phase handler doesn't
                        // catch the event first.
                        e.dataTransfer.setData('text/plain', 'a0a0');
                    }
                });

                toolPieceHolder.addEventListener('dragend', () => {
                    this.#clearGhostPreviews();
                });
            });

        // square listeners
        const boardContainer = document.getElementById('editor-board-container');

        // Intercept drag-start events on board pieces (capture phase) so we can
        // initiate our own drag for re-positioning a piece. BoardGui has its own
        // dragstart handler that cancels the drag when player moves are disabled
        // (which is the case in the editor); stopImmediatePropagation prevents it.
        boardContainer.addEventListener('dragstart', (e) => {
            const img = e.target && e.target.closest ? e.target.closest('.piece-image') : null;
            if (img == null || !boardContainer.contains(img)) {
                return;
            }
            const holder = img.parentElement;
            if (holder == null || !holder.classList.contains('piece-holder')) {
                return;
            }
            const fromPosition = parsePositionFromElementId(holder.id);
            const piece = this.#editorBoardGui.board.getPieceAt(fromPosition);
            if (piece == null) {
                return;
            }

            e.stopImmediatePropagation();

            this.#state.forceSelectPiece(piece.pieceChar);
            this.#refreshSelectionHighlight();

            if (e.dataTransfer) {
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData(TOOL_PIECE_DRAG_MIME, piece.pieceChar);
                e.dataTransfer.setData(BOARD_PIECE_DRAG_MIME, fromPosition.toUci());
                // see tools drag-start for rationale
                e.dataTransfer.setData('text/plain', 'a0a0');
            }
        }, true /* useCapture */);

        htmlCollectionToArray(boardContainer.getElementsByClassName('piece-holder'))
            .forEach(pieceHolder => {
                const clickedPosition = parsePositionFromElementId(pieceHolder.id);

                pieceHolder.addEventListener('click', () => {
                    this.#handleSquareClickedEvent(clickedPosition);
                });

                // ghost preview on hover when a piece is selected
                pieceHolder.addEventListener('mouseenter', () => {
                    this.#showGhostPreviewAt(pieceHolder, clickedPosition);
                    this.#showEraserHoverAt(pieceHolder);
                });
                pieceHolder.addEventListener('mouseleave', () => {
                    this.#removeGhostPreviewFrom(pieceHolder);
                    this.#removeEraserHoverFrom(pieceHolder);
                });

                // ghost preview during drag
                pieceHolder.addEventListener('dragenter', () => {
                    this.#showGhostPreviewAt(pieceHolder, clickedPosition);
                });
                pieceHolder.addEventListener('dragleave', (e) => {
                    if (!pieceHolder.contains(e.relatedTarget)) {
                        this.#removeGhostPreviewFrom(pieceHolder);
                    }
                });

                // drop handler registered in capturing phase so we can stop the BoardGui
                // default drop handler (which would try to parse text/plain as a UCI move)
                pieceHolder.addEventListener('drop', (e) => {
                    const types = e.dataTransfer ? Array.from(e.dataTransfer.types || []) : [];
                    const isToolDrag = types.includes(TOOL_PIECE_DRAG_MIME);
                    const isBoardDrag = types.includes(BOARD_PIECE_DRAG_MIME);
                    if (!isToolDrag && !isBoardDrag) {
                        return;
                    }
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    const pieceChar = e.dataTransfer.getData(TOOL_PIECE_DRAG_MIME);
                    this.#removeGhostPreviewFrom(pieceHolder);

                    if (pieceChar && isValidPiecePlacement(pieceChar, clickedPosition)) {
                        if (isBoardDrag) {
                            const fromUci = e.dataTransfer.getData(BOARD_PIECE_DRAG_MIME);
                            const fromPosition = Position.parseUci(fromUci);
                            if (!Position.areEquals(fromPosition, clickedPosition)) {
                                this.#applyBoardUpdate((board) => {
                                    board.removePieceFrom(fromPosition);
                                    this.#placePieceOnBoard(board, pieceChar, clickedPosition);
                                });
                            }
                        } else {
                            this.#applyBoardUpdate((board) => {
                                this.#placePieceOnBoard(board, pieceChar, clickedPosition);
                            });
                        }
                    }

                    // a drag-and-drop is a one-shot action: clear the selection
                    // so we don't stay in "piece selected" mode afterwards.
                    this.#state.clear();
                    this.#refreshSelectionHighlight();
                }, true /* useCapture */);
            });

        // tool tips
        addToolTip(this.#defaultStartPositionButton, 'Load default start position');
        addToolTip(this.#clearPositionButton, 'Clear board');
        addToolTip(this.#eraserButton, 'Eraser tool (remove pieces)');
        addToolTip(this.#undoButton, 'Undo last change');
    }

    /**
     * Places a piece on the board, enforcing unicity constraints that don't
     * otherwise exist on the underlying Board (e.g. only one general per color).
     *
     * @param boardGui {BoardGui}
     * @param pieceChar {string}
     * @param position {Position}
     */
    #placePieceOnBoard(boardGui, pieceChar, position) {
        function findPiecePositions(pieceChar) {
            return boardGui.board.listPositionsForPiece(pieceChar);
        }

        // ensure board can only contain one general of each color
        // if the user places a new king, first wipe any existing general of the same color.
        if (pieceChar.toLowerCase() === 'k') {
            findPiecePositions(pieceChar).forEach(p => {
                boardGui.removePieceFrom(p);
            });
        }

        boardGui.addPieceAt(pieceChar, position, true);
    }

    /**
     * @param clickedPosition {Position}
     */
    #handleSquareClickedEvent(clickedPosition) {
        switch (this.#state.mode) {
            case PositionEditorMode.PIECE_SELECTED: {
                const pieceChar = this.#state.selectedPiece;
                if (!isValidPiecePlacement(pieceChar, clickedPosition)) {
                    return;
                }
                this.#applyBoardUpdate((board) => {
                    this.#placePieceOnBoard(board, pieceChar, clickedPosition);
                });
                break;
            }
            case PositionEditorMode.ERASER:
                this.#applyBoardUpdate((board) => {
                    board.removePieceFrom(clickedPosition);
                });
                break;
            default:
                // nothing selected - ignore
                break;
        }
    }

    /**
     * Highlights the currently selected tool piece (if any), and toggles the
     * eraser button's "active" state.
     */
    #refreshSelectionHighlight() {
        const selected = this.#state.selectedPiece;
        this.#toolPieceHolders.forEach(el => {
            const split = el.id.split('-');
            const pieceChar = split[split.length - 1];
            if (selected != null && pieceChar === selected) {
                el.classList.add('tools-piece-holder-selected');
            } else {
                el.classList.remove('tools-piece-holder-selected');
            }
        });

        if (this.#state.mode === PositionEditorMode.ERASER) {
            this.#eraserButton.classList.add('position-editor-tool-selected');
        } else {
            this.#eraserButton.classList.remove('position-editor-tool-selected');
        }

        // when switching modes or selection, remove any lingering previews
        this.#clearGhostPreviews();
        this.#clearEraserHovers();
    }

    /**
     * Sync the "to play" radio buttons with the current board state.
     */
    #refreshToPlayRadios() {
        const color = this.#editorBoardGui.board.getColorToPlay();
        this.#redToPlayRadio.checked = (color === Color.RED);
        this.#blackToPlayRadio.checked = (color === Color.BLACK);
    }

    /**
     * @param pieceHolder {HTMLElement}
     * @param position {Position}
     */
    #showGhostPreviewAt(pieceHolder, position) {
        if (this.#state.mode !== PositionEditorMode.PIECE_SELECTED) {
            return;
        }
        const pieceChar = this.#state.selectedPiece;
        if (pieceChar == null) {
            return;
        }
        // don't show a ghost on invalid positions
        if (!isValidPiecePlacement(pieceChar, position)) {
            return;
        }
        // already a ghost present?
        if (pieceHolder.getElementsByClassName('piece-image-ghost').length > 0) {
            return;
        }

        const ghost = document.createElement('img');
        ghost.className = 'piece-image piece-image-ghost';
        ghost.src = this.#editorBoardGui.getPieceImageSource(pieceChar);
        ghost.setAttribute('draggable', 'false');
        // the ghost should not interfere with pointer events (drag, drop, click)
        ghost.style.pointerEvents = 'none';
        pieceHolder.prepend(ghost);
    }

    /**
     * @param pieceHolder {HTMLElement}
     */
    #removeGhostPreviewFrom(pieceHolder) {
        const ghosts = pieceHolder.getElementsByClassName('piece-image-ghost');
        while (ghosts.length > 0) {
            ghosts[0].remove();
        }
    }

    #clearGhostPreviews() {
        const ghosts = document.getElementsByClassName('piece-image-ghost');
        while (ghosts.length > 0) {
            ghosts[0].remove();
        }
    }

    /**
     * When the eraser tool is active and the hovered square contains a piece,
     * mark the holder so the piece visually indicates it can be removed.
     *
     * @param pieceHolder {HTMLElement}
     */
    #showEraserHoverAt(pieceHolder) {
        if (this.#state.mode !== PositionEditorMode.ERASER) {
            return;
        }
        if (pieceHolder.getElementsByClassName('piece-image').length === 0) {
            return;
        }
        pieceHolder.classList.add('piece-holder-eraser-hover');
    }

    /**
     * @param pieceHolder {HTMLElement}
     */
    #removeEraserHoverFrom(pieceHolder) {
        pieceHolder.classList.remove('piece-holder-eraser-hover');
    }

    #clearEraserHovers() {
        const hovers = document.getElementsByClassName('piece-holder-eraser-hover');
        while (hovers.length > 0) {
            hovers[0].classList.remove('piece-holder-eraser-hover');
        }
    }

    /**
     * Apply a board change and store the historical position,
     * so it can be undone later.
     *
     * @param updateCb {function(BoardGui)}
     */
    #applyBoardUpdate(updateCb) {
        const fenBefore = resetFenFullMovesCount(this.#editorBoardGui.outputFen());
        updateCb(this.#editorBoardGui);
        const fenAfter = resetFenFullMovesCount(this.#editorBoardGui.outputFen());
        if (fenBefore !== fenAfter) {
            this.#fenHistory.push(fenBefore);
            enableDivButton(this.#undoButton);
        }
        // board may have been re-rendered, make sure ghosts/hovers are gone
        this.#clearGhostPreviews();
        this.#clearEraserHovers();
    }

}
