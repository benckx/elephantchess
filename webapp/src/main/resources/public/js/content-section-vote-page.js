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

class ContentSectionVotePage extends BasePage {

    #votesBySectionId = new Map();
    #pageId;

    constructor() {
        super();

        this.#pageId = document.querySelector('.main-container-text')?.dataset.pageId;
        if (!this.#pageId) {
            return;
        }

        UI.preloadModal('content-section-feedback');
        const sections = document.querySelectorAll('.main-container-text h1[id]');
        sections.forEach((section) => this.#addVotePanel(section));
    }

    /**
     * @param section {HTMLElement}
     */
    #addVotePanel(section) {
        const votePanel = document.createElement('div');
        votePanel.className = 'content-section-vote-panel';
        votePanel.innerHTML = `
            <img class="content-section-vote-button" src="/images/icons/thumbs-up.png" alt="vote up" loading="lazy"/>
            <img class="content-section-vote-button" src="/images/icons/thumbs-down.png" alt="vote down" loading="lazy"/>
            <span class="content-section-feedback-link action-link">Tell me why</span>
        `;
        section.append(votePanel);

        const upButton = votePanel.children[0];
        const downButton = votePanel.children[1];
        const feedbackLink = votePanel.children[2];
        const sectionId = section.id;
        const sectionTitle = section.textContent.trim();

        upButton.addEventListener('click', () => this.#vote(sectionId, true, upButton, downButton, feedbackLink));
        downButton.addEventListener('click', () => this.#vote(sectionId, false, upButton, downButton, feedbackLink));
        feedbackLink.addEventListener('click', () => this.#openFeedbackModal(sectionId, sectionTitle));
    }

    /**
     * @param sectionId {string}
     * @param upVoted {boolean}
     * @param upButton {HTMLElement}
     * @param downButton {HTMLElement}
     * @param feedbackLink {HTMLElement}
     */
    #vote(sectionId, upVoted, upButton, downButton, feedbackLink) {
        postAndHandle('/api/content-section-vote/submit', {pageId: this.#pageId, sectionId, upVoted}, () => {
            this.#votesBySectionId.set(sectionId, upVoted);
            this.#renderVote(upVoted, upButton, downButton);
            feedbackLink.classList.add('content-section-feedback-link-visible');
            UI.pushInfoNotification('Thanks for your feedback!');
        });
    }

    /**
     * @param upVoted {boolean}
     * @param upButton {HTMLElement}
     * @param downButton {HTMLElement}
     */
    #renderVote(upVoted, upButton, downButton) {
        if (upVoted) {
            upButton.classList.add('voted-for');
            upButton.classList.remove('voted-against');
            downButton.classList.add('voted-against');
            downButton.classList.remove('voted-for');
        } else {
            upButton.classList.add('voted-against');
            upButton.classList.remove('voted-for');
            downButton.classList.add('voted-for');
            downButton.classList.remove('voted-against');
        }
    }

    /**
     * @param sectionId {string}
     * @param sectionTitle {string}
     */
    #openFeedbackModal(sectionId, sectionTitle) {
        if (!this.#votesBySectionId.has(sectionId)) {
            UI.pushErrorNotification('Please vote first.');
            return;
        }

        UI.showModalByName('content-section-feedback', () => {
            const sectionLabel = document.getElementById('content-section-feedback-section-label');
            sectionLabel.innerText = sectionTitle;

            const submitButton = document.getElementById('content-section-feedback-submit-button');
            submitButton.onclick = () => {
                const textarea = document.getElementById('content-section-feedback-textarea');
                const feedback = textarea.value.trim();
                postAndHandle('/api/content-section-vote/submit', {
                    pageId: this.#pageId,
                    sectionId,
                    upVoted: this.#votesBySectionId.get(sectionId),
                    feedback,
                }, () => {
                    UI.hideModal(null);
                    UI.pushInfoNotification('Thanks for telling us more!');
                });
            };
        });
    }

}

window.onload = () => new ContentSectionVotePage();
