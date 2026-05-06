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

const MESSAGE_MIN_CHARS = 10;
const MESSAGE_MAX_CHARS = 1000;

class ContactPage extends BasePage {

    #emailAddressField = document.getElementById('email-address');
    #textArea = document.getElementById('contact-form-textarea');
    #sendButton = document.getElementById('send-button');

    constructor() {
        super();

        // captcha
        const captchaContainer = document.getElementById('captcha-container');
        grecaptcha.ready(function () {
            grecaptcha.render(captchaContainer, {sitekey: RE_CAPTCHA_SITE_KEY});
        });

        this.#sendButton.addEventListener('click', () => {
            const isEmailValid = UI.validateEmail(this.#emailAddressField);
            const isMessageValid = this.#validateMessage();
            const isCaptchaValid = grecaptcha.getResponse() !== '';

            const validationErrors = [];
            if (!isEmailValid) {
                validationErrors.push('e-mail address is not properly formatted');
            }
            if (!isMessageValid) {
                validationErrors.push(`message must be between ${MESSAGE_MIN_CHARS} and ${MESSAGE_MAX_CHARS} characters`);
            }
            if (!isCaptchaValid) {
                validationErrors.push('reCAPTCHA is invalid')
            }

            if (validationErrors.length === 0) {
                const body = {
                    email: this.#emailAddressField.value,
                    message: this.#textArea.value,
                }

                postAndHandle('/api/contact/form/submit', body, () => {
                    this.#sendButton.disabled = true;
                    this.#sendButton.classList.add('app-buttons-disabled');
                    this.#emailAddressField.disabled = true;
                    this.#textArea.disabled = true;

                    const timeout = 5_000;
                    UI.pushInfoNotification('Message has been sent to us!', timeout);
                    setInterval(() => window.location.href = '/', timeout);
                });
            } else {
                UI.showValidationErrors(validationErrors);
            }
        });
    }

    #validateMessage() {
        const value = this.#textArea.value;
        let isValid = value.length >= MESSAGE_MIN_CHARS && value.length <= MESSAGE_MAX_CHARS;
        if (isValid) {
            this.#textArea.classList.remove('incorrect-data');
        } else {
            this.#textArea.classList.add('incorrect-data');
        }
        return isValid;
    }

}

window.onload = () => new ContactPage();
