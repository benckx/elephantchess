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

const SCREEN_WAKE_LOCK_IDLE_TIMEOUT = 10 * 60 * 1000;

/**
 * Prevents the screen from dimming or locking (e.g. on mobile while thinking about a move),
 * using the Screen Wake Lock API. It does nothing on browsers that do not support the API.
 *
 * The lock is given up after {@link SCREEN_WAKE_LOCK_IDLE_TIMEOUT} without activity,
 * so that a device left unattended on the page can still go to sleep. It is acquired again
 * on the next user interaction, or the next call to {@link ScreenWakeLock.keepAwake}.
 */
class ScreenWakeLock {

    static #isSupported = 'wakeLock' in navigator;
    static #isWanted = false;
    static #isListening = false;
    static #isRequesting = false;
    static #lastActivity = 0;
    static #idleTimeout = null;

    /**
     * @type {WakeLockSentinel|null}
     */
    static #sentinel = null;

    /**
     * Keep the screen awake, until {@link ScreenWakeLock.release} is called or the user is idle for too long.
     * Can be called again to notify of activity not coming from the user (e.g. a move played by the opponent).
     */
    static keepAwake() {
        if (!ScreenWakeLock.#isSupported) {
            return;
        }

        ScreenWakeLock.#isWanted = true;
        ScreenWakeLock.#listenToActivity();
        ScreenWakeLock.#registerActivity();
    }

    /**
     * Let the screen dim and lock as usual.
     */
    static release() {
        ScreenWakeLock.#isWanted = false;
        clearTimeout(ScreenWakeLock.#idleTimeout);
        ScreenWakeLock.#releaseSentinel();
    }

    static #listenToActivity() {
        if (!ScreenWakeLock.#isListening) {
            ScreenWakeLock.#isListening = true;

            const options = {capture: true, passive: true};
            document.addEventListener('pointerdown', () => ScreenWakeLock.#registerActivity(), options);
            document.addEventListener('keydown', () => ScreenWakeLock.#registerActivity(), options);

            // the browser releases the lock when the page is hidden (tab switch, app in background, screen off)
            document.addEventListener('visibilitychange', () => {
                if (document.visibilityState === 'visible') {
                    ScreenWakeLock.#registerActivity();
                }
            });
        }
    }

    static #registerActivity() {
        if (ScreenWakeLock.#isWanted) {
            ScreenWakeLock.#lastActivity = Date.now();
            clearTimeout(ScreenWakeLock.#idleTimeout);
            ScreenWakeLock.#idleTimeout =
                setTimeout(() => ScreenWakeLock.#releaseSentinel(), SCREEN_WAKE_LOCK_IDLE_TIMEOUT);
            ScreenWakeLock.#acquireSentinel();
        }
    }

    /**
     * @returns {boolean}
     */
    static #shouldHold() {
        return ScreenWakeLock.#isWanted &&
            document.visibilityState === 'visible' &&
            Date.now() - ScreenWakeLock.#lastActivity < SCREEN_WAKE_LOCK_IDLE_TIMEOUT;
    }

    static #acquireSentinel() {
        if (ScreenWakeLock.#sentinel != null || ScreenWakeLock.#isRequesting || !ScreenWakeLock.#shouldHold()) {
            return;
        }

        ScreenWakeLock.#isRequesting = true;
        navigator.wakeLock
            .request('screen')
            .then((sentinel) => {
                sentinel.addEventListener('release', () => {
                    if (ScreenWakeLock.#sentinel === sentinel) {
                        ScreenWakeLock.#sentinel = null;
                    }
                });
                ScreenWakeLock.#sentinel = sentinel;

                // released or timed out while the request was pending
                if (!ScreenWakeLock.#shouldHold()) {
                    ScreenWakeLock.#releaseSentinel();
                }
            })
            .catch(() => {
                // refused by the browser (e.g. battery saver mode), the screen behaves as usual
            })
            .finally(() => {
                ScreenWakeLock.#isRequesting = false;
            });
    }

    static #releaseSentinel() {
        const sentinel = ScreenWakeLock.#sentinel;
        if (sentinel != null) {
            ScreenWakeLock.#sentinel = null;
            sentinel.release().catch(() => {
                // already released
            });
        }
    }

}
