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

class AdminNewsletterStatsPage extends BasePage {

    /**
     * @type {HTMLTableElement}
     */
    #newsletterStatsTable = document.getElementById('newsletter-stats');

    /**
     * @type {HTMLTableElement}
     */
    #newsletterLinkClicksTable = document.getElementById('newsletter-link-clicks');

    /**
     * @type {HTMLSpanElement}
     */
    #potentialRecipientsSpan = document.getElementById('potential-recipients');

    constructor() {
        super();
        getAndHandle(`${ADMIN_URL_PREFIX}/newsletter-stats`, json => {
            this.#renderNewsletterStats(json);
        });
    }

    /**
     * @param json {object}
     */
    #renderNewsletterStats(json) {
        /** @type {NewsletterStatsEntryDto[]} */
        const entries = (json.entries || []).map(e => new NewsletterStatsEntryDto(e));
        const tbody = this.#newsletterStatsTable.getElementsByTagName('tbody')[0];

        // clear existing rows
        emptyTable(this.#newsletterStatsTable);

        // update potential recipients count
        this.#potentialRecipientsSpan.innerText = formatNumber(json.potentialRecipients || 0);

        // add rows for each newsletter
        entries.forEach(entry => {
            const row = tbody.insertRow();

            // template name
            const templateCell = row.insertCell();
            templateCell.innerText = entry.templateName;
            templateCell.style.fontWeight = '600';

            // Subject
            const subjectCell = row.insertCell();
            subjectCell.innerText = entry.subject;
            subjectCell.style.maxWidth = '200px';
            subjectCell.style.overflow = 'hidden';
            subjectCell.style.textOverflow = 'ellipsis';
            subjectCell.style.whiteSpace = 'nowrap';
            subjectCell.title = entry.subject;

            // first sent time
            const firstSentCell = row.insertCell();
            firstSentCell.className = 'label-cell';
            firstSentCell.innerText = entry.firstSentTime ? formatTimestampDefaultDateFormat(entry.firstSentTime) : '-';

            // last sent time
            const lastSentCell = row.insertCell();
            lastSentCell.className = 'label-cell';
            lastSentCell.innerText = entry.lastSentTime ? formatTimestampDefaultDateFormat(entry.lastSentTime) : '-';

            // days to send
            const daysCell = row.insertCell();
            daysCell.className = 'numeric-cell';
            daysCell.innerText = entry.daysToSend !== null ? entry.daysToSend.toString() : '-';

            // total emails
            const totalCell = row.insertCell();
            totalCell.className = 'numeric-cell';
            totalCell.innerText = formatNumber(entry.totalEmails);

            // sent count
            const sentCell = row.insertCell();
            sentCell.className = 'numeric-cell';
            sentCell.innerText = formatNumber(entry.sentCount);
            if (entry.isFullySent) {
                sentCell.style.color = '#44bb44';
                sentCell.style.fontWeight = 'bold';
            }

            // pending count
            const pendingCell = row.insertCell();
            pendingCell.className = 'numeric-cell';
            pendingCell.innerText = formatNumber(entry.pendingCount);
            if (entry.hasPending) {
                pendingCell.style.color = '#ff8800';
            }

            // link clicks
            const clicksCell = row.insertCell();
            clicksCell.className = 'numeric-cell';
            clicksCell.innerText = formatNumber(entry.linkClicks);

            // unsubscribed from newsletter
            const unsubCell = row.insertCell();
            unsubCell.className = 'numeric-cell';
            unsubCell.innerText = formatNumber(entry.unsubscribedNewsletterCount);
            if (entry.unsubscribedNewsletterCount > 0) {
                unsubCell.style.color = '#ff4444';
            }

            // unsubscribed from all
            const unsubAllCell = row.insertCell();
            unsubAllCell.className = 'numeric-cell';
            unsubAllCell.innerText = formatNumber(entry.unsubscribedAllCount);
            if (entry.unsubscribedAllCount > 0) {
                unsubAllCell.style.color = '#ff4444';
                unsubAllCell.style.fontWeight = 'bold';
            }
        });

        this.#renderLinkClicks(entries);
    }

    /**
     * @param entries {NewsletterStatsEntryDto[]}
     */
    #renderLinkClicks(entries) {
        const tbody = this.#newsletterLinkClicksTable.getElementsByTagName('tbody')[0];

        // clear existing rows
        emptyTable(this.#newsletterLinkClicksTable);

        // one row per clicked link, grouped by newsletter
        entries.forEach(entry => {
            entry.linkClickDetails.forEach(detail => {
                const row = tbody.insertRow();

                // newsletter
                const templateCell = row.insertCell();
                templateCell.innerText = entry.templateName;
                templateCell.style.fontWeight = '600';

                // link
                const linkCell = row.insertCell();
                // only render internal (relative) paths as clickable links
                if (detail.link.startsWith('/')) {
                    const link = document.createElement('a');
                    link.href = detail.link;
                    link.innerText = detail.link;
                    link.target = '_blank';
                    link.rel = 'noopener noreferrer';
                    linkCell.appendChild(link);
                } else {
                    linkCell.innerText = detail.link;
                }

                // clicks
                const clicksCell = row.insertCell();
                clicksCell.className = 'numeric-cell';
                clicksCell.innerText = formatNumber(detail.clicks);
            });
        });
    }
}

window.onload = () => new AdminNewsletterStatsPage();
