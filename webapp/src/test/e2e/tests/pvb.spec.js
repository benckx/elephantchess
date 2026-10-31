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
const {newGuestContext, gameIdFromUrl, playMove, captureStep} = require('./helpers');

// Two pawn pushes: legal whatever the bot replies after the first one (no
// black piece can reach c4/g4 or give check in a single move).
const USER_MOVES = ['c3c4', 'g3g4'];

const ENGINES = [
    {name: 'Pikafish', optionId: '#engine-pika'},
    {name: 'Fairy Stockfish', optionId: '#engine-fairy'},
];

// End-to-end coverage for creating a PvB game from the "play bot" modal with
// each engine. Regression test for the Jackson 3 upgrade, which broke the
// deserialization of the `engine` field for Fairy Stockfish.
test.describe('PvB workflow (guest vs bot)', () => {
    for (const engine of ENGINES) {
        test(`create game with ${engine.name}, play 2 moves, game is listed in my bot games`, async ({
                                                                                                         browser,
                                                                                                     }, testInfo) => {
            const player = await newGuestContext(browser);
            const page = player.page;

            // --- Create the game from the lobby modal: Red, depth 1, chosen engine ---
            await page.goto('/?pvb', {waitUntil: 'networkidle'});
            await page.waitForSelector('#play-bot-modal', {state: 'visible'});
            await page.click('#variant-xiangqi-bot');
            await page.click('#play-as-red');
            await page.click(engine.optionId);
            await page.locator('#play-bot-depth').fill('1');
            await page.click('#play-bot-button');
            await page.waitForURL(/\/playbot\?id=/);
            const gameId = gameIdFromUrl(page.url());
            expect(gameId).toBeTruthy();

            await expect(page.locator('#info-engine')).toHaveText(engine.name);
            await expect(page.locator('#info-depth')).toHaveText('1');

            // --- Play 2 moves; the server answers each one with the bot's reply ---
            for (const uci of USER_MOVES) {
                const response = page.waitForResponse(/\/api\/botgame\/play-move/);
                await playMove(page, uci);
                const json = await (await response).json();
                expect(json.botMove).toMatch(/^[a-i]\d[a-i]\d$/);
            }
            await captureStep(testInfo, `1-${engine.name}-game`, [['player', page]]);

            // --- The game shows up in "my bot games" with the right engine and state ---
            await page.goto('/userdata/botgames', {waitUntil: 'networkidle'});
            const item = page.locator(`a.my-game-item[href="/playbot?id=${gameId}"]`);
            await expect(item).toHaveCount(1);
            await expect(item.locator('.opponent')).toHaveText(`${engine.name} (1)`);
            await expect(item.locator('.game-status')).toHaveText('ongoing');
            await expect(item.locator('.move-index')).toHaveText(`move ${USER_MOVES.length}`);
            await captureStep(testInfo, `2-${engine.name}-my-bot-games`, [['player', page]]);

            await player.context.close();
        });
    }
});
