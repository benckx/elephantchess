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
const fs = require('fs');
const path = require('path');
const {newGuestContext, playMove, captureStep} = require('./helpers');

const fixturePath = path.resolve(__dirname, '../../../../../xiangqi-core-test-utils/src/main/resources/janggi.txt');
const fixture = fs.readFileSync(fixturePath, 'utf8').split(/\r?\n/).find(line => line.startsWith('246a7a10;'));
if (!fixture) {
    throw new Error('Missing Janggi checkmate fixture 246a7a10');
}
const [, moves, outcome] = fixture.split(';');
const SYNTHETIC_JANGGI_MOVES = moves.split(',');

test.describe('Janggi test board', () => {
    test('replays a synthetic game line and captures screenshots', async ({browser}, testInfo) => {
        expect(outcome).toBe('CHECKMATE');
        SYNTHETIC_JANGGI_MOVES.forEach(move => expect(move).toMatch(/^[a-i][0-9][a-i][0-9]$/));
        const player = await newGuestContext(browser);
        const page = player.page;

        await page.goto('/janggi-test-board', {waitUntil: 'networkidle'});
        await expect(page.locator('#board-container')).toBeVisible();
        await captureStep(testInfo, '1-janggi-test-board-initial', [['board', page]]);

        for (let i = 0; i < SYNTHETIC_JANGGI_MOVES.length; i++) {
            await playMove(page, SYNTHETIC_JANGGI_MOVES[i]);
            if (i === Math.floor(SYNTHETIC_JANGGI_MOVES.length / 2)) {
                await captureStep(testInfo, '2-janggi-test-board-midgame', [['board', page]]);
            }
        }

        const moveHistoryLabels = page.locator('.move-label-placeholder');
        await expect(moveHistoryLabels).toHaveCount(SYNTHETIC_JANGGI_MOVES.length);

        const renderedLabels = await moveHistoryLabels.allTextContents();
        renderedLabels.forEach((label) => expect(label.trim().length).toBeGreaterThan(0));

        await expect(page.locator('.highlighted-checkmate')).toHaveCount(1);
        await captureStep(testInfo, '3-janggi-test-board-finished-line', [['board', page]]);

        await player.context.close();
    });
});
