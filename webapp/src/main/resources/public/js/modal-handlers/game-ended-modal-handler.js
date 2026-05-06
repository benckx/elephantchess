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

class GameEndedModalHandler extends ModalHandler {

    /**
     * @param userRatingUpdate {UserRatingUpdate}
     */
    constructor(userRatingUpdate) {
        super();
        if (userRatingUpdate != null) {
            const ratingBox = document.getElementById('rating-update-box');
            const valueBox = document.getElementById('user-rating-value-box');
            const deltaBox = document.getElementById('user-rating-delta-value-box');

            valueBox.innerText = userRatingUpdate.ratingFrom;

            if (userRatingUpdate.isActuallyUpdated) {
                if (userRatingUpdate.delta > 0) {
                    deltaBox.classList.add('user-rating-delta-value-box-positive');
                    deltaBox.innerText = '+' + userRatingUpdate.delta.toString();
                } else if (userRatingUpdate.delta < 0) {
                    deltaBox.classList.add('user-rating-delta-value-box-negative');
                    deltaBox.innerText = userRatingUpdate.delta.toString();
                }
            } else {
                deltaBox.innerText = 'n/a';
            }

            ratingBox.style.display = 'block';
            deltaBox.style.visibility = 'visible';
        }

        // TODO: same for 'Enter' or 'Space' keys
        document
            .getElementById('ok-button')
            .addEventListener('click', () => UI.hideModal(null));
    }

}
