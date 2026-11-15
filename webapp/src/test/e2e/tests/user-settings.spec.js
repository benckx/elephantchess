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
const {newGuestContext} = require('./helpers');

test.describe('user settings public pages', () => {
    test('guest can open /user/settings and gets dynamic sessions/views without 403', async ({browser}) => {
        const {context, page} = await newGuestContext(browser);
        try {
            const sessionsResponsePromise = page.waitForResponse(response =>
                response.request().method() === 'GET' &&
                response.url().includes('/api/user/settings/sessions?')
            );
            const viewsResponsePromise = page.waitForResponse(response =>
                response.request().method() === 'GET' &&
                response.url().includes('/api/user/settings/views?')
            );

            const htmlResponse = await page.goto('/user/settings', {waitUntil: 'networkidle'});
            expect(htmlResponse.status()).toBe(200);

            expect((await sessionsResponsePromise).status()).toBe(200);
            expect((await viewsResponsePromise).status()).toBe(200);

            await expect(page.locator('#description')).toHaveCount(0);
            await expect(page.locator('#countries')).toHaveCount(0);
            await expect(page.locator('#save-notifications-button')).toHaveCount(0);
            await expect(page.locator('#email-address')).toHaveCount(0);
            await expect(page.locator('#user-sessions-table')).toBeVisible();
            await expect(page.locator('#user-page-views-table')).toBeVisible();
        } finally {
            await context.close();
        }
    });

    test('guest can open /user/settings/delete-account and sees auth warning', async ({browser}) => {
        const {context, page} = await newGuestContext(browser);
        try {
            const response = await page.goto('/user/settings/delete-account', {waitUntil: 'networkidle'});
            expect(response.status()).toBe(200);

            await expect(page.locator('#delete-account-auth-warning')).toBeVisible();
            await expect(page.locator('#delete-account-login-button')).toBeVisible();
            await expect(page.locator('#delete-account-signup-button')).toBeVisible();
            await expect(page.locator('#delete-account-confirm-checkbox')).toBeDisabled();
            await expect(page.locator('#delete-account-button')).toBeDisabled();
        } finally {
            await context.close();
        }
    });

    test('authenticated user sees full profile settings and can arm delete checkbox', async ({browser}) => {
        const {context, page} = await newGuestContext(browser);
        try {
            const suffix = `${Date.now()}${Math.floor(Math.random() * 1_000_000)}`;
            await page.evaluate(async (idSuffix) => {
                const response = await fetch('/api/signup', {
                    method: 'POST',
                    headers: headers(),
                    body: JSON.stringify({
                        username: `e2e${idSuffix}`,
                        email: `e2e${idSuffix}@gmail.com`,
                        password: `pass${idSuffix}A1`,
                    }),
                });
                if (!response.ok) {
                    throw new Error(`signup failed: ${response.status} ${await response.text()}`);
                }
                const json = await response.json();
                eraseAllIdentificationCookies();
                setAuthenticationCookies(json);
            }, suffix);
            await page.reload({waitUntil: 'networkidle'});

            const sessionsResponsePromise = page.waitForResponse(response =>
                response.request().method() === 'GET' &&
                response.url().includes('/api/user/settings/sessions?')
            );
            const viewsResponsePromise = page.waitForResponse(response =>
                response.request().method() === 'GET' &&
                response.url().includes('/api/user/settings/views?')
            );
            const settingsResponse = await page.goto('/user/settings', {waitUntil: 'networkidle'});
            expect(settingsResponse.status()).toBe(200);
            expect((await sessionsResponsePromise).status()).toBe(200);
            expect((await viewsResponsePromise).status()).toBe(200);

            await expect(page.locator('#description')).toBeVisible();
            await expect(page.locator('#countries')).toBeVisible();
            await expect(page.locator('#save-notifications-button')).toBeVisible();
            await expect(page.locator('#email-address')).toBeVisible();

            const deleteResponse = await page.goto('/user/settings/delete-account', {waitUntil: 'networkidle'});
            expect(deleteResponse.status()).toBe(200);
            await expect(page.locator('#delete-account-auth-warning')).toBeHidden();
            await expect(page.locator('#delete-account-confirm-checkbox')).toBeEnabled();
        } finally {
            await context.close();
        }
    });
});
