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
const {newGuestContext, captureStep, authenticateUser} = require('./helpers');

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
