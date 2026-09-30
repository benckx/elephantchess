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

const GAME_API = '/api/game';
const CANCELLED_ITEM_CLASS = 'canceled-item';
const BELOW_MOVE_3_ITEM_CLASS = 'below-move-3-item';

class MyGamesPage extends InfiniteScrollPage {

    #includeCanceledCheckbox = document.getElementById('include-canceled-checkbox');
    #includeBelowMove3Checkbox = document.getElementById('include-below-move-3-checkbox');

    #myGameItems = document.getElementById('my-game-items');
    #noGameMessage = document.getElementById('no-game-played-message');

    constructor() {
        super();
        this.fetchItems();
        makeCheckboxesClickable();

        // listeners
        function setUpCheckboxListeners(element, className) {
            element.addEventListener('change', (e) => {
                if (e.target.checked) {
                    getElementsByClassNameArray(className)
                        .forEach((el) => {
                            el.style.display = 'flex';
                        });
                } else {
                    getElementsByClassNameArray(className)
                        .forEach((el) => {
                            el.style.display = 'none';
                        });
                }
            });
        }

        setUpCheckboxListeners(this.#includeCanceledCheckbox, CANCELLED_ITEM_CLASS);
        setUpCheckboxListeners(this.#includeBelowMove3Checkbox, BELOW_MOVE_3_ITEM_CLASS);
    }

    baseUrl() {
        return `${GAME_API}/list-user-games`;
    }

    deserializeJsonEntry(jsonEntry) {
        return new GameEntryDto(jsonEntry);
    }

    /**
     * @param entry {GameEntryDto}
     */
    extractToken(entry) {
        return entry.lastUpdated.toString();
    }

    /**
     * @param value {boolean}
     */
    showNoItem(value) {
        this.#noGameMessage.style.display = value ? 'block' : 'none';
    }

    /**
     * @param entries {GameEntryDto[]}
     */
    addEntries(entries) {

        /**
         * @param entry {GameEntryDto}
         * @return {HTMLDivElement}
         */
        function buildTimeControlCategoryIcon(entry) {
            // image
            const iconImg = document.createElement('img');
            iconImg.className = 'time-control-icons';
            iconImg.src = `/images/icons/${timeControlCategoryIconMap.get(entry.timeControlCategory)}`;

            // div with tooltip
            const div = wrapInDiv(iconImg);
            div.id = 'tc-' + entry.gameId;
            // div.style.position = 'relative';
            addToolTip(div, entry.timeControlCategory.toString().toLowerCase());
            return div;
        }

        /**
         * @param entry {GameEntryDto}
         * @returns {HTMLDivElement|null}
         */
        function buildTimeControlDurationDiv(entry) {
            if (entry.timeControl == null) return null;
            const div = buildDivWithClass('time-control-duration-cell');
            div.innerText = entry.timeControl.printShort(' +');
            return div;
        }

        /**
         * @param {GameEntryDto} entry
         * @return {string}
         */
        function formattedStatus(entry) {
            switch (entry.status) {
                case GameEventType.CREATED:
                    return 'waiting';
                case GameEventType.CANCELED:
                    return 'canceled';
                case GameEventType.AUTO_CANCELED:
                    return 'canceled (auto)';
                case GameEventType.JOINED:
                case GameEventType.DRAW_PROPOSED:
                case GameEventType.DRAW_DECLINED:
                    if (entry.isUserTurnToPlay) {
                        return 'your turn ‼️';
                    } else {
                        return 'opponent\'s turn';
                    }
                case GameEventType.DRAW_ACCEPTED:
                    return 'draw';
                case GameEventType.RESIGNED:
                    return 'resigned';
                case GameEventType.CHECKMATED:
                    return 'checkmated';
                case GameEventType.STALEMATED:
                    return 'stalemated';
                case GameEventType.FLAGGED:
                    return 'flagged';
                case GameEventType.PERPETUAL_CHECKING:
                    return 'perpetual checking';
                default:
                    return '??';
            }
        }

        /**
         * @param entry {GameEntryDto}
         * @returns {HTMLDivElement}
         */
        function buildOpponentDiv(entry) {
            const opponentDiv = document.createElement('div');
            if (entry.hasOpponent) {
                opponentDiv.append(
                    buildUsernameSpan(entry.opponentUserId, entry.opponentUsername, entry.opponentUserType)
                );
            } else {
                opponentDiv.classList.add('not-joined');
                if (entry.isCanceled()) {
                    opponentDiv.innerText = 'canceled';
                } else if (entry.status === GameEventType.CREATED) {
                    opponentDiv.innerText = 'waiting for opponent';
                } else {
                    // should not happen
                    opponentDiv.innerText = 'no opponent';
                }
            }

            return opponentDiv;
        }

        /**
         *
         * @param entry {GameEntryDto}
         * @returns {HTMLDivElement|null}
         */
        function buildChatMessagesDiv(entry) {
            if (entry.numberOfMessages > 0) {
                // icon
                const iconImg = document.createElement('img');
                iconImg.className = 'icon';
                iconImg.src = `/images/icons/chat.png`;

                // number of messages in circle
                // TODO: use formatNotificationCircleCounter
                const circleDiv = document.createElement('div');
                circleDiv.classList.add('notification-counter-circle', 'number-of-games-circle');
                if (entry.numberOfMessages > 9) {
                    circleDiv.innerText = '9+';
                } else {
                    circleDiv.innerText = entry.numberOfMessages.toString();
                }

                const chatDiv = document.createElement('div');
                chatDiv.append(iconImg, circleDiv);
                circleDiv.style.visibility = 'visible';
                return chatDiv;
            } else {
                return null;
            }
        }

        /**
         * @param entry {GameEntryDto}
         * @returns {HTMLDivElement}
         */
        function buildRatingModeDiv(entry) {
            const ratingModeDiv = document.createElement('div');
            ratingModeDiv.className = 'rating-mode';
            if (entry.isRated) {
                ratingModeDiv.innerText = 'rated';
            } else {
                ratingModeDiv.innerText = 'casual';
            }
            return ratingModeDiv;
        }

        /**
         * @param entry {GameEntryDto}
         * @returns {HTMLDivElement|null}
         */
        function buildRatingDiv(entry) {
            if (entry.hasRatingDelta) {
                // base value
                const ratingFromSpan = document.createElement('span');
                ratingFromSpan.className = 'rating-from';
                ratingFromSpan.innerText = entry.ratingFrom.toString();

                // delta
                const ratingDeltaSpan = document.createElement('span');
                ratingDeltaSpan.classList.add('user-rating-delta-value-box', 'user-rating-delta-value-smaller');
                ratingDeltaSpan.style.visibility = 'visible';
                ratingDeltaSpan.innerText = ' ' + entry.formattedRatingDelta;
                if (entry.isRatingUpdatePositive) {
                    ratingDeltaSpan.classList.add('user-rating-delta-value-box-positive');
                } else if (entry.isRatingUpdateNegative) {
                    ratingDeltaSpan.classList.add('user-rating-delta-value-box-negative');
                }

                // structure
                const ratingDiv = document.createElement('div');
                ratingDiv.append(ratingFromSpan, ratingDeltaSpan);
                return ratingDiv;
            } else {
                return null;
            }
        }

        /**
         * @param entry {GameEntryDto}
         * @returns {HTMLDivElement|null}
         */
        function buildIsOngoingDiv(entry) {
            const isOngoing = isStatusInProgress(entry.status);
            if (isOngoing) {
                const container = buildDivWithClass('is-ongoing-indicator-container');
                container.append(
                    buildImg('/images/icons/record-button.png', 'live-icon'),
                    buildDivWithTextAndClass('ongoing', 'ongoing-label')
                );
                return container;
            } else {
                return null;
            }
        }

        entries.forEach((entry) => {
            // structure
            const timeControlPane = buildDivWithClass('time-control-icon-pane');
            const variantPane = buildDivWithClass('variant-pane');
            const middlePane = buildDivWithClass('middle-pane');
            const chatIndicatorPane = buildDivWithClass('indicator-pane');
            const outcomeIndicatorPane = buildDivWithClass('indicator-pane');
            const ratingDeltaIndicatorPane = buildDivWithClass('indicator-pane');
            const rightPane = buildDivWithClass('right-pane');
            const item = buildAnchorWithClass(entry.gameUrl,null, 'my-game-item');

            item.append(
                variantPane,
                timeControlPane,
                middlePane,
                chatIndicatorPane,
                ratingDeltaIndicatorPane,
                outcomeIndicatorPane,
                rightPane
            );

            this.#myGameItems.append(item);
            addMiniboardDiv(item, entry.gameId, entry.currentFen, entry.color);

            // filtering
            if (entry.isCanceled()) {
                item.classList.add(CANCELLED_ITEM_CLASS);
            } else if (entry.fullMoveIndex < 3 && !isStatusInProgress(entry.status) && entry.status !== GameEventType.CREATED) {
                item.classList.add(BELOW_MOVE_3_ITEM_CLASS);
            }

            // left pane
            timeControlPane.append(buildTimeControlCategoryIcon(entry));

            // variant pane
            variantPane.append(buildVariantCell(entry.variant));

            // middle pane
            const middlePaneItems = [
                buildOpponentDiv(entry),
                wrapInDiv(buildColorSpan(entry.color)),
            ];
            const durationDiv = buildTimeControlDurationDiv(entry);
            if (durationDiv != null) {
                middlePaneItems.push(durationDiv);
            }
            middlePane.append(...middlePaneItems);

            // number of messages indicator pane
            const chatDiv = buildChatMessagesDiv(entry);
            if (chatDiv != null) {
                chatIndicatorPane.append(chatDiv);
            }

            // rating delta indicator pane
            ratingDeltaIndicatorPane.classList.add('rating-pane');
            const ratingDiv = buildRatingDiv(entry);
            if (ratingDiv != null) {
                ratingDeltaIndicatorPane.append(ratingDiv);
            }
            ratingDeltaIndicatorPane.append(buildRatingModeDiv(entry));

            // outcome indicator pane
            const outcomeDiv = buildUserOutcomeDiv(entry);
            if (outcomeDiv != null) {
                outcomeIndicatorPane.append(outcomeDiv);
            } else {
                const ongoingDiv = buildIsOngoingDiv(entry);
                if (ongoingDiv != null) {
                    outcomeIndicatorPane.append(ongoingDiv);
                }
            }

            // right pane
            const lastModifiedDiv = document.createElement('div');
            lastModifiedDiv.className = 'last-modified';
            lastModifiedDiv.id = `last-modified-${entry.gameId}`;
            setRelativeTimeAndToolTip(lastModifiedDiv, entry.lastUpdated);

            rightPane.append(
                buildDivWithTextAndClass(formattedStatus(entry), 'game-status'),
                lastModifiedDiv,
                buildDivWithTextAndClass(`move ${entry.fullMoveIndex}`, 'move-index')
            );

            // duration (only for games that actually ended, i.e. not canceled and not ongoing)
            if (isStatusFinished(entry.status) && !entry.isCanceled()) {
                const durationDiv = buildDivWithTextAndClass(
                    formatDurationShorthand(entry.lastUpdated - entry.created),
                    'game-duration'
                );
                addToolTip(durationDiv, 'game duration');
                rightPane.append(durationDiv);
            }
        });
    }

}

window.onload = () => new MyGamesPage();
