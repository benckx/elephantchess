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

const TURN_TO_PLAY_GAMES_PREFIX = 'user-turn-to-play-games'

class UserTurnToPlayIndicatorWidget {

    #nbrOfElements = 0;

    #panel = document.getElementById(`${TURN_TO_PLAY_GAMES_PREFIX}-panel`);
    #listContainer = document.getElementById(`${TURN_TO_PLAY_GAMES_PREFIX}-list-container`);
    #counterCircle = document.getElementById(`${TURN_TO_PLAY_GAMES_PREFIX}-counter-circle`);
    #counterValue = document.getElementById(`${TURN_TO_PLAY_GAMES_PREFIX}-counter-value`);
    #emptyListMessage = document.getElementById(`${TURN_TO_PLAY_GAMES_PREFIX}-empty-list-message`);

    #isFirstRefresh = true;
    #newNotificationsAudio = new Audio('/audio/542013__rob_marion__gasp_ui_notification_2.mp3');

    constructor() {
        this.hide();

        document
            .getElementById('user-turn-to-play-games-header')
            .addEventListener('click', () => this.toggle());
    }

    /**
     * @param update {GamesToPlayUpdateDto}
     */
    refresh(update) {
        // filter entries that must be rendered
        const entriesToRender = [];
        const currentGameId = this.#currentGameId();
        update.turnToPlayGames.forEach(entry => {
            if (entry.gameId !== currentGameId) {
                entriesToRender.push(entry);
            }
        });

        // play audio notification if we didn't have any entries before and we have some now
        if (this.#nbrOfElements === 0 && entriesToRender.length > 0 && !this.#isFirstRefresh) {
            if (isPlaySoundsEnabled()) {
                this.#newNotificationsAudio
                    .play()
                    .catch(() => {
                        // ignored, spam error in console in dev
                    });
            }
        }

        this.#nbrOfElements = entriesToRender.length;
        this.#renderList(entriesToRender);
        this.#isFirstRefresh = false;
    }

    /**
     * @param entries {GameToPlayDto[]}
     */
    #renderList(entries) {
        formatNotificationCircleCounter(this.#counterCircle, this.#counterValue, this.#nbrOfElements);
        this.#renderAsEmpty(entries.length === 0);
        this.#listContainer.innerHTML = '';
        const prefix = TURN_TO_PLAY_GAMES_PREFIX;

        entries.forEach(entry => {
                const vsSpan = document.createElement('span');
                vsSpan.innerText = ' vs. ';

                const gameLinkSpan = document.createElement('span');
                const gameUrl = gameIdToPageLink(new GameId(GameType.PVP, entry.gameId));
                gameLinkSpan.append(buildLink(gameUrl, entry.opponentUsername));

                const isOpponentOnlineSpan = document.createElement('span');
                isOpponentOnlineSpan.classList.add('online-status-indicator', 'online-status-indicator-smaller');
                if (entry.isOpponentOnline) {
                    isOpponentOnlineSpan.classList.add(IS_ONLINE_CSS_CLASS);
                }

                const gameDiv = document.createElement('div');
                gameDiv.append(buildColorSpan(entry.opponentColor), vsSpan, gameLinkSpan, isOpponentOnlineSpan);

                let ratingMode;
                if (entry.isRated) {
                    ratingMode = 'rated';
                } else {
                    ratingMode = 'casual';
                }

                const dateAndModeDiv = document.createElement('div');
                dateAndModeDiv.className = prefix + '-entry-last-updated';
                dateAndModeDiv.innerHTML = `updated ${formatTimestampToRelativeTime(entry.lastUpdated)} - ${ratingMode}`;

                const entryDiv = document.createElement('div');
                entryDiv.className = prefix + '-entry';
                entryDiv.append(gameDiv, dateAndModeDiv);
                entryDiv.addEventListener('click', () => {
                    window.open(gameUrl, '_self');
                });

                this.#listContainer.append(entryDiv);
            }
        )
    }

    /**
     * @param isListEmpty {boolean}
     */
    #renderAsEmpty(isListEmpty) {
        this.#counterCircle.style.visibility = isListEmpty ? 'hidden' : 'visible';
        this.#emptyListMessage.style.display = isListEmpty ? 'block' : 'none';
        this.#listContainer.style.display = isListEmpty ? 'none' : 'block';
    }

    /**
     * If we are currently on a game page where we have to play, do not display this game in the list.
     */
    #currentGameId() {
        const bit = window.location.href.split('/').pop();
        if (bit != null && bit.startsWith('game?id=')) {
            return getQueryParam('id');
        }
        return null;
    }

    isVisible() {
        return this.#panel.style.display !== 'none';
    }

    show() {
        this.#panel.style.display = 'block';
    }

    hide() {
        this.#panel.style.display = 'none';
    }

    toggle() {
        if (this.isVisible()) {
            this.hide();
        } else {
            this.show();
        }
    }

}
