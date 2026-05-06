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

class JoinGameModalHandler extends ModalHandler {

    #opponentLabel = document.getElementById('join-game-opponent');
    #ratingModeLabel = document.getElementById('join-game-rating-mode');
    #ratingLabel = document.getElementById('join-game-rating');
    #colorLabel = document.getElementById('join-game-color');
    #timeControlLabel = document.getElementById('join-game-tc');
    #isOnlineIndicator = document.getElementById('join-game-status-indicator');

    #joinButton = document.getElementById('join-game-join-button');
    #declineButton = document.getElementById('join-game-decline-button');

    /**
     * @param dto {GameDto}
     * @param joinCb {function()}
     */
    constructor(dto, joinCb) {
        super();
        this.#opponentLabel.append(
            buildUsernameSpan(
                dto.inviterId,
                dto.inviterUsername,
                dto.inviterUserType
            )
        );

        if (dto.isRated) {
            this.#ratingModeLabel.innerText = 'Rated';
        } else {
            this.#ratingModeLabel.innerText = 'Casual';
        }

        this.#ratingLabel.innerText = dto.inviterRating.toString()

        this.#colorLabel.append(buildColorSpan(dto.inviterColor));

        if (dto.timeControl.increment != null) {
            this.#timeControlLabel.innerText = `${dto.timeControl.base.printShort()} +${dto.timeControl.increment.printShort()}`;
        } else {
            this.#timeControlLabel.innerText = dto.timeControl.base.printShort();
        }

        fetchAreOnline([dto.inviterId], areOnlineUserIds => {
            if (areOnlineUserIds.includes(dto.inviterId)) {
                this.#isOnlineIndicator.classList.add(IS_ONLINE_CSS_CLASS);
            } else {
                this.#isOnlineIndicator.classList.remove(IS_ONLINE_CSS_CLASS);
            }
        });

        this.#joinButton.onclick = () => {
            joinCb();
            UI.hideModal(null);
        };
        this.#declineButton.onclick = () => {
            UI.hideModal(null);
        };
    }

}
