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

const NO_LABEL_DAYS = 50;
const USER_SETTINGS_API = '/api/user/settings';
const PROFILE_URL = USER_SETTINGS_API + '/profile';

class UserProfilePage extends BasePage {

    #isOwnProfile = document.querySelector('body').dataset.isOwnProfile === 'true';
    #country = document.querySelector('body').dataset.profileCountry ?? '';
    #userId = document.querySelector('body').dataset.userId;
    #username = document.querySelector('body').dataset.username;
    #client = new UserProfileClient(this.#userId);
    #statusIndicator = document.getElementById('status-indicator');
    #puzzleStatsSection = document.getElementById('puzzle-stats-section');
    #profileDescription = document.getElementById('profile-description');
    #profileDescriptionEditButton = document.getElementById('profile-description-edit-button');
    #profileDescriptionEditor = document.getElementById('profile-description-editor');
    #profileDescriptionSaveButton = document.getElementById('profile-description-save-button');
    #profileCountryEditButton = document.getElementById('profile-country-edit-button');
    #profileCountrySelect = document.getElementById('profile-country-select');

    constructor() {
        super();
        this.#fetchLatestPvpGames();
        this.#fetchPuzzlesStatsSummary();
        this.#fetchPuzzlesStatsRating();
        this.#fetchPuzzlesStatsNumbers();
        this.#updateIndicatorStatusAtInterval();

        const flagHeaderPanel = document.getElementById('flag-header-panel');
        if (flagHeaderPanel?.dataset.countryCode) {
            addCountryToolTip(flagHeaderPanel, flagHeaderPanel.dataset.countryCode);
        }

        this.#setupCountryEditor();
        this.#setupDescriptionEditor();
    }

    #fetchLatestPvpGames() {
        if (this.#username) {
            new UserProfileGames(this.#username);
        }
    }

    #fetchPuzzlesStatsSummary() {
        this.#client.fetchPuzzleStatsSummary(summaryDto => {
            document.getElementById('summary-data-rating').innerText = formatNumber(summaryDto.rating);
            document.getElementById('summary-data-max-rating').innerText = formatNumber(summaryDto.maxRating);
            document.getElementById('summary-data-total-played').innerText = formatNumber(summaryDto.totalPlayed);
        })
    }

    #fetchPuzzlesStatsRating() {
        this.#client.fetchPuzzleRatingHistory(historyDto => {
            const chart = new PuzzleRatingHistoryLineChart('rating-line-chart', historyDto);
            if (chart.render()) {
                this.#hideLoadingPlaceholderAndShowStats();
            }
        });
    }

    #fetchPuzzlesStatsNumbers() {
        this.#client.fetchPuzzleDailyNumbers(historyDto => {
            const chart = new PuzzleNumbersBarChart('number-bar-chart', historyDto);
            if (chart.render()) {
                this.#hideLoadingPlaceholderAndShowStats();
            }
        });
    }

    #hideLoadingPlaceholderAndShowStats() {
        const placeHolderElement = document.getElementById('profile-puzzle-stats-placeholder');
        placeHolderElement.classList.remove('empty-block-placeholder');
        const spans = placeHolderElement.getElementsByClassName('loading')
        for (let i = 0; i < spans.length; i++) {
            spans[i].remove();
        }

        document.getElementById('profile-puzzle-stats-data').style.visibility = 'visible';
        this.#puzzleStatsSection.style.display = 'block';
    }

    #updateIndicatorStatusAtInterval() {
        const className = 'online-status-indicator-is-online';

        setIntervalNoDelay(() => {
            this.#client.fetchIsOnline(isOnline => {
                if (isOnline) {
                    this.#statusIndicator.classList.add(className);
                } else {
                    this.#statusIndicator.classList.remove(className);
                }
            });
        }, 2_000);
    }

    #setupDescriptionEditor() {
        if (!this.#isOwnProfile || this.#profileDescriptionEditButton == null ||
            this.#profileDescriptionEditor == null || this.#profileDescriptionSaveButton == null ||
            this.#profileDescription == null) {
            return;
        }

        this.#profileDescriptionEditButton.addEventListener('click', () => {
            this.#profileDescriptionEditor.value = this.#profileDescription.classList.contains('empty-block-placeholder') ?
                '' : this.#profileDescription.innerText.trim();
            this.#profileDescription.classList.add('hidden-by-default');
            this.#profileDescriptionEditButton.classList.add('hidden-by-default');
            this.#profileDescriptionEditor.classList.remove('hidden-by-default');
            this.#profileDescriptionSaveButton.classList.remove('hidden-by-default');
            this.#profileDescriptionEditor.focus();
        });

        this.#profileDescriptionSaveButton.addEventListener('click', () => {
            const rawCountry = this.#profileCountrySelect?.value ?? this.#country;
            const country = rawCountry === 'none' ? '' : rawCountry;
            postAndHandle(PROFILE_URL, {
                description: this.#profileDescriptionEditor.value,
                country: country
            }, () => {
                UI.pushInfoNotification('Profile settings successfully updated!', UI_NOTIFICATION_TIMEOUT);
                window.location.reload();
            });
        });
    }

    #setupCountryEditor() {
        if (!this.#isOwnProfile || this.#profileCountryEditButton == null || this.#profileCountrySelect == null ||
            this.#profileDescriptionSaveButton == null) {
            return;
        }

        fillSelect('profile-country-select');
        const initialCountry = this.#country?.trim()?.toLowerCase();
        if (!initialCountry) {
            this.#profileCountrySelect.value = 'none';
        } else {
            const option = Array.from(this.#profileCountrySelect.options)
                .find(o => o.value.toLowerCase() === initialCountry);
            if (option != null) {
                option.selected = true;
            }
        }

        this.#profileCountryEditButton.addEventListener('click', () => {
            this.#profileCountryEditButton.classList.add('hidden-by-default');
            this.#profileCountrySelect.classList.remove('hidden-by-default');
            this.#profileDescriptionSaveButton.classList.remove('hidden-by-default');
            this.#profileCountrySelect.focus();
        });
    }

}

window.onload = () => new UserProfilePage();
