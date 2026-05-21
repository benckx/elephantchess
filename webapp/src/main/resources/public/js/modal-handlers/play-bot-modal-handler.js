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

const DEFAULT_DEPTH = 6;

class PlayBotModalHandler extends ModalHandler {

    #variantRadios = document.getElementsByName('play-bot-variant');
    #colorRadios = document.getElementsByName('play-bot-color');
    #engineRatios = document.getElementsByName('play-bot-engine');
    #levelRadios = document.getElementsByName('play-bot-level');
    #startFenStandardRadio = document.getElementById('start-fen-standard');
    #startFenCustomRadio = document.getElementById('start-fen-custom');
    #startFenInput = document.getElementById('start-fen');
    #playBotButton = document.getElementById('play-bot-button');
    #enginePikaContainer = document.getElementById('engine-pika-container');
    #enginePikaRadio = document.getElementById('engine-pika');
    #engineFairyRadio = document.getElementById('engine-fairy');

    constructor() {
        super();
        this.#startFenStandardRadio.addEventListener('click', () => {
            this.#startFenInput.disabled = true;
        });
        this.#startFenCustomRadio.addEventListener('click', () => {
            this.#startFenInput.disabled = false
        });
        this.#playBotButton.addEventListener('click', () => {
            this.#handleCreateGameClickEvent()
        });

        // When Manchu variant is selected, disable Pikafish and switch to Fairy Stockfish
        this.#variantRadios.forEach(radio => {
            radio.addEventListener('change', () => {
                if (radio.value === 'MANCHU') {
                    this.#enginePikaRadio.disabled = true;
                    this.#enginePikaContainer.classList.add('standard-radio-disabled');
                    this.#engineFairyRadio.checked = true;
                } else {
                    this.#enginePikaRadio.disabled = false;
                    this.#enginePikaContainer.classList.remove('standard-radio-disabled');
                }
            });
        });

        makeRadioClickable();

        if (isUserAuthenticated()) {
            // enable all levels if users is authenticated
            for (let i = 4; i <= 7; i++) {
                document.getElementById(`level-${i}`).disabled = false;
                document.getElementById(`level-${i}-container`).classList.remove('standard-radio-disabled');
            }
        } else {
            // add tooltip if user is not authenticated
            for (let i = 4; i <= 7; i++) {
                const text = 'You must be logged in to play with that depth. You can make an account for free.';
                const div = document.getElementById(`level-${i}-container`);
                addToolTip(div, text);
            }

            getElementsByClassNameArray('add-asterisk').forEach (element => {
                element.innerText += '*';
            });

            showGuestMessages();
        }
    }

    #handleCreateGameClickEvent() {
        function levelIdToDepth(levelId) {
            let level = Number(levelId.split('-').pop());
            if (level === 0) {
                return 1;
            } else {
                return level * 2;
            }
        }

        // variant param
        let variant = 'XIANGQI';
        for (let i = 0; i < this.#variantRadios.length; i++) {
            if (this.#variantRadios[i].checked) {
                variant = this.#variantRadios[i].value;
            }
        }

        // color param
        let color = Color.RED;
        if (this.#colorRadios[1].checked) {
            color = Color.BLACK;
        }

        // depth param
        let depth = DEFAULT_DEPTH;
        for (let i = 0; i < this.#levelRadios.length; i++) {
            let levelRadio = this.#levelRadios[i];
            console.log(levelRadio);
            if (levelRadio.checked) {
                depth = levelIdToDepth(levelRadio.id);
            }
        }

        // engine param
        let engine = 'PIKAFISH';
        if (this.#engineRatios[1].checked) {
            engine = 'FAIRYSTOCKFISH';
        }

        // start fen param
        let startFenValue = this.#readStartFenValue();

        try {
            if (startFenValue !== null) {
                validateStartFen(startFenValue);
            }
            this.#startFenInputSetValid(true);
            let body = {
                'color': color,
                'depth': depth,
                'engine': engine,
                'startFen': startFenValue,
                'variant': variant,
            };
            postAndHandle('/api/botgame/create', body, json => {
                window.open('/playbot?id=' + json.gameId, '_self');
            });
        } catch (error) {
            UI.pushErrorNotification(error.message);
            this.#startFenInputSetValid(false);
        }
    }

    /**
     * @return {string|null}
     */
    #readStartFenValue() {
        if (this.#startFenCustomRadio.checked) {
            return this.#startFenInput.value
        } else {
            return null;
        }
    }

    #startFenInputSetValid(isValid) {
        if (isValid) {
            this.#startFenInput.classList.remove('incorrect-data');
        } else {
            this.#startFenInput.classList.add('incorrect-data');
        }
    }

}
