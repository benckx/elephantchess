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
const BOT_TIME_CONTROL_OPTION_GROUP = 'bot-tc-option-button-div';

class PlayBotModalHandler extends ModalHandler {

    #exclusionGroups = [
        BOT_VARIANT_OPTION_GROUP,
        BOT_COLOR_OPTION_GROUP,
        BOT_ENGINE_OPTION_GROUP,
        BOT_TIME_CONTROL_OPTION_GROUP,
    ];
    #optionDivs;

    #depthInput = document.getElementById('play-bot-depth');
    #depthValue = document.getElementById('play-bot-depth-value');
    #depthMaxValue = document.getElementById('play-bot-depth-max-value');

    #openingRadios = document.getElementsByName('play-bot-opening');
    #openingRadioOptions = getElementsByClassNameArray('play-bot-opening-radio-option');
    #openingByFrequencyRadio = document.getElementById('opening-by-frequency');
    #openingEngineOnlyRadio = document.getElementById('opening-engine-only');

    #startFenStandardRadio = document.getElementById('start-fen-standard');
    #startFenCustomRadio = document.getElementById('start-fen-custom');
    #startFenInput = document.getElementById('start-fen');

    #playBotButton = document.getElementById('play-bot-button');

    #mainPanel = document.getElementById('play-bot-main-panel');
    #timeControlPanel = document.getElementById('play-bot-time-control-panel');
    #timeControlButton = document.getElementById('play-bot-time-control-button');
    #timeControlLabel = document.getElementById('play-bot-time-control-label');
    #timeControlBackButton = document.getElementById('play-bot-time-control-back-button');
    #modalTitle = document.getElementById('play-bot-modal-title');

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
                    this.#updateOpeningModeAvailabilityFromVariant();
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

        this.#timeControlButton.addEventListener('click', () => {
            this.#showTimeControlPanel();
        });
        this.#timeControlBackButton.addEventListener('click', () => {
            this.#showMainPanel();
        });
        this.#optionDivs
            .filter(item => item.classList.contains(BOT_TIME_CONTROL_OPTION_GROUP))
            .forEach(item => {
                item.addEventListener('click', () => {
                    this.#updateTimeControlLabel();
                    this.#showMainPanel();
                });
            });

        // Auto-detect Manchu variant from FEN: if piece section contains an 'M', select Manchu
        this.#startFenInput.addEventListener('input', () => {
            const piecePart = this.#startFenInput.value.split(' ')[0];
            const isManchu = piecePart.includes('M');
            this.#setSelectedVariant(isManchu ? Variant.MANCHU : Variant.XIANGQI);
            this.#updateEngineAvailabilityFromVariant();
            this.#updateOpeningModeAvailabilityFromVariant();
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
        this.#updateOpeningModeAvailabilityFromVariant();
    }

    #handleCreateGameClickEvent() {
        const variant = this.#getSelectedVariant();
        const color = this.#getSelectedColor();
        const depth = Number(this.#depthInput.value);
        const engine = this.#getSelectedEngine();
        const startFenValue = this.#readStartFenValue();

        // opening mode param
        let openingMode = 'BY_FREQUENCY';
        for (let i = 0; i < this.#openingRadios.length; i++) {
            if (this.#openingRadios[i].checked) {
                openingMode = this.#openingRadios[i].value;
            }
        }

        try {
            if (startFenValue !== null) {
                validateStartFen(startFenValue);
            }
            this.#startFenInputSetValid(true);
            const {base: timeControlBase, increment: timeControlIncrement} = this.#getSelectedTimeControl();
            const body = {
                'color': color,
                'depth': depth,
                'engine': engine,
                'startFen': startFenValue,
                'openingMode': openingMode,
                'variant': variant,
                'timeControlBase': timeControlBase,
                'timeControlIncrement': timeControlIncrement,
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

    #updateOpeningModeAvailabilityFromVariant() {
        const isManchu = this.#getSelectedVariant() === Variant.MANCHU;

        for (let i = 0; i < this.#openingRadios.length; i++) {
            this.#openingRadios[i].disabled = isManchu;
        }

        this.#openingRadioOptions.forEach(option => {
            if (isManchu) {
                option.classList.add('standard-radio-disabled');
            } else {
                option.classList.remove('standard-radio-disabled');
            }
        });

        if (isManchu) {
            this.#openingEngineOnlyRadio.checked = true;
        } else {
            this.#openingByFrequencyRadio.checked = true;
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

    #showTimeControlPanel() {
        this.#mainPanel.style.display = 'none';
        this.#timeControlPanel.style.display = '';
        this.#playBotButton.style.display = 'none';
        this.#timeControlBackButton.style.display = '';
        this.#modalTitle.innerText = 'Time control';
    }

    #showMainPanel() {
        this.#mainPanel.style.display = '';
        this.#timeControlPanel.style.display = 'none';
        this.#playBotButton.style.display = '';
        this.#timeControlBackButton.style.display = 'none';
        this.#modalTitle.innerText = 'Play Bot';
    }

    #getSelectedTimeControlButton() {
        return this.#optionDivs
            .filter(item => item.classList.contains(BOT_TIME_CONTROL_OPTION_GROUP))
            .find(item => item.classList.contains(BOT_OPTION_BUTTON_SELECTED_CLASS));
    }

    #updateTimeControlLabel() {
        const selected = this.#getSelectedTimeControlButton();
        if (selected != null) {
            this.#timeControlLabel.innerText = selected.dataset.tcLabel;
        }
    }

    /**
     * @return {{base: number|null, increment: number|null}}
     */
    #getSelectedTimeControl() {
        const selected = this.#getSelectedTimeControlButton();
        if (selected == null) {
            return {base: null, increment: null};
        }
        const base = selected.dataset.tcBase === '' ? null : Number(selected.dataset.tcBase);
        const increment = selected.dataset.tcIncrement === '' ? null : Number(selected.dataset.tcIncrement);
        return {base, increment};
    }

}
