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
const RESEND_EMAIL_CONFIRMATION_URL = EMAIL_SETTINGS_URL + '/resend-confirmation';

const USERNAME_MAX_DESCRIPTION_LENGTH = 1_000;

class UserSettingsPage extends BasePage {

    // profile section
    #saveProfileButton = document.getElementById('save-profile-button');
    #descriptionField = document.getElementById('description');
    #countryField = document.getElementById('countries');
    #descriptionCharacterCounter = document.getElementById('description-character-counter');
    #showPvpGamesCheckbox = document.getElementById('show-pvp-games-on-profile');
    #showPvbGamesCheckbox = document.getElementById('show-pvb-games-on-profile');

    // email notifications section
    #notificationSettingsWidget = new NotificationSettingsWidget();
    #saveNotificationsButton = document.getElementById('save-notifications-button');

    // TODO: email address section
    #emailAddressField = document.getElementById('email-address');

    // sessions section
    #sessionsWidget = new UserSessionsWidget({limit: 8, selectable: false});

    // page views section
    #pageViewsWidget = new UserPageViewsWidget({limit: 8});

    // Serialized snapshots of the last saved (unchanged) values, used to detect
    // whether the current form state differs and the save buttons should enable.
    #profileBaseline;
    #notificationsBaseline;

    constructor() {
        super();

        // profile section
        fillSelect('countries');
        this.#selectLoadedCountry();
        this.#updateDescriptionCharacterCounter();
        this.#saveProfileButton.addEventListener('click', () => this.#updateProfileSettings());
        this.#descriptionField.addEventListener('input', () => this.#updateDescriptionCharacterCounter());
        this.#descriptionField.setAttribute('maxlength', USERNAME_MAX_DESCRIPTION_LENGTH.toString());
        this.#profileBaseline = this.#profileStateSignature();
        [this.#descriptionField, this.#countryField, this.#showPvpGamesCheckbox, this.#showPvbGamesCheckbox]
            .forEach(field => {
                field.addEventListener('input', () => this.#refreshProfileSaveButton());
                field.addEventListener('change', () => this.#refreshProfileSaveButton());
            });
        this.#refreshProfileSaveButton();

        // notifications section
        this.#saveNotificationsButton.addEventListener('click', () => {
            this.#notificationSettingsWidget.updateSettings(() => {
                UI.pushInfoNotification('Notifications settings successfully updated!', UI_NOTIFICATION_TIMEOUT);
                this.#notificationsBaseline = this.#notificationSettingsWidget.stateSignature();
                this.#refreshNotificationsSaveButton();
            })
        });
        this.#notificationsBaseline = this.#notificationSettingsWidget.stateSignature();
        this.#notificationSettingsWidget.addChangeListener(() => this.#refreshNotificationsSaveButton());
        document.getElementById('notifications-select-all').addEventListener('click', () => {
            this.#notificationSettingsWidget.setAll(true);
            this.#refreshNotificationsSaveButton();
        });
        document.getElementById('notifications-unselect-all').addEventListener('click', () => {
            this.#notificationSettingsWidget.setAll(false);
            this.#refreshNotificationsSaveButton();
        });
        this.#refreshNotificationsSaveButton();

        // email address section
        this.#fetchEmailAddressSettings();

        // sessions section
        this.#sessionsWidget.fetchAndRender();

        // page views section
        this.#pageViewsWidget.fetchAndRender();
    }

    #profileStateSignature() {
        return JSON.stringify({
            description: this.#descriptionField.value,
            country: this.#countryField.value,
            showPvpGamesOnProfile: this.#showPvpGamesCheckbox.checked,
            showPvbGamesOnProfile: this.#showPvbGamesCheckbox.checked,
        });
    }

    #refreshProfileSaveButton() {
        this.#saveProfileButton.disabled = this.#profileStateSignature() === this.#profileBaseline;
    }

    #refreshNotificationsSaveButton() {
        this.#saveNotificationsButton.disabled =
            this.#notificationSettingsWidget.stateSignature() === this.#notificationsBaseline;
    }

    #selectLoadedCountry() {
        const country = this.#countryField.dataset.selectedCountry;
        if (country == null || country === '') {
            return;
        }
        const options = this.#countryField.getElementsByTagName('option');
        for (let i = 0; i < options.length; i++) {
            const option = options[i];
            if (option.value.toLowerCase() === country.toLowerCase()) {
                option.selected = true;
                break;
            }
        }
    }

    #updateProfileSettings() {
        let description = this.#descriptionField.value;
        let country = this.#countryField.value;
        if (country === 'none') {
            country = '';
        }
        let body = {
            'description': description,
            'country': country,
            'showPvpGamesOnProfile': this.#showPvpGamesCheckbox.checked,
            'showPvbGamesOnProfile': this.#showPvbGamesCheckbox.checked,
        };
        postAndHandle(PROFILE_URL, body, () => {
            UI.pushInfoNotification('Profile settings successfully updated!', UI_NOTIFICATION_TIMEOUT);
            this.#profileBaseline = this.#profileStateSignature();
            this.#refreshProfileSaveButton();
        });
    }

    #updateDescriptionCharacterCounter() {
        this.#descriptionCharacterCounter.innerText = `${this.#descriptionField.value.length} / ${USERNAME_MAX_DESCRIPTION_LENGTH}`;
    }

    #fetchEmailAddressSettings() {
        getAndHandle(EMAIL_SETTINGS_URL, json => {
            this.#emailAddressField.value = json.email;
            let elementId;
            switch (json.validityStatus) {
                case 'MANUALLY_CONFIRMED':
                    elementId = 'email-manually-confirmed';
                    break;
                case 'AUTOMATED_VALID':
                    elementId = 'email-automated-valid';
                    break;
                case 'AUTOMATED_BOUNCED':
                    elementId = 'email-automated-bounced';
                    break;
                case 'AUTOMATED_INVALID':
                    elementId = 'email-automated-invalid';
                    break;
                default:
                    elementId = 'email-validity-unknown';
            }
            document.getElementById(elementId).classList.remove('hidden');

            // The resend button is shown for any status except MANUALLY_CONFIRMED, since manual
            // confirmation is the strongest signal and a resend would not change anything.
            if (json.validityStatus !== 'MANUALLY_CONFIRMED') {
                const container = document.getElementById('resend-email-confirmation-container');
                const button = document.getElementById('resend-email-confirmation-button');
                container.classList.remove('hidden');
                button.addEventListener('click', () => this.#resendEmailConfirmation());
            }
        });
    }

    #resendEmailConfirmation() {
        const button = document.getElementById('resend-email-confirmation-button');
        button.disabled = true;
        postAndHandle(RESEND_EMAIL_CONFIRMATION_URL, null, () => {
            UI.pushInfoNotification('Confirmation email sent!', UI_NOTIFICATION_TIMEOUT);
            button.disabled = false;
        });
    }

}

window.onload = () => new UserSettingsPage();
