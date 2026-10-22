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

// Helpers shared by the PvP end-to-end tests.
//
// A PvP game is played by two independent remote players, so each player gets
// its own Playwright browser context (an isolated cookie jar). A fresh context
// that visits any page receives its own guest identity (the `guest.user.token`
// cookie), which is exactly how two anonymous visitors play against each other.

const { expect } = require('@playwright/test');
const fs = require('node:fs');

// Third-party scripts (analytics, reCAPTCHA, tag manager, Ko-fi, ApexCharts)
// are not needed to exercise the game flow and only add noise / flakiness, so
// they are stubbed out with an empty body.
const THIRD_PARTY_NOISE =
  /recaptcha|googletagmanager|google-analytics|googleadservices|ko-fi|storage\.ko-fi|apexcharts|gtag\/js/;

// The front-end loads dayjs from a CDN (jsdelivr). Serve it from the local
// `dayjs` dev dependency instead so the tests are hermetic and do not depend
// on CDN availability.
const DAYJS_MAIN = fs.readFileSync(require.resolve('dayjs/dayjs.min.js'), 'utf8');
const DAYJS_UTC = fs.readFileSync(require.resolve('dayjs/plugin/utc.js'), 'utf8');

// The board uses the same UCI file/column letters as the engine
// (see webapp/src/main/resources/public/js/modules/xiangqi.js: UCI_LETTER).
const UCI_LETTER = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'];

async function stubThirdPartyNoise(context) {
  await context.route('**/*', (route) => {
    const url = route.request().url();
    if (/dayjs(@\d+)?\/dayjs(\.min)?\.js/.test(url)) {
      return route.fulfill({ contentType: 'application/javascript', body: DAYJS_MAIN });
    }
    if (/dayjs(@\d+)?\/plugin\/utc(\.js)?/.test(url)) {
      return route.fulfill({ contentType: 'application/javascript', body: DAYJS_UTC });
    }
    if (THIRD_PARTY_NOISE.test(url)) {
      return route.fulfill({ contentType: 'application/javascript', body: '' });
    }
    return route.continue();
  });
}

// Open a new isolated browser context and wait until it has obtained a guest
// identity, so subsequent game actions are authenticated as that guest.
async function newGuestContext(browser) {
  const context = await browser.newContext();
  await stubThirdPartyNoise(context);
  const page = await context.newPage();
  await page.goto('/', { waitUntil: 'networkidle' });
  await waitForGuestToken(context, page);
  return { context, page };
}

async function waitForGuestToken(context, page) {
  for (let i = 0; i < 20; i++) {
    const cookies = await context.cookies();
    if (cookies.some((c) => c.name === 'guest.user.token')) {
      return;
    }
    await page.waitForTimeout(500);
  }
  throw new Error('guest token cookie was never set');
}

// Create a PvP game from the lobby and return the shareable game URL.
//
// `color` picks the inviter's side ('red', 'black' or 'any', the default).
// Choosing 'red' makes the inviter move first, which keeps a scripted game
// deterministic. `rated` keeps the game rated so the Elo update is exercised.
async function createGame(
  page,
  { privateInvite = false, timeControlId = '#tc-1800', color = 'any', rated = true } = {}
) {
  await page.click('#create-new-game-button');
  await page.waitForSelector('#create-new-game-modal', { state: 'visible' });
  if (privateInvite) {
    await page.click('#private-invite');
  }
  const colorOption = { red: '#option-red', black: '#option-black', any: '#option-any-color' }[color];
  await page.click(colorOption);
  if (!rated) {
    await page.uncheck('#is-rated');
  }
  await page.click(timeControlId);
  await page.click('#create-new-game-button-modal');
  await page.waitForURL(/\/game\?id=/);
  return page.url();
}

function gameIdFromUrl(url) {
  return new URL(url).searchParams.get('id');
}

// Join a game the current context is a potential joiner of (via the join modal
// shown on the game page).
async function joinGame(page) {
  await page.waitForSelector('#join-game-join-button', { state: 'visible' });
  await page.click('#join-game-join-button');
}

// Translate a UCI position (e.g. 'h2') into logical board coordinates.
function coordsFromUci(uciPosition) {
  return { x: UCI_LETTER.indexOf(uciPosition[0]), y: Number(uciPosition.slice(1)) };
}

// The board square element id (e.g. 'board-container-square-7-2'). Square ids
// always encode the logical position, independent of whether the board is
// flipped, so the same id works for both players.
function squareIdFromUci(uciPosition) {
  const { x, y } = coordsFromUci(uciPosition);
  return `#board-container-square-${x}-${y}`;
}

// The legal-move placeholder element id for a square. When a piece is selected,
// each of its legal destinations gets a `legal-move-place-holder` (empty square)
// or `possible-capture` (enemy piece) class on this element.
function legalMoveIdFromUci(uciPosition) {
  const { x, y } = coordsFromUci(uciPosition);
  return `#board-container-legal_move-${x}-${y}`;
}

// The piece image element id for a square (e.g. 'board-container-image-7-9').
function imageIdFromUci(uciPosition) {
  const { x, y } = coordsFromUci(uciPosition);
  return `#board-container-image-${x}-${y}`;
}

// Play a single half-move (UCI, e.g. 'h2e2') for the player on `page`. The
// board plays a short animation when the previous move is applied, during which
// piece image ids are temporarily renamed; wait for the `from` piece to be
// marked movable (`piece-image-can-move`) so the animation has committed, then
// click it to select, wait until the `to` square is shown as a legal
// destination, and finally click the `to` square.
async function playMove(page, uci) {
  const fromUci = uci.substring(0, 2);
  const toUci = uci.substring(2, 4);
  await expect(page.locator(imageIdFromUci(fromUci))).toHaveClass(/piece-image-can-move/);
  await page.click(squareIdFromUci(fromUci));
  const target = page.locator(legalMoveIdFromUci(toUci));
  await expect(target).toHaveClass(/legal-move-place-holder|possible-capture/);
  await page.click(squareIdFromUci(toUci));
}

// Play a scripted game to its end. `movers` maps parity to the two player pages:
// index 0 plays the odd moves (1st, 3rd, ...), index 1 the even moves. Between
// moves the mover waits until it is its turn so each half-move is acknowledged
// (via websocket) before the next is attempted.
async function playScriptedGame(movers, uciMoves) {
  for (let i = 0; i < uciMoves.length; i++) {
    const mover = movers[i % 2];
    await expect(mover.locator('#game-status')).toHaveText(/Your turn/);
    await playMove(mover, uciMoves[i]);
    // Confirm the move was accepted before moving on: on every half-move except
    // the last (which ends the game) the mover hands the turn to the opponent.
    if (i < uciMoves.length - 1) {
      await expect(mover.locator('#game-status')).toHaveText(/Opponent's turn/);
    }
  }
}

// Capture a screenshot of each given page and attach it to the Playwright
// report under a readable name, so the milestones of the scenario are visible
// in the HTML report (and thus in the workflow artifact). `labelByPage` maps a
// page to the label used for its attachment, e.g. `{ [inviter.page]: 'inviter' }`.
async function captureStep(testInfo, step, labelByPage) {
  for (const [label, page] of labelByPage) {
    const body = await page.screenshot({ fullPage: true });
    await testInfo.attach(`${step} - ${label}`, { body, contentType: 'image/png' });
  }
}

module.exports = {
  stubThirdPartyNoise,
  newGuestContext,
  waitForGuestToken,
  createGame,
  gameIdFromUrl,
  joinGame,
  squareIdFromUci,
  playMove,
  playScriptedGame,
  captureStep,
};
