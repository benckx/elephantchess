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

const {test, expect} = require('@playwright/test');
const {newGuestContext, playMove, captureStep} = require('./helpers');

// Synthetic Janggi self-play game (id `iaxeugSr`) from
// `xiangqi-core-test-utils/src/main/resources/uci.txt`.
const SYNTHETIC_JANGGI_MOVES = [
    'h2e2', 'h9g7', 'h0g2', 'i9h9', 'i0h0', 'b9c7',
    'c3c4', 'g6g5', 'h0h6', 'h7i7', 'h6g6', 'i7i8',
];

test.describe('Janggi test board', () => {
    test('replays a synthetic game line and captures screenshots', async ({browser}, testInfo) => {
        const player = await newGuestContext(browser);
        const page = player.page;

        await page.goto('/janggi-test-board', {waitUntil: 'networkidle'});
        await expect(page.locator('#board-container')).toBeVisible();
        await captureStep(testInfo, '1-janggi-test-board-initial', [['board', page]]);

        for (let i = 0; i < SYNTHETIC_JANGGI_MOVES.length; i++) {
            await playMove(page, SYNTHETIC_JANGGI_MOVES[i]);
            if (i === 5) {
                await captureStep(testInfo, '2-janggi-test-board-midgame', [['board', page]]);
            }
        }

        await expect(page.locator('#board-container-image-8-8')).toHaveClass(/piece-image/);
        await captureStep(testInfo, '3-janggi-test-board-finished-line', [['board', page]]);

        await player.context.close();
    });
});
