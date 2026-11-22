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

class GuestPieceStyleModalHandler extends ModalHandler {

    constructor() {
        super();
        const modal = document.getElementById('guest-piece-style-modal');
        modal.querySelector('#guest-piece-style-user').textContent = new User().toString();
        getAndHandle('/api/token-expiration-date', (json) => {
            modal.querySelector('#guest-piece-style-expiration').textContent =
                formatTimestampDefaultDateFormat(Number(json.expiration));
        });
        modal.querySelectorAll('[data-piece-style]').forEach(button => {
            button.addEventListener('click', () => {
                const pieceStyle = button.dataset.pieceStyle;
                new SettingsManager().pieceStyle = pieceStyle;
                window.dispatchEvent(new CustomEvent('piece-style-selected', {detail: pieceStyle}));
                UI.hideModal(null);
            });
        });
    }

}
