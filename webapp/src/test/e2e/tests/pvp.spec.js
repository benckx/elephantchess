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

const { test, expect } = require('@playwright/test');
const { newGuestContext, createGame, gameIdFromUrl, joinGame } = require('./helpers');

// End-to-end coverage for the online PvP workflow with two remote players.
//
// Two independent guests play against each other, so each player runs in its
// own browser context (its own guest identity). The scenario reproduces the
// bug from issue #901: the inviter's action buttons (resign / propose draw)
// used to stay disabled until a manual page reload after the opponent joined.
test.describe('PvP workflow (two remote guests)', () => {
  test('private game: buttons update live on join, chat works, game hidden from lobby', async ({ browser }) => {
    const inviter = await newGuestContext(browser);
    const opponent = await newGuestContext(browser);

    // --- Inviter creates a private game and waits for an opponent ---
    const gameUrl = await createGame(inviter.page, { privateInvite: true });
    const gameId = gameIdFromUrl(gameUrl);

    const resign = inviter.page.locator('#resign-button');
    const draw = inviter.page.locator('#propose-draw-button');
    const cancel = inviter.page.locator('#cancel-button');

    // Before anyone joins: resign / propose-draw are disabled (grey) because
    // the game is not in progress; only cancel is available.
    await expect(resign).toHaveClass(/disabled-button/);
    await expect(draw).toHaveClass(/disabled-button/);
    await expect(cancel).toHaveClass(/enabled-button/);

    // A private invite shows the "not visible in the lobby" board mask overlay.
    await expect(inviter.page.locator('#inviter-private-invite-board-mask')).toBeVisible();

    // --- A private game must NOT appear in the public lobby ---
    const lobby = await newGuestContext(browser);
    await lobby.page.goto('/', { waitUntil: 'networkidle' });
    await expect(lobby.page.locator('#games-to-join-list')).not.toContainText(gameId);
    await lobby.context.close();

    // --- Opponent opens the shared link and joins ---
    await opponent.page.goto(gameUrl, { waitUntil: 'networkidle' });
    await joinGame(opponent.page);

    // --- Regression assertion (issue #901) ---
    // The inviter did NOT reload: the buttons must become enabled live once the
    // opponent joins and the game is in progress.
    await expect(resign).toHaveClass(/enabled-button/);
    await expect(draw).toHaveClass(/enabled-button/);

    // The waiting board mask overlay is gone once the game starts.
    await expect(inviter.page.locator('#inviter-private-invite-board-mask')).toBeHidden();

    // --- Chat: messages are delivered and the author name is colored ---
    await inviter.page.fill('#chat-box-input', 'hello from the inviter');
    await inviter.page.click('#chat-box-send-message-button');

    await expect(opponent.page.locator('#chat-box-messages-container')).toContainText(
      'hello from the inviter'
    );
    // Once colors are assigned, the author name carries a red/black color class.
    await expect(inviter.page.locator('.chat-box-message .message-author').first()).toHaveClass(
      /(red|black)-author/
    );

    await inviter.context.close();
    await opponent.context.close();
  });
});
