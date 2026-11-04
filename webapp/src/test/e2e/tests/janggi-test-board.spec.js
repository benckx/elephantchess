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

// Synthetic Janggi self-play game (id `ug2vcPrh`) from
// `xiangqi-core-test-utils/src/main/resources/uci.txt`.
const SYNTHETIC_JANGGI_MOVES = [
    'b0c2', 'c6c5', 'g3g4', 'b9c7', 'h0g2', 'a9a8', 'i0i1',
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

        const moveHistoryLabels = page.locator('.move-label-placeholder');
        await expect(moveHistoryLabels).toHaveCount(SYNTHETIC_JANGGI_MOVES.length);

        const renderedLabels = await moveHistoryLabels.allTextContents();
        renderedLabels.forEach((label) => expect(label.trim().length).toBeGreaterThan(0));

        const movablePieces = page.locator('[id^="board-container-image-"][class*="piece-image-can-move"]');
        expect(await movablePieces.count()).toBeGreaterThan(0);
        await captureStep(testInfo, '3-janggi-test-board-finished-line', [['board', page]]);

        await player.context.close();
    });
});
