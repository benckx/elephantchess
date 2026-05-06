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

class SevenKingdomsPlaygroundPage extends BasePage {

    #boardGui = new BoardGui7k({containerId: 'board-container'});
    #fenOutputArea = document.getElementById('fen-output-area');
    #shareFenAction = document.getElementById('share-fen-action')

    #settingsManager = new SettingsManager7k();

    constructor() {
        super();

        let fen = getQueryParam('fen');
        if (fen == null) {
            fen = DEFAULT_START_FEN_7K;
        }

        this.#boardGui.loadFen(fen);
        this.#fenOutputArea.innerText = fen;
        this.#boardGui.addPostMoveListener(() => {
            this.#fenOutputArea.innerText = this.#boardGui.outputFen();
        });
        this.#shareFenAction.addEventListener('click', () => {
            const link = `${getFullHost()}/7k/playground?fen=${encodeURI(this.#boardGui.outputFen())}`;
            copyTextToClipboardAndNotify(link, 'FEN link copied to clipboard!');
        });

        document
            .getElementById('piece-type-icons')
            .addEventListener('click', () => {
                this.#settingsManager.pieceStyle7k = PieceStyleSetting7K.WESTERNIZED_ICONS;
                this.#boardGui.render();
            });

        document
            .getElementById('piece-type-chinese')
            .addEventListener('click', () => {
                this.#settingsManager.pieceStyle7k = PieceStyleSetting7K.CHINESE_CHAR;
                this.#boardGui.render();
            });

        document
            .getElementById('piece-type-uci')
            .addEventListener('click', () => {
                this.#settingsManager.pieceStyle7k = PieceStyleSetting7K.UCI_LETTER;
                this.#boardGui.render();
            });
    }
}

window.onload = () => new SevenKingdomsPlaygroundPage();
