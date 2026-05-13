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

class UserProfilePage extends BasePage {

    #userId = document.querySelector('body').dataset.userId;
    #client = new UserProfileClient(this.#userId);
    #statusIndicator = document.getElementById('status-indicator');
    #puzzleStatsSection = document.getElementById('puzzle-stats-section');

    constructor() {
        super();
        this.#fetchGamesRatings();
        this.#fetchPuzzlesStatsSummary();
        this.#fetchPuzzlesStatsRating();
        this.#fetchPuzzlesStatsNumbers();
        this.#updateIndicatorStatusAtInterval();

        const flagHeaderPanel = document.getElementById('flag-header-panel');
        if (flagHeaderPanel != null) {
            addCountryToolTip(flagHeaderPanel, flagHeaderPanel.dataset.countryCode);
        }
    }

    #fetchGamesRatings() {
        this.#client.fetchGameRatings(stats => {
            document.getElementById('rating-bullet').innerText = stats.bullet.toString();
            document.getElementById('rating-blitz').innerText = stats.blitz.toString();
            document.getElementById('rating-rapid').innerText = stats.rapid.toString();
            document.getElementById('rating-classical').innerText = stats.classical.toString()
            document.getElementById('rating-correspondence').innerText = stats.correspondence.toString();
        });
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

}

window.onload = () => new UserProfilePage();
