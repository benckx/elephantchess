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

const NOTIFICATION_DURATION = 4_000;
const NOTIFICATION_DURATION_SUCCESSFUL = 5_000;

class PasswordRecoveryUpdatePasswordPage extends BasePage {

    /**
     * @type {string}
     */
    #email;

    /**
     * @type {string}
     */
    #code;

    #updatePasswordButton = document.getElementById('update-password-button');
    #passwordField = document.getElementById('password-field');

    constructor() {
        super();
        if (isUserAuthenticated()) {
            // to home page
            window.open('/', '_self');
        } else {
            this.#email = getQueryParam('email');
            this.#code = getQueryParam('code');

            if (this.#email == null || this.#code == null) {
                UI.pushErrorNotification('Invalid URL: missing email and/or recovery code', NOTIFICATION_DURATION);
                setTimeout(() => window.open('/', '_self'), NOTIFICATION_DURATION);
            }

            this.#updatePasswordButton.addEventListener('click', () => {
                const isPasswordValid = UI.validatePassword(this.#passwordField);
                let validationErrors = [];

                if (!isPasswordValid) {
                    validationErrors.push(`Password must be between ${PASSWORD_MIN_LENGTH} and ${PASSWORD_MAX_LENGTH} characters`);
                }

                console.log('validationErrors: ' + validationErrors);

                if (validationErrors.length === 0) {
                    this.setValid(true);
                    this.#sendRecoveryRequest();
                } else {
                    this.setValid(false);
                    UI.showValidationErrors(validationErrors);
                }
            });
        }
    }

    /**
     * @param valid {boolean}
     */
    setValid(valid) {
        if (valid) {
            this.#passwordField.classList.remove('incorrect-data');
        } else {
            this.#passwordField.classList.add('incorrect-data');
        }
    }

    disable() {
        this.#passwordField.disabled = true;
        this.#updatePasswordButton.disabled = true;
        this.#updatePasswordButton.classList.add('app-buttons-disabled');
    }

    #sendRecoveryRequest() {
        const url = '/api/user/password/recovery/finalize';
        const body = {
            email: this.#email,
            code: this.#code,
            newPassword: this.#passwordField.value
        };
        const handler = new PasswordRecoveryResponseHandler(this);
        postAndHandleWith(url, body, handler);
    }

}

class PasswordRecoveryResponseHandler extends SimpleResponseHandler {

    /**
     * @type {PasswordRecoveryUpdatePasswordPage}
     */
    #page;

    constructor(page) {
        super();
        this.#page = page;
    }

    // 200
    handleOk() {
        this.#page.setValid(true);
        this.#page.disable();
        let message = 'Password has been successfully updated. You can now login with this new password.'
        UI.pushInfoNotification(message, NOTIFICATION_DURATION_SUCCESSFUL);
        setTimeout(() => window.open('/', '_self'), NOTIFICATION_DURATION_SUCCESSFUL);
    }

    // 401
    handleUnauthorized() {
        UI.pushErrorNotification('Error 401: Forbidden', 5000);
    }

    // 404
    handleNotFound() {
        this.#page.setValid(true);
        UI.pushErrorNotification('Recovery code not found: invalid email, invalid code or code already used', NOTIFICATION_DURATION);
    }

    // 406
    handleNotAcceptable() {
        this.#page.setValid(false);
        super.handleNotAcceptable(); // show error notification
    }

}

window.onload = () => new PasswordRecoveryUpdatePasswordPage();
