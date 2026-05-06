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

class AdminChatMessageDto {

    #gameId;
    #index;
    #author;
    #messageTime;
    #content;

    /**
     * @param {Object} json
     */
    constructor(json) {
        this.#gameId = json.gameId;
        this.#index = json.index;
        this.#author = new UserDto(json.author);
        this.#messageTime = json.messageTime;
        this.#content = json.content;
    }

    /**
     * @returns {GameId}
     */
    get gameId() {
        return new GameId(GameType.PVP, this.#gameId);
    }

    /**
     * @returns {number}
     */
    get index() {
        return this.#index;
    }

    /**
     * @returns {UserDto}
     */
    get author() {
        return this.#author;
    }

    /**
     * @returns {number}
     */
    get messageTime() {
        return this.#messageTime;
    }

    /**
     * @returns {string}
     */
    get content() {
        return this.#content;
    }
}

class AdminChatMessagesPage extends BasePage {

    #lastChatMessagesTable = document.getElementById('last-chat-messages');

    constructor() {
        super();
        getAndHandle(`${ADMIN_URL_PREFIX}/last-chat-messages`, json => {
            const tbody = emptyTable(this.#lastChatMessagesTable);
            const chatMessages = json.messages.map(message => new AdminChatMessageDto(message));
            chatMessages.forEach(message => {
                const anchor = buildLink(gameIdToPageLink(message.gameId), message.gameId.id);
                const author = buildUsernameSpan(message.author.userId, message.author.username, message.author.userType);
                const content = cropText(message.content, 60);
                const timestamp = formatTimestampDefaultDateFormat(message.messageTime);

                const row = tbody.insertRow();
                row.insertCell().append(anchor);
                row.insertCell().append(author);
                row.insertCell().innerText = content;
                row.insertCell().innerText = timestamp;
            });
        });
    }

}

window.onload = () => new AdminChatMessagesPage();
