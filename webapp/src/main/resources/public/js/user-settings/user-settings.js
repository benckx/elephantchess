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

const UI_NOTIFICATION_TIMEOUT = 2_500;

const USER_SETTINGS_API = '/api/user/settings';
const PROFILE_URL = USER_SETTINGS_API + '/profile';
const EMAIL_SETTINGS_URL = USER_SETTINGS_API + '/email-address';

const USERNAME_MAX_DESCRIPTION_LENGTH = 1_000;

class UserSettingsPage extends BasePage {

    // profile section
    #saveProfileButton = document.getElementById('save-profile-button');
    #descriptionField = document.getElementById('description');
    #countryField = document.getElementById('countries');
    #descriptionCharacterCounter = document.getElementById('description-character-counter');

    // email notifications section
    #notificationSettingsWidget = new NotificationSettingsWidget();
    #saveNotificationsButton = document.getElementById('save-notifications-button');

    // TODO: email address section
    #emailAddressField = document.getElementById('email-address');

    // sessions section
    #sessionsWidget = new UserSessionsWidget({limit: 5});

    constructor() {
        super();

        // profile section
        fillSelect('countries');
        this.#fetchProfileSettings();
        this.#saveProfileButton.addEventListener('click', () => this.#updateProfileSettings());
        this.#descriptionField.addEventListener('input', () => this.#updateDescriptionCharacterCounter());
        this.#descriptionField.setAttribute('maxlength', USERNAME_MAX_DESCRIPTION_LENGTH.toString());

        // notifications section
        let notificationsSettingsTable = document.getElementById('notifications-settings-table');
        this.#notificationSettingsWidget.renderToTable(notificationsSettingsTable);
        this.#saveNotificationsButton.addEventListener('click', () => {
            this.#notificationSettingsWidget.updateSettings(() => {
                UI.pushInfoNotification('Notifications settings successfully updated!', UI_NOTIFICATION_TIMEOUT);
            })
        });

        // email address section
        this.#fetchEmailAddressSettings();

        // sessions section
        this.#sessionsWidget.fetchAndRender();
    }

    #fetchProfileSettings() {
        getAndHandle(PROFILE_URL, json => {
            this.#descriptionField.value = json.description;
            this.#updateDescriptionCharacterCounter();
            if (json.country != null) {
                let countryName = getCountryName(json.country)
                if (countryName != null) {
                    let select = document.getElementById('countries');
                    let options = select.getElementsByTagName('option');
                    for (let i = 0; i < options.length; i++) {
                        let option = options[i];
                        if (option.value.toLowerCase() === json.country.toLowerCase()) {
                            option.selected = true;
                            break;
                        }
                    }
                }
            }
        });
    }

    // TODO: 'none' option selected
    #updateProfileSettings() {
        let description = this.#descriptionField.value;
        let country = this.#countryField.value;
        let body = {'description': description, 'country': country};
        postAndHandle(PROFILE_URL, body, () => {
            UI.pushInfoNotification('Profile settings successfully updated!', UI_NOTIFICATION_TIMEOUT);
        });
    }

    #updateDescriptionCharacterCounter() {
        this.#descriptionCharacterCounter.innerText = `${this.#descriptionField.value.length} / ${USERNAME_MAX_DESCRIPTION_LENGTH}`;
    }

    #fetchEmailAddressSettings() {
        getAndHandle(EMAIL_SETTINGS_URL, json => {
            let email = json.email;
            let isValid = json.isValid;

            this.#emailAddressField.value = email;
            let elementId = 'email-validity-unknown';
            if (isValid === true) {
                elementId = 'email-valid';
            } else if (isValid === false) {
                elementId = 'email-invalid';
            }

            document.getElementById(elementId).classList.remove('hidden');
        });
    }

}

window.onload = () => new UserSettingsPage();
