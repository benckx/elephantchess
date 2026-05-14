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

class IccsParser extends UciMoveParser {

    get name() {
        return 'ICCS';
    }

    tokenize() {
        function isMetadata(line) {
            return line.startsWith('[');
        }

        function isEmpty(line) {
            return line.trim() === '';
        }

        function isResult(line) {
            let trimmed = line.trim();
            return trimmed === '*' || trimmed === '1-0' || trimmed === '0-1' || trimmed === '1/2-1/2';
        }

        function mustSkip(line) {
            return isMetadata(line) || isEmpty(line) || isResult(line);
        }

        let lines = this.input.split('\n');
        let tokensLine = lines.filter(line => !mustSkip(line)).join(' ');
        return new SimpleTokenParser(tokensLine)
            .tokenize()
            .map(token => new ParsingToken(token.move.replaceAll('-', '').toLowerCase(), token.annotation));
    }

}
