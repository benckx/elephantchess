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

const MAX_ABS_CP = 7_706;

class InfoLineResult {

    #line;
    #fen;
    #depth;
    #cp;
    #mate;
    #pv;
    #bestMove;
    #isCheckmate;
    #colorToPlay;

    constructor(json) {
        this.#line = json.line;
        this.#fen = json.fen;
        this.#depth = json.depth;
        this.#cp = json.cp;
        this.#mate = json.mate;
        this.#pv = json.pv;
        this.#bestMove = json.bestMove;
        // noinspection EqualityComparisonWithCoercionJS
        this.#isCheckmate = json.isCheckmate;

        if (this.#fen.includes('b - -')) {
            this.#colorToPlay = Color.BLACK;
        } else if (this.#fen.includes('w - -')) {
            this.#colorToPlay = Color.RED;
        } else {
            this.#colorToPlay = null;
        }
    }

    /**
     * @return {string}
     */
    get rawLine() {
        return this.#line;
    }

    /**
     * @return {string}
     */
    get rawLineNoPv() {
        let split = this.#line.split(' ');
        let i = split.indexOf('pv');
        split.splice(i, split.length - i);
        return split.join(' ');
    }

    /**
     * @return {string}
     */
    get fen() {
        return this.#fen;
    }

    /**
     * @return {number}
     */
    get depth() {
        return this.#depth;
    }

    /**
     * @return {number|null }
     */
    get cp() {
        return this.#cp;
    }

    /**
     * @return {number|null}
     */
    get mate() {
        return this.#mate;
    }

    /**
     * @return {HalfMove[]}
     */
    get pv() {
        return this.#pv.map(uci => HalfMove.parseUci(uci));
    }

    /**
     * @returns {boolean}
     */
    get isCheckmate() {
        return this.#isCheckmate;
    }

    /**
     * @returns {string}
     */
    get colorToPlay() {
        return this.#colorToPlay;
    }

    /**
     * cp or mate normalized to -100 to 100, used for the eval bar
     *
     * @return {number|null}
     */
    get eval() {
        if (this.#isCheckmate && this.#colorToPlay === Color.BLACK) {
            return 100;
        } else if (this.#isCheckmate && this.#colorToPlay === Color.RED) {
            return -100;
        } else if (this.#cp != null) {
            return this.linearNormalizedCp();
        } else if (this.#mate != null) {
            return this.#linearNormalizedMate();
        } else {
            return null;
        }
    }

    /**
     * Evaluation as shown in the move history
     *
     * @return {string}
     */
    get evalAsString() {
        if (this.#isCheckmate) {
            return '#';
        } else if (this.#cp != null) {
            return formatCp(this.linearNormalizedCp());
        } else if (this.#mate != null) {
            const normalizedMate = this.normalizedMate();
            if (normalizedMate > 0) {
                return 'm' + Math.abs(normalizedMate);
            } else if (normalizedMate < 0) {
                return '-m' + Math.abs(normalizedMate);
            }
        }

        return '';
    }

    /**
     * @return {number|null}
     */
    linearNormalizedCp() {
        if (this.#cp != null) {
            let result = this.#cp;

            if (this.#colorToPlay === Color.BLACK) {
                result = -result;
            }
            if (result > MAX_ABS_CP) {
                result = MAX_ABS_CP;
            } else if (result < -MAX_ABS_CP) {
                result = -MAX_ABS_CP;
            }

            // map to 100
            return (result / MAX_ABS_CP) * 100;
        } else {
            return null;
        }
    }

    /**
     * @return {number|null}
     */
    #linearNormalizedMate() {
        let mate = this.normalizedMate();
        if (mate != null) {
            if (mate > 0) {
                return 100;
            } else if (mate < 0) {
                return -100;
            }
        }
        return null;
    }

    /**
     * "Mate In" from the point of view of RED.
     *
     * @return {number|null}
     */
    normalizedMate() {
        let result = this.#mate;
        if (this.#colorToPlay === Color.BLACK) {
            result = -result;
        }
        return result;
    }

    toLiteral(fenKey) {
        return {
            fenKey: fenKey,
            line: this.#line,
            depth: this.#depth,
            cp: this.#cp,
            mate: this.#mate,
            pv: this.#pv,
            isCheckmate: this.#isCheckmate
        };
    }

}
