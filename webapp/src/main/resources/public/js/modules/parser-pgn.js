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

class PgnParser extends MoveParser {

    #isZeroBased = true;

    constructor(input, isZeroBased) {
        super(input);
        this.#isZeroBased = isZeroBased;
    }

    get name() {
        if (this.#isZeroBased) {
            return 'PGN-0';
        } else {
            return 'PGN-1';
        }
    }

    /**
     * @returns {ParsingToken[]}
     */
    tokenize() {
        function isMetadata(line) {
            return line.startsWith('[');
        }

        function isEmpty(line) {
            return line.trim() === '';
        }

        function isIndicator(line) {
            return line === '#' || line === '*' || line === '+';
        }

        function mustSkip(line) {
            return isMetadata(line) || isEmpty(line) || isIndicator(line);
        }

        let lines = this.input.split('\n');
        let tokensLine = lines.filter(line => !mustSkip(line)).join(' ');
        let tokenParser = new SimpleTokenParser(tokensLine);
        let tokens = tokenParser.tokenize();
        if (!this.#isZeroBased) {
            tokens = tokens.map(token => token.decrement());
        }
        return tokens;
    }

    /**
     * @return {MoveAndAnnotation[]}
     */
    parse() {
        function isFile(value) {
            return value.length > 0 && value[0].match(/[a-i]/i);
        }

        function isCapitalized(value) {
            return value.length > 0 && value[0].toUpperCase() === value[0];
        }

        let board = new Board();
        board.loadFen(DEFAULT_START_FEN);

        return this.tokenize().map(token => {
            // strip trailing check (+) / mate (#) indicators, e.g. "Cb0+", "Rxf9+", "Hd3#"
            let move = token.move;
            while (move.length > 0 && (move.endsWith('+') || move.endsWith('#'))) {
                move = move.slice(0, -1);
            }

            if (move.length > 5) {
                throw new Error('Invalid move: ' + move);
            }

            let to = Position.parseUci(move.slice(-2));
            let piece = null;
            let rankDigit = null;
            let fileChar = null;

            if (!isCapitalized(move)) {
                piece = 'p'
            } else {
                switch (move.length) {
                    case 2:
                        piece = 'p';
                        break;
                    case 3:
                        piece = move[0];
                        break;
                    case 4:
                    case 5:
                        piece = move[0];
                        if (isFile(move[1])) {
                            fileChar = move[1];
                        } else if (isCharDigit(move[1])) {
                            rankDigit = Number(move[1]);
                        }
                        break;
                    default:
                        throw new Error('Invalid move: ' + move);
                }
            }

            if (piece.toLowerCase() === 'h') {
                piece = 'n';
            } else if (piece.toLowerCase() === 'e') {
                piece = 'b';
            }

            switch (board.getColorToPlay()) {
                case Color.RED:
                    piece = piece.toUpperCase();
                    break;
                case Color.BLACK:
                    piece = piece.toLowerCase();
                    break;
            }

            let candidates =
                board
                    .listPositionsForPiece(piece)
                    .map(from => new HalfMove(from, to));

            if (fileChar != null) {
                candidates = candidates.filter(move => move.from.file === fileChar);
            }

            if (rankDigit != null) {
                candidates = candidates.filter(move => move.from.rank === rankDigit);
            }

            if (candidates.length > 1) {
                candidates = candidates.filter(move => board.isLegalMove(move));
            }

            switch (candidates.length) {
                case 0:
                    board.printBoard();
                    console.warn('token: ' + token);
                    console.warn('piece: ' + piece);
                    console.warn('rankDigit: ' + rankDigit);
                    console.warn('fileChar: ' + fileChar);
                    throw new Error(`No legal move found for ${token.move} (${board.getColorToPlay()} to play)`);
                case 1:
                    // console.log('-> ' + candidates[0].toAlgebraic());
                    board.registerMove(candidates[0]);
                    return new MoveAndAnnotation(candidates[0], token.annotation);
                default:
                    board.printBoard();
                    throw new Error(`Ambiguous move for ${token.move} (${board.getColorToPlay()} to play)`);
            }
        });
    }

}
