/*
 * Copyright (C) 2026  Encelade SRL
 * Copyright (C) 2026  elephantchess.io
 * Copyright (C) 2026  Benoît Vleminckx (benckx)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see <https://www.gnu.org/licenses/>.
 */

const RESIZE_PERSISTENCE_DEFAULT_SAVE_DEBOUNCE_MS = 150;

/**
 * Persists the vertical height of a CSS-resizable container (a container using
 * `resize: vertical`) through host-provided storage callbacks, while staying
 * storage-agnostic itself. The container's CSS `min-height`/`max-height` are
 * used as the valid bounds both when restoring and when saving a height.
 */
class ResizePersistence {

    /**
     * The resizable container being observed.
     * @type {null|HTMLElement}
     */
    #container = null;

    /**
     * Optional callback to load a previously saved height (for example from cookies).
     * @type {null|(() => number|null)}
     */
    #loadPersistedHeight = null;

    /**
     * Optional callback to persist new heights while this helper remains
     * storage-agnostic.
     * @type {null|((height: number) => void)}
     */
    #persistHeight = null;

    /**
     * Debounce delay (ms) to avoid writing on every single resize tick.
     * @type {number}
     */
    #saveDebounceMs = RESIZE_PERSISTENCE_DEFAULT_SAVE_DEBOUNCE_MS;

    /**
     * ResizeObserver watching the container so drag-resize changes can be detected
     * immediately when the browser supports ResizeObserver.
     * @type {null|ResizeObserver}
     */
    #resizeObserver = null;

    /**
     * Debounce timer id used to avoid persisting on every single resize tick.
     * @type {null|number}
     */
    #resizeSaveTimeout = null;

    /**
     * Last persisted pixel height so unchanged values are skipped and don't trigger
     * redundant writes.
     * @type {null|number}
     */
    #lastSavedHeight = null;

    /**
     * Mouse/touch fallback listener for older browsers where ResizeObserver is not
     * available, so resize persistence still works after drag release.
     * @type {null|(() => void)}
     */
    #fallbackResizeListener = null;

    /**
     * MutationObserver used to detect container removal from DOM and clean up all
     * resize-related listeners/observers.
     * @type {null|MutationObserver}
     */
    #disposalObserver = null;

    /**
     * @param options {{
     *     container: HTMLElement,
     *     loadPersistedHeight?: (() => number|null),
     *     persistHeight?: ((height: number) => void),
     *     saveDebounceMs?: number
     * }}
     */
    constructor(options) {
        if (options.container === undefined || options.container === null) {
            throw new Error('container is undefined');
        }
        this.#container = options.container;

        if (options.loadPersistedHeight !== undefined) {
            this.#loadPersistedHeight = options.loadPersistedHeight;
        }
        if (options.persistHeight !== undefined) {
            this.#persistHeight = options.persistHeight;
        }
        if (options.saveDebounceMs !== undefined) {
            this.#saveDebounceMs = options.saveDebounceMs;
        }
    }

    /**
     * Restores a previously persisted height (when valid) and starts watching for
     * further resize changes so they can be persisted.
     */
    start() {
        this.#applySavedHeight();
        this.#setUp();
    }

    // Restores a previously persisted height when it is valid.
    // Invalid, missing, or out-of-bounds values are ignored.
    #applySavedHeight() {
        if (this.#loadPersistedHeight === null) {
            return;
        }

        const persistedHeight = this.#loadPersistedHeight();
        if (persistedHeight === null) {
            return;
        }

        if (
            !Number.isFinite(persistedHeight)
            || !Number.isInteger(persistedHeight)
            || persistedHeight < this.#minResizeHeight()
            || persistedHeight > this.#maxResizeHeight()
        ) {
            return;
        }

        this.#container.style.height = `${persistedHeight}px`;
        this.#lastSavedHeight = persistedHeight;
    }

    // Reads the container's CSS min-height and returns it as a pixel integer.
    // Falls back to 0 when parsing is not possible.
    #minResizeHeight() {
        const parsed = Number.parseInt(window.getComputedStyle(this.#container).minHeight, 10);
        if (Number.isFinite(parsed) && parsed > 0) {
            return parsed;
        }
        return 0;
    }

    // Reads the container's CSS max-height and returns it as a pixel integer.
    // Falls back to positive infinity when no usable max-height is set.
    #maxResizeHeight() {
        const parsed = Number.parseInt(window.getComputedStyle(this.#container).maxHeight, 10);
        if (Number.isFinite(parsed) && parsed > 0) {
            return parsed;
        }
        return Number.POSITIVE_INFINITY;
    }

    // Watches resize changes and saves height through the persistence callback.
    // Cleans up observers/listeners when the container leaves the DOM.
    #setUp() {
        const saveHeight = () => {
            if (this.#resizeSaveTimeout !== null) {
                clearTimeout(this.#resizeSaveTimeout);
            }

            this.#resizeSaveTimeout = setTimeout(() => {
                const height = this.#container.offsetHeight;
                if (
                    height >= this.#minResizeHeight()
                    && height <= this.#maxResizeHeight()
                    && height !== this.#lastSavedHeight
                ) {
                    if (this.#persistHeight !== null) {
                        this.#persistHeight(height);
                    }
                    this.#lastSavedHeight = height;
                }
            }, this.#saveDebounceMs);
        };

        // Prefer ResizeObserver when available; keep a listener fallback for older browsers.
        if (typeof ResizeObserver !== 'undefined') {
            this.#resizeObserver = new ResizeObserver(() => saveHeight());
            this.#resizeObserver.observe(this.#container);
        } else {
            this.#fallbackResizeListener = saveHeight;
            this.#container.addEventListener('mouseup', this.#fallbackResizeListener);
            this.#container.addEventListener('touchend', this.#fallbackResizeListener);
        }

        if (document.body !== null) {
            // Stop persistence observers if this container gets detached from the DOM.
            this.#disposalObserver = new MutationObserver(() => {
                if (!document.body.contains(this.#container)) {
                    this.teardown();
                }
            });
            this.#disposalObserver.observe(document.body, {childList: true, subtree: true});
        }
    }

    /**
     * Stops all resize observers/listeners and clears any pending save timer.
     * Called automatically when the container leaves the DOM, but can also be
     * invoked explicitly by a host that disposes of the widget.
     */
    teardown() {
        if (this.#resizeObserver !== null) {
            this.#resizeObserver.disconnect();
            this.#resizeObserver = null;
        }
        if (this.#fallbackResizeListener !== null) {
            this.#container.removeEventListener('mouseup', this.#fallbackResizeListener);
            this.#container.removeEventListener('touchend', this.#fallbackResizeListener);
            this.#fallbackResizeListener = null;
        }
        if (this.#resizeSaveTimeout !== null) {
            clearTimeout(this.#resizeSaveTimeout);
            this.#resizeSaveTimeout = null;
        }
        if (this.#disposalObserver !== null) {
            this.#disposalObserver.disconnect();
            this.#disposalObserver = null;
        }
    }

}
