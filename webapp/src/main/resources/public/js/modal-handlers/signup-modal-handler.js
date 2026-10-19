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

const SIGN_UP_VALIDATION_DEBOUNCE_DELAY_MS = 600;

class SignUpModalHandler extends ModalHandler {

    /**
     * @type {HTMLInputElement}
     */
    #usernameField = document.getElementById('signup-username-field');

    /**
     * @type {HTMLInputElement}
     */
    #emailField = document.getElementById('sign-up-email-field');

    /**
     * @type {HTMLInputElement}
     */
    #passwordField = document.getElementById('sign-up-password-field');

    /**
     * @type {HTMLButtonElement}
     */
    #signUpButton = document.getElementById('signup-button');

    /**
     * @type {HTMLSpanElement}
     */
    #loginSpan = document.getElementById('login-span');

    /**
     * @type {number|null}
     */
    #validationTimer = null;

    constructor() {
        super();
        this.#signUpButton.addEventListener('click', () => {
            const isCaptchaValid = grecaptcha.getResponse() !== '';

            if (isCaptchaValid) {
                this.#sendSignUpRequest();
            } else {
                UI.showValidationErrors(['reCAPTCHA is invalid']);
            }
        });

        this.#usernameField.addEventListener('input', () => this.#scheduleValidation());
        this.#emailField.addEventListener('input', () => this.#scheduleValidation());
        this.#passwordField.addEventListener('input', () => this.#scheduleValidation());

        this.#usernameField.focus();

        const captchaContainer = document.getElementById('captcha-container');
        grecaptcha.ready(function () {
            grecaptcha.render(captchaContainer, {sitekey: RE_CAPTCHA_SITE_KEY});
        });

        this.#loginSpan.addEventListener('click', () => showLoginModal());
    }

    #scheduleValidation() {
        if (this.#validationTimer !== null) {
            clearTimeout(this.#validationTimer);
        }

        this.#validationTimer = setTimeout(() => {
            this.#callValidation();
        }, SIGN_UP_VALIDATION_DEBOUNCE_DELAY_MS);
    }

    #callValidation() {
        const username = this.#usernameField.value;
        const email = this.#emailField.value;
        const password = this.#passwordField.value;

        const body = {
            'username': username,
            'email': email,
            'password': password
        };

        const handler = new ValidationResponseHandler(
            () => {
                // valid response (no errors)
                this.#updateAllFieldsValidation([]);
            },
            (errors) => {
                // validation errors
                this.#updateAllFieldsValidation(errors);
            }
        );

        postAndHandleWith('/api/validate-signup', body, handler);
    }

    #sendSignUpRequest() {
        const username = document.getElementById('signup-username-field').value;
        const email = document.getElementById('sign-up-email-field').value;
        const password = document.getElementById('sign-up-password-field').value;

        const body = {
            'username': username,
            'email': email,
            'password': password
        };

        const transferCheckbox = document.getElementById('signup-transfer-games-checkbox');
        if (isUserIdentifiedAsGuest() && transferCheckbox && transferCheckbox.checked) {
            body['transferGuestData'] = true;
        }

        const handler = new ValidationResponseHandler(
            (json) => {
                eraseAllIdentificationCookies();
                setAuthenticationCookies(json);
                UI.hideModal(null);
                gtagReportSignUpConversion(window.location.href);
                window.location.reload();
            },
            (errors) => {
                this.#updateAllFieldsValidation(errors);
            }
        );

        postAndHandleWith('/api/signup', body, handler);
    }

    /**
     * @param {string[]} errors
     */
    #updateAllFieldsValidation(errors) {
        /**
         * @param {string} fieldName
         * @param {string[]} errors
         * @param {HTMLInputElement} inputField
         */
        function updateFieldValidation(fieldName, errors, inputField) {
            const labelRow = inputField.parentElement.querySelector('.signup-label-row');
            const validIcon = labelRow.querySelector('.signup-valid-icon');
            const invalidIcon = labelRow.querySelector('.signup-invalid-icon');
            const validText = labelRow.querySelector('.signup-valid-text');
            const invalidText = labelRow.querySelector('.signup-invalid-text');

            const fieldError = errors.find(e => e.toLowerCase().includes(fieldName.toLowerCase()));

            if (fieldError) {
                // show invalid state
                validIcon.style.display = 'none';
                invalidIcon.style.display = 'inline';
                validText.style.display = 'none';
                invalidText.style.display = 'inline';
                invalidText.textContent = fieldError;
                inputField.classList.add('incorrect-data');
            } else {
                // show valid state
                validIcon.style.display = 'inline';
                invalidIcon.style.display = 'none';
                validText.style.display = 'inline';
                validText.textContent = 'OK';
                invalidText.style.display = 'none';
                inputField.classList.remove('incorrect-data');
            }
        }

        updateFieldValidation('username', errors, this.#usernameField);
        updateFieldValidation('email', errors, this.#emailField);
        updateFieldValidation('password', errors, this.#passwordField);
    }

}
