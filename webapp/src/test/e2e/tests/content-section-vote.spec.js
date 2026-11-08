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
const {newGuestContext, captureStep} = require('./helpers');

for (const {pageId, sectionId} of [
    {pageId: 'roadmap', sectionId: 'chat-emojis'},
    {pageId: 'faq', sectionId: 'why-sign-up'},
]) {
    test(`${pageId} sections support voting and persisted feedback`, async ({browser}, testInfo) => {
        const {context, page} = await newGuestContext(browser);
        try {
            await page.goto(`/about/${pageId}`, {waitUntil: 'networkidle'});
            const sections = page.locator('.main-container-text > .votable-content-section');
            const headingCount = await page.locator('.main-container-text h1[id], .main-container-text h2[id]').count();
            expect(headingCount).toBeGreaterThan(0);
            await expect(sections).toHaveCount(headingCount);
            await expect(page.locator('.content-section-vote-panel')).toHaveCount(headingCount);
            await expect(page.locator('.votable-content-section > .content-section-vote-panel')).toHaveCount(headingCount);
            await expect(sections.getByAltText('vote up', {exact: true})).toHaveCount(headingCount);
            await expect(sections.getByAltText('vote down', {exact: true})).toHaveCount(headingCount);
            const sectionLayouts = await sections.evaluateAll(elements => elements.map(element => ({
                marginBottom: parseFloat(getComputedStyle(element).marginBottom),
                lastChildIsVotePanel: element.lastElementChild.classList.contains('content-section-vote-panel'),
            })));
            for (const layout of sectionLayouts) {
                expect(layout.marginBottom).toBeGreaterThanOrEqual(24);
                expect(layout.lastChildIsVotePanel).toBe(true);
            }
            if (pageId === 'roadmap') {
                await expect(page.locator('.main-container-text > br')).toHaveCount(0);
                const headingMargins = await page.locator('.main-container-text > h1:not(:first-child)')
                    .evaluateAll(headings => headings.map(heading => parseFloat(getComputedStyle(heading).marginTop)));
                expect(headingMargins).toHaveLength(9);
                headingMargins.forEach(margin => expect(margin).toBeGreaterThanOrEqual(48));
            }

            const section = sections.filter({has: page.locator(`#${sectionId}`)});
            const upButton = section.getByAltText('vote up', {exact: true});
            const downButton = section.getByAltText('vote down', {exact: true});
            const feedbackLink = section.getByText('Tell us more', {exact: true});
            await expect(upButton).toBeVisible();
            await expect(downButton).toBeVisible();
            await expect(feedbackLink).toBeHidden();

            for (const upVoted of [true, false]) {
                const voteResponse = page.waitForResponse(response =>
                    response.url().endsWith('/api/content-section-vote/submit') &&
                    response.request().method() === 'POST'
                );
                await (upVoted ? upButton : downButton).click();
                expect((await voteResponse).ok()).toBe(true);
                await expect(upVoted ? upButton : downButton).toHaveClass(/voted-for/);
                await expect(upVoted ? downButton : upButton).toHaveClass(/voted-against/);
                await feedbackLink.click();

                const modal = page.locator('#content-section-feedback-modal');
                await expect(modal).toBeVisible();
                await expect(modal.locator('#content-section-feedback-section-label'))
                    .toHaveText(await section.locator('h1, h2').innerText());
                const feedback = `E2E ${pageId} ${upVoted ? 'up' : 'down'} vote feedback`;
                await modal.locator('textarea').fill(feedback);
                await captureStep(testInfo, `${pageId}-${upVoted ? 'up' : 'down'}-feedback`, [['page', page]], {fullPage: false});
                const feedbackResponse = page.waitForResponse(response =>
                    response.url().endsWith('/api/content-section-vote/submit') &&
                    response.request().method() === 'POST'
                );
                await modal.locator('#content-section-feedback-submit-button').click();
                expect((await feedbackResponse).ok()).toBe(true);
                await expect(modal).toBeHidden();

                const votesResponse = page.waitForResponse(response =>
                    response.url().includes(`/api/content-section-vote/list?pageId=${pageId}`)
                );
                await page.reload({waitUntil: 'networkidle'});
                const response = await votesResponse;
                expect(response.ok()).toBe(true);
                const {entries} = await response.json();
                expect(entries.find(entry => entry.sectionId === sectionId)).toEqual({sectionId, upVoted, feedback});
                await expect(upVoted ? upButton : downButton).toHaveClass(/voted-for/);
                await feedbackLink.click();
                await expect(modal.locator('textarea')).toHaveValue(feedback);
                await page.keyboard.press('Escape');
                await expect(modal).toBeHidden();
            }
        } finally {
            await context.close();
        }
    });
}
