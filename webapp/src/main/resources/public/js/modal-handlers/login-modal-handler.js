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

class LoginResponseHandler extends SimpleResponseHandler {

    #invalidCb;

    constructor(cb, invalidCb) {
        super(cb);
        this.#invalidCb = invalidCb;
    }

    handleNotFound() {
        this.#invalidCb();
    }

    handleUnauthorized() {
        this.#invalidCb();
    }

}

class LoginModalHandler extends ModalHandler {

    #loginField = document.getElementById('login-field');
    #passwordField = document.getElementById('password-field');
    #loginOkButton = document.getElementById('login-ok-button');
    #signUpSpan = document.getElementById('signup-span');

    constructor() {
        super();
        this.#loginField.classList.remove('incorrect-data');
        this.#loginField.value = '';

        this.#passwordField.classList.remove('incorrect-data');
        this.#passwordField.value = '';

        this.#loginOkButton.addEventListener('click', () => this.#attemptLogin());
        this.#loginField.focus();

        this.#signUpSpan.addEventListener('click', () => showSignUpModal());
    }

    #attemptLogin() {
        if (this.#loginField.value.trim() === '') {
            this.#loginField.classList.add('incorrect-data');
            this.#passwordField.classList.add('incorrect-data');
            UI.pushErrorNotification('Please input login');
        } else if (this.#passwordField.value.trim() === '') {
            this.#loginField.classList.add('incorrect-data');
            this.#passwordField.classList.add('incorrect-data');
            UI.pushErrorNotification('Please input password');
        } else {
            const responseHandler =
                new LoginResponseHandler(
                    (json) => {
                        this.#loginField.classList.remove('incorrect-data');
                        this.#passwordField.classList.remove('incorrect-data');
                        eraseAllIdentificationCookies();
                        setAuthenticationCookies(json);
                        UI.hideModal(null);
                        window.location.reload();
                    },
                    () => {
                        eraseAllIdentificationCookies();
                        this.#loginField.classList.add('incorrect-data');
                        this.#passwordField.classList.add('incorrect-data');
                        UI.pushErrorNotification('Invalid credentials');
                    }
                );

            const body = {
                'login': this.#loginField.value,
                'password': this.#passwordField.value
            }

            postAndHandleWith('/api/login', body, responseHandler);
        }
    }

}
