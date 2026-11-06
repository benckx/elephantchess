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

    test('lobby Janggi link opens the bot modal with Janggi selected', async ({browser}) => {
        const player = await newGuestContext(browser);
        const page = player.page;
        await page.locator('.action-link[onclick="showPlayBotModal(Variant.JANGGI)"]').click();
        await expect(page.locator('#play-bot-modal')).toBeVisible();
        await expect(page.locator('#variant-janggi-bot')).toHaveClass(/option-button-div-selected/);
        await expect(page.locator('#janggi-setup-selectors')).toBeVisible();
        await expect(page.locator('#play-as-red')).toHaveText('Blue');
        await expect(page.locator('#engine-fairy')).toHaveClass(/option-button-div-selected/);
        await expect(page.locator('#opening-engine-only')).toBeChecked();
        await player.context.close();
    });

    test('Janggi stalemate draws after the final human move', async ({browser}, testInfo) => {
        const player = await newGuestContext(browser);
        const page = player.page;
        const gameId = await page.evaluate(async () => {
            const response = await fetch('/api/botgame/create', {
                method: 'POST',
                headers: headers(),
                body: JSON.stringify({
                    color: Color.WHITE,
                    depth: 1,
                    engine: 'FAIRYSTOCKFISH',
                    openingMode: 'ENGINE_ONLY',
                    variant: Variant.JANGGI,
                    // Game 7f0d05cd in janggi.txt, before move 155 (d5d7).
                    startFen: '4k4/9/4P4/9/3R5/P5PP1/9/9/4A4/5K3 w - - 0 78'
                })
            });
            if (!response.ok) throw new Error(await response.text());
            return (await response.json()).gameId;
        });
        await page.goto(`/playbot?id=${gameId}`, {waitUntil: 'networkidle'});
        await expect(page.locator('.flex-triptych-left-container #janggi-beta-message')).toBeVisible();
        await expect(page.locator('#info-outcome')).toHaveText('--');
        await captureStep(testInfo, 'Janggi-before-stalemate', [['player', page]]);

        const replyPromise = page.waitForResponse(response => response.url().endsWith('/api/botgame/play-move'));
        await playMove(page, 'd5d7');
        const reply = await (await replyPromise).json();
        expect(reply.statusUpdate).toBe('STALEMATED');
        expect(reply.outcome).toBe('DRAW');
        expect(reply.botMove).toBeNull();
        await expect(page.locator('#info-outcome')).toHaveText('Draw');
        await expect(page.locator('.piece-image-can-move')).toHaveCount(0);
        await expect(page.locator('#game-win-modal, #game-loss-modal')).not.toBeVisible();
        await captureStep(testInfo, 'Janggi-stalemate-draw', [['player', page]]);

        await page.reload({waitUntil: 'networkidle'});
        await expect(page.locator('#info-outcome')).toHaveText('Draw');
        await expect(page.locator('.piece-image-can-move')).toHaveCount(0);
        await page.setViewportSize({width: 390, height: 844});
        await expect(page.locator('#janggi-beta-message')).toBeVisible();
        await captureStep(testInfo, 'Janggi-draw-mobile', [['player', page]]);
        await player.context.close();
    });

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
        await captureStep(testInfo, 'Janggi-new-game-modal', [['player', page]], {fullPage: false});
        await page.click('#play-bot-button');
        await page.waitForURL(/\/playbot\?id=/);
        const gameId = gameIdFromUrl(page.url());

        await expect(page.locator('#info-variant')).toHaveText('Janggi');
        await expect(page.locator('#info-user-color')).toHaveText('Blue');
        await expect(page.locator('#info-engine')).toHaveText('Fairy Stockfish');

        // Browse PvB lists games after at least six half-moves.
        for (const [index, uci] of ['a3a4', 'c3c4', 'e3e4', 'a4a5'].entries()) {
            await playMove(page, uci, {readyTimeout: 60_000});
            await waitForBotReply(page);
            await captureStep(testInfo, `Janggi-move-${index + 1}`, [['player', page]]);
        }

        await page.goto('/userdata/botgames', {waitUntil: 'networkidle'});
        const item = page.locator(`a.my-game-item[href="/playbot?id=${gameId}"]`);
        await expect(item).toHaveCount(1);
        await expect(item.locator('.variant-name')).toHaveText('Janggi');
        await expect(item.locator('.middle-pane')).toContainText('Blue');
        await expect(page.locator(`#custom-start-fen-${gameId}`)).toHaveCount(0);
        await captureStep(testInfo, 'Janggi-my-bot-games', [['player', page]]);

        await page.goto('/browse/player-vs-bot', {waitUntil: 'networkidle'});
        const thumb = page.locator('.pvb-game-thumb').filter({
            has: page.locator(`a[href^="/playbot?id=${gameId}"]`),
        });
        await expect(thumb).toHaveCount(1, {timeout: 30_000});
        await expect(thumb.locator('.game-thumb-variant')).toHaveText('장');

        await page.goto('/', {waitUntil: 'networkidle'});
        await expect(page.getByRole('heading', {name: 'Latest Bot Games'})).toBeVisible();
        const latestBotGame = page.locator('.latest-pvb-game-thumb').filter({
            has: page.locator(`a[href^="/playbot?id=${gameId}"]`),
        });
        await expect(latestBotGame).toHaveCount(1);
        await captureStep(testInfo, 'Janggi-latest-bot-game-in-lobby', [['player', page]]);

        await player.context.close();
    });
});
