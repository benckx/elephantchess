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

class NewsletterStatsEntryDto {

    #templateName;
    #subject;
    #createdTime;
    #firstSentTime;
    #lastSentTime;
    #daysToSend;
    #totalEmails;
    #sentCount;
    #pendingCount;
    #unsubscribedNewsletterCount;
    #unsubscribedAllCount;
    #linkClicks;
    #linkClickDetails;

    constructor(json) {
        this.#templateName = json.templateName;
        this.#subject = json.subject;
        this.#createdTime = json.createdTime;
        this.#firstSentTime = json.firstSentTime;
        this.#lastSentTime = json.lastSentTime;
        this.#daysToSend = json.daysToSend;
        this.#totalEmails = json.totalEmails;
        this.#sentCount = json.sentCount;
        this.#pendingCount = json.pendingCount;
        this.#unsubscribedNewsletterCount = json.unsubscribedNewsletterCount;
        this.#unsubscribedAllCount = json.unsubscribedAllCount;
        this.#linkClicks = json.linkClicks;
        this.#linkClickDetails = (json.linkClickDetails || []).map(d => new NewsletterLinkClickDto(d));
    }


    /**
     * @return {string}
     */
    get templateName() {
        return this.#templateName;
    }

    /**
     * @return {string}
     */
    get subject() {
        return this.#subject;
    }

    /**
     * @return {number|null}
     */
    get createdTime() {
        return this.#createdTime;
    }

    /**
     * @return {number|null}
     */
    get firstSentTime() {
        return this.#firstSentTime;
    }

    /**
     * @return {number|null}
     */
    get lastSentTime() {
        return this.#lastSentTime;
    }

    /**
     * @return {number|null}
     */
    get daysToSend() {
        return this.#daysToSend;
    }

    /**
     * @return {number}
     */
    get totalEmails() {
        return this.#totalEmails;
    }

    /**
     * @return {number}
     */
    get sentCount() {
        return this.#sentCount;
    }

    /**
     * @return {number}
     */
    get pendingCount() {
        return this.#pendingCount;
    }

    /**
     * @return {number}
     */
    get unsubscribedNewsletterCount() {
        return this.#unsubscribedNewsletterCount;
    }

    /**
     * @return {number}
     */
    get unsubscribedAllCount() {
        return this.#unsubscribedAllCount;
    }

    /**
     * @return {number}
     */
    get linkClicks() {
        return this.#linkClicks;
    }

    /**
     * @return {NewsletterLinkClickDto[]}
     */
    get linkClickDetails() {
        return this.#linkClickDetails;
    }

    /**
     * @return {boolean}
     */
    get isFullySent() {
        return this.#sentCount === this.#totalEmails && this.#totalEmails > 0;
    }

    /**
     * @return {boolean}
     */
    get hasPending() {
        return this.#pendingCount > 0;
    }

}

class NewsletterLinkClickDto {

    #link;
    #clicks;

    constructor(json) {
        this.#link = json.link;
        this.#clicks = json.clicks;
    }

    /**
     * @return {string}
     */
    get link() {
        return this.#link;
    }

    /**
     * @return {number}
     */
    get clicks() {
        return this.#clicks;
    }

}
