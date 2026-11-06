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

async function waitForBotReply(page) {
    await page.waitForFunction(() => {
        const pieces = [...document.querySelectorAll('[id^="board-container-image-"]')];
        const white = pieces.filter((el) => el.className.includes('piece-image-white')
            && el.className.includes('piece-image-can-move')).length;
        const black = pieces.filter((el) => el.className.includes('piece-image-black')
            && el.className.includes('piece-image-can-move')).length;
        return black > 0 && white === 0;
    }, null, {timeout: 15_000});

    await page.waitForFunction(() => {
        const pieces = [...document.querySelectorAll('[id^="board-container-image-"]')];
        const white = pieces.filter((el) => el.className.includes('piece-image-white')
            && el.className.includes('piece-image-can-move')).length;
        const black = pieces.filter((el) => el.className.includes('piece-image-black')
            && el.className.includes('piece-image-can-move')).length;
        return white > 0 && black === 0;
    }, null, {timeout: 60_000});
}

// End-to-end coverage for creating a PvB game from the "play bot" modal with
// each engine. Regression test for the Jackson 3 upgrade, which broke the
// deserialization of the `engine` field for Fairy Stockfish.
test.describe('PvB workflow (guest vs bot)', () => {
    test.setTimeout(180_000);

    for (const engine of ENGINES) {
        test(`create game with ${engine.name}, play 2 moves, game is listed in my bot games`, async ({
                                                                                                         browser,
                                                                                                     }, testInfo) => {
            const player = await newGuestContext(browser);
            const page = player.page;

            // --- Create the game from the lobby modal: Red, depth 1, chosen engine ---
            await page.goto('/?pvb', {waitUntil: 'networkidle'});
            await page.waitForSelector('#play-bot-modal', {state: 'visible'});
            await captureStep(testInfo, `0-${engine.name}-new-game-modal`, [['player', page]], {fullPage: false});
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
            for (let i = 0; i < USER_MOVES.length; i++) {
                const uci = USER_MOVES[i];
                await playMove(page, uci, {readyTimeout: 60_000});
                await waitForBotReply(page);
                if (i === 0) {
                    await captureStep(testInfo, `1-${engine.name}-move-1`, [['player', page]]);
                }
            }
            await captureStep(testInfo, `2-${engine.name}-game`, [['player', page]]);

            // --- The game shows up in "my bot games" with the right engine and state ---
            await page.goto('/userdata/botgames', {waitUntil: 'networkidle'});
            const item = page.locator(`a.my-game-item[href="/playbot?id=${gameId}"]`);
            await expect(item).toHaveCount(1);
            await expect(item.locator('.opponent')).toHaveText(`${engine.name} (1)`);
            await expect(item.locator('.game-status')).toHaveText('ongoing');
            await expect(item.locator('.move-index')).toHaveText(`move ${USER_MOVES.length}`);
            await captureStep(testInfo, `3-${engine.name}-my-bot-games`, [['player', page]]);

            await player.context.close();
        });
    }

    test('create and browse a Janggi game with independent setups', async ({browser}, testInfo) => {
        test.setTimeout(180_000);
        const player = await newGuestContext(browser);
        const page = player.page;

        await page.goto('/?pvb', {waitUntil: 'networkidle'});
        await page.waitForSelector('#play-bot-modal', {state: 'visible'});
        await page.click('#variant-janggi-bot');
        expect(await page.locator('#variant-janggi-bot').evaluate((button) => button.closest('tr').rowIndex)).toBe(1);
        await expect(page.locator('#play-as-red')).toHaveText('Blue');
        await expect(page.locator('#play-as-black')).toHaveText('Red');
        await expect(page.locator('#start-fen-options')).toBeHidden();
        await expect(page.locator('#janggi-setup-selectors')).toBeVisible();
        await expect(page.locator('#janggi-setup-selectors')).toHaveCSS('flex-direction', 'column');
        await expect(page.locator('#janggi-blue-setup')).toHaveClass(/janggi-setup-select-blue/);
        await expect(page.locator('.janggi-setup-label-blue')).toHaveCSS('color', 'rgb(31, 74, 168)');
        await expect(page.locator('#janggi-red-setup')).toHaveClass(/janggi-setup-select-red/);
        await expect(page.locator('.janggi-setup-label-red')).toHaveCSS('color', 'rgb(165, 38, 38)');
        await page.selectOption('#janggi-blue-setup', 'rbna1abnr');
        await page.selectOption('#janggi-red-setup', 'bnra1arnb');
        await expect(page.locator('#opening-engine-only')).toBeChecked();
        await page.click('#engine-fairy');
        await page.locator('#play-bot-depth').fill('1');
        await page.click('#play-bot-button');
        await page.waitForURL(/\/playbot\?id=/);
        const gameId = gameIdFromUrl(page.url());

        await expect(page.locator('#info-variant')).toHaveText('Janggi');
        await expect(page.locator('#info-user-color')).toHaveText('Blue');
        await expect(page.locator('#info-engine')).toHaveText('Fairy Stockfish');

        // Browse PvB lists games after at least six half-moves.
        for (const uci of ['a3a4', 'c3c4', 'e3e4']) {
            await playMove(page, uci, {readyTimeout: 60_000});
            await waitForBotReply(page);
        }

        await page.goto('/userdata/botgames', {waitUntil: 'networkidle'});
        const item = page.locator(`a.my-game-item[href="/playbot?id=${gameId}"]`);
        await expect(item).toHaveCount(1);
        await expect(item.locator('.variant-name')).toHaveText('Janggi');
        await expect(item.locator('.middle-pane')).toContainText('Blue');
        await captureStep(testInfo, 'Janggi-my-bot-games', [['player', page]]);

        await page.goto('/browse/player-vs-bot', {waitUntil: 'networkidle'});
        const thumb = page.locator('.pvb-game-thumb').filter({
            has: page.locator(`a[href^="/playbot?id=${gameId}"]`),
        });
        await expect(thumb).toHaveCount(1, {timeout: 30_000});
        await expect(thumb.locator('.game-thumb-variant')).toHaveText('장');

        await player.context.close();
    });
});
