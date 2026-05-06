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

const TokenParserState = Object.freeze({
    NONE: 'NONE',
    MOVE_NUMBER: 'MOVE_NUMBER',
    MOVE: 'MOVE',
    ANNOTATION: 'ANNOTATION'
});

const SubTokenParserState = Object.freeze({
    NONE: 'NONE',
    NUMBER: 'NUMBER',
    LETTER: 'LETTER',
});

function isWxfMoveChar(char) {
    return char === '+' || char === '-' || char === '.' || char === '=';
}

/**
 * A tuple representing a move in a string format, independently of its format (PGN, WXF, etc.; i.e. just a label representing a move),
 * and its accompanying annotation if any.
 */
class ParsingToken {

    #move;
    #annotation;

    /**
     * @param move {string}
     * @param annotation {string|null}
     */
    constructor(move, annotation) {
        this.#move = move;
        this.#annotation = annotation;
    }

    /**
     * @return {string}
     */
    get move() {
        return this.#move;
    }

    /**
     * @return {string|null}
     */
    get annotation() {
        return this.#annotation;
    }

    set annotation(annotation) {
        this.#annotation = annotation;
    }

    /**
     * Transform the move label from algebraic notation (1-based) to UCI notation (0-based).
     * First separates text bits from number bits, then decrements the number bits.
     *
     * @return {ParsingToken}
     */
    decrement() {
        let subTokens = [];
        let state = SubTokenParserState.NONE;
        let tokenAccumulator = '';

        for (let i = 0; i < this.#move.length; i++) {
            let char = this.#move.charAt(i);
            switch (state) {
                case SubTokenParserState.NONE:
                    if (isCharLetter(char)) {
                        state = SubTokenParserState.LETTER;
                        tokenAccumulator += char;
                    } else if (isCharDigit(char)) {
                        state = SubTokenParserState.NUMBER;
                        tokenAccumulator += char;
                    }
                    break;
                case SubTokenParserState.NUMBER:
                    if (isCharLetter(char)) {
                        subTokens.push(tokenAccumulator);
                        tokenAccumulator = char;
                        state = SubTokenParserState.LETTER;
                    } else if (isCharDigit(char)) {
                        tokenAccumulator += char;
                    }
                    break;
                case SubTokenParserState.LETTER:
                    if (isCharLetter(char)) {
                        tokenAccumulator += char;
                    } else if (isCharDigit(char)) {
                        subTokens.push(tokenAccumulator);
                        tokenAccumulator = char;
                        state = SubTokenParserState.NUMBER;
                    }
            }
        }
        subTokens.push(tokenAccumulator);

        let decrementedMoveLabel =
            subTokens
                .map(subToken => {
                    if (isNumber(subToken)) {
                        return (parseInt(subToken) - 1).toString();
                    } else {
                        return subToken;
                    }
                })
                .join('');

        return new ParsingToken(decrementedMoveLabel, this.#annotation);
    }

    toString() {
        return this.#move + (this.#annotation ? ' {' + this.#annotation + '}' : '');
    }

}

class MoveAndAnnotation {

    #move;
    #annotation;

    /**
     * @param move {HalfMove}
     * @param annotation {string|null}
     */
    constructor(move, annotation) {
        this.#move = move;
        this.#annotation = annotation;
    }

    /**
     * @return {HalfMove}
     */
    get move() {
        return this.#move;
    }

    /**
     * @return {string|null}
     */
    get annotation() {
        return this.#annotation;
    }

}

/**
 * Transform a string into an array of {@link ParsingToken}.
 */
class TokenParser {

    #line;
    #state = TokenParserState.NONE;

    constructor(line) {
        this.#line = line;
    }

    /**
     * @return {string}
     */
    get line() {
        return this.#line;
    }

    /**
     * @return {string[]}
     */
    get allChars() {
        let chars = [];
        for (let i = 0; i < this.line.length; i++) {
            let char = this.line.charAt(i);
            chars.push(char);
        }
        return chars;
    }

    get state() {
        return this.#state;
    }

    set state(state) {
        this.#state = state;
    }

    /**
     * @return {ParsingToken[]}
     */
    tokenize() {
        throw new Error('Not implemented');
    }

}

class SimpleTokenParser extends TokenParser {

    /**
     * @return {ParsingToken[]}
     */
    tokenize() {
        let tokens = [];
        let tokenAccumulator = '';

        this.allChars.forEach(char => {
            switch (this.state) {
                case TokenParserState.NONE:
                    if (isCharDigit(char)) {
                        this.state = TokenParserState.MOVE_NUMBER;
                    } else if (isCharLetter(char)) {
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
                        tokens.push(new ParsingToken(tokenAccumulator, null));
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
            tokens.push(new ParsingToken(tokenAccumulator, null));
        }

        return tokens;
    }

}

/**
 * Parse a raw string input, then transform {@link ParsingToken} into {@link MoveAndAnnotation}.
 * Uses a {@link TokenParser} to get the {@link ParsingToken}.
 */
class MoveParser {

    #input;

    constructor(input) {
        this.#input = input;
    }

    /**
     * @return {string}
     */
    get input() {
        return this.#input;
    }

    /**
     * @return {string}
     */
    get name() {
        throw new Error('Not implemented');
    }

    /**
     * @return {ParsingToken[]}
     */
    tokenize() {
        throw new Error('Not implemented');
    }

    /**
     * @return {MoveAndAnnotation[]}
     */
    parse() {
        throw new Error('Not implemented');
    }

}

/**
 * @param input {string}
 * @return {MoveAndAnnotation[]}
 */
function parseToMoves(input) {

    /**
     * @param input {string}
     * @return {MoveParser[]}
     */
    function buildMoveParsers(input) {
        return [
            new PgnParser(input, false),
            new PgnParser(input, true),
            new UciMoveParser(input),
            new AlgebraicParser(input),
            new WxfParser(input)
        ];
    }

    let parsers = buildMoveParsers(input);

    let errors = [];

    for (let i = 0; i < parsers.length; i++) {
        let parser = parsers[i];
        try {
            console.log('trying ' + parser.name + '...');
            let parserMoves = parser.parse();
            if (parserMoves.length > 0) {
                return parserMoves;
            }
        } catch (e) {
            console.warn(e);
            errors.push('- [' + parser.name + '] ' + e.message);
        }
    }

    throw new Error(errors.join('<br/>'));
}
