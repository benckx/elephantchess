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

/**
 * @param gameId {string}
 * @returns {HTMLDivElement}
 */
function buildPreAnalyzedIcon(gameId) {
    const iconImg = buildImg('/images/icons/database.png', 'icon');
    iconImg.alt = 'This game is pre-analyzed';
    iconImg.style.opacity = '75%';

    const div = wrapInDiv(iconImg);
    div.id = `pre-analyzed-${gameId}`;
    addToolTip(div, 'This game is pre-analyzed');
    return div;
}
