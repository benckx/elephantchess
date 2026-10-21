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
// `privateInvite` controls whether the game is listed in the lobby.
async function createGame(page, { privateInvite = false, timeControlId = '#tc-1800' } = {}) {
  await page.click('#create-new-game-button');
  await page.waitForSelector('#create-new-game-modal', { state: 'visible' });
  if (privateInvite) {
    await page.click('#private-invite');
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

module.exports = {
  stubThirdPartyNoise,
  newGuestContext,
  waitForGuestToken,
  createGame,
  gameIdFromUrl,
  joinGame,
};
