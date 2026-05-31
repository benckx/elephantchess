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

const CONTENT_SECTION_FEEDBACK_MAX_LENGTH = 1_000;

class ContentSectionVoteWidget {

    #votesBySectionId = new Map();
    #votePanelBySectionId = new Map();
    #pageId;
    #feedbackSubmitListener = null;

    constructor(pageId) {
        this.#pageId = pageId;
        if (!this.#pageId) {
            return;
        }

        UI.preloadModal('content-section-feedback');
        const sections = document.querySelectorAll('.main-container-text h1[id]');
        sections.forEach((section) => this.#addVotePanel(section));
        this.#fetchCurrentVotes();
    }

    /**
     * @param section {HTMLElement}
     */
    #addVotePanel(section) {
        const sectionId = section.id;
        const sectionTitle = section.textContent.trim();

        const votePanel = document.createElement('div');
        votePanel.className = 'content-section-vote-panel';
        votePanel.innerHTML = `
            <img class="content-section-vote-button content-section-vote-button-up" src="/images/icons/thumbs-up-black.png" alt="vote up" loading="lazy"/>
            <img class="content-section-vote-button content-section-vote-button-down" src="/images/icons/thumbs-up-black.png" alt="vote down" loading="lazy"/>
            <span class="content-section-feedback-link action-link">Tell us more</span>
        `;

        // Insert the vote panel at the end of the section: walk the siblings
        // following the heading until the next h1, then insert before it. If
        // there is no following h1, append after the last sibling.
        let lastSectionNode = section;
        let nextNode = section.nextElementSibling;
        while (nextNode != null && nextNode.tagName.toLowerCase() !== 'h1') {
            lastSectionNode = nextNode;
            nextNode = nextNode.nextElementSibling;
        }
        lastSectionNode.after(votePanel);

        const upButton = votePanel.children[0];
        const downButton = votePanel.children[1];
        const feedbackLink = votePanel.children[2];

        this.#votePanelBySectionId.set(sectionId, {upButton, downButton, feedbackLink, sectionTitle});

        upButton.addEventListener('click', () => this.#vote(sectionId, true, upButton, downButton, feedbackLink));
        downButton.addEventListener('click', () => this.#vote(sectionId, false, upButton, downButton, feedbackLink));
        feedbackLink.addEventListener('click', () => this.#openFeedbackModal(sectionId, sectionTitle));
    }

    #fetchCurrentVotes() {
        getAndHandle(`/api/content-section-vote/list?pageId=${encodeURIComponent(this.#pageId)}`, json => {
            json.entries.forEach(entry => {
                const votePanel = this.#votePanelBySectionId.get(entry.sectionId);
                if (votePanel == null) {
                    return;
                }

                this.#votesBySectionId.set(entry.sectionId, {
                    upVoted: entry.upVoted,
                    feedback: entry.feedback ?? '',
                });
                this.#renderVote(entry.upVoted, votePanel.upButton, votePanel.downButton);
                votePanel.feedbackLink.classList.add('content-section-feedback-link-visible');
            });
        });
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
            const currentVote = this.#votesBySectionId.get(sectionId);
            this.#votesBySectionId.set(sectionId, {
                upVoted,
                feedback: currentVote?.feedback ?? '',
            });
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
        const voteState = this.#votesBySectionId.get(sectionId);
        if (voteState == null) {
            UI.pushErrorNotification('Please vote first.');
            return;
        }

        UI.showModalByName('content-section-feedback', () => {
            const sectionLabel = document.getElementById('content-section-feedback-section-label');
            sectionLabel.innerText = sectionTitle;
            const textarea = document.getElementById('content-section-feedback-textarea');
            textarea.value = voteState.feedback;
            this.#updateFeedbackCounter(textarea);

            const submitButton = document.getElementById('content-section-feedback-submit-button');
            if (this.#feedbackSubmitListener != null) {
                submitButton.removeEventListener('click', this.#feedbackSubmitListener);
            }
            this.#feedbackSubmitListener = () => {
                const feedback = textarea.value.trim();
                const latestVote = this.#votesBySectionId.get(sectionId);
                if (latestVote == null) {
                    UI.pushErrorNotification('Please vote first.');
                    return;
                }
                postAndHandle('/api/content-section-vote/submit', {
                    pageId: this.#pageId,
                    sectionId,
                    upVoted: latestVote.upVoted,
                    feedback,
                }, () => {
                    this.#votesBySectionId.set(sectionId, {
                        upVoted: latestVote.upVoted,
                        feedback,
                    });
                    UI.hideModal(null);
                    UI.pushInfoNotification('Thanks for telling us more!');
                });
            };
            submitButton.addEventListener('click', this.#feedbackSubmitListener);
            textarea.oninput = () => this.#updateFeedbackCounter(textarea);
        });
    }

    /**
     * @param textarea {HTMLTextAreaElement}
     */
    #updateFeedbackCounter(textarea) {
        const characterCounter = document.getElementById('content-section-feedback-character-counter');
        if (characterCounter) {
            characterCounter.innerText = `${textarea.value.length} / ${CONTENT_SECTION_FEEDBACK_MAX_LENGTH}`;
        }
    }

}

document.addEventListener('DOMContentLoaded', () => {
    const pageId = document.querySelector('.main-container-text')?.dataset.pageId;
    if (pageId) new ContentSectionVoteWidget(pageId);
});
