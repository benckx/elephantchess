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
const MOVE_FORMAT_SETTING = 'setting.move.format';
const MOVE_NODE_EVAL_FORMAT = 'setting.move.node.eval.format';
const SHOW_ANALYTICS_ARROWS = 'setting.show.analytics.arrows';
const COORDINATES_STYLE_SETTING = 'setting.coordinates.style';
const FLIP_OPPONENT_PIECES_SETTING = 'setting.flip.opponent.pieces';
const PLAY_SOUNDS_SETTING = 'setting.play.sounds';
const COLORBLIND_FRIENDLY_BLACK_PIECES_SETTING = 'setting.colorblind.friendly.black.pieces';

/**
 * User-facing style of the board coordinate labels.
 * Combines the WXF orientation and (for WXF) the numeral system used for file labels.
 */
const CoordinatesStyle = Object.freeze({
    /** WXF: Arabic numerals (1..9) on both sides. */
    WXF_ARABIC: 'WXF_ARABIC',
    /** WXF: Chinese numerals (一..九) on both sides. */
    WXF_CHINESE: 'WXF_CHINESE',
    /** WXF: Chinese numerals on red's side; Arabic on black's side. */
    WXF_CHINESE_RED_ONLY: 'WXF_CHINESE_RED_ONLY',
    /** WXF: Chinese numerals on black's side; Arabic on red's side. */
    WXF_CHINESE_BLACK_ONLY: 'WXF_CHINESE_BLACK_ONLY',
    /** WXF: Chinese numerals on the bottom side of the screen only. */
    WXF_CHINESE_LOWER_ONLY: 'WXF_CHINESE_LOWER_ONLY',
    /** WXF: Chinese numerals on the top side of the screen only. */
    WXF_CHINESE_TOP_ONLY: 'WXF_CHINESE_TOP_ONLY',
    /** Algebraic: letters a..i for files and 1..10 for ranks. */
    ALGEBRAIC: 'ALGEBRAIC',
    DEFAULT: 'WXF_CHINESE_RED_ONLY',
});

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
    get isPlaySoundsEnabled() {
        const cookieValue = getCookie(PLAY_SOUNDS_SETTING);
        return cookieValue === null ? true : cookieValue === "true";
    }

    /**
     * @param value {boolean}
     */
    set isPlaySoundsEnabled(value) {
        setCookie(PLAY_SOUNDS_SETTING, value.toString(), CHROME_COOKIE_MAX_TTL);
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
     * @return {string} one of {@link CoordinatesStyle}
     */
    get coordinatesStyle() {
        const cookieValue = getCookie(COORDINATES_STYLE_SETTING);
        if (cookieValue !== null && Object.values(CoordinatesStyle).includes(cookieValue)) {
            return cookieValue;
        }
        // backward-compat default: tie to the move format chosen by the user
        // (PGN/Algebraic users get algebraic letters; WXF users get the default WXF flavor)
        const isWxfMoveFormat = this.moveFormat === MoveFormatSetting.WXF_DOT
            || this.moveFormat === MoveFormatSetting.WXF_EQUALS;
        return isWxfMoveFormat ? CoordinatesStyle.DEFAULT : CoordinatesStyle.ALGEBRAIC;
    }

    /**
     * @param value {string}
     */
    set coordinatesStyle(value) {
        setCookie(COORDINATES_STYLE_SETTING, value, CHROME_COOKIE_MAX_TTL);
    }

    /**
     * @return {boolean}
     */
    get isFlipOpponentPiecesEnabled() {
        const cookieValue = getCookie(FLIP_OPPONENT_PIECES_SETTING);
        return cookieValue === 'true';
    }

    /**
     * @param value {boolean}
     */
    set isFlipOpponentPiecesEnabled(value) {
        setCookie(FLIP_OPPONENT_PIECES_SETTING, value.toString(), CHROME_COOKIE_MAX_TTL);
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
        return this.coordinatesStyle === CoordinatesStyle.ALGEBRAIC
            ? CoordinatesOrientation.ALGEBRAIC
            : CoordinatesOrientation.WXF;
    }

    /**
     * Resolves the user's preference into a {@link FileNumbersStyle} value
     * (only meaningful when the orientation is WXF).
     *
     * @return {string}
     */
    getFileNumbersStyle() {
        switch (this.coordinatesStyle) {
            case CoordinatesStyle.WXF_ARABIC:
                return FileNumbersStyle.ARABIC_BOTH;
            case CoordinatesStyle.WXF_CHINESE:
                return FileNumbersStyle.CHINESE_BOTH;
            case CoordinatesStyle.WXF_CHINESE_RED_ONLY:
                return FileNumbersStyle.CHINESE_RED_ONLY;
            case CoordinatesStyle.WXF_CHINESE_BLACK_ONLY:
                return FileNumbersStyle.CHINESE_BLACK_ONLY;
            case CoordinatesStyle.WXF_CHINESE_LOWER_ONLY:
                return FileNumbersStyle.CHINESE_LOWER_ONLY;
            case CoordinatesStyle.WXF_CHINESE_TOP_ONLY:
                return FileNumbersStyle.CHINESE_TOP_ONLY;
            default:
                return FileNumbersStyle.DEFAULT;
        }
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
        playSounds: settingsManager.isPlaySoundsEnabled,
        pieceStyle: settingsManager.pieceStyle,
        colorblindFriendlyBlackPieces: settingsManager.isColorblindFriendlyBlackPiecesEnabled,
        flipOpponentPieces: settingsManager.isFlipOpponentPiecesEnabled,
        fileNumbersStyle: settingsManager.getFileNumbersStyle(),
        // when developing locally, serve the assets from the local server
        // (otherwise default to the production CDN baked into BoardGui)
        ...(isLocalHost ? {assetsBaseUrl: ''} : {}),
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

let activeAdvancedSettingsEscapeListener = null;
let activeAdvancedSettingsCloseHandler = null;

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

    // base settings
    #selectPieceStyleTraditional = document.getElementById('select-piece-style-traditional');
    #selectPieceStyleRomanizedRounded = document.getElementById('select-piece-style-romanized-rounded');
    #flipBoard = document.getElementById('flip-board-button');

    #advancedSettingsToggle = document.getElementById('advanced-settings-toggle');
    #advancedSettingsBox = document.getElementById('advanced-settings-box');
    #advancedSettingsCloseButton = document.getElementById('advanced-settings-close-button');
    #modalBackground = document.getElementById('modal-background');
    #advancedSettingsUsesModalBackground = false;

    // advanced settings
    #advancedMoveFormatSettingItem = document.getElementById('advanced-move-format-setting-item');

    #moveFormatRadioWxfDot = document.getElementById('move-format-radio-wxf-dot');
    #moveFormatRadioWxfEquals = document.getElementById('move-format-radio-wxf-equals');
    #moveFormatRadioPgn = document.getElementById('move-format-radio-pgn');
    #moveFormatRadioAlgebraic = document.getElementById('move-format-radio-algebraic');

    #showCoordinatesEnabledRadio = document.getElementById('show-coordinates-enabled-radio');
    #showCoordinatesDisabledRadio = document.getElementById('show-coordinates-disabled-radio');

    #coordinatesStyleWxfArabicRadio = document.getElementById('coordinates-style-wxf-arabic-radio');
    #coordinatesStyleWxfChineseRadio = document.getElementById('coordinates-style-wxf-chinese-radio');
    #coordinatesStyleWxfChineseRedOnlyRadio = document.getElementById('coordinates-style-wxf-chinese-red-only-radio');
    #coordinatesStyleWxfChineseBlackOnlyRadio = document.getElementById('coordinates-style-wxf-chinese-black-only-radio');
    #coordinatesStyleWxfChineseLowerOnlyRadio = document.getElementById('coordinates-style-wxf-chinese-lower-only-radio');
    #coordinatesStyleWxfChineseTopOnlyRadio = document.getElementById('coordinates-style-wxf-chinese-top-only-radio');
    #coordinatesStyleAlgebraicRadio = document.getElementById('coordinates-style-algebraic-radio');
    #coordinatesMoveFormatMismatchWarning = document.getElementById('coordinates-move-format-mismatch-warning');


    #flipOpponentPiecesEnabledRadio = document.getElementById('flip-opponent-pieces-enabled-radio');
    #flipOpponentPiecesDisabledRadio = document.getElementById('flip-opponent-pieces-disabled-radio');

    #playSoundsEnabledRadio = document.getElementById('play-sounds-enabled-radio');
    #playSoundsDisabledRadio = document.getElementById('play-sounds-disabled-radio');

    #colorblindFriendlyBlackPiecesEnabledRadio = document.getElementById('colorblind-friendly-black-pieces-enabled-radio');
    #colorblindFriendlyBlackPiecesDisabledRadio = document.getElementById('colorblind-friendly-black-pieces-disabled-radio');

    // optional (for Analysis Board)
    #showAnalyticsArrowsItem = document.getElementById('show-analytics-arrows-item');
    #showAnalyticsArrowsImg = document.getElementById('show-analytics-arrows');

    // optional (for Analysis Board)
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
        const isWxfMoveFormat = (mf) => mf === MoveFormatSetting.WXF_DOT || mf === MoveFormatSetting.WXF_EQUALS;
        const isWxfCoordinatesStyle = (cs) =>
            cs === CoordinatesStyle.WXF_ARABIC
            || cs === CoordinatesStyle.WXF_CHINESE
            || cs === CoordinatesStyle.WXF_CHINESE_RED_ONLY
            || cs === CoordinatesStyle.WXF_CHINESE_BLACK_ONLY
            || cs === CoordinatesStyle.WXF_CHINESE_LOWER_ONLY
            || cs === CoordinatesStyle.WXF_CHINESE_TOP_ONLY;
        const updateCoordinatesMoveFormatMismatchWarning = () => {
            const mf = this.#settingsManager.moveFormat;
            const cs = this.#settingsManager.coordinatesStyle;
            const mismatch = isWxfMoveFormat(mf) !== isWxfCoordinatesStyle(cs);
            if (this.#coordinatesMoveFormatMismatchWarning != null) {
                this.#coordinatesMoveFormatMismatchWarning.hidden = !mismatch;
            }
        };
        const applyMoveFormat = (moveFormat) => {
            this.#settingsManager.moveFormat = moveFormat;
            if (this.#moveTreeWidget != null) {
                // Move format only affects how moves are displayed in the move tree widget;
                // it must not redraw the board (would clear transient overlays like check highlights).
                this.#moveTreeWidget.updateMoveFormat(moveFormat);
                this.#selectMoveFormatMenuListeners.forEach(listener => listener(moveFormat));
            }
            updateCoordinatesMoveFormatMismatchWarning();
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
        applyMoveFormat(this.#settingsManager.moveFormat);
        if (this.#moveTreeWidget == null) {
            this.#advancedMoveFormatSettingItem.style.display = 'none';
        }

        // show coordinates
        const updateShowCoordinatesRadios = (enabled) => {
            this.#showCoordinatesEnabledRadio.checked = enabled;
            this.#showCoordinatesDisabledRadio.checked = !enabled;
        }
        const setShowCoordinatesEnabled = (enabled) => {
            if (this.#settingsManager.isShowCoordinatesEnabled === enabled) {
                return;
            }
            this.#boardGuis.forEach(board => board.toggleShowCoordinates());
            this.#settingsManager.isShowCoordinatesEnabled = enabled;
        }
        updateShowCoordinatesRadios(this.#settingsManager.isShowCoordinatesEnabled);
        this.#showCoordinatesEnabledRadio.onchange = () => {
            if (this.#showCoordinatesEnabledRadio.checked) {
                setShowCoordinatesEnabled(true);
            }
        }
        this.#showCoordinatesDisabledRadio.onchange = () => {
            if (this.#showCoordinatesDisabledRadio.checked) {
                setShowCoordinatesEnabled(false);
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

        // flip opponent pieces
        const updateFlipOpponentPiecesRadios = (enabled) => {
            this.#flipOpponentPiecesEnabledRadio.checked = enabled;
            this.#flipOpponentPiecesDisabledRadio.checked = !enabled;
        }
        updateFlipOpponentPiecesRadios(this.#settingsManager.isFlipOpponentPiecesEnabled);
        this.#flipOpponentPiecesEnabledRadio.onchange = () => {
            if (this.#flipOpponentPiecesEnabledRadio.checked) {
                this.#settingsManager.isFlipOpponentPiecesEnabled = true;
                this.#boardGuis.forEach(board => board.setFlipOpponentPiecesEnabled(true));
            }
        }
        this.#flipOpponentPiecesDisabledRadio.onchange = () => {
            if (this.#flipOpponentPiecesDisabledRadio.checked) {
                this.#settingsManager.isFlipOpponentPiecesEnabled = false;
                this.#boardGuis.forEach(board => board.setFlipOpponentPiecesEnabled(false));
            }
        }

        // play sounds
        const updatePlaySoundsRadios = (enabled) => {
            this.#playSoundsEnabledRadio.checked = enabled;
            this.#playSoundsDisabledRadio.checked = !enabled;
        }
        const setPlaySoundsEnabled = (enabled) => {
            if (this.#settingsManager.isPlaySoundsEnabled === enabled) {
                return;
            }
            this.#settingsManager.isPlaySoundsEnabled = enabled;
            this.#boardGuis.forEach(boardGui => boardGui.updatePlaySounds(enabled));
        }
        updatePlaySoundsRadios(this.#settingsManager.isPlaySoundsEnabled);
        this.#playSoundsEnabledRadio.onchange = () => {
            if (this.#playSoundsEnabledRadio.checked) {
                setPlaySoundsEnabled(true);
            }
        }
        this.#playSoundsDisabledRadio.onchange = () => {
            if (this.#playSoundsDisabledRadio.checked) {
                setPlaySoundsEnabled(false);
            }
        }

        // coordinates style (WXF flavors + Algebraic letters)
        const coordinatesStyleRadios = {
            [CoordinatesStyle.WXF_ARABIC]: this.#coordinatesStyleWxfArabicRadio,
            [CoordinatesStyle.WXF_CHINESE]: this.#coordinatesStyleWxfChineseRadio,
            [CoordinatesStyle.WXF_CHINESE_RED_ONLY]: this.#coordinatesStyleWxfChineseRedOnlyRadio,
            [CoordinatesStyle.WXF_CHINESE_BLACK_ONLY]: this.#coordinatesStyleWxfChineseBlackOnlyRadio,
            [CoordinatesStyle.WXF_CHINESE_LOWER_ONLY]: this.#coordinatesStyleWxfChineseLowerOnlyRadio,
            [CoordinatesStyle.WXF_CHINESE_TOP_ONLY]: this.#coordinatesStyleWxfChineseTopOnlyRadio,
            [CoordinatesStyle.ALGEBRAIC]: this.#coordinatesStyleAlgebraicRadio,
        };
        const applyCoordinatesStyle = (style) => {
            this.#settingsManager.coordinatesStyle = style;
            const orientation = this.#settingsManager.getCoordinatesOrientation();
            const fileNumbersStyle = this.#settingsManager.getFileNumbersStyle();
            this.#boardGuis.forEach(board => {
                board.setCoordinatesOrientation(orientation);
                board.setFileNumbersStyle(fileNumbersStyle);
            });
            updateCoordinatesMoveFormatMismatchWarning();
        };
        const currentCoordinatesStyle = coordinatesStyleRadios[this.#settingsManager.coordinatesStyle]
            ? this.#settingsManager.coordinatesStyle
            : CoordinatesStyle.DEFAULT;
        coordinatesStyleRadios[currentCoordinatesStyle].checked = true;
        Object.entries(coordinatesStyleRadios).forEach(([style, radio]) => {
            radio.onchange = () => {
                if (radio.checked) {
                    applyCoordinatesStyle(style);
                }
            };
        });

        // when "show coordinates" is off, grey out the coordinates style options
        const updateCoordinatesStyleEnabledState = () => {
            const enabled = this.#settingsManager.isShowCoordinatesEnabled;
            Object.values(coordinatesStyleRadios).forEach(radio => {
                if (radio == null) {
                    return;
                }
                radio.disabled = !enabled;
                const label = radio.closest('label');
                if (label != null) {
                    label.classList.toggle('advanced-setting-option-disabled', !enabled);
                }
            });
        };
        updateCoordinatesStyleEnabledState();
        this.#showCoordinatesEnabledRadio.addEventListener('change', updateCoordinatesStyleEnabledState);
        this.#showCoordinatesDisabledRadio.addEventListener('change', updateCoordinatesStyleEnabledState);
        updateCoordinatesMoveFormatMismatchWarning();

        // advanced settings
        const isMobileAdvancedSettingsLayout = () => window.matchMedia('(max-width: 1000px)').matches;
        const closeAdvancedSettings = () => {
            this.#advancedSettingsBox.classList.remove('advanced-settings-box-open');
            if (activeAdvancedSettingsCloseHandler === closeAdvancedSettings) {
                activeAdvancedSettingsCloseHandler = null;
            }
            if (this.#advancedSettingsUsesModalBackground) {
                this.#advancedSettingsUsesModalBackground = false;
                this.#modalBackground.style.display = 'none';
            }
        };
        const openAdvancedSettings = () => {
            if (isMobileAdvancedSettingsLayout() && this.#modalBackground != null) {
                const isModalBackgroundHidden = this.#modalBackground.style.display === ''
                    || this.#modalBackground.style.display === 'none';
                if (isModalBackgroundHidden) {
                    this.#advancedSettingsUsesModalBackground = true;
                    this.#modalBackground.style.display = 'flex';
                }
            }
            activeAdvancedSettingsCloseHandler = closeAdvancedSettings;
            this.#advancedSettingsBox.classList.add('advanced-settings-box-open');
        };
        this.#advancedSettingsToggle.onclick = (e) => {
            e.preventDefault();
            if (this.#advancedSettingsBox.classList.contains('advanced-settings-box-open')) {
                closeAdvancedSettings();
            } else {
                openAdvancedSettings();
            }
        };
        this.#advancedSettingsCloseButton.onclick = (e) => {
            e.preventDefault();
            closeAdvancedSettings();
        };
        if (activeAdvancedSettingsEscapeListener === null) {
            activeAdvancedSettingsEscapeListener = (event) => {
                if (event.key === 'Escape' && typeof activeAdvancedSettingsCloseHandler === 'function') {
                    activeAdvancedSettingsCloseHandler();
                }
            };
            document.addEventListener('keydown', activeAdvancedSettingsEscapeListener);
        }
        if (!showAdvancedSettingsLink) {
            this.#advancedSettingsToggle.style.display = 'none';
            this.#advancedSettingsBox.style.display = 'none';
        } else {
            document.addEventListener('click', (event) => {
                const isInsideAdvancedBox = this.#advancedSettingsBox.contains(event.target);
                const isAdvancedToggle = this.#advancedSettingsToggle.contains(event.target);
                if (!isInsideAdvancedBox && !isAdvancedToggle) {
                    closeAdvancedSettings();
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
