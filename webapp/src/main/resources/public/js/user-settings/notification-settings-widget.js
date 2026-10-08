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

    constructor(id) {
        this.#id = id;
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

    /**
     * @return {boolean}
     */
    get isChecked() {
        return this.checkbox.checked;
    }

}

class NotificationSettingsWidget {

    // The table and checked states are rendered server-side; the ids must match
    // the checkbox ids injected by UserSettingsPageRenderer.
    #notificationSettings = [
        new NotificationSetting('newsletter'),
        new NotificationSetting('opponent-joined-game'),
        new NotificationSetting('opponent-played-move'),
        new NotificationSetting('opponent-resigned'),
        new NotificationSetting('opponent-proposed-draw'),
        new NotificationSetting('opponent-accepted-draw'),
        new NotificationSetting('opponent-declined-draw'),
    ];

    /**
     * @param cb {function}
     */
    updateSettings(cb) {
        let body = {
            'newsletter': this.#findSettingById('newsletter').isChecked,
            'opponentJoinedGame': this.#findSettingById('opponent-joined-game').isChecked,
            'opponentPlayedMove': this.#findSettingById('opponent-played-move').isChecked,
            'opponentResigned': this.#findSettingById('opponent-resigned').isChecked,
            'opponentProposedDraw': this.#findSettingById('opponent-proposed-draw').isChecked,
            'opponentAcceptedDraw': this.#findSettingById('opponent-accepted-draw').isChecked,
            'opponentDeclinedDraw': this.#findSettingById('opponent-declined-draw').isChecked,
        };

        postAndHandle(NOTIFICATIONS_API, body, cb);
    }

    /**
     * @param checked {boolean}
     */
    setAll(checked) {
        this.#notificationSettings.forEach(setting => setting.checkbox.checked = checked);
    }

    /**
     * @return {string}
     */
    stateSignature() {
        return JSON.stringify(this.#notificationSettings.map(setting => setting.isChecked));
    }

    /**
     * @param cb {function}
     */
    addChangeListener(cb) {
        this.#notificationSettings.forEach(setting => setting.checkbox.addEventListener('change', cb));
    }

    /**
     * @param id {string}
     * @return {NotificationSetting|null}
     */
    #findSettingById(id) {
        return this.#notificationSettings.find(setting => setting.id === id)
    }

}
