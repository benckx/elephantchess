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

'use strict';

const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const boardGuiSource = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/public/js/widgets/board-gui.js'),
    'utf8'
);
const settingsSource = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/web_fragments/settings_info_box.html'),
    'utf8'
);
const testBoardSource = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/public/js/test-board.js'),
    'utf8'
);

test('board-gui exposes Janggi styles and image folders', () => {
    assert.match(boardGuiSource, /JANGGI_CURSIVE/);
    assert.match(boardGuiSource, /JANGGI_MODERN/);
    assert.match(boardGuiSource, /JANGGI_WESTERNIZED/);
    assert.match(boardGuiSource, /case PieceStyleSetting\.JANGGI_CURSIVE:\s*return 'janggi_cursive';/);
    assert.match(boardGuiSource, /case PieceStyleSetting\.JANGGI_MODERN:\s*return 'janggi_modern';/);
    assert.match(boardGuiSource, /case PieceStyleSetting\.JANGGI_WESTERNIZED:\s*return 'janggi_westernized';/);
    assert.match(boardGuiSource, /janggiPieceImageNames\.set\('k', 'blue_general\.png'\);/);
    assert.match(boardGuiSource, /janggiPieceImageNames\.set\('p', 'blue_soldier\.png'\);/);
});

test('settings and test-board surface the Janggi option', () => {
    assert.match(settingsSource, /value="JANGGI_CURSIVE">Janggi Cursive<\/option>/);
    assert.match(settingsSource, /value="JANGGI_MODERN">Janggi Modern<\/option>/);
    assert.match(settingsSource, /value="JANGGI_WESTERNIZED">Janggi Westernized<\/option>/);
    assert.match(testBoardSource, /document\.getElementById\('janggi-explanation'\)\.style\.display = 'block';/);
    assert.match(testBoardSource, /const isJanggi = variant === 'janggi';/);
});
