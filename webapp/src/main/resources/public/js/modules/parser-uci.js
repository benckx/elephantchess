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

class UciMoveParser extends MoveParser {

    get name() {
        return 'UCI';
    }

    tokenize() {
        let lines = this.input.split('\n');
        let tokensLine = lines.join(' ');
        return new SimpleTokenParser(tokensLine).tokenize();
    }

    parse() {
        return this.tokenize().map(token => {
            let move = HalfMove.parseUci(token.move);
            return new MoveAndAnnotation(move, null);
        });
    }

}

class AlgebraicParser extends UciMoveParser {

    get name() {
        return 'Algebraic';
    }

    tokenize() {
        return super.tokenize().map(token => token.decrement());
    }

}
