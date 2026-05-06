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

class AttemptPasswordRecoveryPage extends BasePage {

    #emailField = document.getElementById('email-field');
    #button = document.getElementById('attempt-password-recovery-button');

    constructor() {
        super();
        if (isUserAuthenticated()) {
            // to home page
            window.open('/', '_self');
        } else {
            this.#button.addEventListener('click', () => {
                let isEmailValid = UI.validateEmail(this.#emailField);
                if (isEmailValid) {
                    this.#sendAttempt();
                } else {
                    UI.pushErrorNotification('Invalid email address', NOTIFICATION_DURATION);
                }
            });
        }
    }

    #sendAttempt() {
        const url = '/api/user/password/recovery/attempt';
        const email = this.#emailField.value;
        const body = {email: email};
        postAndHandle(url, body, () => {
            // show confirmation and redirect to home page
            let message = 'Password recovery attempt via ' + email + ' has been received. Check your inbox.'
            UI.pushInfoNotification(message, NOTIFICATION_DURATION_SUCCESSFUL);
            setTimeout(() => window.open('/', '_self'), NOTIFICATION_DURATION_SUCCESSFUL);

            // disable UI elements
            this.#button.disabled = true;
            this.#button.classList.add('app-buttons-disabled');
            this.#emailField.disabled = true;
        });
    }

}

window.onload = () => new AttemptPasswordRecoveryPage();
