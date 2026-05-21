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

const BOARD_WIDTH = 9;
const BOARD_HEIGHT = 10;
const UCI_LETTER = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'];
const PIECES_CHARS = ['c', 'r', 'n', 'b', 'a', 'k', 'p', 'w'];

const DEFAULT_START_FEN = 'rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0';

/**
 * Manchu chess (Yitong) start FEN.
 * Red has only: general, 2 advisors, 2 elephants, 5 soldiers, and the super-chariot (W/w) at a1.
 * The super-chariot combines the powers of the chariot, horse, and cannon.
 */
const MANCHU_START_FEN = 'rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/9/9/W1BAKAB2 w - - 0 0';

// Piece color enum. Lives here (rather than in enums.js) so that xiangqi.js
// can be used standalone (together with board-gui.js) without pulling in the
// rest of the webapp's modules.
const Color = Object.freeze({
    RED: 'RED',
    BLACK: 'BLACK'
});

/**
 * @param char {string}
 * @return {boolean}
 */
function isRedPiece(char) {
    return char.length === 1 && char.toUpperCase() === char;
}

/**
 * @param char {string}
 * @return {boolean}
 */
function isBlackPiece(char) {
    return char.length === 1 && char.toLowerCase() === char;
}

/**
 * @param char {string}
 * @return {string}
 */
function charToPieceColor(char) {
    if (char.length !== 1) {
        throw new Error('Not a single char: ' + char);
    } else {
        if (isRedPiece(char)) {
            return Color.RED;
        } else if (isBlackPiece(char)) {
            return Color.BLACK;
        } else {
            throw new Error('Illegal piece ' + char);
        }
    }
}

function reverseColor(color) {
    switch (color.toUpperCase()) {
        case Color.RED:
            return Color.BLACK;
        case Color.BLACK:
            return Color.RED;
        default:
            throw Error('Illegal argument color ' + color);
    }
}

function colorToUci(color) {
    switch (color.toUpperCase()) {
        case Color.RED:
            return 'w';
        case Color.BLACK:
            return 'b';
        default:
            throw Error('Illegal argument color ' + color);
    }
}

/**
 * Reset full count to 0
 */
function resetFenFullMovesCount(fen) {
    let split = fen.split(' ');
    return split.slice(0, split.length - 1).join(' ') + ' 0';
}

function validateStartFen(fen) {
    let board = new Board();
    board.loadFen(fen);

    if (board.isCheckmate(Color.RED) || board.isCheckmate(Color.BLACK)) {
        throw new Error('Start position is checkmate');
    } else if (board.isStalemate(Color.RED) || board.isStalemate(Color.BLACK)) {
        throw new Error('Start position is stalemate');
    }
}

/**
 * Add full moves count and convert to single string
 * @param {string[]} movesAsPgn
 */
function toSingleLinePgn(movesAsPgn) {
    let pgn = '';
    for (let i = 0; i < movesAsPgn.length; i++) {
        let move = movesAsPgn[i];
        if (i % 2 === 0) {
            pgn += (Math.floor(i / 2) + 1) + '. ';
        }
        pgn += move + ' ';
    }
    return pgn;
}

/**
 * https://en.wikipedia.org/wiki/Xiangqi#System_3
 *
 * @param {HalfMove[]} moves
 * @param {boolean} renderCheckIndicators
 * @param {string} startFen
 * @return {string[]}
 */
function translateMovesToPgn(moves, renderCheckIndicators = true, startFen = DEFAULT_START_FEN) {
    let movesAsPgn = [];

    let board = new Board();
    board.loadFen(startFen);

    moves.forEach(move => {
        let pieceFrom = board.getPieceAt(move.from);
        let pieceTarget = board.getPieceAt(move.to);
        let newPosition = move.to.toAlgebraic();

        let capture = '';
        if (pieceTarget != null) {
            capture = 'x';
        }
        let pieceLetter = pieceFrom.pieceChar.toUpperCase();
        let formerPosition = UCI_LETTER[move.from.x];
        let sameFile = move.from.x === move.to.x;
        if (pieceLetter === 'K' || sameFile) {
            formerPosition = '';
        }

        board.registerMove(move);

        let checkIndicator = '';
        if (renderCheckIndicators) {
            if (board.isCheckmate(Color.RED) || board.isCheckmate(Color.BLACK)) {
                checkIndicator += '#';
            } else if (board.isInCheck(Color.RED) || board.isInCheck(Color.BLACK)) {
                checkIndicator += '+';
            }
        }

        let moveString = `${pieceLetter}${formerPosition}${capture}${newPosition}${checkIndicator}`;
        movesAsPgn.push(moveString);
    });

    return movesAsPgn;
}

/**
 * @param moves {HalfMove[]}
 * @param startFen {string}
 * @return {string[]}
 */
function translateMovesToPrefixedAlgebraic(moves, startFen = DEFAULT_START_FEN) {
    const formattedMoves = [];
    const board = new Board();
    board.loadFen(startFen);

    moves.forEach(move => {
        const pieceChar = board.getPieceAt(move.from).pieceChar.toUpperCase();
        const moveString = `${pieceChar} ${move.toAlgebraic()}`;
        board.registerMove(move);
        formattedMoves.push(moveString);
    });

    return formattedMoves;
}

/**
 * @param moves {HalfMove[]}
 * @param horizontalSeparator {string} can be '=' or '.'
 * @param startFen {string}
 * @return {string[]}
 */
function translateMovesToWxf(moves, horizontalSeparator, startFen) {

    /**
     * File number in WXF format starts at 1 from the right hand of the player
     *
     * @return {number}
     */
    function fileAsWxf(color, file) {
        switch (color) {
            case Color.RED:
                return BOARD_WIDTH - file;
            case Color.BLACK:
                return file + 1;
            default:
                throw new Error('Illegal color ' + color);
        }
    }

    /**
     * @param color {string}
     * @param move {HalfMove}
     * @return {string} e.g. Direction indicator sign (+ or -)
     */
    function verticalMoveDirectionChar(color, move) {
        if ((color === Color.BLACK && move.from.y > move.to.y) || (color === Color.RED && move.from.y < move.to.y)) {
            return '+';
        } else {
            return '-';
        }
    }

    /**
     * @param color {string}
     * @param move {HalfMove}
     * @return {string} e.g. Direction indicator sign (+ or -) followed by distance
     */
    function verticalMove(color, move) {
        let direction = verticalMoveDirectionChar(color, move);
        let distance = Math.abs(move.from.y - move.to.y);
        return `${direction}${distance}`;
    }

    /**
     * File number or sign (+ or -)
     *
     * @param board {Board}
     * @param move {HalfMove}
     * @return {string}
     */
    function fileNumberOrSign(board, move) {
        let physicalPiece = board.getPieceAt(move.from);
        let color = physicalPiece.color;

        let allSimilarPiecesOnFile = board.listPiecePositions().filter(pieceAtPosition => {
            return pieceAtPosition.piece.pieceChar === physicalPiece.pieceChar && pieceAtPosition.position.x === move.from.x
        });

        if (allSimilarPiecesOnFile.length === 2) {
            let currentFileY;
            let otherFileY;
            if (move.from.y === allSimilarPiecesOnFile[0].position.y) {
                currentFileY = allSimilarPiecesOnFile[0].position.y;
                otherFileY = allSimilarPiecesOnFile[1].position.y;
            } else {
                currentFileY = allSimilarPiecesOnFile[1].position.y;
                otherFileY = allSimilarPiecesOnFile[0].position.y;
            }

            let isOnTop = (currentFileY > otherFileY && color === Color.RED)
                || (currentFileY < otherFileY && color === Color.BLACK);

            return isOnTop ? '+' : '-';
        }

        return fileAsWxf(color, move.from.x).toString();
    }

    let formattedMoves = [];
    let board = new Board();
    board.loadFen(startFen);

    moves.forEach(move => {
        let physicalPiece = board.getPieceAt(move.from);
        if (physicalPiece == null) {
            board.printBoard();
            throw new Error(`no piece at ${move.from.toAlgebraic()}`);
        }
        let color = physicalPiece.color;
        let pieceType = physicalPiece.pieceChar.toUpperCase();

        let moveStr = '';
        switch (pieceType) {
            case 'P':
            case 'C':
            case 'R':
            case 'K':
                if (move.isHorizontal()) {
                    let fileStr = fileNumberOrSign(board, move);
                    let newFile = fileAsWxf(color, move.to.x);
                    moveStr = `${fileStr}${horizontalSeparator}${newFile}`;
                } else {
                    moveStr = `${fileNumberOrSign(board, move)}${verticalMove(color, move)}`;
                }
                break;
            case 'B':
            case 'A':
            case 'N':
                let currentFileStr = fileNumberOrSign(board, move);
                let direction = verticalMoveDirectionChar(color, move);
                let newFile = fileAsWxf(color, move.to.x);
                moveStr = `${currentFileStr}${direction}${newFile}`;
                break;
            case 'W':
                if (move.isHorizontal()) {
                    let fileStr = fileNumberOrSign(board, move);
                    let newFile = fileAsWxf(color, move.to.x);
                    moveStr = `${fileStr}${horizontalSeparator}${newFile}`;
                } else if (move.isVertical()) {
                    moveStr = `${fileNumberOrSign(board, move)}${verticalMove(color, move)}`;
                } else {
                    let currentFileStr = fileNumberOrSign(board, move);
                    let direction = verticalMoveDirectionChar(color, move);
                    let newFile = fileAsWxf(color, move.to.x);
                    moveStr = `${currentFileStr}${direction}${newFile}`;
                }
                break;
        }

        let pieceCharacter;

        switch (pieceType) {
            case 'N':
                pieceCharacter = 'H';
                break;
            case 'B':
                pieceCharacter = 'E';
                break;
            default:
                pieceCharacter = pieceType;
                break;
        }

        formattedMoves.push(`${pieceCharacter}${moveStr}`);
        board.registerMove(move);
    });

    return formattedMoves;
}

/**
 *
 * Generic version of the functions above
 *
 * @param moves {HalfMove[]}
 * @param moveFormat {string}
 * @param startFen {string}
 * @return {string[]}
 */
function translateMovesFormat(moves, moveFormat, startFen) {
    switch (moveFormat) {
        case MoveFormatSetting.WXF_DOT:
            return translateMovesToWxf(moves, '.', startFen);
        case MoveFormatSetting.WXF_EQUALS:
            return translateMovesToWxf(moves, '=', startFen);
        case MoveFormatSetting.PGN:
            return translateMovesToPgn(moves, false, startFen);
        case MoveFormatSetting.ALGEBRAIC_EN:
            return translateMovesToPrefixedAlgebraic(moves, startFen);
        default:
            throw new Error('Unsupported move format ' + moveFormat);
    }
}

/**
 *
 * Mainly for debugging purposes
 *
 * @param allMoves {HalfMove[]}
 * @param format {string}
 * @param startFen {string}
 * @return {string[]}
 */
function safeTranslateMovesFormat(allMoves, format, startFen) {
    try {
        return translateMovesFormat(allMoves, format, startFen);
    } catch (e) {
        console.error(e);
        console.info(`startFen ${startFen}`);
        console.info(`moves [${allMoves.length}] ${allMoves.map(move => move.toAlgebraic())}`);
        return allMoves.map(move => move.toAlgebraic());
    }
}

/**
 *
 * @param moves {HalfMove[]}
 * @param moveFormat {string}
 * @param startFen {string}
 * @return {null|string}
 */
function translateMovesFormatTakeLast(moves, moveFormat, startFen) {
    let translated = translateMovesFormat(moves, moveFormat, startFen);
    if (translated.length > 0) {
        return translated[translated.length - 1];
    } else {
        return null;
    }
}

/**
 * @param {HalfMove[]} moves
 * @return {string}
 */
function exportMovesToPgnLine(moves) {
    return toSingleLinePgn(translateMovesToPgn(moves));
}

/**
 * @param moves {HalfMove[]}
 * @param startFen {string}
 * @return {string}
 */
function calculateFen(moves, startFen = DEFAULT_START_FEN) {
    const board = new Board();
    board.loadFen(startFen);
    moves.forEach(move => board.registerMove(move));
    return board.outputFen();
}

class Position {

    static redGeneralStartingPosition = new Position(Math.floor(BOARD_WIDTH / 2), 0);
    static blackGeneralStartingPosition = new Position(Math.floor(BOARD_WIDTH / 2), BOARD_HEIGHT - 1);

    constructor(x, y) {
        this.x = x;
        this.y = y;
    }

    get file() {
        return UCI_LETTER[this.x];
    }

    /**
     * @return {number}
     */
    get rank() {
        return this.y;
    }

    existsOnBoard() {
        return this.x >= 0 &&
            this.y >= 0 &&
            this.x < BOARD_WIDTH &&
            this.y < BOARD_HEIGHT;
    }

    isInRedPalace() {
        return this.x >= 3 &&
            this.x <= 5 &&
            this.y <= 2;
    }

    isInBlackPalace() {
        return this.x >= 3 &&
            this.x <= 5 &&
            this.y >= BOARD_HEIGHT - 3;
    }

    getTop() {
        return new Position(this.x, this.y + 1);
    }

    getBottom() {
        return new Position(this.x, this.y - 1);
    }

    getLeft() {
        return new Position(this.x - 1, this.y);
    }

    getRight() {
        return new Position(this.x + 1, this.y);
    }

    /**
     * @return {Position[]}
     */
    getAllTop() {
        let squares = [];
        for (let y = this.y + 1; y < BOARD_HEIGHT; y++) {
            squares.push(new Position(this.x, y));
        }
        return squares;
    }

    /**
     * @return {Position[]}
     */
    getAllBottom() {
        let squares = [];
        for (let y = this.y - 1; y >= 0; y--) {
            squares.push(new Position(this.x, y));
        }
        return squares;
    }

    /**
     * @return {Position[]}
     */
    getAllLeft() {
        let squares = [];
        for (let x = this.x - 1; x >= 0; x--) {
            squares.push(new Position(x, this.y));
        }
        return squares;
    }

    /**
     * @return {Position[]}
     */
    getAllRight() {
        let squares = [];
        for (let x = this.x + 1; x < BOARD_WIDTH; x++) {
            squares.push(new Position(x, this.y));
        }
        return squares;
    }

    getTopLeft() {
        return new Position(this.x - 1, this.y + 1);
    }

    getTopRight() {
        return new Position(this.x + 1, this.y + 1);
    }

    getBottomLeft() {
        return new Position(this.x - 1, this.y - 1);
    }

    getBottomRight() {
        return new Position(this.x + 1, this.y - 1);
    }

    isEqualsTo(other) {
        return this.x === other.x && this.y === other.y;
    }

    /**
     * 0-based
     */
    toUci() {
        return this.file + this.y;
    }

    /**
     * 1-based
     */
    toAlgebraic() {
        return this.file + (this.y + 1);
    }

    toString() {
        return this.toUci();
    }

    /**
     * @return {Position[]}
     */
    static getAll() {
        let positions = [];
        for (let x = 0; x < BOARD_WIDTH; x++) {
            for (let y = 0; y < BOARD_HEIGHT; y++) {
                positions.push(new Position(x, y));
            }
        }
        return positions;
    }

    static areEquals(p1, p2) {
        return p1 != null && p2 != null && p1.isEqualsTo(p2);
    }

    /**
     * @param uci {string}
     * @return {Position}
     */
    static parseUci(uci) {
        if (uci.length !== 2) {
            throw new Error('Incorrect position UCI: ' + uci);
        }
        let x = UCI_LETTER.indexOf(uci[0])
        if (x < 0) {
            throw new Error('Incorrect position UCI: ' + uci);
        }
        if (x >= BOARD_WIDTH) {
            throw new Error('Incorrect position UCI: ' + uci);
        }
        return new Position(x, Number(uci[1]));
    }

}

class PhysicalPiece {

    #pieceChar;
    #initPosition;

    /**
     * @param pieceChar {string}
     * @param initPosition {Position}
     */
    constructor(pieceChar, initPosition) {
        if (!PIECES_CHARS.includes(pieceChar.toLowerCase())) {
            throw new Error('Invalid piece ' + pieceChar);
        }

        this.#pieceChar = pieceChar;
        this.#initPosition = initPosition;
    }

    /**
     * @return {string}
     */
    get pieceChar() {
        return this.#pieceChar;
    }

    get color() {
        return charToPieceColor(this.#pieceChar);
    }

    /**
     * @param other {PhysicalPiece}
     * @return {boolean}
     */
    isEqualsTo(other) {
        return other != null &&
            this.#pieceChar === other.#pieceChar &&
            this.#initPosition.isEqualsTo(other.#initPosition);
    }

    toString() {
        return `${this.#pieceChar}{${this.#initPosition.toAlgebraic()}}`;
    }

}

class PieceAtPosition {

    /**
     * @param piece {PhysicalPiece}
     * @param position {Position}
     */
    constructor(piece, position) {
        this.piece = piece;
        this.position = position;
    }

    /**
     * @return {string}
     */
    get pieceColor() {
        return this.piece.color;
    }

    isColor(color) {
        return this.pieceColor === color;
    }

    /**
     * @param other {PieceAtPosition}
     */
    isEqualsTo(other) {
        return other != null &&
            this.piece.pieceChar === other.piece.pieceChar &&
            this.position.isEqualsTo(other.position);
    }

    toString() {
        return this.piece.pieceChar + ' at ' + this.position.toAlgebraic();
    }

}

class HalfMove {

    /**
     * @type {Position}
     */
    from;

    /**
     * @type {Position}
     */
    to;

    constructor(from, to) {
        this.from = from;
        this.to = to;
    }

    toUci() {
        return this.from.toUci() + this.to.toUci();
    }

    toAlgebraic() {
        return this.from.toAlgebraic() + this.to.toAlgebraic();
    }

    isHorizontal() {
        return this.from.y === this.to.y;
    }

    isVertical() {
        return this.from.x === this.to.x;
    }

    toString() {
        return this.toAlgebraic();
    }

    /**
     * @param uci {string}
     * @return {HalfMove}
     */
    static parseUci(uci) {
        if (uci.length !== 4) {
            throw new Error('Incorrect move UCI: ' + uci);
        }
        let from = Position.parseUci(uci.substring(0, 2));
        let to = Position.parseUci(uci.substring(2, 4));
        return new HalfMove(from, to);
    }

    /**
     * @param movesAsUci {string[]}
     */
    static parseUciMultipleMoves(movesAsUci) {
        return movesAsUci.map(moveAsUci => HalfMove.parseUci(moveAsUci));
    }

    static areEquals(m1, m2) {
        return m1 != null && m2 != null && Position.areEquals(m1.from, m2.from) && Position.areEquals(m1.to, m2.to);
    }

    static uciToAlgebraic(uci) {
        return HalfMove.parseUci(uci).toAlgebraic();
    }

}

class Board {

    #content = [];
    #redToPlay = true;
    #enforceColorTurn = true;
    #fullMovesCounts = 0;

    constructor() {
        for (let x = 0; x < BOARD_WIDTH; x++) {
            this.#content.push([null]);
            for (let y = 0; y < BOARD_HEIGHT; y++) {
                this.#content[x][y] = null;
            }
        }
    }

    // TODO: update "full moves count" accordingly
    loadFen(fen) {
        const split = fen.split(' ');
        const positionsFen = split[0];
        const gameStateFen = split[1];
        const fenLines = positionsFen.trim().split("/")
        if (fenLines.length !== BOARD_HEIGHT) {
            throw new Error('Invalid FEN: wrong number of component');
        }
        this.clearBoard();
        for (let y = BOARD_HEIGHT - 1; y >= 0; y--) {
            this.#loadFenLine(fenLines[BOARD_HEIGHT - 1 - y], y);
        }

        switch (gameStateFen.toLowerCase().trim()[0]) {
            case 'w':
            case 'r':
                this.#redToPlay = true;
                break;
            case 'b':
                this.#redToPlay = false;
                break;
            default:
                throw new Error('Invalid FEN: can not determine which side plays next');
        }
    }

    /**
     * @param fenLine {string}
     * @param y {number}
     */
    #loadFenLine(fenLine, y) {
        let x = 0
        for (let c = 0; fenLine.length - 1 && x < BOARD_WIDTH; c++) {
            const char = fenLine.charAt(c);
            if (char >= '0' && char <= '9') {
                x += Number(char);
            } else {
                this.#content[x][y] = new PhysicalPiece(char, new Position(x, y));
                x++;
            }
        }
    }

    outputFen() {
        let ranks = [];
        for (let y = BOARD_HEIGHT - 1; y >= 0; y--) {
            ranks.push(this.#rankToFen(y));
        }
        return ranks.join('/') + ' ' + colorToUci(this.getColorToPlay()) + ' - - 0 ' + this.#fullMovesCounts.toString();
    }

    #rankToFen(rank) {
        let rankPieces = Position
            .getAll()
            .filter(position => position.y === rank)
            .map(position => this.getPieceAt(position));

        let count = 0
        let result = '';
        rankPieces.forEach(piece => {
            if (piece == null) {
                count += 1;
            } else {
                if (count > 0) {
                    result += count.toString();
                    count = 0;
                }
                result = result.concat(piece.pieceChar);
            }
        });
        if (count > 0) {
            result += count.toString();
        }

        return result;
    }

    /**
     * @param position {Position}
     * @return {PhysicalPiece|null}
     */
    getPieceAt(position) {
        return this.#content[position.x][position.y];
    }

    /**
     * @param position {Position}
     */
    removePieceFrom(position) {
        this.#content[position.x][position.y] = null;
    }

    /**
     *
     * @param pieceChar {string}
     * @param position {Position}
     * @param enforcePlacementRules {boolean}
     */
    addPieceAt(pieceChar, position, enforcePlacementRules) {
        if (!PIECES_CHARS.includes(pieceChar.toLowerCase())) {
            throw new Error('Invalid char: ' + pieceChar);
        }

        // TODO: enforce rules (elephant, king, etc.)

        const physicalPiece = new PhysicalPiece(pieceChar, position);
        this.#setPieceAt(physicalPiece, position);
    }

    /**
     * @param piece {PhysicalPiece}
     * @param position {Position}
     */
    #setPieceAt(piece, position) {
        this.#content[position.x][position.y] = piece;
    }

    /**
     * @return {PieceAtPosition[]}
     */
    listPiecePositions() {
        return Position
            .getAll()
            .filter(position => this.#hasPieceAt(position))
            .map(position => new PieceAtPosition(this.getPieceAt(position), position));
    }

    /**
     * Find all positions where a piece of type {@param piece} is located
     * (i.e. max 5 for pawns, max 2 for knights, etc.)
     *
     * @param pieceChar {string}
     * @return {Position[]}
     */
    listPositionsForPiece(pieceChar) {
        let result = [];
        Position.getAll().forEach(position => {
            let physicalPiece = this.getPieceAt(position);
            if (physicalPiece != null && physicalPiece.pieceChar === pieceChar) {
                result.push(position);
            }
        });
        return result;
    }

    clearBoard() {
        this.#fullMovesCounts = 0;
        Position.getAll().forEach(position => this.removePieceFrom(position));
    }

    /**
     * @return {boolean} true if the piece at {@param position} matches the color that has to play now,
     * or if "enforcedColorTurn" is disabled
     */
    isAllowedToPlayPieceAt(position) {
        if (this.#enforceColorTurn) {
            return this.#hasPieceAt(position) && this.isColorToPlay(this.getPieceAt(position));
        } else {
            // FIXME: here we don't check it's not empty? doesn't seem consistent
            return true;
        }
    }

    getColorToPlay() {
        if (this.#redToPlay) {
            return Color.RED;
        } else {
            return Color.BLACK;
        }
    }

    /**
     * @param piece {PhysicalPiece}
     * @return {boolean}
     */
    isColorToPlay(piece) {
        return (piece.color === Color.RED && this.#redToPlay) || (piece.color === Color.BLACK && !this.#redToPlay);
    }

    /**
     * @param color {string}
     */
    forceColorToPlay(color) {
        this.#redToPlay = (color === Color.RED);
    }

    /**
     * Return captured piece, or null if no piece was taken
     */
    registerMove(move) {
        let piece = this.getPieceAt(move.from);
        if (piece == null) {
            throw new Error('No piece to move at ' + move.from);
        } else if (this.#enforceColorTurn && !this.isColorToPlay(piece)) {
            throw new Error(`It is ${this.getColorToPlay()}'s turn to play`);
        } else {
            if (this.#isPossibleMove(move)) {
                return this.#registerMove(piece, move)
            } else {
                throw new Error(`Move ${move} is not possible for ${piece.pieceChar}`);
            }
        }
    }

    /**
     * @param piece {PhysicalPiece}
     * @param move {HalfMove}
     * @return {PhysicalPiece|null} captured piece, or null if no piece was taken
     */
    #registerMove(piece, move) {
        let targetPiece = this.getPieceAt(move.to);
        this.removePieceFrom(move.from);
        this.#setPieceAt(piece, move.to);
        this.#redToPlay = !this.#redToPlay;
        if (piece.color === Color.BLACK) {
            this.#fullMovesCounts++;
        }
        return targetPiece;
    }

    /**
     * @param color {string}
     * @return {boolean}
     */
    isInCheck(color) {
        let generalPosition = this.findGeneral(color).position;
        return this
            .#listAllMovesForColor(reverseColor(color))
            .map(move => move.to)
            .some(position => position.isEqualsTo(generalPosition));
    }

    isCheckmate(color) {
        return color === this.getColorToPlay() && this.isInCheck(color) && this.#allMovesAreIllegal(color);
    }

    isStalemate(color) {
        return color === this.getColorToPlay() && !this.isInCheck(color) && this.#allMovesAreIllegal(color);
    }

    #allMovesAreIllegal(color) {
        return this.#listAllMovesForColor(color).every(move => this.#isMoveIllegal(move, color));
    }

    isMated() {
        return this.isCheckmate(Color.RED) ||
            this.isCheckmate(Color.BLACK) ||
            this.isStalemate(Color.RED) ||
            this.isStalemate(Color.BLACK);
    }

    /**
     * @param color
     * @return {PieceAtPosition|null}
     */
    findGeneral(color) {
        return this
            .listPiecePositions()
            .find(piecePosition =>
                piecePosition.piece.pieceChar.toUpperCase() === 'K' && piecePosition.isColor(color)
            );
    }

    #isMoveIllegal(move, color) {
        let boardCopy = this.copy();
        if (boardCopy.#isPossibleMove(move)) {
            boardCopy.registerMove(move);
            return boardCopy.#areGeneralsFacing() || boardCopy.isInCheck(color);
        } else {
            return true;
        }
    }

    #areGeneralsFacing() {
        let red = this.findGeneral(Color.RED);
        let black = this.findGeneral(Color.BLACK);
        if (red == null || black == null) {
            return false;
        } else {
            let redGeneralPosition = red.position;
            let blackGeneralPosition = black.position;
            return red.position.x === black.position.x && this.#noPieceOnFileBetween(redGeneralPosition, blackGeneralPosition);
        }
    }

    #noPieceOnFileBetween(p1, p2) {
        if (p1.x !== p2.x) {
            return false;
        } else {
            for (let y = p1.y + 1; y < p2.y; y++) {
                let position = new Position(p1.x, y);
                if (this.#hasPieceAt(position)) {
                    return false;
                }
            }
        }
        return true;
    }

    #listAllMovesForColor(color) {
        return this
            .listPiecePositions()
            .filter(piecePosition => piecePosition.isColor(color))
            .flatMap(piecePosition => this.#listAllMovesFrom(piecePosition.position));
    }

    #isPossibleMove(move) {
        return this
            .#listAllMovesFromAsPositions(move.from)
            .some(targetPosition => Position.areEquals(move.to, targetPosition));
    }

    isLegalMove(move) {
        let color = this.getColorAt(move.from);
        if (color == null) {
            return false;
        } else {
            return !this.#isMoveIllegal(move, color);
        }
    }

    /**
     * @returns {HalfMove[]}
     */
    listLegalMovesFrom(position) {
        let color = this.getColorAt(position);
        if (color == null) {
            return [];
        } else {
            return this.#listAllMovesFrom(position).filter(move => !this.#isMoveIllegal(move, color));
        }
    }

    /**
     * Lists every legal move available to the given color (defaults to the
     * color whose turn it is to play).
     *
     * @param color {string} optional, defaults to {@link getColorToPlay}
     * @returns {HalfMove[]}
     */
    listAllLegalMoves(color = this.getColorToPlay()) {
        const moves = [];
        for (let x = 0; x < BOARD_WIDTH; x++) {
            for (let y = 0; y < BOARD_HEIGHT; y++) {
                const pos = new Position(x, y);
                if (this.getColorAt(pos) === color) {
                    moves.push(...this.listLegalMovesFrom(pos));
                }
            }
        }
        return moves;
    }

    /**
     * @returns Array of {@class HalfMove} one can go to from {@param from}. Includes illegal moves (e.g. that would put player in check)
     */
    #listAllMovesFrom(from) {
        return this
            .#listAllMovesFromAsPositions(from)
            .map(to => new HalfMove(from, to));
    }

    /**
     * @returns {Position[]} one can go to from {@param position}. Includes illegal moves (e.g. that would put player in check)
     */
    #listAllMovesFromAsPositions(position) {
        switch (this.getPieceAt(position).pieceChar.toLowerCase()) {
            case 'c':
                return this.#listMovesForCannon(position);
            case 'r':
                return this.#listMovesForChariot(position);
            case 'n':
                return this.#listMovesForHorse(position);
            case 'b':
                return this.#listMovesForElephant(position);
            case 'a':
                return this.#listMovesForAdvisor(position);
            case 'k':
                return this.#listMovesForGeneral(position);
            case 'p':
                return this.#listMovesForSoldier(position);
            case 'w':
                return this.#listMovesForSuperChariot(position);
            default:
                throw new Error('Not implemented for ' + this.getPieceAt(position).pieceChar)
        }
    }

    #listMovesForCannon(position) {
        let result = [];
        result = result.concat(this.#filterMovesForCannon(position, position.getAllTop()));
        result = result.concat(this.#filterMovesForCannon(position, position.getAllBottom()));
        result = result.concat(this.#filterMovesForCannon(position, position.getAllLeft()));
        result = result.concat(this.#filterMovesForCannon(position, position.getAllRight()));
        return result;
    }

    #filterMovesForCannon(position, allTargetPosition) {
        let result = [];
        let foundPivot = false;

        for (let i = 0; i < allTargetPosition.length; i++) {
            let targetPosition = allTargetPosition[i];
            if (!foundPivot) {
                if (!this.#hasPieceAt(targetPosition)) {
                    result.push(targetPosition);
                } else {
                    foundPivot = true;
                }
            } else {
                if (this.containOppositeColors(position, targetPosition)) {
                    result.push(targetPosition);
                    return result;
                } else if (this.containSameColors(position, targetPosition)) {
                    return result;
                }
            }
        }

        return result;
    }

    #listMovesForChariot(position) {
        let result = [];
        result = result.concat(this.#filterMovesForChariot(position, position.getAllTop()));
        result = result.concat(this.#filterMovesForChariot(position, position.getAllBottom()));
        result = result.concat(this.#filterMovesForChariot(position, position.getAllLeft()));
        result = result.concat(this.#filterMovesForChariot(position, position.getAllRight()));
        return result;
    }

    #filterMovesForChariot(position, allTargetPositions) {
        let result = [];

        for (let i = 0; i < allTargetPositions.length; i++) {
            let targetPosition = allTargetPositions[i];
            if (!this.#hasPieceAt(targetPosition)) {
                result.push(targetPosition);
            } else if (this.containOppositeColors(position, targetPosition)) {
                result.push(targetPosition);
                return result;
            } else if (this.containSameColors(position, targetPosition)) {
                return result;
            }
        }

        return result;
    }

    #listMovesForHorse(position) {
        let result = [];
        let top = position.getTop();
        let bottom = position.getBottom();
        let left = position.getLeft();
        let right = position.getRight();
        if (top.existsOnBoard() && !this.#hasPieceAt(top)) {
            result.push(top.getTopLeft());
            result.push(top.getTopRight());
        }
        if (bottom.existsOnBoard() && !this.#hasPieceAt(bottom)) {
            result.push(bottom.getBottomLeft());
            result.push(bottom.getBottomRight());
        }
        if (left.existsOnBoard() && !this.#hasPieceAt(left)) {
            result.push(left.getTopLeft());
            result.push(left.getBottomLeft());
        }
        if (right.existsOnBoard() && !this.#hasPieceAt(right)) {
            result.push(right.getTopRight());
            result.push(right.getBottomRight());
        }

        return result.filter(targetPosition =>
            targetPosition.existsOnBoard() && !this.containSameColors(targetPosition, position)
        );
    }

    #listMovesForElephant(position) {
        let result = [];
        let topLeft = position.getTopLeft();
        let topRight = position.getTopRight();
        let bottomRight = position.getBottomRight();
        let bottomLeft = position.getBottomLeft();

        if (topLeft.existsOnBoard() && !this.#hasPieceAt(topLeft)) {
            result.push(topLeft.getTopLeft());
        }
        if (topRight.existsOnBoard() && !this.#hasPieceAt(topRight)) {
            result.push(topRight.getTopRight());
        }
        if (bottomRight.existsOnBoard() && !this.#hasPieceAt(bottomRight)) {
            result.push(bottomRight.getBottomRight());
        }
        if (bottomLeft.existsOnBoard() && !this.#hasPieceAt(bottomLeft)) {
            result.push(bottomLeft.getBottomLeft());
        }

        return result.filter(targetPosition =>
            targetPosition.existsOnBoard() &&
            !this.containSameColors(position, targetPosition) &&
            !this.#areOnOppositeSidesOfTheRiver(position, targetPosition)
        );
    }

    #listMovesForAdvisor(position) {
        let result = [];
        result.push(position.getTopLeft());
        result.push(position.getTopRight());
        result.push(position.getBottomRight());
        result.push(position.getBottomLeft());
        return this.#filterMovesInItsPalace(position, result);
    }

    #listMovesForGeneral(position) {
        let result = [];
        result.push(position.getTop());
        result.push(position.getBottom());
        result.push(position.getLeft());
        result.push(position.getRight());
        return this.#filterMovesInItsPalace(position, result);
    }

    #filterMovesInItsPalace(position, allTargetPosition) {
        if (this.#hasRedPieceAt(position)) {
            return allTargetPosition.filter(target =>
                target.existsOnBoard() && target.isInRedPalace() && !this.#hasRedPieceAt(target)
            );
        } else if (this.#hasBlackPieceAt(position)) {
            return allTargetPosition.filter(target =>
                target.existsOnBoard() && target.isInBlackPalace() && !this.#hasBlackPieceAt(target)
            );
        } else {
            console.error("we should never pass here")
            return [];
        }
    }

    #listMovesForSoldier(position) {
        let result = [];
        if (this.#hasRedPieceAt(position)) {
            // is red
            result.push(position.getTop());
            if (this.#areOnOppositeSidesOfTheRiver(position, Position.redGeneralStartingPosition)) {
                result.push(position.getLeft());
                result.push(position.getRight());
            }
        } else if (this.#hasBlackPieceAt(position)) {
            // is black
            result.push(position.getBottom());
            if (this.#areOnOppositeSidesOfTheRiver(position, Position.blackGeneralStartingPosition)) {
                result.push(position.getLeft());
                result.push(position.getRight());
            }
        } else {
            console.warn("we should never pass here")
            return [];
        }

        return result.filter(targetPosition =>
            targetPosition.existsOnBoard() && !this.containSameColors(position, targetPosition)
        );
    }

    /**
     * Super-chariot (Manchu Banner): combines the powers of the chariot, horse, and cannon.
     */
    #listMovesForSuperChariot(position) {
        let result = [];
        // Chariot moves
        result = result.concat(this.#filterMovesForChariot(position, position.getAllTop()));
        result = result.concat(this.#filterMovesForChariot(position, position.getAllBottom()));
        result = result.concat(this.#filterMovesForChariot(position, position.getAllLeft()));
        result = result.concat(this.#filterMovesForChariot(position, position.getAllRight()));
        // Cannon moves
        result = result.concat(this.#filterMovesForCannon(position, position.getAllTop()));
        result = result.concat(this.#filterMovesForCannon(position, position.getAllBottom()));
        result = result.concat(this.#filterMovesForCannon(position, position.getAllLeft()));
        result = result.concat(this.#filterMovesForCannon(position, position.getAllRight()));
        // Horse moves
        const top = position.getTop();
        const bottom = position.getBottom();
        const left = position.getLeft();
        const right = position.getRight();
        if (top.existsOnBoard() && !this.#hasPieceAt(top)) {
            result.push(top.getTopLeft());
            result.push(top.getTopRight());
        }
        if (bottom.existsOnBoard() && !this.#hasPieceAt(bottom)) {
            result.push(bottom.getBottomLeft());
            result.push(bottom.getBottomRight());
        }
        if (left.existsOnBoard() && !this.#hasPieceAt(left)) {
            result.push(left.getTopLeft());
            result.push(left.getBottomLeft());
        }
        if (right.existsOnBoard() && !this.#hasPieceAt(right)) {
            result.push(right.getTopRight());
            result.push(right.getBottomRight());
        }
        // Filter: must be on board and not capture same-color pieces
        return result.filter(targetPosition =>
            targetPosition.existsOnBoard() && !this.containSameColors(position, targetPosition)
        );
    }

    /**
     * @param position {Position}
     * @returns {null|string}
     */
    getColorAt(position) {
        let piece = this.getPieceAt(position);
        if (piece != null) {
            return piece.color;
        } else {
            return null;
        }
    }

    /**
     * @param position {Position}
     * @return {boolean}
     */
    #hasPieceAt(position) {
        return this.#content[position.x][position.y] != null;
    }

    /**
     * @param position {Position}
     * @return {boolean}
     */
    #hasRedPieceAt(position) {
        return this.#hasPieceOfColorAt(Color.RED, position);
    }

    /**
     * @param position {Position}
     * @return {boolean}
     */
    #hasBlackPieceAt(position) {
        return this.#hasPieceOfColorAt(Color.BLACK, position);
    }

    #hasPieceOfColorAt(color, position) {
        let piece = this.getPieceAt(position);
        return piece != null && piece.color === color;
    }

    #areOnOppositeSidesOfTheRiver(p1, p2) {
        return (p1.y <= 4 && p2.y >= 5) || (p2.y <= 4 && p1.y >= 5);
    }

    containSameColors(p1, p2) {
        return (this.#hasRedPieceAt(p1) && this.#hasRedPieceAt(p2))
            || (this.#hasBlackPieceAt(p1) && this.#hasBlackPieceAt(p2));
    }

    containOppositeColors(p1, p2) {
        return (this.#hasRedPieceAt(p1) && this.#hasBlackPieceAt(p2))
            || (this.#hasRedPieceAt(p2) && this.#hasBlackPieceAt(p1));
    }

    printBoardToLines() {
        let lines = [];
        for (let y = BOARD_HEIGHT - 1; y >= 0; y--) {
            let row = '';
            for (let x = 0; x < BOARD_WIDTH; x++) {
                let piece = this.getPieceAt(new Position(x, y));
                if (piece == null) {
                    row += '  ';
                } else {
                    row += piece.pieceChar + ' ';
                }
            }
            lines.push(row);
        }
        return lines;
    }

    printBoard() {
        let oneLine = '';
        this.printBoardToLines().forEach(line => oneLine += line + '\n');
        console.log(oneLine);
    }

    /**
     * @return {Board}
     */
    copy() {
        let copy = new Board();
        copy.loadFen(this.outputFen());
        return copy
    }

}
