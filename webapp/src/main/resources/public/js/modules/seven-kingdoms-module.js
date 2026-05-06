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

const BOARD_SIZE_7K = 19;

const EMPEROR_X = 9;
const EMPEROR_Y = 9;

const CAPTURE_THRESHOLD_TO_WIN = 30;
const CAPTURE_THRESHOLD_TO_LOSE = 10;
const CAPTURED_KINGDOMS_THRESHOLD_TO_WIN = 2;

const DEFAULT_START_FEN_7K =
    '2dNdSdBdQdRdSdN1pNpSpBpQpRpSpN2/' +
    '3dNdSdCdSdN3pNpSpCpSpN3/' +
    '4dAdWdA5pApWpA3gN/' +
    '5dH7pH3gNgS/' +
    '5dG7pG2gAgSgB/' +
    '14gGgHgWgCgQ/' +
    'wN15gAgSgR/' +
    'wSwN15gNgS/' +
    'wRwSwA15gN/' +
    'wQwCwWwHwG14/' + // include the emperor?
    'wBwSwA15bN/' +
    'wSwN15bNbS/' +
    'wN15bAbSbB/' +
    '14bGbHbWbCbQ/' +
    '5rG7oG2bAbSbR/' +
    '5rH7oH3bNbS/' +
    '4rArWrA5oAoWoA3bN/' +
    '3rNrSrCrSrN3oNoSoCoSoN3/' +
    '2rNrSrBrQrRrSrN1oNoSoBoQoRoSoN2';

const PIECE_STYLE_SETTING_7k = 'setting.piece.style.7k';

const PieceStyleSetting7K = Object.freeze({
    CHINESE_CHAR: 'CHINESE_CHAR',
    WESTERNIZED_ICONS: 'WESTERNIZED_ICONS',
    UCI_LETTER: 'UCI_LETTER',
    DEFAULT: 'WESTERNIZED_ICONS',
});

class SettingsManager7k extends SettingsManager {

    /**
     * @return {string}
     */
    get pieceStyle7k() {
        const cookieValue = getCookie(PIECE_STYLE_SETTING_7k);
        if (cookieValue === null) {
            return PieceStyleSetting7K.DEFAULT;
        } else {
            return cookieValue;
        }
    }

    /**
     * @param value {string}
     */
    set pieceStyle7k(value) {
        setCookie(PIECE_STYLE_SETTING_7k, value, CHROME_COOKIE_MAX_TTL);
    }

}

/**
 * @param n {number}
 * @returns {number[]}
 */
function arrayOfIntOneBased(n) {
    return Array.from({length: n}, (_, i) => i + 1);
}

// TODO: common type between position xiangqi and position 7k: AbstractPosition (in module "board-common" or something)
class Position7k {

    #x;
    #y;

    /**
     * @param x {number}
     * @param y {number}
     */
    constructor(x, y) {
        this.#x = x;
        this.#y = y;
    }

    /**
     * @returns {number}
     */
    get x() {
        return this.#x;
    }

    /**
     * @returns {number}
     */
    get y() {
        return this.#y;
    }

    /**
     * @returns {boolean}
     */
    get isEmperor() {
        return this.x === EMPEROR_X && this.y === EMPEROR_Y;
    }

    /**
     * @returns {boolean}
     */
    get isOnBoard() {
        return this.x >= 0 && this.x < BOARD_SIZE_7K &&
            this.y >= 0 && this.y < BOARD_SIZE_7K;
    }

    /**
     * @returns {string}
     */
    get uci() {
        const file = String.fromCharCode('a'.charCodeAt(0) + this.#x);
        const rank = (this.#y).toString();
        return `${file}${rank}`;
    }

    /**
     * @returns {string}
     */
    get algebraic() {
        const file = String.fromCharCode('a'.charCodeAt(0) + this.#x);
        const rank = (this.#y + 1).toString();
        return `${file}${rank}`;
    }

    /**
     * @param n {number}
     * @returns {Position7k[]}
     */
    allTopFor(n) {
        return arrayOfIntOneBased(n)
            .map(i => new Position7k(this.x, this.y + i));
    }

    /**
     * @returns {Position7k}
     */
    get top() {
        return this.allTopFor(1)[0];
    }

    /**
     * @param n {number}
     * @returns {Position7k[]}
     */
    allBottomFor(n) {
        return arrayOfIntOneBased(n)
            .map(i => new Position7k(this.x, this.y - i));
    }

    /**
     * @returns {Position7k}
     */
    get bottom() {
        return this.allBottomFor(1)[0];
    }

    /**
     * @param n {number}
     * @returns {Position7k[]}
     */
    allLeftFor(n) {
        return arrayOfIntOneBased(n)
            .map(i => new Position7k(this.x - i, this.y));
    }

    /**
     * @returns {Position7k}
     */
    get left() {
        return this.allLeftFor(1)[0];
    }

    /**
     * @param n {number}
     * @returns {Position7k[]}
     */
    allRightFor(n) {
        return arrayOfIntOneBased(n)
            .map(i => new Position7k(this.x + i, this.y));
    }

    /**
     * @returns {Position7k}
     */
    get right() {
        return this.allRightFor(1)[0];
    }

    /**
     * @param n {number}
     * @returns {Position7k[]}
     */
    allTopLeftDiagonalsFor(n) {
        return arrayOfIntOneBased(n)
            .map(i => new Position7k(this.x - i, this.y + i));
    }

    /**
     * @param n {number}
     * @returns {Position7k[]}
     */
    allTopRightDiagonalsFor(n) {
        return arrayOfIntOneBased(n)
            .map(i => new Position7k(this.x + i, this.y + i));
    }

    /**
     * @param n {number}
     * @returns {Position7k[]}
     */
    allBottomLeftDiagonalsFor(n) {
        return arrayOfIntOneBased(n)
            .map(i => new Position7k(this.x - i, this.y - i));
    }

    /**
     * @param n {number}
     * @returns {Position7k[]}
     */
    allBottomRightDiagonalsFor(n) {
        return arrayOfIntOneBased(n)
            .map(i => new Position7k(this.x + i, this.y - i));
    }

    toString() {
        return `(${this.#x}, ${this.#y})`;
    }

    /**
     * @param other {Position7k}
     * @return {boolean}
     */
    equalsTo(other) {
        return Position7k.areEquals(this, other);
    }

    /**
     * @param p1 {Position7k}
     * @param p2 {Position7k}
     * @returns {boolean}
     */
    static areEquals(p1, p2) {
        return p1.x === p2.x && p1.y === p2.y;
    }

    /**
     * @returns {Position7k[]}
     */
    static listAll() {
        const positions = [];
        for (let x = 0; x < BOARD_SIZE_7K; x++) {
            for (let y = 0; y < BOARD_SIZE_7K; y++) {
                positions.push(new Position7k(x, y));
            }
        }
        return positions;
    }

}

class Move {

    #from;
    #to;

    /**
     * @param from {Position7k}
     * @param to {Position7k}
     */
    constructor(from, to) {
        this.#from = from;
        this.#to = to;
    }

    /**
     * @returns {Position7k}
     */
    get from() {
        return this.#from;
    }

    /**
     * @returns {Position7k}
     */
    get to() {
        return this.#to;
    }

    /**
     * @returns {string}
     */
    get uci() {
        return `${this.#from.uci}${this.#to.uci}`;
    }

    /**
     * @returns {string}
     */
    get algebraic() {
        return `${this.#from.algebraic}${this.#to.algebraic}`;
    }

    /**
     * @returns {string}
     */
    toString() {
        return this.algebraic;
    }

    /**
     * Parses a UCI string to create a Move instance.
     *
     * @param {string} uci - The UCI string to parse.
     * @returns {Move} - The parsed Move instance.
     * @throws {Error} - If the UCI string is invalid.
     */
    static parseFromUci(uci) {
        const firstLetter = uci[0];
        if (!/[a-s]/.test(firstLetter)) {
            throw new Error(`Invalid UCI: ${uci}`);
        }

        const secondLetterIndex = uci.substring(1).search(/[a-s]/) + 1;
        if (secondLetterIndex === 0) {
            throw new Error(`Invalid UCI: ${uci}`);
        }

        const secondLetter = uci[secondLetterIndex];
        if (!/[a-s]/.test(secondLetter)) {
            throw new Error(`Invalid UCI: ${uci}`);
        }

        const from = new Position7k(
            firstLetter.charCodeAt(0) - 'a'.charCodeAt(0),
            parseInt(uci.substring(1, secondLetterIndex))
        );

        const to = new Position7k(
            secondLetter.charCodeAt(0) - 'a'.charCodeAt(0),
            parseInt(uci.substring(secondLetterIndex + 1))
        );

        if (!from.isOnBoard) {
            throw new Error(`Error parsing ${uci}, ${from} is out of board`);
        }

        if (!to.isOnBoard) {
            throw new Error(`Error parsing ${uci}, ${to} is out of board`);
        }

        return new Move(from, to);
    }

    /**
     * @param m1 {Move}
     * @param m2 {Move}
     * @returns {boolean}
     */
    static areEquals(m1, m2) {
        return m1.from.x === m2.from.x &&
            m1.from.y === m2.from.y &&
            m1.to.x === m2.to.x &&
            m1.to.y === m2.to.y;
    }

}

class ArmyCapturedEvent {

    #capturingColor;
    #capturedColor;
    #generalCapture;

    /**
     * @param capturingColor {Color7k}
     * @param capturedColor {Color7k}
     * @param generalCapture {boolean}
     */
    constructor(capturingColor, capturedColor, generalCapture) {
        this.#capturingColor = capturingColor;
        this.#capturedColor = capturedColor;
        this.#generalCapture = generalCapture;
    }

    /**
     * @returns {Color7k}
     */
    get capturingColor() {
        return this.#capturingColor;
    }

    /**
     * @returns {Color7k}
     */
    get capturedColor() {
        return this.#capturedColor;
    }

    toString() {
        const middle = this.#generalCapture
            ? `${this.#capturingColor} captures ${this.#capturedColor} general`
            : `${this.#capturingColor} captures ${this.#capturedColor} army`;

        return `${ArmyCapturedEvent}{${middle}}`;
    }
}

class ExtraEliminationEvent {

    #index;
    #colors;

    /**
     * @param index {number}
     * @param colors {Color7k[]}
     */
    constructor(index, colors) {
        this.#index = index;
        this.#colors = colors;
    }

    /**
     * @return {number}
     */
    get index() {
        return this.#index;
    }

    /**
     * @return {Color7k[]}
     */
    get colors() {
        return this.#colors;
    }
}

class HistoricalMove {

    /**
     * @type {Position7k}
     */
    #from;

    /**
     * @type {Position7k}
     */
    #to;

    /**
     * @type {Piece7k}
     */
    #piece;

    /**
     * @type {PieceAtPosition7k|null}
     */
    #capture;

    /**
     * @type {ArmyCapturedEvent|null}
     */
    #armyCapturedEvent;

    /**
     * @param from {Position7k}
     * @param to {Position7k}
     * @param piece {Piece7k}
     * @param capture {PieceAtPosition7k|null}
     * @param armyCapturedEvent {ArmyCapturedEvent|null}
     */
    constructor(
        from,
        to,
        piece,
        capture = null,
        armyCapturedEvent = null
    ) {
        this.#from = from;
        this.#to = to;
        this.#piece = piece;
        this.#capture = capture;
        this.#armyCapturedEvent = armyCapturedEvent;
    }

    /**
     * @returns {Piece7k}
     */
    get piece() {
        return this.#piece;
    }

    /**
     * @returns {PieceAtPosition7k|null}
     */
    get capture() {
        return this.#capture;
    }

    /**
     * @returns {ArmyCapturedEvent|null}
     */
    get armyCapturedEvent() {
        return this.#armyCapturedEvent;
    }

    /**
     * @returns {Color7k}
     */
    get color() {
        return this.#piece.color;
    }

    /**
     * @returns {Move}
     */
    get move() {
        return new Move(this.#from, this.#to);
    }

    /**
     * Creates a copy of the HistoricalMove with a new army captured event.
     *
     * @param armyCapturedEvent {ArmyCapturedEvent|null}
     * @returns {HistoricalMove}
     */
    copyWithArmyCapturedEvent(armyCapturedEvent) {
        return new HistoricalMove(
            this.#from,
            this.#to,
            this.#piece,
            this.#capture,
            armyCapturedEvent
        );
    }

    toString() {
        let result = `${this.#piece.color} played ${this.move}`;
        if (this.#capture !== null) {
            result += ` and captures ${this.#capture.piece}`;
        }
        return result;
    }
}

class Color7k {

    #colorName;
    #fenColorChar;
    #armyName;
    #armyChineseName;
    #colorCode;
    #lightText;

    /**
     * @param colorName {string}
     * @param fenColorChar {string}
     * @param armyName {string}
     * @param armyChineseName {string}
     * @param colorCode {string}
     * @param lightText {boolean}
     */
    constructor(
        colorName,
        fenColorChar,
        armyName,
        armyChineseName,
        colorCode,
        lightText = true
    ) {
        this.#colorName = colorName;
        this.#fenColorChar = fenColorChar;
        this.#armyName = armyName;
        this.#armyChineseName = armyChineseName;
        this.#colorCode = colorCode;
        this.#lightText = lightText;
    }

    /**
     * @returns {string}
     */
    get colorName() {
        return this.#colorName;
    }

    /**
     * @return {string}
     */
    get fenColorChar() {
        return this.#fenColorChar;
    }

    /**
     * @returns {string}
     */
    get armyName() {
        return this.#armyName;
    }

    /**
     * @returns {string}
     */
    get armyChineseName() {
        return this.#armyChineseName;
    }

    /**
     * @returns {string}
     */
    get colorCode() {
        return this.#colorCode;
    }

    /**
     * @returns {boolean}
     */
    get isLightText() {
        return this.#lightText;
    }

    /**
     * @return {string}
     */
    get textColorCode() {
        if (this.#lightText === true) {
            return '#FFFFFF';
        } else {
            return '#000000';
        }
    }

    /**
     * @returns {string}
     */
    toString() {
        return formatEnumValue(this.colorName);
    }

    /**
     * @param c1 {Color7k}
     * @param c2 {Color7k}
     * @return {boolean}
     */
    static areEquals(c1, c2) {
        return c1.fenColorChar === c2.fenColorChar;
    }

    /**
     * @param name {string}
     * @returns {Color7k}
     */
    static findByName(name) {
        for (const color of allColors()) {
            if (color.colorName === name) {
                return color;
            }
        }
        throw new Error(`Color not found: ${name}`);
    }

    /**
     * Sorts an array of Color7k instances based on the order defined in ColorTypes.
     *
     * @param {Color7k[]} colors - The array of Color7k instances to sort.
     * @returns {Color7k[]} - The sorted array of Color7k instances.
     */
    static sort(colors) {
        const colorOrder = Object.keys(ColorTypes);
        return colors.sort((a, b) => {
            return colorOrder.indexOf(a.colorName) - colorOrder.indexOf(b.colorName);
        });
    }

}

/**
 * Object.values(ColorTypes) -> Color7k[]
 * Object.keys(ColorTypes) -> string[]
 * Object.entries(ColorTypes) -> [string, Color7k][]
 *
 * @type {Readonly<{WHITE: Color7k, RED: Color7k, ORANGE: Color7k, BLUE: Color7k, GREEN: Color7k, PURPLE: Color7k, BLACK: Color7k}>}
 */
const ColorTypes = Object.freeze({
    WHITE:
        new Color7k(
            'WHITE',
            'w',
            'Qin',
            '秦',
            '#FFFFFF',
            false
        ),
    RED:
        new Color7k(
            'RED',
            'r',
            'Chu',
            '楚',
            '#ca0808'
        ),
    ORANGE:
        new Color7k(
            'ORANGE',
            'o',
            'Han',
            '韓',
            'rgb(253, 189, 71)',
            false
        ),
    BLUE:
        new Color7k(
            'BLUE',
            'b',
            'Qi',
            '齊',
            'rgb(6, 6, 211)'
        ),
    GREEN:
        new Color7k(
            'GREEN',
            'g',
            'Wei',
            '魏',
            'rgb(1, 83, 1)'
        ),
    PURPLE:
        new Color7k(
            'PURPLE',
            'p',
            'Zhao',
            '趙',
            'rgb(98, 1, 98)'
        ),
    BLACK:
        new Color7k(
            'BLACK',
            'd',
            'Yan',
            '燕',
            '#000000'
        ),
});

/**
 * @returns {Color7k[]}
 */
function allColors() {
    return Object.values(ColorTypes);
}

/**
 * Piece type without color
 */
class AbstractPieceType7k {

    #pieceName;
    #chineseName;
    #uci;

    /**
     * @param pieceName {string}
     * @param chineseName {string}
     * @param uci {string}
     */
    constructor(pieceName, chineseName, uci) {
        this.#pieceName = pieceName;
        this.#chineseName = chineseName;
        this.#uci = uci;
    }

    /**
     * @returns {string}
     */
    get pieceName() {
        return this.#pieceName;
    }

    /**
     * @returns {string}
     */
    get chineseName() {
        return this.#chineseName;
    }

    /**
     * @returns {string}
     */
    get uci() {
        return this.#uci;
    }

    toString() {
        return formatEnumValue(this.#pieceName);
    }

}

const PieceTypes = Object.freeze({
    GENERAL: new AbstractPieceType7k('GENERAL', '將', 'Q'),
    CHANCELLOR: new AbstractPieceType7k('CHANCELLOR', '偏', 'R'),
    DIPLOMAT: new AbstractPieceType7k('DIPLOMAT', '裨', 'B'),
    CANNON: new AbstractPieceType7k('CANNON', '砲', 'C'),
    GO_BETWEEN: new AbstractPieceType7k('GO_BETWEEN', '行人', 'G'),
    ARCHER: new AbstractPieceType7k('ARCHER', '弓', 'H'),
    CROSSBOWMAN: new AbstractPieceType7k('CROSSBOWMAN', '弩', 'W'),
    DAGGER_SOLDIER: new AbstractPieceType7k('DAGGER_SOLDIER', '刀', 'A'),
    SWORDSMAN: new AbstractPieceType7k('SWORDSMAN', '劍', 'S'),
    KNIGHT: new AbstractPieceType7k('KNIGHT', '騎', 'N')
});

class Piece7k {

    #color;
    #abstractPieceType;

    /**
     * @param color {Color7k}
     * @param abstractPieceType {AbstractPieceType7k}
     */
    constructor(color, abstractPieceType) {
        this.#color = color;
        this.#abstractPieceType = abstractPieceType;
    }

    /**
     *
     * @returns {Color7k}
     */
    get color() {
        return this.#color;
    }

    /**
     * @returns {AbstractPieceType7k}
     */
    get abstractPieceType() {
        return this.#abstractPieceType;
    }

    /**
     * @returns {Piece7k}
     */
    copy() {
        return new Piece7k(this.#color, this.#abstractPieceType);
    }

    toString() {
        return `${this.#color} ${this.#abstractPieceType}`;
    }

}

class PieceAtPosition7k {

    #piece;
    #position;

    /**
     * @param piece {Piece7k}
     * @param position {Position7k}
     */
    constructor(piece, position) {
        this.#piece = piece;
        this.#position = position;
    }

    /**
     * @returns {Piece7k}
     */
    get piece() {
        return this.#piece;
    }

    /**
     * Primitive property
     *
     * @returns {Position7k}
     */
    get position() {
        return this.#position;
    }

    /**
     * @returns {AbstractPieceType7k}
     */
    get abstractPieceType() {
        return this.#piece.abstractPieceType;
    }

    /**
     * @returns {Color7k}
     */
    get color() {
        return this.#piece.color;
    }

    /**
     * Derived property
     *
     * @return {string}
     */
    get uci() {
        return this.#piece.abstractPieceType.uci;
    }

    /**
     * Derived property
     *
     * @returns {string}
     */
    get chinesePieceName() {
        return this.piece.abstractPieceType.chineseName;
    }

    /**
     * Derived property
     *
     * @return {string}
     */
    get backgroundColor() {
        return this.#piece.color.colorCode;
    }

    /**
     * Derived property
     *
     * @return {string}
     */
    get textColor() {
        return this.#piece.color.textColorCode;
    }

    /**
     * @param color {Color7k}
     * @returns {PieceAtPosition7k}
     */
    copyWithColor(color) {
        return new PieceAtPosition7k(new Piece7k(color, this.#piece.abstractPieceType), this.#position);
    }

    toString() {
        return `${this.#piece.color.fenColorChar.toLowerCase()}${this.uci} at ${this.#position}`;
    }

}

class Board7k {

    /**
     * @type {Piece7k[][]}
     */
    #content = [];

    #colorToPlay = ColorTypes.WHITE;

    /**
     * @type {Map<Color7k, PieceAtPosition7k[]>}
     */
    #captures = new Map();

    /**
     * @type {Map<Color7k, Color7k[]>}
     */
    #capturedKingdomsMap = new Map();

    /**
     *
     * @type {Color7k|null}
     */
    #winner = null;

    /**
     *
     * @type {HistoricalMove[]}
     */
    #historicalMoves = [];

    /**
     * @type {ExtraEliminationEvent[]}
     */
    #extraEliminationEvents = [];

    /**
     * Colors that are in the game from the beginning
     *
     * @type {Color7k[]}
     */
    #initColors = allColors();

    constructor(initFen = DEFAULT_START_FEN_7K) {
        this.clear();
        this.loadFen(initFen);
    }

    /**
     * @returns {boolean}
     */
    get isGameOver() {
        return this.#winner != null;
    }

    /**
     * @returns {number}
     */
    get currentIndex() {
        return this.#historicalMoves.length;
    }

    /**
     * @returns {Color7k}
     */
    get colorToPlay() {
        return this.#colorToPlay;
    }

    /**
     * @returns {Map<Color7k, PieceAtPosition7k[]>}
     */
    get captures() {
        return this.#captures;
    }

    /**
     * @returns {Map<Color7k, number>}
     */
    get capturesCount() {
        const result = new Map();
        for (const [color, pieces] of this.captures) {
            result.set(color, pieces.length);
        }
        return result;
    }

    /**
     * @returns {PieceAtPosition7k[]}
     */
    get allCapturedPieces() {
        const allCapturedPieces = [];
        for (const pieces of this.#captures.values()) {
            allCapturedPieces.push(...pieces);
        }
        return allCapturedPieces;
    }

    /**
     * @returns {Map<Color7k, PieceAtPosition7k[]>}
     */
    get losses() {
        const allCapturedPieces = this.allCapturedPieces;
        const lossesMap = new Map();
        for (const color of allColors()) {
            lossesMap.set(color, []);
            const capturedPiecesOfColor = allCapturedPieces.filter(it => it.color === color);
            if (capturedPiecesOfColor.length > 0) {
                lossesMap.get(color).push(...capturedPiecesOfColor);
            }
        }

        return lossesMap;
    }

    /**
     * @returns {Map<Color7k, number>}
     */
    get lossesCount() {
        const result = new Map();
        for (const [color, pieces] of this.losses) {
            result.set(color, pieces.length);
        }
        return result;
    }

    /**
     * @type {Map<Color7k, Color7k[]>}
     */
    get capturedKingdomsMap() {
        return this.#capturedKingdomsMap;
    }

    /**
     * @returns {Color7k[]}
     */
    get capturedKingdoms() {
        const result = [];
        for (const [_, kingdoms] of this.#capturedKingdomsMap) {
            for (const kingdom of kingdoms) {
                if (!result.includes(kingdom)) {
                    result.push(kingdom);
                }
            }
        }

        return Color7k.sort(result);
    }

    /**
     * @returns {HistoricalMove[]}
     */
    get historicalMoves() {
        return this.#historicalMoves;
    }

    get winner() {
        return this.#winner;
    }

    /**
     * @returns {ExtraEliminationEvent[]}
     */
    get extraEliminationEvents() {
        return this.#extraEliminationEvents;
    }

    /**
     * @param events {ExtraEliminationEvent[]}
     */
    set extraEliminationEvents(events) {
        this.#extraEliminationEvents = events;
    }

    /**
     * @param colors {Color7k[]}
     */
    set initColors(colors) {
        this.#initColors = colors;
    }

    /**
     * @return {Color7k[]}
     */
    allEliminatedColors() {
        const result = [];
        result.push(...this.capturedKingdoms);

        const appliedExtraEliminationsColors =
            this.#extraEliminationEvents
                .filter(event => event.index <= this.currentIndex)
                .flatMap(event => event.colors);

        result.push(...appliedExtraEliminationsColors);

        // distinct and sorted
        return Color7k.sort(Array.from(new Set(result)));
    }

    /**
     * @returns {Color7k[]}
     */
    colorsStillInGame() {
        const eliminated = this.allEliminatedColors();
        const result = [];

        for (const color of this.#initColors) {
            if (!eliminated.includes(color)) {
                result.push(color);
            }
        }

        return result;
    }

    /**
     * @param fen {string}
     */
    loadFen(fen) {
        const piecesAtPositions = parseFenToPiecesAtPositions(fen);
        for (const pieceAtPosition of piecesAtPositions) {
            const position = pieceAtPosition.position;
            this.#content[position.x][position.y] = pieceAtPosition.piece;
        }
    }

    /**
     * @returns {string}
     */
    outputFen() {
        const ranks = [];

        for (let y = BOARD_SIZE_7K - 1; y >= 0; y--) {
            let spaces = 0;
            let currentRank = '';
            for (let x = 0; x < BOARD_SIZE_7K; x++) {
                const piece = this.#content[x][y];
                if (piece == null) {
                    spaces++;
                } else {
                    if (spaces > 0) {
                        currentRank += spaces.toString();
                        spaces = 0;
                    }
                    currentRank += piece.color.fenColorChar.toLowerCase();
                    currentRank += piece.abstractPieceType.uci.toUpperCase();
                }
            }
            if (spaces > 0) {
                currentRank += spaces.toString();
                spaces = 0;
            }
            ranks.push(currentRank);
        }

        return ranks.join("/");
    }


    /**
     * @return {PieceAtPosition7k[]}
     */
    listAllPieces() {
        const piecesAtPosition = [];
        Position7k.listAll().forEach(position => {
            const piece = this.pieceAt(position);
            if (piece != null) {
                piecesAtPosition.push(new PieceAtPosition7k(piece, position));
            }
        });

        return piecesAtPosition;
    }

    listPiecesByColor(color) {
        return this.listAllPieces().filter(pieceAtPosition => pieceAtPosition.color === color);
    }

    clear() {
        this.#content = [];
        for (let x = 0; x < BOARD_SIZE_7K; x++) {
            this.#content.push([null]);
            for (let y = 0; y < BOARD_SIZE_7K; y++) {
                this.#content[x][y] = null;
            }
        }
        this.#colorToPlay = ColorTypes.WHITE;
        this.#captures = new Map();
        this.#capturedKingdomsMap = new Map();
        for (const color of allColors()) {
            this.#captures.set(color, []);
            this.#capturedKingdomsMap.set(color, []);
        }
        this.#winner = null;
        this.#historicalMoves = [];
    }

    /**
     * @param pieceAtPosition {PieceAtPosition7k}
     */
    setPiece(pieceAtPosition) {
        this.#content[pieceAtPosition.position.x][pieceAtPosition.position.y] = pieceAtPosition.piece;
    }

    /**
     * @param position {Position7k}
     * @returns {Piece7k|null}
     */
    pieceAt(position) {
        return this.#content[position.x][position.y];
    }

    /**
     * @param position {Position7k}
     * @returns {boolean}
     */
    hasPieceAt(position) {
        return this.pieceAt(position) != null;
    }

    /**
     * Takes into account "color to play"
     *
     * @param move {Move}
     * @returns {boolean}
     */
    isLegalMove(move) {
        return this
            .listCurrentLegalMoves()
            .filter(otherMove => otherMove.from.equalsTo(move.from))
            .find(otherMove => otherMove.to.equalsTo(move.to)) != null;
    }

    /**
     * List current legal moves (for the color that has to play now)
     *
     * @returns {Move[]}
     */
    listCurrentLegalMoves() {
        if (this.isGameOver || this.colorToPlay == null) {
            return [];
        } else {
            return this.listLegalMovesForColor(this.colorToPlay);
        }
    }

    /**
     * List legal moves for a specific color
     *
     * @param color {Color7k}
     * @returns {Move[]}
     */
    listLegalMovesForColor(color) {
        return this.listAllPieces()
            .filter(pieceAtPosition => pieceAtPosition.color === color)
            .flatMap(pieceAtPosition => this.listLegalMovesFrom(pieceAtPosition.position));
    }

    /**
     * @param moves {Move[]}
     */
    registerMoves(moves) {
        moves.forEach(move => this.registerMove(move));
    }

    /**
     * @param move {Move}
     * @return {Piece7k|null} The piece that was captured, if any
     */
    registerMove(move) {
        // if (this.#colorToPlay == null) {
        //     throw new Error("Game is over");
        // }

        const piece = this.pieceAt(move.from);

        if (piece == null) {
            throw new Error(`No piece at ${move.from}`);
        }

        if (piece.color !== this.#colorToPlay) {
            throw new Error(`It's not ${piece.color} turn to play, it's ${this.#colorToPlay} turn`);
        }

        if (!this.isLegalMove(move)) {
            throw new Error(`${move} is not a legal move`);
        }

        const capturedPiece = this.pieceAt(move.to);
        this.#content[move.from.x][move.from.y] = null;
        this.#content[move.to.x][move.to.y] = piece;

        const capturedPieceAtPosition = capturedPiece ? new PieceAtPosition7k(capturedPiece, move.to) : null;
        this.#historicalMoves.push(new HistoricalMove(move.from, move.to, piece.copy(), capturedPieceAtPosition));

        if (capturedPieceAtPosition != null) {
            this.#captures.get(piece.color).push(capturedPieceAtPosition);
            this.#checkWinConditions(capturedPieceAtPosition);
        }

        this.#updateColorToPlayOrGameOver();

        return capturedPiece;
    }

    /**
     * @param capturedPiece {PieceAtPosition7k}
     */
    #checkWinConditions(capturedPiece) {
        // a player is out when he loses his general or more than 10 pieces
        if (
            capturedPiece.abstractPieceType === PieceTypes.GENERAL ||
            this.#hasLostEnoughPiecesToBeEliminated(capturedPiece.color)
        ) {
            this.#convertCapturedArmy(this.#calculateArmyCapturedEvent(capturedPiece));
        }

        // TODO: flag winner if captures >= 30 or captured kingdoms >= 2
    }

    /**
     * @param color {Color7k}
     */
    #hasLostEnoughPiecesToBeEliminated(color) {
        const allCapturedPiecesOfColor = [];
        for (const pieces of this.#captures.values()) {
            allCapturedPiecesOfColor.push(...pieces.filter(it => it.color === color));
        }

        return allCapturedPiecesOfColor.length >= CAPTURE_THRESHOLD_TO_LOSE;
    }

    /**
     * @param capturedPiece {PieceAtPosition7k}
     */
    #calculateArmyCapturedEvent(capturedPiece) {
        const capturedColor = capturedPiece.piece.color;
        if (capturedPiece.abstractPieceType === PieceTypes.GENERAL) {
            // general capture
            return new ArmyCapturedEvent(this.#colorToPlay, capturedColor, true);
        } else {
            // capture the most pieces of the captured army
            const stillPlayingColors = this.colorsStillInGame();
            let longest = 0;
            for (const [capturing, capturedPieces] of this.#captures) {
                if (capturing !== capturedColor && stillPlayingColors.includes(capturing)) {
                    if (capturedPieces.length > longest) {
                        longest = capturedPieces.length;
                    }
                }
            }

            const candidateToWinTheRemainingArmy = [];
            for (const [capturing, capturedPieces] of this.#captures) {
                if (capturing !== capturedColor && stillPlayingColors.includes(capturing)) {
                    if (capturedPieces.length === longest) {
                        candidateToWinTheRemainingArmy.push(capturing);
                    }
                }
            }

            const capturingColor = this.#winnerTieBreak(candidateToWinTheRemainingArmy);
            return new ArmyCapturedEvent(capturingColor, capturedColor, false)
        }
    }

    /**
     *
     * @param candidateColors {Color7k[]}
     */
    #winnerTieBreak(candidateColors) {
        switch (candidateColors.length) {
            case 0:
                throw new Error("candidateColors is empty");
            case 1:
                return candidateColors[0];
            default:
                if (candidateColors.includes(this.#colorToPlay)) {
                    // candidate who did the capture that triggered the ArmyCapturedEvent
                    return this.#colorToPlay
                } else {
                    // candidate who last played before that
                    // historicalMoves.last { move -> candidateColors.contains(move.color) }.color
                    const matching = this.#historicalMoves.filter(move => candidateColors.includes(move.color));
                    return matching[matching.length - 1].color;
                }
        }
    }

    /**
     * @param event {ArmyCapturedEvent}
     */
    #convertCapturedArmy(event) {
        const capturedPieces = this.listPiecesByColor(event.capturedColor);

        // keep track of captures
        this.#captures.get(event.capturingColor).push(...capturedPieces);

        // transfer remaining army to the capturing player
        capturedPieces
            .map(pieceAtPosition => pieceAtPosition.copyWithColor(event.capturingColor))
            .forEach(pieceAtPosition => this.setPiece(pieceAtPosition));

        // keep track of captured kingdoms
        this.#capturedKingdomsMap.get(event.capturingColor).push(event.capturedColor);

        // add ArmyCapturedEvent to the last historical move
        const lastIndex = this.#historicalMoves.length - 1;
        this.#historicalMoves[lastIndex] = this.#historicalMoves[lastIndex].copyWithArmyCapturedEvent(event);
    }

    #updateColorToPlayOrGameOver() {
        const colorsStillInGame = this.colorsStillInGame();
        if (colorsStillInGame.length === 1) {
            this.#winner = colorsStillInGame[0];
        } else {
            const index = colorsStillInGame.indexOf(this.#colorToPlay);
            this.#colorToPlay = (index === colorsStillInGame.length - 1)
                ? colorsStillInGame[0]
                : colorsStillInGame[index + 1];
        }
    }

    /**
     * @param position {Position7k}
     * @returns {boolean}
     */
    hasGoBetweenAt(position) {
        const piece = this.pieceAt(position);
        return piece != null && piece.abstractPieceType === PieceTypes.GO_BETWEEN;
    }

    /**
     * @param position {Position7k}
     * @returns {Move[]}
     */
    listLegalMovesFrom(position) {
        if (!position.isOnBoard || position.isEmperor) {
            return [];
        }

        const piece = this.pieceAt(position);
        if (piece == null) {
            return [];
        }

        // most pieces move in both orthogonal and diagonal directions, on in one of the two
        // sometimes with a limit of squares

        let max = 0;
        let movesOrthogonal = false;
        let movesDiagonal = false;
        let captureEnabled = true;

        switch (piece.abstractPieceType) {
            case PieceTypes.GENERAL:
                // similar to chess queen
                max = BOARD_SIZE_7K;
                movesOrthogonal = true;
                movesDiagonal = true;
                break;
            case PieceTypes.CHANCELLOR:
                // similar to chess rook
                max = BOARD_SIZE_7K;
                movesOrthogonal = true;
                break;
            case PieceTypes.DIPLOMAT:
                // similar to chess bishop
                max = BOARD_SIZE_7K;
                movesDiagonal = true;
                break;
            case PieceTypes.ARCHER:
                // similar to chess queen, but limited
                max = 4;
                movesOrthogonal = true;
                movesDiagonal = true;
                break;
            case PieceTypes.CROSSBOWMAN:
                // similar to chess queen, but limited
                max = 5;
                movesOrthogonal = true;
                movesDiagonal = true;
                break;
            case PieceTypes.SWORDSMAN:
                // similar to xiangqi general
                max = 1;
                movesOrthogonal = true;
                break;
            case PieceTypes.DAGGER_SOLDIER:
                // similar to xiangqi advisor
                max = 1;
                movesDiagonal = true;
                break;
            case PieceTypes.GO_BETWEEN:
                max = BOARD_SIZE_7K;
                movesOrthogonal = true;
                movesDiagonal = true;
                captureEnabled = false;
                break;
            default:
                break;
        }

        const destinations = [];
        switch (piece.abstractPieceType) {
            case PieceTypes.GENERAL:
            case PieceTypes.CHANCELLOR:
            case PieceTypes.DIPLOMAT:
            case PieceTypes.ARCHER:
            case PieceTypes.CROSSBOWMAN:
            case PieceTypes.DAGGER_SOLDIER:
            case PieceTypes.SWORDSMAN:
            case PieceTypes.GO_BETWEEN:
                if (movesOrthogonal) {
                    // TODO: move to Position7k
                    const lines = [
                        position.allTopFor(max),
                        position.allBottomFor(max),
                        position.allLeftFor(max),
                        position.allRightFor(max)
                    ];

                    lines.forEach((line) => {
                        destinations.push(...this.#filterLineOfPositions(position, line, captureEnabled));
                    });
                }
                if (movesDiagonal) {
                    // TODO: move to Position7k
                    const lines = [
                        position.allTopRightDiagonalsFor(max),
                        position.allTopLeftDiagonalsFor(max),
                        position.allBottomLeftDiagonalsFor(max),
                        position.allBottomRightDiagonalsFor(max)
                    ];

                    lines.forEach((line) => {
                        destinations.push(...this.#filterLineOfPositions(position, line, captureEnabled));
                    });
                }
                break;
            case PieceTypes.CANNON:
                const lines = [
                    position.allTopFor(BOARD_SIZE_7K),
                    position.allBottomFor(BOARD_SIZE_7K),
                    position.allLeftFor(BOARD_SIZE_7K),
                    position.allRightFor(BOARD_SIZE_7K)
                ];

                lines.forEach((line) => {
                    destinations.push(...this.#filterMovesForCannon(position, line));
                });

                break;
            case PieceTypes.KNIGHT:
                const top = position.top;
                if (top.isOnBoard && !top.isEmperor && !this.hasPieceAt(top)) {
                    destinations.push(...this.#filterLineOfPositions(position, top.allTopLeftDiagonalsFor(3)));
                    destinations.push(...this.#filterLineOfPositions(position, top.allTopRightDiagonalsFor(3)));
                }

                const bottom = position.bottom;
                if (bottom.isOnBoard && !bottom.isEmperor && !this.hasPieceAt(bottom)) {
                    destinations.push(...this.#filterLineOfPositions(position, bottom.allBottomLeftDiagonalsFor(3)));
                    destinations.push(...this.#filterLineOfPositions(position, bottom.allBottomRightDiagonalsFor(3)));
                }

                const left = position.left;
                if (left.isOnBoard && !left.isEmperor && !this.hasPieceAt(left)) {
                    destinations.push(...this.#filterLineOfPositions(position, left.allTopLeftDiagonalsFor(3)));
                    destinations.push(...this.#filterLineOfPositions(position, left.allBottomLeftDiagonalsFor(3)));
                }

                const right = position.right;
                if (right.isOnBoard && !right.isEmperor && !this.hasPieceAt(right)) {
                    destinations.push(...this.#filterLineOfPositions(position, right.allTopRightDiagonalsFor(3)));
                    destinations.push(...this.#filterLineOfPositions(position, right.allBottomRightDiagonalsFor(3)));
                }

                break;
            default:
                throw new Error(`Unknown piece type ${piece.abstractPieceType}`);
        }

        return destinations.map(to => new Move(position, to));
    }

    /**
     * Filter lines of positions (orthogonal or diagonal) from a given position
     * Works "classical" types of movement (like rook, bishop, etc.) but not for cannon
     *
     * @param from {Position7k}
     * @param positions {Position7k[]}
     * @param captureEnabled {boolean} The Go-Between can not capture
     */
    #filterLineOfPositions(from, positions, captureEnabled = true) {
        const filtered = [];

        for (let i = 0; i < positions.length; i++) {
            const position = positions[i];
            if (position.isEmperor || !position.isOnBoard || this.containSameColors(from, position) || this.hasGoBetweenAt(position)) {
                return filtered;
            } else if (this.containDifferentColors(from, position)) {
                if (captureEnabled) {
                    filtered.push(position);
                }
                return filtered;
            } else {
                filtered.push(position);
            }
        }

        return filtered;
    }

    #filterMovesForCannon(from, positions) {
        const filtered = [];
        let foundPivot = false;

        for (let i = 0; i < positions.length; i++) {
            const position = positions[i];
            if (!position.isOnBoard) {
                return filtered;
            }

            if (!foundPivot) {
                if (this.hasPieceAt(position) || position.isEmperor) {
                    foundPivot = true;
                } else {
                    filtered.push(position);
                }
            } else {
                if (position.isEmperor) {
                    return filtered;
                } else if (this.containDifferentColors(from, position)) {
                    if (!this.hasGoBetweenAt(position)) {
                        filtered.push(position);
                    }
                    return filtered;
                } else if (this.containSameColors(from, position)) {
                    return filtered;
                }
            }
        }

        return filtered;
    }

    /**
     * @param p1 {Position7k}
     * @param p2 {Position7k}
     * @returns {boolean}
     */
    containSameColors(p1, p2) {
        const piece1 = this.pieceAt(p1);
        const piece2 = this.pieceAt(p2);
        if (piece1 == null || piece2 == null) {
            return false;
        }

        return Color7k.areEquals(piece1.color, piece2.color);
    }

    /**
     * @param p1 {Position7k}
     * @param p2 {Position7k}
     * @returns {boolean}
     */
    containDifferentColors(p1, p2) {
        const piece1 = this.pieceAt(p1);
        const piece2 = this.pieceAt(p2);
        if (piece1 == null || piece2 == null) {
            return false;
        }

        return !Color7k.areEquals(piece1.color, piece2.color);
    }

}

/**
 * @param char {string}
 * @return {Color7k|null}
 */
function getColorFromFenColorChar(char) {
    for (const [_, colorInstance] of Object.entries(ColorTypes)) {
        if (char.toLowerCase() === colorInstance.fenColorChar) {
            return colorInstance;
        }
    }
    return null;
}

/**
 * @param uci {string}
 * @return {AbstractPieceType7k|null}
 */
function getPieceTypeByUci(uci) {
    for (const [_, pieceType] of Object.entries(PieceTypes)) {
        if (uci.toUpperCase() === pieceType.uci.toUpperCase()) {
            return pieceType;
        }
    }
    return null;
}

/**
 * @param fen {string}
 * @return {PieceAtPosition7k[]}
 */
function parseFenToPiecesAtPositions(fen) {

    /**
     * @param fenRank {string}
     * @param y {number}
     * @return {PieceAtPosition7k[]}
     */
    function parseFenRank(fenRank, y) {
        let isParsingNumeral = false;
        let numberBuilder = '';
        let skipNextChar = false;
        let x = 0;
        const pieces = [];

        for (let i = 0; i < fenRank.length; i++) {
            const char = fenRank.charAt(i);

            if (isCharDigit(char)) {
                isParsingNumeral = true;
                numberBuilder += char;
            } else {
                if (isParsingNumeral) {
                    x += Number(numberBuilder);
                    isParsingNumeral = false;
                    numberBuilder = '';
                }

                if (!skipNextChar) {
                    // find color char
                    const color = getColorFromFenColorChar(char);
                    if (color == null) {
                        throw new Error(`Not found color ${char} in FEN`);
                    }

                    if (i === fenRank.length - 1) {
                        throw new Error(`Missing piece type at the end of the FEN rank`);
                    }

                    // piece char should be next
                    // characters for color char + piece char always go 2 by 2
                    const nextChar = fenRank.charAt(i + 1);
                    const pieceType = getPieceTypeByUci(nextChar);
                    if (pieceType == null) {
                        throw new Error(`Not found pieceType ${nextChar} in FEN`);
                    }

                    const piece = new Piece7k(color, pieceType);
                    const position = new Position7k(x, y);
                    const pieceAtPosition = new PieceAtPosition7k(piece, position);
                    pieces.push(pieceAtPosition);
                    skipNextChar = true;
                    x += 1;
                } else {
                    skipNextChar = false;
                }
            }
        }

        return pieces;
    }

    const fenRanks = fen.split('/');
    const pieces = [];
    for (let i = 0; i < BOARD_SIZE_7K; i++) {
        if (i < fenRanks.length) {
            pieces.push(...parseFenRank(fenRanks[i], BOARD_SIZE_7K - i - 1));
        }
    }

    return pieces;
}
