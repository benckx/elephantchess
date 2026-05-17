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

const NOTIFICATIONS_API = '/api/user/settings/notifications';

class NotificationSetting {

    #id;
    #label;

    constructor(id, label) {
        this.#id = id;
        this.#label = label;
    }

    get id() {
        return this.#id;
    }

    get checkboxId() {
        return this.#id + '-checkbox';
    }

    get checkbox() {
        return document.getElementById(this.checkboxId);
    }

    get hitBoxId() {
        return this.#id + '-hit-box';
    }

    get hitBox() {
        return document.getElementById(this.hitBoxId);
    }

    /**
     * @return {boolean}
     */
    get isChecked() {
        return this.checkbox.checked;
    }

    /**
     * @param value {boolean}
     */
    set isChecked(value) {
        this.checkbox.checked = value;
    }

    /**
     * @param table {HTMLTableElement}
     */
    appendToTable(table) {
        table.append(this.#renderRow(table));
        this.#linkCheckboxToHitBox();
    }

    /**
     * @param table {HTMLTableElement}
     * @return {HTMLTableRowElement}
     */
    #renderRow(table) {
        let row = table.insertRow();

        let checkboxCell = row.insertCell();
        checkboxCell.append(this.#buildCheckboxCellContent());
        checkboxCell.classList.add('checkbox-cell');

        let labelCell = row.insertCell();
        labelCell.append(this.#buildLabelCellContent());

        row.append(checkboxCell, labelCell);
        return row;
    }

    #buildCheckboxCellContent() {
        let input = document.createElement('input');
        input.type = 'checkbox';
        input.id = this.#id + '-checkbox';
        return this.#buildCellContent(input)
    }

    #buildLabelCellContent() {
        let span = document.createElement('span');
        span.innerText = this.#label;
        let outerDiv = this.#buildCellContent(span);
        outerDiv.id = this.#id + '-hit-box';
        outerDiv.classList.add('notifications-hit-box');
        return outerDiv;
    }

    #buildCellContent(content) {
        let outerDiv = document.createElement('div');
        let innerDiv = document.createElement('div');

        innerDiv.append(content);
        outerDiv.append(innerDiv);
        return outerDiv;
    }

    /**
     * Can only be done once added to the DOM.
     */
    #linkCheckboxToHitBox() {
        let checkbox = this.checkbox;
        let hitBox = this.hitBox;
        hitBox.addEventListener('click', () => checkbox.checked = !checkbox.checked);
    }

}

class NotificationSettingsWidget {

    #notificationSettings = [
        new NotificationSetting('newsletter', 'Newsletter. At most once a month.'),
        new NotificationSetting('opponent-joined-game', 'Somebody joined a game you created - but you\'re offline.'),
        new NotificationSetting('opponent-flagged', 'Your opponent flagged on time - but you\'re offline.'),
        new NotificationSetting('opponent-played-move', 'Your opponent played a move - but you\'re offline.'),
        new NotificationSetting('opponent-resigned', 'Your opponent resigned - but you\'re offline.'),
        new NotificationSetting('opponent-proposed-draw', 'Your opponent proposed a draw - but you\'re offline.'),
        new NotificationSetting('opponent-accepted-draw', 'Your opponent accepted a draw - but you\'re offline.'),
        new NotificationSetting('opponent-declined-draw', 'Your opponent declined a draw - but you\'re offline.'),
    ];

    constructor() {
        getAndHandle(NOTIFICATIONS_API, json => {
            this.#findSettingById('newsletter').isChecked = json.newsletter;
            this.#findSettingById('opponent-joined-game').isChecked = json.opponentJoinedGame;
            this.#findSettingById('opponent-flagged').isChecked = json.opponentFlagged;
            this.#findSettingById('opponent-played-move').isChecked = json.opponentPlayedMove;
            this.#findSettingById('opponent-resigned').isChecked = json.opponentResigned;
            this.#findSettingById('opponent-proposed-draw').isChecked = json.opponentProposedDraw;
            this.#findSettingById('opponent-accepted-draw').isChecked = json.opponentAcceptedDraw;
            this.#findSettingById('opponent-declined-draw').isChecked = json.opponentDeclinedDraw;
        });
    }

    /**
     * @param cb {function}
     */
    updateSettings(cb) {
        let body = {
            'newsletter': this.#findSettingById('newsletter').isChecked,
            'opponentJoinedGame': this.#findSettingById('opponent-joined-game').isChecked,
            'opponentFlagged': this.#findSettingById('opponent-flagged').isChecked,
            'opponentPlayedMove': this.#findSettingById('opponent-played-move').isChecked,
            'opponentResigned': this.#findSettingById('opponent-resigned').isChecked,
            'opponentProposedDraw': this.#findSettingById('opponent-proposed-draw').isChecked,
            'opponentAcceptedDraw': this.#findSettingById('opponent-accepted-draw').isChecked,
            'opponentDeclinedDraw': this.#findSettingById('opponent-declined-draw').isChecked,
        };

        postAndHandle(NOTIFICATIONS_API, body, cb);
    }

    /**
     * @param table {HTMLTableElement}
     */
    renderToTable(table) {
        this.#notificationSettings.forEach(item => {
            item.appendToTable(table);
        });
    }

    /**
     * @param id {string}
     * @return {NotificationSetting|null}
     */
    #findSettingById(id) {
        return this.#notificationSettings.find(setting => setting.id === id)
    }

}
