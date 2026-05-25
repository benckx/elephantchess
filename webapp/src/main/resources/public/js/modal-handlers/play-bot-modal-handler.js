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
const BOT_OPTION_BUTTON_CLASS = 'option-button-div';
const BOT_OPTION_BUTTON_SELECTED_CLASS = 'option-button-div-selected';
const BOT_OPTION_BUTTON_DISABLED_CLASS = 'option-button-div-disabled';
const BOT_VARIANT_OPTION_GROUP = 'bot-variant-option-button-div';
const BOT_COLOR_OPTION_GROUP = 'bot-color-option-button-div';
const BOT_ENGINE_OPTION_GROUP = 'bot-engine-option-button-div';

class PlayBotModalHandler extends ModalHandler {

    #exclusionGroups = [BOT_VARIANT_OPTION_GROUP, BOT_COLOR_OPTION_GROUP, BOT_ENGINE_OPTION_GROUP];
    #optionDivs;
    #startFenStandardRadio = document.getElementById('start-fen-standard');
    #startFenCustomRadio = document.getElementById('start-fen-custom');
    #startFenInput = document.getElementById('start-fen');
    #playBotButton = document.getElementById('play-bot-button');
    #depthInput = document.getElementById('play-bot-depth');
    #depthValue = document.getElementById('play-bot-depth-value');
    #depthMaxValue = document.getElementById('play-bot-depth-max-value');

    constructor() {
        super();
        this.#optionDivs = getElementsByClassNameArray(BOT_OPTION_BUTTON_CLASS);

        this.#optionDivs.forEach(item => {
            item.addEventListener('click', () => {
                if (item.classList.contains(BOT_OPTION_BUTTON_DISABLED_CLASS)) {
                    return;
                }

                const itemExclusionGroup = this.#findExclusionGroup(item);
                if (itemExclusionGroup !== null) {
                    this.#unselectForGroup(itemExclusionGroup);
                    item.classList.add(BOT_OPTION_BUTTON_SELECTED_CLASS);
                }

                if (item.classList.contains(BOT_VARIANT_OPTION_GROUP)) {
                    this.#updateEngineAvailabilityFromVariant();
                    if (this.#startFenStandardRadio.checked) {
                        this.#setStartFenFromVariant();
                    }
                }
            });
        });

        this.#startFenStandardRadio.addEventListener('click', () => {
            this.#startFenInput.disabled = true;
            this.#setStartFenFromVariant();
        });
        this.#startFenCustomRadio.addEventListener('click', () => {
            this.#startFenInput.disabled = false;
        });
        this.#depthInput.addEventListener('input', () => {
            this.#updateDepthInfo();
        });
        this.#playBotButton.addEventListener('click', () => {
            this.#handleCreateGameClickEvent();
        });

        // Auto-detect Manchu variant from FEN: if piece section contains an 'M', select Manchu
        this.#startFenInput.addEventListener('input', () => {
            const piecePart = this.#startFenInput.value.split(' ')[0];
            const isManchu = piecePart.includes('M');
            this.#setSelectedVariant(isManchu ? Variant.MANCHU : Variant.XIANGQI);
            this.#updateEngineAvailabilityFromVariant();
        });

        makeRadioClickable();

        if (isUserAuthenticated()) {
            this.#depthInput.max = '14';
            this.#depthMaxValue.innerText = '14';
        } else {
            this.#depthInput.max = '6';
            this.#depthMaxValue.innerText = '6';
            this.#depthInput.value = String(Math.min(DEFAULT_DEPTH, Number(this.#depthInput.max)));
            addToolTip(
                document.getElementById('play-bot-depth-box'),
                'You must be logged in to play with depth greater than 6. You can make an account for free.'
            );

            getElementsByClassNameArray('add-asterisk').forEach(element => {
                element.innerText += '*';
            });

            showGuestMessages();
        }

        this.#updateDepthInfo();
        this.#updateEngineAvailabilityFromVariant();
    }

    #handleCreateGameClickEvent() {
        const variant = this.#getSelectedVariant();
        const color = this.#getSelectedColor();
        const depth = Number(this.#depthInput.value);
        const engine = this.#getSelectedEngine();
        const startFenValue = this.#readStartFenValue();

        try {
            if (startFenValue !== null) {
                validateStartFen(startFenValue);
            }
            this.#startFenInputSetValid(true);
            const body = {
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

    #updateDepthInfo() {
        this.#depthValue.innerText = this.#depthInput.value;
    }

    /**
     * @param item {Element}
     * @return {string|undefined}
     */
    #findExclusionGroup(item) {
        return this
            .#exclusionGroups
            .find(group => item.classList.contains(group));
    }

    /**
     * @param group {string}
     */
    #unselectForGroup(group) {
        this.#optionDivs
            .filter(item => item.classList.contains(group))
            .forEach(item => item.classList.remove(BOT_OPTION_BUTTON_SELECTED_CLASS));
    }

    /**
     * @param variant {string}
     */
    #setSelectedVariant(variant) {
        this.#unselectForGroup(BOT_VARIANT_OPTION_GROUP);
        const selectedVariantButton = this.#optionDivs
            .filter(item => item.classList.contains(BOT_VARIANT_OPTION_GROUP))
            .find(item => item.dataset.variant === variant)
        if (selectedVariantButton == null) {
            document.getElementById('variant-xiangqi-bot').classList.add(BOT_OPTION_BUTTON_SELECTED_CLASS);
        } else {
            selectedVariantButton.classList.add(BOT_OPTION_BUTTON_SELECTED_CLASS);
        }
    }

    #setStartFenFromVariant() {
        this.#startFenInput.value = this.#getSelectedVariant() === Variant.MANCHU ? MANCHU_START_FEN : DEFAULT_START_FEN;
    }

    #updateEngineAvailabilityFromVariant() {
        const isManchu = this.#getSelectedVariant() === Variant.MANCHU;
        const pikaButton = document.getElementById('engine-pika');

        if (isManchu) {
            pikaButton.classList.add(BOT_OPTION_BUTTON_DISABLED_CLASS);
            if (pikaButton.classList.contains(BOT_OPTION_BUTTON_SELECTED_CLASS)) {
                this.#unselectForGroup(BOT_ENGINE_OPTION_GROUP);
                document.getElementById('engine-fairy').classList.add(BOT_OPTION_BUTTON_SELECTED_CLASS);
            }
        } else {
            pikaButton.classList.remove(BOT_OPTION_BUTTON_DISABLED_CLASS);
        }
    }

    /**
     * @return {string}
     */
    #getSelectedVariant() {
        return this.#optionDivs
            .filter(item => item.classList.contains(BOT_VARIANT_OPTION_GROUP))
            .find(item => item.classList.contains(BOT_OPTION_BUTTON_SELECTED_CLASS))
            ?.dataset.variant ?? Variant.XIANGQI;
    }

    /**
     * @return {string}
     */
    #getSelectedColor() {
        return document.getElementById('play-as-black').classList.contains(BOT_OPTION_BUTTON_SELECTED_CLASS)
            ? Color.BLACK
            : Color.RED;
    }

    /**
     * @return {string}
     */
    #getSelectedEngine() {
        return document.getElementById('engine-fairy').classList.contains(BOT_OPTION_BUTTON_SELECTED_CLASS)
            ? 'FAIRYSTOCKFISH'
            : 'PIKAFISH';
    }

    /**
     * @return {string|null}
     */
    #readStartFenValue() {
        if (this.#startFenCustomRadio.checked) {
            return this.#startFenInput.value;
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
