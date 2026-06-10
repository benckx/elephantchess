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
const SHOW_COLORED_AREAS_SETTING = 'setting.show.colored.areas';
const SHOW_RIVER_AREA_COLOR_SETTING = 'setting.show.river.area.color';
const SHOW_PALACE_AREA_COLOR_SETTING = 'setting.show.palace.area.color';

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
     * @return {boolean}
     */
    get isColoredAreasEnabled() {
        const cookieValue = getCookie(SHOW_COLORED_AREAS_SETTING);
        if (cookieValue !== null) {
            return cookieValue === 'true';
        }
        return getCookie(SHOW_RIVER_AREA_COLOR_SETTING) === 'true'
            || getCookie(SHOW_PALACE_AREA_COLOR_SETTING) === 'true';
    }

    /**
     * @param value {boolean}
     */
    set isColoredAreasEnabled(value) {
        setCookie(SHOW_COLORED_AREAS_SETTING, value.toString(), CHROME_COOKIE_MAX_TTL);
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
     * @return {string} one of {@link FileNumbersStyle} or {@link CoordinatesOrientation}.ALGEBRAIC
     */
    get coordinatesStyle() {
        const cookieValue = getCookie(COORDINATES_STYLE_SETTING);
        if (cookieValue !== null
            && (cookieValue === CoordinatesOrientation.ALGEBRAIC
                || Object.values(FileNumbersStyle).includes(cookieValue))) {
            return cookieValue;
        }
        // backward-compat default: tie to the move format chosen by the user
        // (PGN/Algebraic users get algebraic letters; WXF users get the default WXF flavor)
        const isWxfMoveFormat = this.moveFormat === MoveFormatSetting.WXF_DOT
            || this.moveFormat === MoveFormatSetting.WXF_EQUALS;
        return isWxfMoveFormat ? FileNumbersStyle.DEFAULT : CoordinatesOrientation.ALGEBRAIC;
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
        return this.coordinatesStyle === CoordinatesOrientation.ALGEBRAIC
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
        const style = this.coordinatesStyle;
        return Object.values(FileNumbersStyle).includes(style)
            ? style
            : FileNumbersStyle.DEFAULT;
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
        showColoredAreas: settingsManager.isColoredAreasEnabled,
        fileNumbersStyle: settingsManager.getFileNumbersStyle(),
        // when developing locally, serve the assets from the local server
        // (otherwise default to the production CDN baked into BoardGui)
        ...(isLocalHost ? {assetsBaseUrl: ''} : {}),
        ...overrides,
    };
}

/**
 * Returns cookie-backed persistence callbacks for a move-tree widget instance.
 *
 * @param {string} pageKey
 * @param {string} containerId
 * @return {{loadPersistedHeight: function(): number|null, persistHeight: function(number): void}}
 */
function moveTreeResizeCookiePersistence(pageKey, containerId) {
    const cookieName = `${pageKey}.${containerId}.height`;
    return {
        loadPersistedHeight: () => {
            const rawValue = getCookie(cookieName);
            if (rawValue === null) {
                return null;
            }
            return Number.parseInt(rawValue, 10);
        },
        persistHeight: (height) => {
            setCookie(cookieName, height.toString(), CHROME_COOKIE_MAX_TTL);
        }
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

    // advanced piece style
    #pieceStyleSelect = document.getElementById('piece-style-select');

    #advancedSettingsToggle = document.getElementById('advanced-settings-toggle');
    #advancedSettingsBox = document.getElementById('advanced-settings-box');
    #advancedSettingsCloseButton = document.getElementById('advanced-settings-close-button');
    #modalBackground = document.getElementById('modal-background');
    #advancedSettingsUsesModalBackground = false;

    // advanced settings
    #advancedMoveFormatSettingItem = document.getElementById('advanced-move-format-setting-item');

    #moveFormatSelect = document.getElementById('move-format-select');

    #showCoordinatesCheckbox = document.getElementById('show-coordinates-checkbox');

    #coordinatesStyleSelect = document.getElementById('coordinates-style-select');
    #coordinatesMoveFormatMismatchWarning = document.getElementById('coordinates-move-format-mismatch-warning');


    #flipOpponentPiecesCheckbox = document.getElementById('flip-opponent-pieces-checkbox');

    #playSoundsCheckbox = document.getElementById('play-sounds-checkbox');

    #colorblindFriendlyBlackPiecesCheckbox = document.getElementById('colorblind-friendly-black-pieces-checkbox');
    #coloredAreasCheckbox = document.getElementById('colored-areas-checkbox');

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

        // piece style: 2 quick picks in the main box + all options in advanced settings
        const pieceStyleValues = [
            PieceStyleSetting.TRADITIONAL,
            PieceStyleSetting.ROMANIZED_ROUNDED,
            PieceStyleSetting.MODERN,
            PieceStyleSetting.INK_BRUSH,
        ];
        const applyPieceStyle = (pieceStyle) => {
            this.#settingsManager.pieceStyle = pieceStyle;
            this.#boardGuis.forEach(boardGui => boardGui.updatePieceStyle(pieceStyle));
            if (pieceStyleValues.includes(pieceStyle)) {
                this.#pieceStyleSelect.value = pieceStyle;
            }
        };

        // select piece style 1 (main box)
        this.#selectPieceStyleTraditional.onclick = () => applyPieceStyle(PieceStyleSetting.TRADITIONAL);
        addToolTip(this.#selectPieceStyleTraditional, "Select 'Traditional' piece style");

        // select piece style 2 (main box)
        this.#selectPieceStyleRomanizedRounded.onclick = () => applyPieceStyle(PieceStyleSetting.ROMANIZED_ROUNDED);
        addToolTip(this.#selectPieceStyleRomanizedRounded, "Select 'Romanized Rounded' piece style");

        // piece style (advanced settings: all options)
        const currentPieceStyle = pieceStyleValues.includes(this.#settingsManager.pieceStyle)
            ? this.#settingsManager.pieceStyle
            : PieceStyleSetting.DEFAULT;
        this.#pieceStyleSelect.value = currentPieceStyle;
        this.#pieceStyleSelect.onchange = () => applyPieceStyle(this.#pieceStyleSelect.value);

        // move format
        const isWxfMoveFormat = (mf) => mf === MoveFormatSetting.WXF_DOT || mf === MoveFormatSetting.WXF_EQUALS;
        const isWxfCoordinatesStyle = (cs) => cs !== CoordinatesOrientation.ALGEBRAIC;
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
        this.#moveFormatSelect.onchange = () => applyMoveFormat(this.#moveFormatSelect.value);
        switch (this.#settingsManager.moveFormat) {
            case MoveFormatSetting.WXF_EQUALS:
            case MoveFormatSetting.PGN:
            case MoveFormatSetting.ALGEBRAIC_EN:
                this.#moveFormatSelect.value = this.#settingsManager.moveFormat;
                break;
            default:
                this.#moveFormatSelect.value = MoveFormatSetting.WXF_DOT;
                break;
        }
        applyMoveFormat(this.#settingsManager.moveFormat);
        if (this.#moveTreeWidget == null) {
            this.#advancedMoveFormatSettingItem.style.display = 'none';
        }

        // show coordinates
        const setShowCoordinatesEnabled = (enabled) => {
            if (this.#settingsManager.isShowCoordinatesEnabled === enabled) {
                return;
            }
            this.#boardGuis.forEach(board => board.toggleShowCoordinates());
            this.#settingsManager.isShowCoordinatesEnabled = enabled;
        }
        this.#showCoordinatesCheckbox.checked = this.#settingsManager.isShowCoordinatesEnabled;
        this.#showCoordinatesCheckbox.onchange = () => {
            setShowCoordinatesEnabled(this.#showCoordinatesCheckbox.checked);
        }

        // colorblind-friendly black pieces
        this.#colorblindFriendlyBlackPiecesCheckbox.checked = this.#settingsManager.isColorblindFriendlyBlackPiecesEnabled;
        this.#colorblindFriendlyBlackPiecesCheckbox.onchange = () => {
            const enabled = this.#colorblindFriendlyBlackPiecesCheckbox.checked;
            this.#settingsManager.isColorblindFriendlyBlackPiecesEnabled = enabled;
            this.#boardGuis.forEach(board => board.setColorblindFriendlyBlackPiecesEnabled(enabled));
        }

        // colored areas
        this.#coloredAreasCheckbox.checked = this.#settingsManager.isColoredAreasEnabled;
        this.#coloredAreasCheckbox.onchange = () => {
            const enabled = this.#coloredAreasCheckbox.checked;
            this.#settingsManager.isColoredAreasEnabled = enabled;
            this.#boardGuis.forEach(board => board.setShowColoredAreasEnabled(enabled));
        };

        // flip opponent pieces
        this.#flipOpponentPiecesCheckbox.checked = this.#settingsManager.isFlipOpponentPiecesEnabled;
        this.#flipOpponentPiecesCheckbox.onchange = () => {
            const enabled = this.#flipOpponentPiecesCheckbox.checked;
            this.#settingsManager.isFlipOpponentPiecesEnabled = enabled;
            this.#boardGuis.forEach(board => board.setFlipOpponentPiecesEnabled(enabled));
        }

        // play sounds
        const setPlaySoundsEnabled = (enabled) => {
            if (this.#settingsManager.isPlaySoundsEnabled === enabled) {
                return;
            }
            this.#settingsManager.isPlaySoundsEnabled = enabled;
            this.#boardGuis.forEach(boardGui => boardGui.updatePlaySounds(enabled));
        }
        this.#playSoundsCheckbox.checked = this.#settingsManager.isPlaySoundsEnabled;
        this.#playSoundsCheckbox.onchange = () => {
            setPlaySoundsEnabled(this.#playSoundsCheckbox.checked);
        }

        // coordinates style (WXF flavors + Algebraic letters)
        const coordinatesStyleValues = [
            FileNumbersStyle.ARABIC_BOTH,
            FileNumbersStyle.CHINESE_BOTH,
            FileNumbersStyle.CHINESE_RED_ONLY,
            FileNumbersStyle.CHINESE_BLACK_ONLY,
            FileNumbersStyle.CHINESE_LOWER_ONLY,
            FileNumbersStyle.CHINESE_TOP_ONLY,
            CoordinatesOrientation.ALGEBRAIC,
        ];
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
        const currentCoordinatesStyle = coordinatesStyleValues.includes(this.#settingsManager.coordinatesStyle)
            ? this.#settingsManager.coordinatesStyle
            : FileNumbersStyle.DEFAULT;
        this.#coordinatesStyleSelect.value = currentCoordinatesStyle;
        this.#coordinatesStyleSelect.onchange = () => applyCoordinatesStyle(this.#coordinatesStyleSelect.value);

        // when "show coordinates" is off, grey out and disable the coordinates style select
        const updateCoordinatesStyleEnabledState = () => {
            const enabled = this.#settingsManager.isShowCoordinatesEnabled;
            this.#coordinatesStyleSelect.disabled = !enabled;
            const label = this.#coordinatesStyleSelect.closest('.advanced-setting-options');
            if (label != null) {
                label.classList.toggle('advanced-setting-option-disabled', !enabled);
            }
        };
        updateCoordinatesStyleEnabledState();
        this.#showCoordinatesCheckbox.addEventListener('change', updateCoordinatesStyleEnabledState);
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
