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

class BasePage {

    #gamesToPlayWebSocketSession;
    #gamesToJoinCircle = document.getElementById('number-of-games-in-lobby-counter-circle');
    #gamesToJoinCounter = document.getElementById('number-of-games-in-lobby-counter-counter');

    constructor() {
        UI.init();
        const maybeUser = this.#findIdentifiedUser();
        if (maybeUser == null) {
            getAndHandle('/api/obtain-guest-user-token', (json) => {
                setCookie(GUEST_USER_ID_FIELD, json.id, ANONYMOUS_USER_COOKIE_TTL);
                setCookie(GUEST_USER_TOKEN_FIELD, json.token, ANONYMOUS_USER_COOKIE_TTL);
                this.#postIdentification();
                showGuestMessages();
                UI.pushInfoNotification(`Identified as ${new User().toString()}`);
            });
        } else {
            switch (maybeUser.userType) {
                case UserType.AUTHENTICATED:
                    eraseGuestCookies();
                    this.#postIdentification();
                    break;
                case UserType.GUEST:
                    this.#postIdentification();
                    showGuestMessages();
                    break;
                default:
                    console.error('Unknown user type: ' + maybeUser.userType);
            }
        }

        setTimeout(() => removeTrackingParamsFromUrl(), 800);

        // Ko-fi button - adjust close button position dynamically on mobile
        if (window.innerWidth <= 1000) {
            setTimeout(() => {
                this.#adjustKofiCloseButtonPosition();
            }, 1_000);
        }
    }

    #adjustKofiCloseButtonPosition() {
        const closeButton = document.querySelector('.floating-chat-kofi-popup-iframe-closer-mobi');
        if (!closeButton) return;

        // Get the computed transform scale value from CSS
        const computedStyle = window.getComputedStyle(closeButton);
        const transform = computedStyle.transform;

        // Extract scale value from transform matrix
        let scale = 2.0; // Default fallback
        if (transform && transform !== 'none') {
            const matrix = transform.match(/matrix\(([^)]+)\)/);
            if (matrix) {
                const values = matrix[1].split(',').map(v => parseFloat(v.trim()));
                scale = values[0]; // First value in matrix is scaleX
            }
        }

        // Calculate offset based on scale
        // When an element scales from bottom-left, the top-right corner moves
        // The offset is: (scale - 1) * original_dimension
        // We need to counteract this movement

        // Get the original position (before any transforms)
        const originalRight = 18; // Ko-fi library default
        const originalTop = 18;   // Ko-fi library default

        // Calculate the adjustment needed
        // The close button container width/height need to be considered
        const containerWidth = 328; // From Ko-fi widget
        const containerHeight = 690; // From Ko-fi widget

        // -5 is a fudge factor to make it look better
        const rightOffset = -(containerWidth * (scale - 1)) + originalRight - 10;
        const topOffset = -(containerHeight * (scale - 1)) + originalTop - 10;

        closeButton.style.right = `${rightOffset}px`;
        closeButton.style.top = `${topOffset}px`;
    }

    #findIdentifiedUser() {
        const user = new User();
        if (user.isIdentified) {
            return user;
        } else {
            return null;
        }
    }

    /**
     * @returns {function(GamesToPlayUpdateDto)[]}
     */
    additionalGamesToJoinListeners() {
        return [];
    }

    #postIdentification() {
        UI.initLoginPanel();

        const body = {'currentPage': window.location.href};
        postAndHandleWith('/api/ping-session', body, new PingResponseHandler());

        const allGamesToJoinListeners = [
            ...this.additionalGamesToJoinListeners(),
            update => this.#updateGamesToJoinCounter(update)
        ];

        const turnToPlayGamesListeners = [
            (update) => UI.refreshTurnToPlayWidget(update)
        ]

        this.#gamesToPlayWebSocketSession =
            new GamesToPlayWebSocketSession(allGamesToJoinListeners, turnToPlayGamesListeners);
    }

    /**
     * @param updateDto {GamesToPlayUpdateDto}
     */
    #updateGamesToJoinCounter(updateDto) {
        const userId = userIdOrNull();

        const length =
            updateDto
                .gamesToJoin
                .filter(game => game.opponentUserId !== userId)
                .filter(game => game.isOpponentOnline)
                .length

        formatNotificationCircleCounter(
            this.#gamesToJoinCircle,
            this.#gamesToJoinCounter,
            length
        );
    }

}

const MOVE_TREE_WIDGET_HEIGHT_COOKIE_PREFIX = 'moveTreeWidget.height';

function moveTreeResizeCookiePersistence(pageKey, containerId) {
    const cookieName = `${MOVE_TREE_WIDGET_HEIGHT_COOKIE_PREFIX}.${pageKey}.${containerId}`;
    return {
        loadPersistedHeight: () => {
            const rawValue = getCookie(cookieName);
            if (rawValue === null) {
                return null;
            }
            const parsed = Number.parseInt(rawValue, 10);
            if (!Number.isFinite(parsed)) {
                return null;
            }
            return parsed;
        },
        persistHeight: (height) => {
            setCookie(cookieName, height.toString(), CHROME_COOKIE_MAX_TTL);
        }
    };
}
