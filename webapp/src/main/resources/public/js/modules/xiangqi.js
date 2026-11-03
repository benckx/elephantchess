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
const PIECES_CHARS = ['c', 'r', 'n', 'b', 'a', 'k', 'p', 'm'];

const DEFAULT_START_FEN = 'rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0';

/**
 * Manchu chess (Yitong) start FEN.
 * Red has only: general, 2 advisors, 2 elephants, 5 soldiers, and the super-chariot (M/m) at a1.
 * The super-chariot combines the powers of the chariot, horse, and cannon.
 */
const MANCHU_START_FEN = 'rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/9/9/M1BAKAB2 w - - 0 0';

// TODO: there are actually multiple start FEN
const JANGGI_START_FEN = 'rnba1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RNBA1ABNR w - - 0 1';

// Piece color enum. Lives here (rather than in enums.js) so that xiangqi.js
// can be used standalone (together with board-gui.js) without pulling in the
// rest of the webapp's modules.
// Under the hood we keep engine-side colors neutral (WHITE/BLACK).
const Color = Object.freeze({
    WHITE: 'WHITE',
    BLACK: 'BLACK',
});

/**
 * @param char {string}
 * @return {boolean}
 */
function isWhitePiece(char) {
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
        if (isWhitePiece(char)) {
            return Color.WHITE;
        } else if (isBlackPiece(char)) {
            return Color.BLACK;
        } else {
            throw new Error('Illegal piece ' + char);
        }
    }
}

function reverseColor(color) {
    switch (color.toUpperCase()) {
        case Color.WHITE:
            return Color.BLACK;
        case Color.BLACK:
            return Color.WHITE;
        default:
            throw Error('Illegal argument color ' + color);
    }
}

function colorToUci(color) {
    switch (color.toUpperCase()) {
        case Color.WHITE:
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
    let board = new XiangqiBoard();
    board.loadFen(fen);

    if (board.isCheckmate(Color.WHITE) || board.isCheckmate(Color.BLACK)) {
        throw new Error('Start position is checkmate');
    } else if (board.isStalemate(Color.WHITE) || board.isStalemate(Color.BLACK)) {
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

    let board = new XiangqiBoard();
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
            if (board.isCheckmate(Color.WHITE) || board.isCheckmate(Color.BLACK)) {
                checkIndicator += '#';
            } else if (board.isInCheck(Color.WHITE) || board.isInCheck(Color.BLACK)) {
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
    const board = new XiangqiBoard();
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
            case Color.WHITE:
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
        if ((color === Color.BLACK && move.from.y > move.to.y) || (color === Color.WHITE && move.from.y < move.to.y)) {
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

            let isOnTop = (currentFileY > otherFileY && color === Color.WHITE)
                || (currentFileY < otherFileY && color === Color.BLACK);

            return isOnTop ? '+' : '-';
        }

        return fileAsWxf(color, move.from.x).toString();
    }

    let formattedMoves = [];
    let board = new XiangqiBoard();
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
            case 'M':
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
    const board = new XiangqiBoard();
    board.loadFen(startFen);
    moves.forEach(move => board.registerMove(move));
    return board.outputFen();
}

/**
 * @return {Position} White general's starting position in Xiangqi.
 */
function xiangqiWhiteGeneralStartingPosition() {
    return new Position(Math.floor(BOARD_WIDTH / 2), 0);
}

/**
 * @return {Position} Black general's starting position in Xiangqi.
 */
function xiangqiBlackGeneralStartingPosition() {
    return new Position(Math.floor(BOARD_WIDTH / 2), BOARD_HEIGHT - 1);
}

class Position {

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

    isInWhitePalace() {
        return this.x >= 3 &&
            this.x <= 5 &&
            this.y <= 2;
    }

    isInBlackPalace() {
        return this.x >= 3 &&
            this.x <= 5 &&
            this.y >= BOARD_HEIGHT - 3;
    }

    isInAnyPalace() {
        return this.isInWhitePalace() || this.isInBlackPalace();
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
    #whiteToPlay = true;
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
                this.#whiteToPlay = true;
                break;
            case 'b':
                this.#whiteToPlay = false;
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
            .filter(position => this.hasPieceAt(position))
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
            return this.hasPieceAt(position) && this.isColorToPlay(this.getPieceAt(position));
        } else {
            // FIXME: here we don't check it's not empty? doesn't seem consistent
            return true;
        }
    }

    getColorToPlay() {
        if (this.#whiteToPlay) {
            return Color.WHITE;
        } else {
            return Color.BLACK;
        }
    }

    /**
     * @param piece {PhysicalPiece}
     * @return {boolean}
     */
    isColorToPlay(piece) {
        return (piece.color === Color.WHITE && this.#whiteToPlay) || (piece.color === Color.BLACK && !this.#whiteToPlay);
    }

    /**
     * @param color {string}
     */
    forceColorToPlay(color) {
        this.#whiteToPlay = (color === Color.WHITE);
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
            if (this.isPossibleMove(move)) {
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
        this.#whiteToPlay = !this.#whiteToPlay;
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
        return this.#listAllMovesForColor(color).every(move => this.isMoveIllegal(move, color));
    }

    isMated() {
        return this.isCheckmate(Color.WHITE) ||
            this.isCheckmate(Color.BLACK) ||
            this.isStalemate(Color.WHITE) ||
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

    isMoveIllegal(move, color) {
        throw new Error('Abstract method, should be implemented in subclass');
    }

    #listAllMovesForColor(color) {
        return this
            .listPiecePositions()
            .filter(piecePosition => piecePosition.isColor(color))
            .flatMap(piecePosition => this.listAllMovesFrom(piecePosition.position));
    }

    // TODO: strange naming, more like "pseudo-legal move"
    /**
     * @param move {HalfMove}
     * @returns {boolean}
     */
    isPossibleMove(move) {
        return this
            .listAllMovesFrom(move.from)
            .some(candidate => Position.areEquals(move.to, candidate.to));
    }

    isLegalMove(move) {
        let color = this.getColorAt(move.from);
        if (color == null) {
            return false;
        } else {
            return !this.isMoveIllegal(move, color);
        }
    }

    /**
     * @param position {Position}
     * @returns {HalfMove[]} Legal moves from the position.
     */
    listLegalMovesFrom(position) {
        let color = this.getColorAt(position);
        if (color == null) {
            return [];
        } else {
            return this.listAllMovesFrom(position).filter(move => !this.isMoveIllegal(move, color));
        }
    }

    /**
     * Lists every legal move available to the given color (defaults to the
     * color whose turn it is to play).
     *
     * @param color {string} optional, defaults to {@link getColorToPlay}
     * @returns {HalfMove[]} Legal moves for the color.
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
     * @param from {Position}
     * @returns {HalfMove[]} Moves from the position, including moves that leave the player in check.
     */
    listAllMovesFrom(from) {
        throw new Error('Abstract method, should be implemented in subclass');
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
    hasPieceAt(position) {
        return this.#content[position.x][position.y] != null;
    }

    /**
     * @param position {Position}
     * @return {boolean}
     */
    hasWhitePieceAt(position) {
        return this.#hasPieceOfColorAt(Color.WHITE, position);
    }

    /**
     * @param position {Position}
     * @return {boolean}
     */
    hasBlackPieceAt(position) {
        return this.#hasPieceOfColorAt(Color.BLACK, position);
    }

    #hasPieceOfColorAt(color, position) {
        let piece = this.getPieceAt(position);
        return piece != null && piece.color === color;
    }

    containSameColors(p1, p2) {
        return (this.hasWhitePieceAt(p1) && this.hasWhitePieceAt(p2))
            || (this.hasBlackPieceAt(p1) && this.hasBlackPieceAt(p2));
    }

    containOppositeColors(p1, p2) {
        return (this.hasWhitePieceAt(p1) && this.hasBlackPieceAt(p2))
            || (this.hasWhitePieceAt(p2) && this.hasBlackPieceAt(p1));
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
        throw new Error('Abstract method, should be implemented in subclass');
    }

}

class XiangqiBoard extends Board {

    /**
     *
     * @param move {HalfMove}
     * @param color {string}
     * @returns {boolean}
     */
    isMoveIllegal(move, color) {
        const boardCopy = this.copy();
        if (boardCopy.isPossibleMove(move)) {
            boardCopy.registerMove(move);
            return boardCopy.#areGeneralsFacing() || boardCopy.isInCheck(color);
        } else {
            return true;
        }
    }

    /**
     * @param from {Position}
     * @returns {HalfMove[]} Moves from the position, including moves that leave the player in check.
     */
    listAllMovesFrom(from) {
        return this
            .#listAllMovesFromAsPositions(from)
            .map(to => new HalfMove(from, to));
    }

    /** @returns {Position[]} Target positions, including moves that leave the player in check. */
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
            case 'm':
                return this.#listMovesForSuperChariot(position);
            default:
                throw new Error('Not implemented for ' + this.getPieceAt(position).pieceChar)
        }
    }

    /**
     * @param position {Position}
     * @returns {Position[]} Target positions reachable by the cannon.
     */
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
                if (!this.hasPieceAt(targetPosition)) {
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

    /**
     * @param position {Position}
     * @returns {Position[]} Target positions reachable by the chariot.
     */
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
            if (!this.hasPieceAt(targetPosition)) {
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

    /**
     * @param position {Position}
     * @returns {Position[]} Target positions reachable by the horse.
     */
    #listMovesForHorse(position) {
        let result = [];
        let top = position.getTop();
        let bottom = position.getBottom();
        let left = position.getLeft();
        let right = position.getRight();
        if (top.existsOnBoard() && !this.hasPieceAt(top)) {
            result.push(top.getTopLeft());
            result.push(top.getTopRight());
        }
        if (bottom.existsOnBoard() && !this.hasPieceAt(bottom)) {
            result.push(bottom.getBottomLeft());
            result.push(bottom.getBottomRight());
        }
        if (left.existsOnBoard() && !this.hasPieceAt(left)) {
            result.push(left.getTopLeft());
            result.push(left.getBottomLeft());
        }
        if (right.existsOnBoard() && !this.hasPieceAt(right)) {
            result.push(right.getTopRight());
            result.push(right.getBottomRight());
        }

        return result.filter(targetPosition =>
            targetPosition.existsOnBoard() && !this.containSameColors(targetPosition, position)
        );
    }

    /**
     * @param position {Position}
     * @returns {Position[]} Target positions reachable by the elephant.
     */
    #listMovesForElephant(position) {
        let result = [];
        let topLeft = position.getTopLeft();
        let topRight = position.getTopRight();
        let bottomRight = position.getBottomRight();
        let bottomLeft = position.getBottomLeft();

        if (topLeft.existsOnBoard() && !this.hasPieceAt(topLeft)) {
            result.push(topLeft.getTopLeft());
        }
        if (topRight.existsOnBoard() && !this.hasPieceAt(topRight)) {
            result.push(topRight.getTopRight());
        }
        if (bottomRight.existsOnBoard() && !this.hasPieceAt(bottomRight)) {
            result.push(bottomRight.getBottomRight());
        }
        if (bottomLeft.existsOnBoard() && !this.hasPieceAt(bottomLeft)) {
            result.push(bottomLeft.getBottomLeft());
        }

        return result.filter(targetPosition =>
            targetPosition.existsOnBoard() &&
            !this.containSameColors(position, targetPosition) &&
            !this.#areOnOppositeSidesOfTheRiver(position, targetPosition)
        );
    }

    /**
     * @param position {Position}
     * @returns {Position[]} Target positions reachable by the advisor.
     */
    #listMovesForAdvisor(position) {
        let result = [];
        result.push(position.getTopLeft());
        result.push(position.getTopRight());
        result.push(position.getBottomRight());
        result.push(position.getBottomLeft());
        return this.#filterMovesInItsPalace(position, result);
    }

    /**
     * @param position {Position}
     * @returns {Position[]} Target positions reachable by the general.
     */
    #listMovesForGeneral(position) {
        let result = [];
        result.push(position.getTop());
        result.push(position.getBottom());
        result.push(position.getLeft());
        result.push(position.getRight());
        return this.#filterMovesInItsPalace(position, result);
    }

    #filterMovesInItsPalace(position, allTargetPosition) {
        if (this.hasWhitePieceAt(position)) {
            return allTargetPosition.filter(target =>
                target.existsOnBoard() &&
                target.isInWhitePalace() &&
                !this.hasWhitePieceAt(target)
            );
        } else if (this.hasBlackPieceAt(position)) {
            return allTargetPosition.filter(target =>
                target.existsOnBoard() &&
                target.isInBlackPalace() &&
                !this.hasBlackPieceAt(target)
            );
        } else {
            console.error("we should never pass here")
            return [];
        }
    }

    /**
     * @param position {Position}
     * @returns {Position[]} Target positions reachable by the soldier.
     */
    #listMovesForSoldier(position) {
        let result = [];
        if (this.hasWhitePieceAt(position)) {
            // is red
            result.push(position.getTop());
            if (this.#areOnOppositeSidesOfTheRiver(position, xiangqiWhiteGeneralStartingPosition())) {
                result.push(position.getLeft());
                result.push(position.getRight());
            }
        } else if (this.hasBlackPieceAt(position)) {
            // is black
            result.push(position.getBottom());
            if (this.#areOnOppositeSidesOfTheRiver(position, xiangqiBlackGeneralStartingPosition())) {
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
     * @param position {Position}
     * @returns {Position[]} Unique target positions reachable by the super-chariot.
     */
    #listMovesForSuperChariot(position) {
        let result = [];
        result = result.concat(this.#listMovesForChariot(position));
        result = result.concat(this.#listMovesForCannon(position));
        result = result.concat(this.#listMovesForHorse(position));

        // de-duplicate
        result = result.filter((targetPosition, index) =>
            result.findIndex(candidate => candidate.isEqualsTo(targetPosition)) === index
        );

        // Filter: must be on board and not capture same-color pieces
        return result.filter(targetPosition =>
            targetPosition.existsOnBoard() && !this.containSameColors(position, targetPosition)
        );
    }

    #areOnOppositeSidesOfTheRiver(p1, p2) {
        return (p1.y <= 4 && p2.y >= 5) || (p2.y <= 4 && p1.y >= 5);
    }

    /**
     * @returns {boolean}
     */
    #areGeneralsFacing() {
        const red = this.findGeneral(Color.WHITE);
        const black = this.findGeneral(Color.BLACK);
        if (red == null || black == null) {
            return false;
        } else {
            const redGeneralPosition = red.position;
            const blackGeneralPosition = black.position;
            return red.position.x === black.position.x && this.#noPieceOnFileBetween(redGeneralPosition, blackGeneralPosition);
        }
    }

    /**
     * @param p1 {Position}
     * @param p2 {Position}
     * @returns {boolean}
     */
    #noPieceOnFileBetween(p1, p2) {
        if (p1.x !== p2.x) {
            return false;
        } else {
            for (let y = p1.y + 1; y < p2.y; y++) {
                let position = new Position(p1.x, y);
                if (this.hasPieceAt(position)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @return {XiangqiBoard}
     */
    copy() {
        let copy = new XiangqiBoard();
        copy.loadFen(this.outputFen());
        return copy
    }

}

class JanggiBoard extends Board {

    /**
     * @param move {HalfMove}
     * @param color {string}
     * @returns {boolean}
     */
    isMoveIllegal(move, color) {
        const boardCopy = this.copy();
        if (boardCopy.isPossibleMove(move)) {
            boardCopy.registerMove(move);
            return boardCopy.isInCheck(color);
        } else {
            return true;
        }
    }

    /**
     * @param from {Position}
     * @returns {HalfMove[]} Moves from the position, including moves that leave the player in check.
     */
    listAllMovesFrom(from) {
        const piece = this.getPieceAt(from);
        if (piece == null) {
            return [];
        }

        let targets = [];
        switch (piece.pieceChar.toLowerCase()) {
            case 'c':
                targets = this.#listMovesForCannon(from);
                break;
            case 'r':
                targets = this.#listMovesForChariot(from);
                break;
            case 'n':
                targets = this.#listMovesForHorse(from);
                break;
            case 'b':
                targets = this.#listMovesForElephant(from);
                break;
            case 'a':
                targets = this.#listMovesForAdvisor(from);
                break;
            case 'k':
                targets = this.#listMovesForGeneral(from);
                break;
            case 'p':
                targets = this.#listMovesForSoldier(from);
                break;
            default:
                throw new Error('Not implemented for ' + piece.pieceChar);
        }

        return targets.map(to => new HalfMove(from, to));
    }

    copy() {
        const copy = new JanggiBoard();
        copy.loadFen(this.outputFen());
        return copy;
    }

    #listMovesForCannon(position) {
        let result = [];
        result = result.concat(this.#filterMovesForCannon(position, position.getAllTop()));
        result = result.concat(this.#filterMovesForCannon(position, position.getAllBottom()));
        result = result.concat(this.#filterMovesForCannon(position, position.getAllLeft()));
        result = result.concat(this.#filterMovesForCannon(position, position.getAllRight()));
        this.#listPalaceDiagonalLines(position).forEach(line => {
            result = result.concat(this.#filterMovesForCannon(position, line));
        });
        return result;
    }

    #filterMovesForCannon(position, allTargetPositions) {
        let result = [];
        let foundPivot = false;

        for (let i = 0; i < allTargetPositions.length; i++) {
            const targetPosition = allTargetPositions[i];
            const piece = this.getPieceAt(targetPosition);

            if (!foundPivot) {
                if (piece == null) {
                    // do nothing
                } else if (piece.pieceChar.toLowerCase() === 'c') {
                    return result;
                } else {
                    foundPivot = true;
                }
            } else {
                if (piece == null) {
                    result.push(targetPosition);
                } else if (piece.pieceChar.toLowerCase() === 'c' || this.containSameColors(position, targetPosition)) {
                    return result;
                } else if (this.containOppositeColors(position, targetPosition)) {
                    result.push(targetPosition);
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
        this.#listPalaceDiagonalLines(position).forEach(line => {
            result = result.concat(this.#filterMovesForChariot(position, line));
        });
        return result;
    }

    #filterMovesForChariot(position, allTargetPositions) {
        let result = [];

        for (let i = 0; i < allTargetPositions.length; i++) {
            const targetPosition = allTargetPositions[i];
            if (!this.hasPieceAt(targetPosition)) {
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
        if (top.existsOnBoard() && !this.hasPieceAt(top)) {
            result.push(top.getTopLeft());
            result.push(top.getTopRight());
        }
        if (bottom.existsOnBoard() && !this.hasPieceAt(bottom)) {
            result.push(bottom.getBottomLeft());
            result.push(bottom.getBottomRight());
        }
        if (left.existsOnBoard() && !this.hasPieceAt(left)) {
            result.push(left.getTopLeft());
            result.push(left.getBottomLeft());
        }
        if (right.existsOnBoard() && !this.hasPieceAt(right)) {
            result.push(right.getTopRight());
            result.push(right.getBottomRight());
        }

        return result.filter(targetPosition =>
            targetPosition.existsOnBoard() && !this.containSameColors(targetPosition, position)
        );
    }

    #listMovesForElephant(position) {
        let result = [];
        const moves = [
            {orthogonalStep: [0, 1], firstDiagonalSteps: [[-1, 1], [1, 1]]},
            {orthogonalStep: [0, -1], firstDiagonalSteps: [[-1, -1], [1, -1]]},
            {orthogonalStep: [1, 0], firstDiagonalSteps: [[1, -1], [1, 1]]},
            {orthogonalStep: [-1, 0], firstDiagonalSteps: [[-1, -1], [-1, 1]]},
        ];

        moves.forEach(move => {
            const firstStep = this.#offset(position, move.orthogonalStep[0], move.orthogonalStep[1]);
            if (!firstStep.existsOnBoard() || this.hasPieceAt(firstStep)) {
                return;
            }
            move.firstDiagonalSteps.forEach(diagonalStep => {
                const secondStep = this.#offset(firstStep, diagonalStep[0], diagonalStep[1]);
                const target = this.#offset(secondStep, diagonalStep[0], diagonalStep[1]);
                if (
                    secondStep.existsOnBoard() &&
                    !this.hasPieceAt(secondStep) &&
                    target.existsOnBoard() &&
                    !this.containSameColors(position, target)
                ) {
                    result.push(target);
                }
            });
        });

        return result;
    }

    #listMovesForAdvisor(position) {
        let result = [];
        result.push(position.getTop());
        result.push(position.getBottom());
        result.push(position.getLeft());
        result.push(position.getRight());
        result = result.concat(this.#listPalaceDiagonalNeighbors(position));
        return this.#filterMovesInItsPalace(position, result);
    }

    #listMovesForGeneral(position) {
        let result = [];
        result.push(position.getTop());
        result.push(position.getBottom());
        result.push(position.getLeft());
        result.push(position.getRight());
        result = result.concat(this.#listPalaceDiagonalNeighbors(position));
        return this.#filterMovesInItsPalace(position, result);
    }

    #filterMovesInItsPalace(position, allTargetPosition) {
        if (this.hasWhitePieceAt(position)) {
            return allTargetPosition.filter(target =>
                target.existsOnBoard() &&
                target.isInWhitePalace() &&
                !this.hasWhitePieceAt(target)
            );
        } else if (this.hasBlackPieceAt(position)) {
            return allTargetPosition.filter(target =>
                target.existsOnBoard() &&
                target.isInBlackPalace() &&
                !this.hasBlackPieceAt(target)
            );
        } else {
            console.error('we should never pass here');
            return [];
        }
    }

    #listMovesForSoldier(position) {
        let result = [position.getLeft(), position.getRight()];

        if (this.hasWhitePieceAt(position)) {
            result.push(position.getTop());
            if (position.isInBlackPalace()) {
                result = result.concat(this.#listForwardPalaceDiagonalMoves(position, Color.WHITE));
            }
        } else if (this.hasBlackPieceAt(position)) {
            result.push(position.getBottom());
            if (position.isInWhitePalace()) {
                result = result.concat(this.#listForwardPalaceDiagonalMoves(position, Color.BLACK));
            }
        } else {
            console.error('we should never pass here');
            return [];
        }

        return result.filter(targetPosition =>
            targetPosition.existsOnBoard() && !this.containSameColors(position, targetPosition)
        );
    }

    #listForwardPalaceDiagonalMoves(position, color) {
        const yOffset = color === Color.WHITE ? 1 : -1;
        const candidates = [
            this.#offset(position, -1, yOffset),
            this.#offset(position, 1, yOffset),
        ];
        return candidates.filter(candidate => this.#areConnectedByPalaceDiagonal(position, candidate));
    }

    #listPalaceDiagonalLines(position) {
        const palaceCenter = this.#getPalaceCenter(position);
        if (palaceCenter == null) {
            return [];
        }

        const corners = this.#getPalaceCorners(palaceCenter.y);
        if (position.isEqualsTo(palaceCenter)) {
            return corners.map(corner => [corner]);
        }

        const isCorner = corners.some(corner => corner.isEqualsTo(position));
        if (!isCorner) {
            return [];
        }

        const oppositeCorner = new Position(2 * palaceCenter.x - position.x, 2 * palaceCenter.y - position.y);
        return [[palaceCenter, oppositeCorner]];
    }

    #listPalaceDiagonalNeighbors(position) {
        const palaceCenter = this.#getPalaceCenter(position);
        if (palaceCenter == null) {
            return [];
        }

        if (position.isEqualsTo(palaceCenter)) {
            return this.#getPalaceCorners(palaceCenter.y);
        }

        const corners = this.#getPalaceCorners(palaceCenter.y);
        if (corners.some(corner => corner.isEqualsTo(position))) {
            return [palaceCenter];
        }
        return [];
    }

    #areConnectedByPalaceDiagonal(from, to) {
        if (
            !from.existsOnBoard() ||
            !to.existsOnBoard() ||
            !from.isInAnyPalace() ||
            !to.isInAnyPalace() ||
            from.isInWhitePalace() !== to.isInWhitePalace() ||
            from.isInBlackPalace() !== to.isInBlackPalace()
        ) {
            return false;
        }
        return this.#listPalaceDiagonalNeighbors(from).some(candidate => candidate.isEqualsTo(to));
    }

    #getPalaceCenter(position) {
        if (position.isInWhitePalace()) {
            return new Position(4, 1);
        } else if (position.isInBlackPalace()) {
            return new Position(4, 8);
        } else {
            return null;
        }
    }

    #getPalaceCorners(centerY) {
        return [
            new Position(3, centerY - 1),
            new Position(5, centerY - 1),
            new Position(3, centerY + 1),
            new Position(5, centerY + 1),
        ];
    }

    #offset(position, xOffset, yOffset) {
        return new Position(position.x + xOffset, position.y + yOffset);
    }

}
