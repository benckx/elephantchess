/*
 * Copyright (C) 2023-2026 Encelade SRL
 * Copyright (C) 2023-2026 elephantchess.io
 * Copyright (C) 2023-2026 Benoît Vleminckx (benckx)
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
const {newGuestContext, captureStep, authenticateUser, stubThirdPartyNoise} = require('./helpers');

test.describe('Guest piece style selection', () => {
    for (const [label, style, folder] of [
        ['Traditional (default)', 'TRADITIONAL', 'traditional'],
        ['Westernized', 'ROMANIZED_ROUNDED', 'romanized_rounded'],
    ]) {
        test(`new guest can choose ${label}`, async ({page, context}) => {
            await stubThirdPartyNoise(context);
            await page.goto('/', {waitUntil: 'networkidle'});
            const modal = page.getByRole('dialog', {name: 'Choose your pieces'});
            await expect(modal).toBeVisible();
            const id = (await context.cookies()).find(cookie => cookie.name === 'guest.user.userId').value;
            await expect(modal.locator('#guest-piece-style-user')).toHaveText(`guest #${id}`);
            const expiration = await page.request.get('/api/token-expiration-date', {
                headers: {Authorization: `Bearer ${(await context.cookies()).find(c => c.name === 'guest.user.token').value}`},
            });
            expect(expiration.ok()).toBe(true);
            const expectedExpiration = await page.evaluate(timestamp => formatTimestampDefaultDateFormat(timestamp),
                (await expiration.json()).expiration);
            await expect(modal.locator('#guest-piece-style-expiration')).toHaveText(expectedExpiration);
            await expect(modal.locator('button[data-piece-style] img')).toHaveCount(6);
            await modal.getByRole('button', {name: new RegExp(label.replace(/[()]/g, '\\$&'))}).click();
            await expect(modal).toBeHidden();
            expect((await context.cookies()).find(cookie => cookie.name === 'setting.piece.style').value).toBe(style);
            await expect(page.locator('#piece-style-select')).toHaveValue(style);
            await expect(page.locator('#puzzle-board-container img.piece-image').first()).toHaveAttribute('src',
                new RegExp(`/images/pieces/${folder}/`));
            await page.reload({waitUntil: 'networkidle'});
            await expect(modal).toHaveCount(0);
            await expect(page.locator('#piece-style-select')).toHaveValue(style);
        });
    }

    test('existing piece style skips the modal for a new guest', async ({page, context, baseURL}) => {
        await stubThirdPartyNoise(context);
        await context.addCookies([{name: 'setting.piece.style', value: 'MODERN', url: baseURL}]);
        await page.goto('/', {waitUntil: 'networkidle'});
        await expect(page.locator('#notification-box')).toContainText('Identified as guest #');
        await expect(page.locator('#guest-piece-style-modal')).toHaveCount(0);
        await expect(page.locator('#piece-style-select')).toHaveValue('MODERN');
    });

    test('a direct play-bot link opens after guest setup', async ({page, context}) => {
        await stubThirdPartyNoise(context);
        await page.goto('/?pvb', {waitUntil: 'networkidle'});
        const modal = page.getByRole('dialog', {name: 'Choose your pieces'});
        await expect(modal).toBeVisible();
        await page.waitForTimeout(1000);
        await expect(modal).toBeVisible();
        await modal.getByRole('button', {name: /Westernized/}).click();
        await expect(page.locator('#play-bot-modal')).toBeVisible();
    });

    test('a dismissed guest modal still opens a queued play-bot modal', async ({page, context}) => {
        await stubThirdPartyNoise(context);
        await page.goto('/?pvb', {waitUntil: 'networkidle'});
        await expect(page.getByRole('dialog', {name: 'Choose your pieces'})).toBeVisible();
        await page.waitForTimeout(1000);
        await page.keyboard.press('Escape');
        await expect(page.locator('#play-bot-modal')).toBeVisible();
    });

    test('a failed guest request does not block other modals', async ({page, context}) => {
        await stubThirdPartyNoise(context);
        await context.route('**/api/obtain-guest-user-token', route => route.fulfill({status: 503}));
        await page.goto('/?pvb', {waitUntil: 'networkidle'});
        await expect(page.locator('#play-bot-modal')).toBeVisible();
        await expect(page.locator('#guest-piece-style-modal')).toHaveCount(0);
    });
});

test.describe('User settings access by auth level', () => {
    test('guest only sees identified-user sections in settings and delete checkbox is disabled', async ({browser}, testInfo) => {
        const {context, page} = await newGuestContext(browser);
        try {
            await page.goto('/user/settings', {waitUntil: 'networkidle'});
            await expect(page.getByRole('heading', {name: 'Latest Sessions'})).toBeVisible();
            await expect(page.getByRole('heading', {name: 'Latest Page Views'})).toBeVisible();
            await expect(page.getByRole('heading', {name: 'Profile Settings'})).toHaveCount(0);
            await expect(page.getByRole('heading', {name: 'Email Notifications Settings'})).toHaveCount(0);
            await expect(page.getByRole('heading', {name: 'Email Address'})).toHaveCount(0);
            await expect(page.locator('#save-profile-button')).toHaveCount(0);
            await captureStep(testInfo, '1-guest-user-settings', [['guest', page]]);

            await page.goto('/user/settings/delete-account', {waitUntil: 'networkidle'});
            const deleteCheckbox = page.locator('#delete-account-confirm-checkbox');
            await expect(deleteCheckbox).toBeVisible();
            await expect(deleteCheckbox).toBeDisabled();
            await expect(page.locator('#delete-account-auth-warning')).toBeVisible();
            await captureStep(testInfo, '2-guest-delete-account', [['guest', page]]);
        } finally {
            await context.close();
        }
    });

    test('authenticated user sees all settings sections, can update profile, and delete checkbox is enabled', async ({browser}, testInfo) => {
        const {context, page} = await newGuestContext(browser);
        try {
            await authenticateUser(page, context, {login: 'test1', password: 'password1'});
            await page.goto('/user/settings', {waitUntil: 'networkidle'});

            await expect(page.getByRole('heading', {name: 'Profile Settings'})).toBeVisible();
            await expect(page.getByRole('heading', {name: 'Email Notifications Settings'})).toBeVisible();
            await expect(page.getByRole('heading', {name: 'Email Address'})).toBeVisible();
            await expect(page.getByRole('heading', {name: 'Latest Sessions'})).toBeVisible();
            await expect(page.getByRole('heading', {name: 'Latest Page Views'})).toBeVisible();
            await expect(page.locator('#save-profile-button')).toBeVisible();
            await captureStep(testInfo, '3-authenticated-user-settings', [['authenticated', page]]);

            const initialDescription = await page.locator('#description').inputValue();
            const updatedDescription = `${initialDescription} e2e profile update check`.trim();
            await page.locator('#description').fill(updatedDescription);
            await expect(page.locator('#save-profile-button')).toBeEnabled();
            const profileUpdateResponse = page.waitForResponse((response) =>
                response.url().endsWith('/api/user/settings/profile') && response.request().method() === 'POST'
            );
            await page.locator('#save-profile-button').click();
            expect((await profileUpdateResponse).ok()).toBe(true);
            await expect(page.locator('#save-profile-button')).toBeDisabled();
            await captureStep(testInfo, '4-authenticated-profile-updated', [['authenticated', page]]);

            await page.goto('/user/settings/delete-account', {waitUntil: 'networkidle'});
            const deleteCheckbox = page.locator('#delete-account-confirm-checkbox');
            await expect(deleteCheckbox).toBeVisible();
            await expect(deleteCheckbox).toBeEnabled();
            await expect(page.locator('#delete-account-auth-warning')).toBeHidden();
            await captureStep(testInfo, '5-authenticated-delete-account', [['authenticated', page]]);
        } finally {
            await context.close();
        }
    });
});
