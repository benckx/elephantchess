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

class ResultCandidate {

    #board = new Board();

    /**
     * @type {HalfMove[]}
     */
    #moves = [];

    #valid = true;

    constructor() {
        this.#board.loadFen(DEFAULT_START_FEN);
    }

    isValid() {
        return this.#valid;
    }

    invalidate() {
        this.#valid = false;
    }

    /**
     * @return {MoveAndAnnotation[]}
     */
    listMoves() {
        return this.#moves.map(move => new MoveAndAnnotation(move, null));
    }

    /**
     * @param from {Position}
     * @return {HalfMove[]}
     */
    listLegalMovesFrom(from) {
        return this.#board.listLegalMovesFrom(from)
    }

    /**
     * @param piece {string}
     * @return {Position[]}
     */
    listPositionsForPiece(piece) {
        return this.#board.listPositionsForPiece(piece)
    }

    attemptToAddMove(move) {
        // TODO: replace by "is legal move"
        try {
            this.#board.registerMove(move);
            this.#moves.push(move);
            this.#valid = true;
        } catch (e) {
            this.#valid = false;
        }
    }

    /**
     * @return {ResultCandidate}
     */
    copy() {
        let copy = new ResultCandidate();
        copy.#board = this.#board.copy();
        copy.#moves = this.#moves.slice();
        copy.#valid = this.#valid;
        return copy;
    }

    /**
     * @param moves {HalfMove[]}
     * @return {ResultCandidate[]}
     */
    copyForMoves(moves) {
        return moves.map(move => {
            let copy = this.copy();
            copy.attemptToAddMove(move);
            return copy;
        });
    }

}

class WxfTokenParser extends SimpleTokenParser {

    constructor(input) {
        super(extractWxfLine(input));
    }

    tokenize() {
        let tokens = [];
        let tokenAccumulator = '';

        this.allChars.forEach(char => {
            switch (this.state) {
                case TokenParserState.NONE:
                    if (isCharDigit(char)) {
                        this.state = TokenParserState.MOVE_NUMBER;
                    } else if (isCharLetter(char) || char === '+' || char === '-') {
                        this.state = TokenParserState.MOVE;
                        tokenAccumulator += char;
                    } else if (char === '{') {
                        this.state = TokenParserState.ANNOTATION;
                    }
                    break;
                case TokenParserState.MOVE_NUMBER:
                    if (char === '.' || char === ' ') {
                        this.state = TokenParserState.NONE;
                    }
                    break;
                case TokenParserState.MOVE:
                    if (isCharDigit(char) || isCharLetter(char) || isWxfMoveChar(char)) {
                        tokenAccumulator += char;
                    } else if (char === ' ') {
                        this.state = TokenParserState.NONE;
                        tokens.push(new ParsingToken(normalizeWxfMove(tokenAccumulator), null));
                        tokenAccumulator = '';
                    }
                    break;
                case TokenParserState.ANNOTATION:
                    if (char === '}') {
                        this.state = TokenParserState.NONE;
                        if (tokens.length === 0) {
                            throw new Error(`Annotation can not be matched for ${tokenAccumulator}`);
                        }
                        tokens[tokens.length - 1].annotation = tokenAccumulator;
                        tokenAccumulator = '';
                    } else {
                        tokenAccumulator += char;
                    }
                    break;
            }
        });

        if (tokenAccumulator !== '') {
            tokens.push(new ParsingToken(normalizeWxfMove(tokenAccumulator), null));
        }

        return tokens;
    }

}

class WxfParser extends MoveParser {

    /**
     * @type {ParsingToken}
     */
    #token;

    /**
     * @type {string}
     */
    #piece;

    /**
     * @type {ResultCandidate[]}
     */
    #resultCandidates = [];

    #color = 'RED';

    constructor(input) {
        super(input);
        this.#resultCandidates.push(new ResultCandidate());
    }

    get name() {
        return 'WXF';
    }

    tokenize() {
        let tokenParser = new WxfTokenParser(this.input);
        return tokenParser.tokenize();
    }

    parse() {
        this.tokenize().forEach(token => {
            this.#token = token;
            this.#piece = this.#extractPiece();
            // console.log('[' + this.#color + '] ' + token.move + ' -> ' + this.#piece);

            let movementTypeChar = token.move[2];
            let newCandidates = [];

            this.#resultCandidates.forEach(resultCandidate => {
                let currentPositionCandidates = this.#findCurrentPositions(resultCandidate);

                if (currentPositionCandidates.length > 0) {
                    let candidateMoves = [];
                    for (let i = 0; i < currentPositionCandidates.length; i++) {
                        let from = currentPositionCandidates[i];
                        console.log('[' + this.#color + '] ' + token.move + ' -> from ' + from.toAlgebraic() + '[?]');
                        let legalMoves = resultCandidate.listLegalMovesFrom(from);
                        switch (movementTypeChar) {
                            case '=':
                            case '.':
                                candidateMoves.push(...this.#filterCandidatesHorizontal(from, legalMoves));
                                break;
                            case '+':
                                candidateMoves.push(...this.#filterCandidates(+1, from, legalMoves));
                                break;
                            case '-':
                                candidateMoves.push(...this.#filterCandidates(-1, from, legalMoves));
                                break;
                            default:
                                break;
                        }
                    }

                    if (candidateMoves.length === 1) {
                        let move = candidateMoves[0];
                        console.log(`${this.#color} plays ${move.toAlgebraic()}`);
                        resultCandidate.attemptToAddMove(move);
                    } else {
                        newCandidates.push(...resultCandidate.copyForMoves(candidateMoves));
                        resultCandidate.invalidate();
                    }
                } else {
                    resultCandidate.invalidate();
                }
            });

            this.#resultCandidates.push(...newCandidates);
            this.#resultCandidates = this.#resultCandidates.filter(resultCandidate => resultCandidate.isValid());
            this.#color = reverseColor(this.#color);
        });

        this.#resultCandidates = this.#resultCandidates
            .filter(resultCandidate => resultCandidate.isValid())
            .filter(resultCandidate => resultCandidate.listMoves().length > 0);

        switch (this.#resultCandidates.length) {
            case 0:
                throw new Error(`[${this.#color}] No result found`);
            case 1:
                return this.#resultCandidates[0].listMoves();
            default:
                console.warn(`found ${this.#resultCandidates.length} candidates, returning the first one`);
                return this.#resultCandidates[0].listMoves();
        }
    }

    /**
     * @param resultCandidate {ResultCandidate}
     * @return {Position[]}
     */
    #findCurrentPositions(resultCandidate) {
        let fileChar = this.#token.move[1];
        let candidatePositions = [];

        if (isCharDigit(fileChar)) {
            let fileAsUci = this.#wxfFileToUciLetter(parseInt(fileChar));
            candidatePositions.push(...this.#findPositionFromFile(fileAsUci, resultCandidate));
        } else if ((fileChar === '+' && this.#color === Color.RED) || (fileChar === '-' && this.#color === Color.BLACK)) {
            candidatePositions.push(this.#findTopPosition(resultCandidate));
        } else if ((fileChar === '-' && this.#color === Color.RED) || (fileChar === '+' && this.#color === Color.BLACK)) {
            candidatePositions.push(this.#findBottomPosition(resultCandidate));
        }

        return candidatePositions;
    }

    /**
     * @param file {string} UCI file
     * @param resultCandidate {ResultCandidate}
     * @return {Position[]}
     */
    #findPositionFromFile(file, resultCandidate) {
        return resultCandidate
            .listPositionsForPiece(this.#piece)
            .filter(position => position.file === file);
    }

    /**
     * @param direction {number}
     * @param from {Position}
     * @param legalMoves {HalfMove[]}
     * @return {HalfMove[]}
     */
    #filterCandidates(direction, from, legalMoves) {
        if (this.#color === 'BLACK') {
            direction = -direction;
        }

        if (this.#isGeneral() || this.#isChariot() || this.#isSoldier() || this.#isCannon()) {
            // the number indicate the number of squares to move
            let nbfOfSquares = Number(this.#token.move[3]) * direction;
            return legalMoves.filter(move => move.to.y === (from.y + nbfOfSquares));
        } else {
            // the number indicate the target file
            let fileToAsWxf = Number(this.#token.move[3]);
            let fileToAsUci = this.#wxfFileToUciLetter(fileToAsWxf);

            return legalMoves
                .filter(move => (direction > 0 && move.to.y > from.y) || (direction < 0 && move.to.y < from.y))
                .filter(move => move.to.file === fileToAsUci);
        }
    }

    /**
     * @param from {Position}
     * @param legalMoves {HalfMove[]}
     * @return {HalfMove[]}
     */
    #filterCandidatesHorizontal(from, legalMoves) {
        let fileToAsWxf = Number(this.#token.move[3]);
        let fileToAsUci = this.#wxfFileToUciLetter(fileToAsWxf);

        return legalMoves
            .filter(move => Position.areEquals(move.from, from))
            .filter(move => move.to.file === fileToAsUci);
    }

    #extractPiece() {
        function extractPieceChar(token) {
            let pieceChar = token.move[0].toLowerCase();
            if (pieceChar === 'h') {
                // knight
                return 'n';
            } else if (pieceChar === 'e') {
                // elephant
                return 'b';
            } else {
                return pieceChar;
            }
        }

        let piece = extractPieceChar(this.#token);
        if (this.#color === 'RED') {
            return piece.toUpperCase();
        } else {
            return piece;
        }
    }

    /**
     * @param resultCandidate {ResultCandidate}
     * @return {Position[]}
     */
    #findAllPositionsOfBoard(resultCandidate) {
        let allPositions = resultCandidate.listPositionsForPiece(this.#piece);
        let candidatePositions = [];
        allPositions.forEach(position => {
            let hasTwoOnTheSameFile = allPositions.some(otherPosition => position.y === otherPosition.y && position.x !== otherPosition.x);
            if (!hasTwoOnTheSameFile) {
                candidatePositions.push(position);
            }
        });

        return candidatePositions.sort(sortByY);
    }

    /**
     * @param resultCandidate {ResultCandidate}
     * @return {Position}
     */
    #findTopPosition(resultCandidate) {
        return this.#findAllPositionsOfBoard(resultCandidate).slice().reverse()[0];
    }

    /**
     * @param resultCandidate {ResultCandidate}
     * @return {Position}
     */
    #findBottomPosition(resultCandidate) {
        return this.#findAllPositionsOfBoard(resultCandidate)[0];
    }

    /**
     * @param n {number}
     */
    #wxfFileToUciLetter(n) {
        switch (this.#color) {
            case Color.RED:
                return UCI_LETTER.slice().reverse()[n - 1];
            case Color.BLACK:
                return UCI_LETTER[n - 1];
            default:
                throw new Error('Invalid color: ' + this.#color);
        }
    }

    #isGeneral() {
        return this.#piece === 'k' || this.#piece === 'K';
    }

    #isChariot() {
        return this.#piece === 'r' || this.#piece === 'R';
    }

    #isCannon() {
        return this.#piece === 'c' || this.#piece === 'C';
    }

    #isSoldier() {
        return this.#piece === 'p' || this.#piece === 'P';
    }

}

function extractWxfLine(input) {

    /**
     * @param lines {string[]}
     * @returns {string}
     */
    function linesToResult(lines) {
        return lines.join(' ').replaceAll('!', '');
    }

    let started = false;
    let ended = false;
    let moveLines = [];
    let lines = input.split('\n');
    lines.forEach(line => {
        if (started) {
            if (line.startsWith('}END')) {
                ended = true;
            } else {
                moveLines.push(line);
            }
        } else if (line.startsWith('START{')) {
            started = true;
        }
    });

    if (!started || !ended) {
        return linesToResult(lines)
    } else {
        return linesToResult(moveLines);
    }
}

function normalizeWxfMove(move) {
    if (/^[+-][A-Za-z][+=.-][1-9]$/.test(move)) {
        return move[1] + move[0] + move.slice(2);
    }

    return move;
}

/**
 * @param p1 {Position}
 * @param p2 {Position}
 */
function sortByY(p1, p2) {
    return p1.y - p2.y;
}
