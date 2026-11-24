/*
 * Copyright (C) 2023-2026 Encelade SRL
 * Copyright (C) 2023-2026 elephantchess.io
 * Copyright (C) 2023-2026 Benoît Vleminckx (benckx)
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

class DeleteAccountPage extends BasePage {

    #checkbox = document.getElementById('delete-account-confirm-checkbox');
    #deleteButton = document.getElementById('delete-account-button');
    #cancelButton = document.getElementById('delete-account-cancel-button');
    #authWarningContainer = document.getElementById('delete-account-auth-warning');

    constructor() {
        super();
        const isAuthenticated = isUserAuthenticated();
        this.#checkbox.disabled = !isAuthenticated;
        if (isAuthenticated) {
            this.#checkbox.addEventListener('change', () => this.#refreshDeleteButton());
        } else {
            this.#authWarningContainer.classList.remove('hidden');
        }
        this.#deleteButton.addEventListener('click', () => this.#deleteAccount());
        this.#cancelButton.addEventListener('click', () => window.location.href = '/');
        this.#refreshDeleteButton();
    }

    #refreshDeleteButton() {
        this.#deleteButton.disabled = !this.#checkbox.checked;
    }

    #deleteAccount() {
        this.#deleteButton.disabled = true;
        postAndHandle('/api/user/settings/account/delete', null, () => {
            // the account is gone server-side: drop every identification cookie and leave the
            // authenticated area so the user is fully logged out.
            eraseAllIdentificationCookies();
            window.location.href = '/';
        });
    }

}

window.onload = () => new DeleteAccountPage();
