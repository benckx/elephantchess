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

const UPDATE_TIMESTAMP_INTERVAL = 4_000;

/**
 * How long the "is typing…" indicator stays visible after the last typing event.
 */
const DISPLAY_IS_TYPING_FOR = 1_500;

/**
 * Cool-down (ms) after a chat message is added during which incoming typing
 * notifications are ignored - this avoids flickering the indicator for typing
 * events that were already in flight when the message was sent.
 */
const TYPING_COOL_DOWN_AFTER_CHAT = 1_500;

class ChatBoxMessage {

    #message;
    #userId;
    #userName;
    #timestamp;

    /**
     * @param message {string}
     * @param userId {UserId}
     * @param userName {string}
     * @param timestamp {number}
     */
    constructor(message, userId, userName, timestamp) {
        this.#message = message;
        this.#userId = userId;
        this.#userName = userName;
        this.#timestamp = timestamp;
    }

    /**
     * @return {string}
     */
    get message() {
        return this.#message;
    }

    /**
     * @return {UserId}
     */
    get userId() {
        return this.#userId;
    }

    /**
     * @return {string}
     */
    get userName() {
        return this.#userName;
    }

    /**
     * @return {number}
     */
    get timestamp() {
        return this.#timestamp;
    }

}

class ChatBoxWidget {

    /**
     * @type {function(string): void}
     */
    #sendMessageCb = null;

    /**
     * @type {Map<string, number>}
     */
    #timestampsToUpdate = new Map();

    #messagesContainer = document.getElementById('chat-box-messages-container');
    #input = document.getElementById('chat-box-input');
    #sendButton = document.getElementById('chat-box-send-message-button');
    #typingIndicator = document.getElementById('chat-box-typing-indicator');

    /**
     * userId to color
     * @type {Map<string, string>}
     */
    #colorMapping = new Map();

    /**
     * @type {function()[]}
     */
    #inputGainsFocusListeners = [];

    /**
     * @type {function()[]}
     */
    #inputLosesFocusListeners = [];

    /**
     * @type {function()[]}
     */
    #inputTypingListeners = [];

    /**
     * Debounce timer for the input typing event.
     * @type {number|null}
     */
    #typingDebounceTimer = null;

    /**
     * Timer that hides the "is typing…" indicator after [DISPLAY_IS_TYPING_FOR].
     * @type {number|null}
     */
    #hideIsTypingTimerId = null;

    /**
     * Timestamp (ms) until which incoming typing notifications are ignored
     * (cool-down right after a chat message is added).
     * @type {number}
     */
    #typingCoolDownUntil = 0;

    /**
     * @param sendMessageCb {function(string): void}
     */
    constructor(sendMessageCb) {
        this.#sendMessageCb = sendMessageCb;
        this.enable(false);

        this.#sendButton.addEventListener('click', () => {
            this.#sendMessage();
        });

        this.#input.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.#sendMessage();
            }
        });

        this.#input.addEventListener('focus', () => {
            this.#inputGainsFocusListeners.forEach((listener) => listener());
        });

        this.#input.addEventListener('blur', () => {
            this.#inputLosesFocusListeners.forEach((listener) => listener());
        });

        this.#input.addEventListener('input', () => {
            if (this.#input.value.length === 0) {
                return;
            }
            clearTimeout(this.#typingDebounceTimer);
            this.#typingDebounceTimer = setTimeout(() => {
                this.#inputTypingListeners.forEach((listener) => listener());
            }, 200);
        });

        // update relative time of messages every 5 seconds
        setInterval(() => {
            this.#timestampsToUpdate.forEach((timestamp, id) => {
                const element = document.getElementById(id);
                if (element == null) {
                    this.#timestampsToUpdate.delete(id);
                } else {
                    element.innerText = formatTimestampToRelativeTime(timestamp);
                }
            });
        }, UPDATE_TIMESTAMP_INTERVAL);
    }

    #sendMessage() {
        const message = this.#input.value;
        if (message.length > 0) {
            const user = new User();
            if (user.isIdentified) {
                this.#sendMessageCb(message);
                const userId = new UserId(user.userType, user.userId);
                this.addMessage(new ChatBoxMessage(message, userId, user.username, new Date().getTime()));
            }
            this.#input.value = '';
        }
    }

    /**
     * @param enable {boolean}
     */
    enable(enable) {
        if (enable) {
            enableAppButton(this.#sendButton);
            this.#input.disabled = false;
        } else {
            disableAppButton(this.#sendButton);
            this.#input.disabled = true;
        }
    }

    /**
     * @param listener {function()}
     */
    addInputGainsFocusListener(listener) {
        this.#inputGainsFocusListeners.push(listener);
    }

    /**
     * @param listener {function()}
     */
    addInputLosesFocusListener(listener) {
        this.#inputLosesFocusListeners.push(listener);
    }

    /**
     * @param listener {function()}
     */
    addInputTypingListener(listener) {
        this.#inputTypingListeners.push(listener);
    }

    /**
     * Display (or refresh) the "is typing…" indicator for the given users.
     * Typing events arriving within the cool-down window after a chat message
     * was added are silently ignored, so the indicator doesn't flicker for
     * stale events that were in-flight when the message arrived.
     *
     * @param typingUsers {{userId: string, username: string, typedAt: *}[]}
     */
    notifyTypingUsers(typingUsers) {
        // ignore typing events within the cool-down window after a chat message
        if (Date.now() < this.#typingCoolDownUntil) {
            return;
        }

        if (typingUsers == null || typingUsers.length === 0) {
            this.#hideIsTypingIndicator();
            return;
        }

        const names = typingUsers.map((user) => user.username);
        let label;
        if (names.length === 1) {
            label = `${names[0]} is typing…`;
        } else {
            const allButLast = names.slice(0, -1).join(', ');
            label = `${allButLast} and ${names[names.length - 1]} are typing…`;
        }

        this.#typingIndicator.innerText = label;
        this.#typingIndicator.style.display = 'block';

        clearTimeout(this.#hideIsTypingTimerId);
        this.#hideIsTypingTimerId = setTimeout(() => {
            this.#hideIsTypingIndicator();
        }, DISPLAY_IS_TYPING_FOR);
    }

    #hideIsTypingIndicator() {
        clearTimeout(this.#hideIsTypingTimerId);
        this.#hideIsTypingTimerId = null;
        this.#typingIndicator.style.display = 'none';
        this.#typingIndicator.innerText = '';
    }

    /**
     *
     * @param colorMapping {Map<string, string>}
     */
    updateColorMapping(colorMapping) {
        this.#colorMapping.clear();

        for (const [userId, color] of colorMapping.entries()) {
            this.#colorMapping.set(userId, color);
            const userClass = this.#userClass(userId);
            const colorClass = this.#authorColorClass(color);
            getElementsByClassNameArray(userClass).forEach((element) => {
                element.classList.add(colorClass);
                element.classList.remove(userClass);
            });
        }
    }

    /**
     * @param message {ChatBoxMessage}
     */
    addMessage(message) {
        // a message means the author is no longer typing: hide the indicator
        // and start a cool-down to ignore in-flight typing notifications
        this.#hideIsTypingIndicator();
        this.#typingCoolDownUntil = Date.now() + TYPING_COOL_DOWN_AFTER_CHAT;

        const timestampId = randomId();

        const timestamp = document.createElement('div');
        timestamp.classList.add('timestamp');
        timestamp.id = timestampId;
        setRelativeTimeAndToolTip(timestamp, message.timestamp);

        const timestampContainer = document.createElement('div');
        timestampContainer.classList.add('timestamp-container');
        timestampContainer.append(timestamp);

        const userId = message.userId.userId;

        const username = document.createElement('div');
        username.appendChild(buildUsernameSpan(userId, message.userName, message.userId.userType));
        username.classList.add('message-author');

        if (this.#colorMapping.has(userId)) {
            const color = this.#colorMapping.get(userId);
            username.classList.add(this.#authorColorClass(color));
        } else {
            username.classList.add(this.#userClass(userId));
        }

        const header = document.createElement('div');
        header.classList.add('header');
        header.appendChild(username);
        header.appendChild(timestampContainer);

        const content = document.createElement('div');
        content.classList.add('message-content');
        content.innerText = message.message;

        const messageElement = document.createElement('div');
        messageElement.classList.add('chat-box-message');
        messageElement.appendChild(header);
        messageElement.appendChild(content);

        this.#messagesContainer.appendChild(messageElement);
        this.#messagesContainer.scrollTop = this.#messagesContainer.scrollHeight;

        this.#timestampsToUpdate.set(timestampId, message.timestamp);
    }

    /**
     * @param userId {string}
     * @returns {string}
     */
    #userClass(userId) {
        return `user-${userId}`;
    }

    /**
     * @param color {string}
     * @returns {string}
     */
    #authorColorClass(color) {
        return `${color.toLocaleLowerCase()}-author`;
    }

}
