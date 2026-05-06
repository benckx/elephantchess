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

const ADMIN_URL_PREFIX = '/api/admin/analytics';

// little hack
function userTypeFromName(name) {
    return name.startsWith('guest #') ? UserType.GUEST : UserType.AUTHENTICATED;
}

/**
 * Renders exception entries into a table row
 * @param entry - The exception entry object with properties: exceptionTime, httpCode, exceptionClass, exceptionMessage
 * @param row - The table row to populate
 * @param options - Optional configuration: { showFullyQualifiedClassName: boolean }
 */
function renderExceptionRow(entry, row, options = {}) {
    const showFullyQualifiedClassName = options.showFullyQualifiedClassName ?? true;

    // exception time
    const timeCell = row.insertCell();
    timeCell.className = 'label-cell';
    timeCell.innerText = formatTimestampDefaultDateFormat(entry.exceptionTime);

    // http code
    const httpCodeCell = row.insertCell();
    httpCodeCell.className = 'label-cell';
    httpCodeCell.innerText = entry.httpCode.toString();
    // Color code based on HTTP status
    if (entry.httpCode >= 500) {
        httpCodeCell.style.color = '#ff4444';
        httpCodeCell.style.fontWeight = 'bold';
    } else if (entry.httpCode >= 400) {
        httpCodeCell.style.color = '#ff8800';
    }

    // exception class
    const classCell = row.insertCell();
    classCell.className = 'label-cell';
    classCell.innerText = showFullyQualifiedClassName
        ? entry.exceptionClass
        : entry.exceptionClass.split('.').pop();
    classCell.style.fontFamily = 'monospace';
    classCell.style.fontSize = '0.9em';
    if (!showFullyQualifiedClassName) {
        classCell.title = entry.exceptionClass; // Show full class name on hover
    }

    // exception message
    const messageCell = row.insertCell();
    messageCell.className = 'label-cell';
    messageCell.innerText = entry.exceptionMessage;
    messageCell.style.maxWidth = '500px';
    messageCell.style.overflow = 'hidden';
    messageCell.style.textOverflow = 'ellipsis';
    messageCell.style.whiteSpace = 'nowrap';
    messageCell.title = entry.exceptionMessage; // Show full message on hover
}
