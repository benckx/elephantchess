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

const SITE_URL = 'https://elephantchess.io';

const DEFAULT_DATE_FORMAT = 'ddd. D MMM. YYYY HH:mm';
const DEFAULT_DATE_FORMAT_NO_TIME = 'ddd. D MMM. YYYY';
const DEFAULT_DATE_FORMAT_NO_YEAR = 'ddd. D MMM. HH:mm';
const DAY_FORMAT_PGN = 'YYYY.MM.DD';
const SHORT_DATE_FORMAT = 'D MMM YYYY';

const HTML_WHITE_SPACE = '&nbsp;';
const MOBILE_MAX_WIDTH = 1000;

const MS_PER_MINUTE = 60 * 1000;
const MS_PER_HOUR = MS_PER_MINUTE * 60;
const MS_PER_DAY = MS_PER_HOUR * 24;
const MS_PER_MONTH = MS_PER_DAY * 30;
const MS_PER_YEAR = MS_PER_DAY * 365;

const NUMBER_SUFFIXES = ['', 'k', 'M', 'B', 'T'];

// Server stores timestamps as UTC milliseconds; render them as UTC wall-clock so
// the same timestamp displays consistently regardless of the visitor's timezone.
dayjs.extend(dayjs_plugin_utc);

const eqSet = (xs, ys) =>
    xs.size === ys.size &&
    [...xs].every((x) => ys.has(x));

function isCharDigit(char) {
    return char.length === 1 && char.match(/[0-9]/i);
}

function isCharLetter(char) {
    return char.length === 1 && char.toLowerCase().match(/[a-z]/i);
}

/**
 * @param param {string}
 * @return {string|null}
 */
// TODO: move to api.js
function getQueryParam(param) {
    return new URLSearchParams(window.location.search).get(param);
}

/**
 * @param param {string}
 * @return {string[]}
 */
// TODO: move to api.js
function getAllQueryParam(param) {
    return new URLSearchParams(window.location.search).getAll(param);
}

/**
 * @return {number}
 */
function setIntervalNoDelay(cb, interval) {
    cb();
    return setInterval(cb, interval);
}

function displayPercentage(value, digits = 2) {
    return (Number(value) * 100).toFixed(digits) + '%'
}

/**
 * Empty tbody
 *
 * @param table {HTMLTableElement}
 * @return {HTMLTableSectionElement}
 */
function emptyTable(table) {
    const tbody = table.getElementsByTagName('tbody').item(0);
    tbody.innerHTML = '';
    return tbody;
}

/**
 * @return {string}
 */
function randomId() {
    return Math.random().toString(36).slice(2);
}

function lightenOrDarkenColor(col, amt) {
    col = parseInt(col, 16);
    return (((col & 0x0000FF) + amt) | ((((col >> 8) & 0x00FF) + amt) << 8) | (((col >> 16) + amt) << 16)).toString(16);
}

/**
 * Formats an epoch-millis timestamp in the user's local timezone.
 *
 * @param timestamp {number}
 * @return {string}
 */
function formatTimestampToDateTime(timestamp) {
    const now = dayjs();
    const date = dayjs(timestamp);
    if (now.year() === date.year()) {
        return date.format(DEFAULT_DATE_FORMAT_NO_YEAR);
    } else {
        return date.format(DEFAULT_DATE_FORMAT);
    }
}

/**
 * 'D MMM YYYY'
 *
 * @param timestamp {number}
 * @return {string}
 */
function formatTimestampToShortDate(timestamp) {
    return dayjs(timestamp).format(SHORT_DATE_FORMAT);
}

/**
 * 'ddd. D MMM. YYYY HH:mm'
 *
 * @param timestamp {number}
 * @return {string}
 */
function formatTimestampDefaultDateFormat(timestamp) {
    return dayjs(timestamp).format(DEFAULT_DATE_FORMAT);
}

/**
 * 'ddd. D MMM. YYYY'
 *
 * @param timestamp {number}
 * @return {string}
 */
function formatTimestampDefaultDateFormatNoTime(timestamp) {
    return dayjs(timestamp).format(DEFAULT_DATE_FORMAT_NO_TIME);
}

/**
 * 'YYYY.MM.DD'
 *
 * @param timestamp {number}
 * @return {string}
 */
function formatTimestampToPgnDate(timestamp) {
    return dayjs.utc(timestamp).format(DAY_FORMAT_PGN);
}

/**
 * Input is a date-only string (no time / no TZ); format it as-is using UTC to
 * avoid the local-TZ shift that would turn '2026-05-04' into the previous day
 * for negative offsets.
 *
 * @param day {string}
 * @return {string}
 */
function formatDayToShortDateFormat(day) {
    const date = Date.parse(day);
    return dayjs.utc(date).format(SHORT_DATE_FORMAT);
}

/**
 * @param timestamp {number}
 * @return {string}
 */
function formatTimestampToShortDateFormat(timestamp) {
    return dayjs(timestamp).format(SHORT_DATE_FORMAT);
}

/**
 * @param timestamp {number}
 * @returns {string}
 */
function formatTimestampToRelativeTime(timestamp) {
    return millisToRelativeTime(dayjs.utc().valueOf() - timestamp);
}

/**
 * @param timestamp {number}
 * @returns {string}
 */
function formatTimestampToRelativeTimeShorthand(timestamp) {
    return millisToRelativeTimeShorthand(dayjs.utc().valueOf() - timestamp);
}

/**
 * @param elapsed {number}
 * @param suffix {string}
 * @returns {string}
 */
function millisToRelativeTime(elapsed, suffix = 'ago') {
    function format(value, unit, suffix) {
        if (value <= 1) {
            return value + ' ' + unit + ' ' + suffix;
        } else {
            return value + ' ' + unit + 's ' + suffix;
        }
    }

    if (elapsed <= 0) {
        return 'just now';
    } else if (elapsed < MS_PER_MINUTE) {
        return format(Math.floor(elapsed / 1_000), 'second', suffix);
    } else if (elapsed < MS_PER_HOUR) {
        return format(Math.floor(elapsed / MS_PER_MINUTE), 'minute', suffix);
    } else if (elapsed < MS_PER_DAY) {
        return format(Math.floor(elapsed / MS_PER_HOUR), 'hour', suffix);
    } else if (elapsed < MS_PER_MONTH) {
        return format(Math.floor(elapsed / MS_PER_DAY), 'day', suffix);
    } else if (elapsed < MS_PER_YEAR) {
        return format(Math.floor(elapsed / MS_PER_MONTH), 'month', suffix);
    } else {
        return format(Math.floor(elapsed / MS_PER_YEAR), 'year', suffix);
    }
}

/**
 * Formats a duration in milliseconds as a compact shorthand string showing the
 * largest non-zero unit plus the next smaller unit (e.g. '45s', '5m 12s',
 * '1h 30m', '2d 4h', '3w 2d', '1y 5w'). Negative values are treated as zero.
 *
 * @param elapsed {number}
 * @returns {string}
 */
function formatDurationShorthand(elapsed) {
    if (elapsed == null || elapsed <= 0) {
        return '0s';
    }
    const totalSeconds = Math.floor(elapsed / 1_000);
    if (totalSeconds < 60) {
        return totalSeconds + 's';
    }
    const totalMinutes = Math.floor(totalSeconds / 60);
    if (totalMinutes < 60) {
        const s = totalSeconds % 60;
        return s === 0 ? totalMinutes + 'm' : totalMinutes + 'm ' + s + 's';
    }
    const totalHours = Math.floor(totalMinutes / 60);
    if (totalHours < 24) {
        const m = totalMinutes % 60;
        return m === 0 ? totalHours + 'h' : totalHours + 'h ' + m + 'm';
    }
    const totalDays = Math.floor(totalHours / 24);
    if (totalDays < 7) {
        const h = totalHours % 24;
        return h === 0 ? totalDays + 'd' : totalDays + 'd ' + h + 'h';
    }
    const totalWeeks = Math.floor(totalDays / 7);
    if (totalWeeks < 52) {
        const d = totalDays % 7;
        return d === 0 ? totalWeeks + 'w' : totalWeeks + 'w ' + d + 'd';
    }
    const totalYears = Math.floor(totalWeeks / 52);
    const w = totalWeeks % 52;
    return w === 0 ? totalYears + 'y' : totalYears + 'y ' + w + 'w';
}

/**
 * @param elapsed {number}
 * @returns {string}
 */
function millisToRelativeTimeShorthand(elapsed) {
    if (elapsed <= 0) {
        return 'now';
    } else if (elapsed < MS_PER_MINUTE) {
        return Math.floor(elapsed / 1_000) + 's';
    } else if (elapsed < MS_PER_HOUR) {
        return Math.floor(elapsed / MS_PER_MINUTE) + 'm';
    } else if (elapsed < MS_PER_DAY) {
        return Math.floor(elapsed / MS_PER_HOUR) + 'h';
    } else if (elapsed < MS_PER_MONTH) {
        return Math.floor(elapsed / MS_PER_DAY) + 'd';
    } else if (elapsed < MS_PER_YEAR) {
        return Math.floor(elapsed / MS_PER_MONTH) + 'mo';
    } else {
        return Math.floor(elapsed / MS_PER_YEAR) + 'y';
    }
}

// TODO: move to enum
// TODO: rename to PGN
function formatOutcome(outcome) {
    switch (outcome) {
        case Outcome.RED_WINS:
            return '1 - 0';
        case Outcome.BLACK_WINS:
            return '0 - 1'
        case Outcome.DRAW:
            return '½ - ½';
    }
}

function isNumber(str) {
    return str.length > 0 && !isNaN(str);
}

/**
 * @param value {number}
 * @returns {string}
 */
function formatNumber(value) {
    return Number(value).toLocaleString('en-US')
}

/**
 * Format a number with suffixes (k, M, B, etc.) and a specified number of digits.
 *
 * @param value {number} - The number to format.
 * @param digits {number} - The number of decimal places (default is 1).
 * @param spaces {number} - The number of spaces to add between the number and the suffix (default is 1).
 * @return {string} - The formatted number with a suffix.
 */
function formatNumberWithSuffix(value, digits = 1, spaces = 0) {
    if (value === 0) {
        return '0';
    }

    const tier = Math.floor(Math.log10(Math.abs(value)) / 3);

    if (tier === 0) {
        return value.toString();
    } else {
        const suffix = NUMBER_SUFFIXES[tier];
        const scale = Math.pow(10, tier * 3);
        return (value / scale).toFixed(digits) + ' '.repeat(spaces) + suffix;
    }
}

/**
 * @param value {number}
 * @returns {string}
 */
function formatCp(value) {
    let digits = 0;
    if (Math.abs(value) <= 10) {
        digits = 1;
    }

    return value.toFixed(digits);
}

/**
 * @param value {string}
 * @return {string}
 */
function capitalize(value) {
    const lower = value.toLowerCase();
    return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/**
 * @param value {string}
 * @return {string}
 */
function formatEnumValue(value) {
    return capitalize(value.replaceAll('_', ' '));
}

/**
 * @param engine {string}
 * @return {string}
 */
// TODO: move to enum
// TODO: add param for depth
function formatEngineName(engine) {
    switch (engine) {
        case 'PIKAFISH':
            return 'Pikafish';
        case 'FAIRYSTOCKFISH':
            return 'Fairy Stockfish';
        default:
            return '??';
    }
}

function sanitizeString(str) {
    str = str.replace(/[^a-z0-9áéíóúñü \.,_-]/gim, "");
    return str.trim();
}

/**
 * When possible, we should use css ellipsis instead of this function.
 *
 * @param label {string}
 * @param max {number}
 * @param ellipsis {boolean}
 * @return {string}
 */
function cropText(label, max, ellipsis = true) {
    if (label == null) {
        return '';
    } else if (label.length > max) {
        let result = label.substring(0, max).trim();
        if (ellipsis) {
            result += '...';
        }
        return result;
    } else {
        return label;
    }
}

function cropUrl(url, max = 50) {
    if (url == null) {
        return '';
    } else if (url.length > max) {
        let result = '';
        const parts = url.split('/');

        for (const part of parts) {
            const nextLength = result.length + (result.length > 0 ? 1 : 0) + part.length;

            if (nextLength <= max) {
                if (result.length > 0) {
                    result += '/';
                }
                result += part;
            } else {
                // Add ellipsis and break out of loop
                result += '/...';
                break;
            }
        }

        return result;
    } else {
        return url;
    }
}

/**
 * Zero-based half move index to full move.
 *
 * @param i {number}
 * @return {number}
 */
function halfMoveIndexToFullMove(i) {
    return Math.ceil((i + 1) / 2);
}

/**
 * Zero-based half move index to full move.
 *
 * "current move index" is the next index expected to be played
 *
 * @param i {number}
 * @return {number}
 */
function currentMoveIndexToFullMove(i) {
    if (i === 0) {
        return 1;
    } else {
        return halfMoveIndexToFullMove(i - 1);
    }
}

function copyTextToClipboardAndNotify(textToCopy, textToNotify = 'Copied to clipboard!') {
    navigator
        .clipboard
        .writeText(textToCopy)
        .then(() => UI.pushInfoNotification(textToNotify));
}

/**
 * Use as a predicate for filter(), it removes duplicates from an array.
 */
function onlyUnique(value, index, array) {
    return array.indexOf(value) === index;
}

/**
 * @param htmlCollection {HTMLCollection}
 * @return {HTMLElement[]}
 */
function htmlCollectionToArray(htmlCollection) {
    const array = [];
    for (let i = 0; i < htmlCollection.length; i++) {
        array.push(htmlCollection[i]);
    }
    return array;
}

/**
 * Get the {@link HTMLElement} in an array instead of a {@link HTMLCollection} which is not iterable.
 *
 * @param className
 * @return {HTMLElement[]}
 */
function getElementsByClassNameArray(className) {
    return htmlCollectionToArray(document.getElementsByClassName(className));
}

/**
 * @param className {string}
 * @returns {HTMLElement|null}
 */
function getLastElementOfClassName(className) {
    const elements = document.getElementsByClassName(className);
    if (elements.length > 0) {
        return elements[elements.length - 1];
    } else {
        return null;
    }
}

function gtagReportSignUpConversion(url) {
    try {
        const callback = function () {
            if (typeof (url) != 'undefined') {
                window.location = url;
            }
        };
        gtag('event', 'conversion', {
            'send_to': 'AW-1049989140/ZXazCKq28Z8YEJSg1vQD',
            'event_callback': callback
        });
    } catch (error) {
        console.log('gtag conversion error: ' + error);
    }
    return false;
}

function gtagReportPvpMove3Conversion(url) {
    try {
        const callback = function () {
            if (typeof (url) != 'undefined') {
                window.location = url;
            }
        };
        gtag('event', 'conversion', {
            'send_to': 'AW-1049989140/bzf8CKPI9uoaEJSg1vQD',
            'event_callback': callback
        });
    } catch (error) {
        console.log('gtag conversion error: ' + error);
    }
    return false;
}
