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
const {
  newGuestContext,
  createGame,
  gameIdFromUrl,
  joinGame,
  playScriptedGame,
  captureStep,
} = require('./helpers');

// A real game that ends in checkmate, taken from the shared test data in
// `xiangqi-core-test-utils/src/main/resources/uci.txt` (game id `mTkvv0GI`).
// Red delivers mate on the final (15th) half-move, so the game creator — who
// picks Red and therefore moves first — wins.
const CHECKMATE_GAME_MOVES = [
  'h2e2', 'h9g7', 'h0g2', 'b7d7', 'i0h0', 'g6g5', 'c3c4', 'b9a7',
  'b2b7', 'h7i7', 'b7g7', 'a9b9', 'e2e6', 'b9b5', 'g7e7',
];

// End-to-end coverage for the full online PvP workflow with two remote players.
//
// Two independent guests play against each other, so each player runs in its
// own browser context (its own guest identity). The scenario drives a complete
// game from creation through checkmate and asserts the live UI updates on both
// sides along the way.
test.describe('PvP workflow (two remote guests)', () => {
  test('full game: join updates inviter buttons live, checkmate ends game on both sides with Elo update', async ({
    browser,
  }, testInfo) => {
    const inviter = await newGuestContext(browser);
    const opponent = await newGuestContext(browser);

    // Screenshots of both players at each milestone are attached to the report
    // (visible in the Playwright HTML report / workflow artifact).
    const bothPages = () => [
      ['inviter', inviter.page],
      ['opponent', opponent.page],
    ];

    // --- Inviter creates a rated game, playing Red (so Red moves first) ---
    const gameUrl = await createGame(inviter.page, { color: 'red', rated: true });
    const gameId = gameIdFromUrl(gameUrl);
    expect(gameId).toBeTruthy();

    const resign = inviter.page.locator('#resign-button');
    const draw = inviter.page.locator('#propose-draw-button');
    const cancel = inviter.page.locator('#cancel-button');

    // Before anyone joins: resign / propose-draw are disabled (grey) because the
    // game is not in progress; only cancel is available while waiting.
    await expect(inviter.page.locator('#game-status')).toHaveText(/Waiting for opponent/);
    await expect(resign).toHaveClass(/disabled-button/);
    await expect(draw).toHaveClass(/disabled-button/);
    await expect(cancel).toHaveClass(/enabled-button/);
    await captureStep(testInfo, '1-waiting-for-opponent', bothPages());

    // --- Opponent opens the shared link and joins ---
    await opponent.page.goto(gameUrl, { waitUntil: 'networkidle' });
    await joinGame(opponent.page);

    // --- Regression assertion: buttons update live on join (no reload) ---
    // The inviter did NOT reload: the buttons must become enabled live once the
    // opponent joins and the game is in progress.
    await expect(resign).toHaveClass(/enabled-button/);
    await expect(draw).toHaveClass(/enabled-button/);
    await expect(inviter.page.locator('#game-status')).toHaveText(/Your turn/);
    await expect(opponent.page.locator('#game-status')).toHaveText(/Opponent's turn/);
    await captureStep(testInfo, '2-opponent-joined', bothPages());

    // --- Play the scripted game to checkmate ---
    // Inviter is Red and plays the odd half-moves; opponent is Black.
    await playScriptedGame([inviter.page, opponent.page], CHECKMATE_GAME_MOVES);

    // --- Checkmate: the game-over modal appears on both sides ---
    // The inviter (Red) delivered mate and wins; the opponent (Black) loses.
    const inviterModal = inviter.page.locator('#modal-background');
    const opponentModal = opponent.page.locator('#modal-background');

    await expect(inviterModal).toContainText('You win the game!');
    await expect(opponentModal).toContainText('You lose the game!');

    // The rating box is shown on both modals (rated game -> Elo update).
    await expect(inviter.page.locator('#rating-update-box')).toBeVisible();
    await expect(opponent.page.locator('#rating-update-box')).toBeVisible();
    await expect(inviter.page.locator('#user-rating-delta-value-box')).toBeVisible();
    await expect(opponent.page.locator('#user-rating-delta-value-box')).toBeVisible();
    await captureStep(testInfo, '3-checkmate-modal', bothPages());

    // --- Dismiss the modal; the underlying UI reflects the finished game ---
    await inviter.page.click('#ok-button');
    await opponent.page.click('#ok-button');

    await expect(inviterModal).toBeHidden();
    await expect(opponentModal).toBeHidden();

    // Once dismissed, the game is over on both sides: the action buttons are
    // gone, analysis is now allowed, and the rating delta is shown in the
    // player info panel.
    for (const page of [inviter.page, opponent.page]) {
      await expect(page.locator('#analyze-button-left-side')).not.toHaveClass(
        /app-buttons-disabled/
      );
    }

    // Both players' rating deltas are shown in each player's info panel; check
    // the inviter delta on the inviter's page and the invitee delta on the
    // opponent's page so both finished-game UIs are exercised.
    await expect(inviter.page.locator('#inviter-rating-delta')).toHaveText(/[+-]\d+/);
    await expect(opponent.page.locator('#invitee-rating-delta')).toHaveText(/[+-]\d+/);
    await captureStep(testInfo, '4-finished-game', bothPages());

    await inviter.context.close();
    await opponent.context.close();
  });
});
