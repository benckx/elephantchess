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

        let resultCandidates = [new ResultCandidate()];
        let color = Color.RED;
        let annotations = [];

        this.tokenize().forEach(token => {
            // strip trailing check (+) / mate (#) indicators, e.g. "Cb0+", "Rxf9+", "Hd3#"
            let move = token.move;
            while (move.length > 0 && (move.endsWith('+') || move.endsWith('#'))) {
                move = move.slice(0, -1);
            }

            if (move.length > 5) {
                throw new Error('Invalid move: ' + move);
            }

            annotations.push(token.annotation);

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

            let pieceWithColor;
            switch (color) {
                case Color.RED:
                    pieceWithColor = piece.toUpperCase();
                    break;
                case Color.BLACK:
                    pieceWithColor = piece.toLowerCase();
                    break;
            }

            let newCandidates = [];

            resultCandidates.forEach(resultCandidate => {
                let candidates =
                    resultCandidate
                        .listPositionsForPiece(pieceWithColor)
                        .map(from => new HalfMove(from, to));

                if (fileChar != null) {
                    candidates = candidates.filter(move => move.from.file === fileChar);
                }

                if (rankDigit != null) {
                    candidates = candidates.filter(move => move.from.rank === rankDigit);
                }

                candidates = candidates.filter(move => {
                    let legalMoves = resultCandidate.listLegalMovesFrom(move.from);
                    return legalMoves.some(lm => Position.areEquals(lm.to, move.to));
                });

                if (candidates.length === 1) {
                    resultCandidate.attemptToAddMove(candidates[0]);
                } else if (candidates.length > 1) {
                    newCandidates.push(...resultCandidate.copyForMoves(candidates));
                    resultCandidate.invalidate();
                } else {
                    resultCandidate.invalidate();
                }
            });

            resultCandidates.push(...newCandidates);
            resultCandidates = resultCandidates.filter(rc => rc.isValid());
            color = reverseColor(color);
        });

        resultCandidates = resultCandidates
            .filter(rc => rc.listMoves().length > 0);

        switch (resultCandidates.length) {
            case 0:
                throw new Error('No valid result found');
            default:
                if (resultCandidates.length > 1) {
                    console.warn(`found ${resultCandidates.length} candidates, returning the first one`);
                }
                return resultCandidates[0].listMoves().map((ma, i) => new MoveAndAnnotation(ma.move, annotations[i]));
        }
    }

}
