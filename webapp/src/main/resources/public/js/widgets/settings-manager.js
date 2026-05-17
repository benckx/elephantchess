/*
 * Copyright (C) 2026  Encelade SRL
 * Copyright (C) 2026  elephantchess.io
 * Copyright (C) 2026  Benoît Vleminckx (benckx)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see <https://www.gnu.org/licenses/>.
 */

const PIECE_STYLE_SETTING = 'setting.piece.style';
const SHOW_COORDINATES_SETTING = 'setting.show.coordinates';
const COLORBLIND_FRIENDLY_BLACK_PIECES_SETTING = 'setting.colorblind.friendly.black.pieces';
const MOVE_FORMAT_SETTING = 'setting.move.format';
const MOVE_NODE_EVAL_FORMAT = 'setting.move.node.eval.format';
const SHOW_ANALYTICS_ARROWS = 'setting.show.analytics.arrows';

const MoveFormatSetting = Object.freeze({
    WXF_DOT: 'WXF_DOT',
    WXF_EQUALS: 'WXF_EQUALS',
    PGN: 'PGN',
    ALGEBRAIC_EN: 'ALGEBRAIC_EN',
    DEFAULT: 'WXF_DOT',
});

const MoveNodeEvalFormat = Object.freeze({
    NORMALIZED_CENTI_PAWNS: 'NORMALIZED_CENTI_PAWNS',
    ANNOTATION_SYMBOLS: 'ANNOTATION_SYMBOLS',
    DEFAULT: 'ANNOTATION_SYMBOLS',
});

/**
 * Get/set settings from/to cookies.
 */
class SettingsManager {

    /**
     * @return {string}
     */
    get pieceStyle() {
        const cookieValue = getCookie(PIECE_STYLE_SETTING);
        if (cookieValue === null) {
            return PieceStyleSetting.DEFAULT;
        } else {
            return cookieValue;
        }
    }

    /**
     * @param value {string}
     */
    set pieceStyle(value) {
        setCookie(PIECE_STYLE_SETTING, value, CHROME_COOKIE_MAX_TTL);
    }

    /**
     * @return {boolean}
     */
    get isShowCoordinatesEnabled() {
        const cookieValue = getCookie(SHOW_COORDINATES_SETTING);
        if (cookieValue === null) {
            return true;
        } else {
            return cookieValue === "true";
        }
    }

    /**
     * @param value {boolean}
     */
    set isShowCoordinatesEnabled(value) {
        setCookie(SHOW_COORDINATES_SETTING, value.toString(), CHROME_COOKIE_MAX_TTL);
    }

    /**
     * @return {boolean}
     */
    get isColorblindFriendlyBlackPiecesEnabled() {
        const cookieValue = getCookie(COLORBLIND_FRIENDLY_BLACK_PIECES_SETTING);
        if (cookieValue === null) {
            return false;
        } else {
            return cookieValue === 'true';
        }
    }

    /**
     * @param value {boolean}
     */
    set isColorblindFriendlyBlackPiecesEnabled(value) {
        setCookie(COLORBLIND_FRIENDLY_BLACK_PIECES_SETTING, value.toString(), CHROME_COOKIE_MAX_TTL);
    }

    /**
     * @return {string}
     */
    get moveFormat() {
        const cookieValue = getCookie(MOVE_FORMAT_SETTING);
        if (cookieValue === null) {
            return MoveFormatSetting.DEFAULT;
        } else {
            return cookieValue;
        }
    }

    /**
     * @param value {string}
     */
    set moveFormat(value) {
        setCookie(MOVE_FORMAT_SETTING, value, CHROME_COOKIE_MAX_TTL);
    }

    /**
     * @return {string}
     */
    get moveNodeEvalFormat() {
        const cookieValue = getCookie(MOVE_NODE_EVAL_FORMAT);
        if (cookieValue === null) {
            return MoveNodeEvalFormat.DEFAULT;
        } else {
            return cookieValue;
        }
    }

    /**
     * @param value {string}
     */
    set moveNodeEvalFormat(value) {
        setCookie(MOVE_NODE_EVAL_FORMAT, value, CHROME_COOKIE_MAX_TTL);
    }


    /**
     * @return {boolean}
     */
    get isShowAnalyticsArrowsEnabled() {
        const cookieValue = getCookie(SHOW_ANALYTICS_ARROWS);
        if (cookieValue === null) {
            return true;
        } else {
            return cookieValue === "true";
        }
    }

    /**
     * @param value {boolean}
     */
    set isShowAnalyticsArrowsEnabled(value) {
        setCookie(SHOW_ANALYTICS_ARROWS, value.toString(), CHROME_COOKIE_MAX_TTL);
    }

    /**
     * Resolves the user's preference into a {@link CoordinatesOrientation} value
     * (or `null` if the user disabled the coordinates display).
     *
     * @return {string|null}
     */
    getCoordinatesOrientation() {
        if (!this.isShowCoordinatesEnabled) {
            return null;
        }
        const isWxf = this.moveFormat === MoveFormatSetting.WXF_DOT
            || this.moveFormat === MoveFormatSetting.WXF_EQUALS;
        return isWxf ? CoordinatesOrientation.WXF : CoordinatesOrientation.UCI;
    }

}

/**
 * Webapp-level factory that builds {@link BoardGuiOptions} with the fields
 * resolved from the user's stored preferences (cookies). Use this everywhere
 * in the webapp instead of instantiating {@link BoardGui} directly, so that
 * {@link BoardGui} itself stays free of any dependency on settings/cookies.
 *
 * @param {BoardGuiOptions} [overrides] - explicit options that take precedence
 *                                        over the resolved defaults
 * @return {BoardGuiOptions}
 */
function buildWebappBoardGuiOptions(overrides = {}) {
    const settingsManager = new SettingsManager();
    const hostname = window.location.hostname;
    const isLocalHost = hostname === 'localhost' || hostname === '127.0.0.1';
    // const isLocalHost = false;
    return {
        coordinatesOrientation: settingsManager.getCoordinatesOrientation(),
        pieceStyle: settingsManager.pieceStyle,
        colorblindFriendlyBlackPieces: settingsManager.isColorblindFriendlyBlackPiecesEnabled,
        // when developing locally, serve the assets from the local server
        // (otherwise default to the production CDN baked into BoardGui)
        ...(isLocalHost ? { assetsBaseUrl: '' } : {}),
        ...overrides,
    };
}

/**
 * Convenience wrapper around {@link buildWebappBoardGuiOptions} that
 * instantiates a {@link BoardGui} with the resolved webapp defaults.
 *
 * @param {BoardGuiOptions} [overrides]
 * @return {BoardGui}
 */
function createWebappBoardGui(overrides = {}) {
    return new BoardGui(buildWebappBoardGuiOptions(overrides));
}

let moveLabelMap = new Map([
    [MoveFormatSetting.WXF_DOT, 'WXF .'],
    [MoveFormatSetting.WXF_EQUALS, 'WXF ='],
    [MoveFormatSetting.PGN, 'PGN'],
    [MoveFormatSetting.ALGEBRAIC_EN, 'Algebraic'],
]);

class SelectMoveFormatMenu extends ContextualMenu {

    /**
     * @type {BoardGui}
     */
    #boardGui;

    /**
     * @type {MoveTreeWidget}
     */
    #moveTreeWidget;

    #settingsManager = new SettingsManager();

    /**
     * @type {function(string)[]}
     */
    #listeners = [];

    /**
     * @param boardGui {BoardGui}
     * @param moveTreeWidget {MoveTreeWidget}
     */
    constructor(boardGui, moveTreeWidget) {
        super('select-move-format-menu');
        this.#boardGui = boardGui;
        this.#moveTreeWidget = moveTreeWidget;

        // build menu items
        let selectedMoveFormat = this.#settingsManager.moveFormat
        moveLabelMap.forEach((value, key) => {
            let label = value;
            if (selectedMoveFormat === key) {
                label = `✓ ${value}`;
            }

            this.addSimpleItem(label, () => {
                this.#updateMoveFormat(key);
            });
        });
    }

    /**
     * @param moveFormat {string}
     */
    #updateMoveFormat(moveFormat) {
        this.#settingsManager.moveFormat = moveFormat.toString();
        this.#boardGui.updateMoveFormat(moveFormat);
        this.#moveTreeWidget.updateMoveFormat(moveFormat);
        this.#listeners.forEach(listener => listener(moveFormat));
    }

    /**
     * @param listener {function(string)}
     */
    addListener(listener) {
        this.#listeners.push(listener);
    }

}

class SettingsGui {

    #moveTreeWidget;
    #settingsManager = new SettingsManager();
    #selectMoveFormatMenuListeners = [];

    /**
     * Additional listeners that trigger when the 'show analytics arrows' option is toggled
     *
     * @type {function(boolean)[]}
     */
    #showAnalyticsArrowsListener = [];

    /**
     * Needed for case where the settings will apply to multiple boards displayed on the same page,
     * e.g. in the lobby when we listed the last games and we show the settings as a Tip box.
     *
     * @type {BoardGui[]}
     */
    #boardGuis = [];

    #selectPieceStyleTraditional = document.getElementById('select-piece-style-traditional');
    #selectPieceStyleRomanizedRounded = document.getElementById('select-piece-style-romanized-rounded');
    #advancedSettingsToggle = document.getElementById('advanced-settings-toggle');
    #advancedSettingsBox = document.getElementById('advanced-settings-box');
    #advancedMoveFormatSettingItem = document.getElementById('advanced-move-format-setting-item');
    #moveFormatRadioWxfDot = document.getElementById('move-format-radio-wxf-dot');
    #moveFormatRadioWxfEquals = document.getElementById('move-format-radio-wxf-equals');
    #moveFormatRadioPgn = document.getElementById('move-format-radio-pgn');
    #moveFormatRadioAlgebraic = document.getElementById('move-format-radio-algebraic');
    #showCoordinatesEnabledRadio = document.getElementById('show-coordinates-enabled-radio');
    #showCoordinatesDisabledRadio = document.getElementById('show-coordinates-disabled-radio');
    #colorblindFriendlyBlackPiecesEnabledRadio = document.getElementById('colorblind-friendly-black-pieces-enabled-radio');
    #colorblindFriendlyBlackPiecesDisabledRadio = document.getElementById('colorblind-friendly-black-pieces-disabled-radio');
    #flipBoard = document.getElementById('flip-board-button');

    // optional
    #showAnalyticsArrowsItem = document.getElementById('show-analytics-arrows-item');
    #showAnalyticsArrowsImg = document.getElementById('show-analytics-arrows');

    // optional
    #editPositionItem = document.getElementById('edit-position-item');
    #editPositionButton = document.getElementById('edit-position-button');

    /**
     * @param boardGui {BoardGui}
     * @param moveTreeWidget {MoveTreeWidget|null}
     * @param showArrowsOption {boolean} - whether to show arrows option (it's only for analysis board)
     * @param showAdvancedSettingsLink {boolean} - whether to show "Advanced" settings trigger
     */
    constructor(boardGui, moveTreeWidget, showArrowsOption = false, showAdvancedSettingsLink = true) {
        this.#moveTreeWidget = moveTreeWidget;
        this.#boardGuis.push(boardGui);

        // select piece style 1
        this.#selectPieceStyleTraditional.onclick = () => {
            this.#settingsManager.pieceStyle = PieceStyleSetting.TRADITIONAL;
            this.#boardGuis.forEach(boardGui => boardGui.updatePieceStyle(PieceStyleSetting.TRADITIONAL));
        }
        addToolTip(this.#selectPieceStyleTraditional, "Select 'Traditional' piece style");

        // select piece style 2
        this.#selectPieceStyleRomanizedRounded.onclick = () => {
            this.#settingsManager.pieceStyle = PieceStyleSetting.ROMANIZED_ROUNDED;
            this.#boardGuis.forEach(boardGui => boardGui.updatePieceStyle(PieceStyleSetting.ROMANIZED_ROUNDED));
        }
        addToolTip(this.#selectPieceStyleRomanizedRounded, "Select 'Romanized Rounded' piece style");

        // move format
        const applyMoveFormat = (moveFormat) => {
            this.#settingsManager.moveFormat = moveFormat;
            if (this.#moveTreeWidget != null) {
                boardGui.updateMoveFormat(moveFormat);
                this.#moveTreeWidget.updateMoveFormat(moveFormat);
                this.#selectMoveFormatMenuListeners.forEach(listener => listener(moveFormat));
            }
        }
        this.#moveFormatRadioWxfDot.onchange = () => {
            if (this.#moveFormatRadioWxfDot.checked) {
                applyMoveFormat(MoveFormatSetting.WXF_DOT);
            }
        }
        this.#moveFormatRadioWxfEquals.onchange = () => {
            if (this.#moveFormatRadioWxfEquals.checked) {
                applyMoveFormat(MoveFormatSetting.WXF_EQUALS);
            }
        }
        this.#moveFormatRadioPgn.onchange = () => {
            if (this.#moveFormatRadioPgn.checked) {
                applyMoveFormat(MoveFormatSetting.PGN);
            }
        }
        this.#moveFormatRadioAlgebraic.onchange = () => {
            if (this.#moveFormatRadioAlgebraic.checked) {
                applyMoveFormat(MoveFormatSetting.ALGEBRAIC_EN);
            }
        }
        switch (this.#settingsManager.moveFormat) {
            case MoveFormatSetting.WXF_EQUALS:
                this.#moveFormatRadioWxfEquals.checked = true;
                break;
            case MoveFormatSetting.PGN:
                this.#moveFormatRadioPgn.checked = true;
                break;
            case MoveFormatSetting.ALGEBRAIC_EN:
                this.#moveFormatRadioAlgebraic.checked = true;
                break;
            default:
                this.#moveFormatRadioWxfDot.checked = true;
                break;
        }
        if (this.#moveTreeWidget == null) {
            this.#advancedMoveFormatSettingItem.style.display = 'none';
        }

        // show coordinates
        const updateShowCoordinatesRadios = (enabled) => {
            this.#showCoordinatesEnabledRadio.checked = enabled;
            this.#showCoordinatesDisabledRadio.checked = !enabled;
        }
        updateShowCoordinatesRadios(this.#settingsManager.isShowCoordinatesEnabled);
        this.#showCoordinatesEnabledRadio.onchange = () => {
            if (this.#showCoordinatesEnabledRadio.checked && !this.#settingsManager.isShowCoordinatesEnabled) {
                this.#boardGuis.forEach(board => board.toggleShowCoordinates());
                this.#settingsManager.isShowCoordinatesEnabled = true;
            }
        }
        this.#showCoordinatesDisabledRadio.onchange = () => {
            if (this.#showCoordinatesDisabledRadio.checked && this.#settingsManager.isShowCoordinatesEnabled) {
                this.#boardGuis.forEach(board => board.toggleShowCoordinates());
                this.#settingsManager.isShowCoordinatesEnabled = false;
            }
        }

        // colorblind-friendly black pieces
        const updateColorblindRadios = (enabled) => {
            this.#colorblindFriendlyBlackPiecesEnabledRadio.checked = enabled;
            this.#colorblindFriendlyBlackPiecesDisabledRadio.checked = !enabled;
        }
        updateColorblindRadios(this.#settingsManager.isColorblindFriendlyBlackPiecesEnabled);
        this.#colorblindFriendlyBlackPiecesEnabledRadio.onchange = () => {
            if (this.#colorblindFriendlyBlackPiecesEnabledRadio.checked) {
                this.#settingsManager.isColorblindFriendlyBlackPiecesEnabled = true;
                this.#boardGuis.forEach(board => board.setColorblindFriendlyBlackPiecesEnabled(true));
            }
        }
        this.#colorblindFriendlyBlackPiecesDisabledRadio.onchange = () => {
            if (this.#colorblindFriendlyBlackPiecesDisabledRadio.checked) {
                this.#settingsManager.isColorblindFriendlyBlackPiecesEnabled = false;
                this.#boardGuis.forEach(board => board.setColorblindFriendlyBlackPiecesEnabled(false));
            }
        }

        // advanced settings
        this.#advancedSettingsToggle.onclick = (e) => {
            e.preventDefault();
            this.#advancedSettingsBox.classList.toggle('advanced-settings-box-open');
        };
        if (!showAdvancedSettingsLink) {
            this.#advancedSettingsToggle.style.display = 'none';
            this.#advancedSettingsBox.style.display = 'none';
        } else {
            document.addEventListener('click', (event) => {
                const isInsideAdvancedBox = this.#advancedSettingsBox.contains(event.target);
                const isAdvancedToggle = this.#advancedSettingsToggle.contains(event.target);
                if (!isInsideAdvancedBox && !isAdvancedToggle) {
                    this.#advancedSettingsBox.classList.remove('advanced-settings-box-open');
                }
            });
        }

        // flip board
        this.#flipBoard.onclick = () => {
            this.#boardGuis.forEach(boardGui => boardGui.flip());
        }
        addToolTip(this.#flipBoard, 'Flip board');

        // show analytics arrows
        if (showArrowsOption) {
            this.#showAnalyticsArrowsItem.style.display = 'block';
            this.#showAnalyticsArrowsImg.onclick = () => {
                this.#settingsManager.isShowAnalyticsArrowsEnabled = !this.#settingsManager.isShowAnalyticsArrowsEnabled;
                const arrowsEnabled = this.#settingsManager.isShowAnalyticsArrowsEnabled;
                this.#showAnalyticsArrowsListener.forEach(listener => listener(arrowsEnabled));
            }
            addToolTip(this.#showAnalyticsArrowsImg, 'Show or hide analytics arrows');
        }
    }

    /**
     * @param boardGui {BoardGui}
     */
    addBoardGui(boardGui) {
        this.#boardGuis.push(boardGui);
    }

    /**
     * @param listener {function(string)}
     */
    addMoveFormatUpdateListener(listener) {
        this.#selectMoveFormatMenuListeners.push(listener);
    }

    /**
     * @param listener {function(boolean)}
     */
    addShowAnalyticsArrowsListener(listener) {
        this.#showAnalyticsArrowsListener.push(listener);
    }

    /**
     * Enable the "edit position" button. When clicked, it opens the position
     * editor modal.
     *
     * @param getCurrentStartFenCb {function(): string}
     * @param selectedFenCb {function(string)} called when the user confirms a new FEN
     */
    enableEditPositionButton(getCurrentStartFenCb, selectedFenCb) {
        this.#editPositionItem.style.display = 'block';
        this.#editPositionButton.onclick = () => {
            UI.showModalByName(Modals.POSITION_EDITOR, () => {
                new PositionEditorHandler(getCurrentStartFenCb, selectedFenCb);
            });
        };
        addToolTip(this.#editPositionButton, 'Edit start position');

        // pre-load the modal so it opens instantly on first click
        setTimeout(() => UI.preloadModal(Modals.POSITION_EDITOR), 2_000);
    }

}
