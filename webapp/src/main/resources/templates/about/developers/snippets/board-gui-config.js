/**
 * How file numbers are rendered around the board in WXF mode. Only affects the
 * file-number labels; the algebraic orientation (a..i letters) is unaffected.
 */
const FileNumbersStyle = Object.freeze({
    ARABIC_BOTH: 'ARABIC_BOTH',
    CHINESE_BOTH: 'CHINESE_BOTH',
    CHINESE_RED_ONLY: 'CHINESE_RED_ONLY',
    CHINESE_BLACK_ONLY: 'CHINESE_BLACK_ONLY',
    CHINESE_LOWER_ONLY: 'CHINESE_LOWER_ONLY',
    CHINESE_TOP_ONLY: 'CHINESE_TOP_ONLY',
    DEFAULT: 'CHINESE_RED_ONLY',
});

/**
 * Visual style of the board pieces. Used to pick the corresponding image folder
 * (under `${assetsBaseUrl}/images/pieces/<style-lowercased>/`).
 */
const PieceStyleSetting = Object.freeze({
    TRADITIONAL: 'TRADITIONAL',
    ROMANIZED_ROUNDED: 'ROMANIZED_ROUNDED',
    DEFAULT: 'TRADITIONAL',
});

/**
 * @typedef {Object} BoardGuiOptions
 * @property {string}      [elementId]              - id of the container element
 * @property {boolean}     [showCoordinates]        - whether to reserve space for file/rank coordinates
 * @property {string|null} [coordinatesOrientation] - one of {@link CoordinatesOrientation} or `null` to
 *                                                    hide the labels (space is still reserved when
 *                                                    `showCoordinates` is true). The caller is in
 *                                                    charge of resolving any user preference (e.g. cookies).
 * @property {boolean}     [mini]                   - whether this is a mini (thumb) board
 * @property {boolean}     [forceRenderChecks]      - render checks even on mini boards
 * @property {boolean}     [svg]                    - enable the SVG overlay (used for engine arrows)
 * @property {boolean}     [playSounds]             - whether board sounds are enabled
 * @property {string}      [assetsBaseUrl]          - base URL prepended to every static asset path
 *                                                    (images, audio). Default: `https://elephantchess.io`.
 *                                                    Pass an empty string to use relative paths (e.g. when
 *                                                    serving the assets from the current host on localhost).
 * @property {string}      [pieceStyle]             - one of {@link PieceStyleSetting}; selects the piece
 *                                                    image folder.
 * @property {boolean}     [colorblindFriendlyBlackPieces] - if true, black piece images get an invert
 *                                                    CSS filter for improved contrast.
 * @property {boolean}     [flipOpponentPieces]     - if true, opponent piece images are rotated
 *                                                    180° to simulate the OTB appearance.
 * @property {string}      [fileNumbersStyle]       - one of {@link FileNumbersStyle}; selects how
 *                                                    file numbers are rendered in WXF mode.
 */

/** @type {Readonly<Required<BoardGuiOptions>>} */
const DEFAULT_BOARD_GUI_OPTIONS = Object.freeze({
    elementId: 'board-container',
    showCoordinates: true,
    coordinatesOrientation: 'WXF',
    mini: false,
    forceRenderChecks: false,
    svg: false,
    playSounds: true,
    assetsBaseUrl: 'https://cdn.elephantchess.io/static',
    pieceStyle: PieceStyleSetting.DEFAULT,
    colorblindFriendlyBlackPieces: false,
    flipOpponentPieces: false,
    fileNumbersStyle: FileNumbersStyle.DEFAULT,
});
