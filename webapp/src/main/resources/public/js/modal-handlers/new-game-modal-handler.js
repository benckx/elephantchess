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

const OPTION_BUTTON_CLASS = 'option-button-div'
const OPTION_BUTTON_SELECTED_CLASS = 'option-button-div-selected'
const COLOR_OPTION_GROUP = 'color-option-button-div'
const TIME_CONTROL_OPTION_GROUP = 'tc-option-button-div'
const SECONDS_IN_ONE_DAY = 86_400;

class NewGameHandler extends ModalHandler {

    /**
     * Only one option can be selected in each group.
     *
     * @type {string[]}
     */
    #exclusionGroups = [COLOR_OPTION_GROUP, TIME_CONTROL_OPTION_GROUP];

    /**
     * @type {Element[]}
     */
    #optionsDivs = getElementsByClassNameArray(OPTION_BUTTON_CLASS);
    #isRatedCheckbox = document.getElementById('is-rated');
    #alwaysVisibleInLobbyCheckBox = document.getElementById('always-visible-in-lobby');
    #allowGuestsCheckBox = document.getElementById('allow-guests');
    #privateInvite = document.getElementById('private-invite');

    #newGameButton = document.getElementById('create-new-game-button-modal');

    constructor() {
        super();

        const correspondenceTimeControls = ['tc-86400', 'tc-259200', 'tc-604800'];

        // option div click listener
        this.#optionsDivs.forEach(item => {
            item.addEventListener('click', () => {
                const isDisabled = item.classList.contains('option-button-div-disabled');
                if (isDisabled) {
                    return;
                }

                const itemExclusionGroup = this.#findExclusionGroup(item);

                // select this element and un-select all other elements in the same group
                if (itemExclusionGroup !== null) {
                    this.#unselectedForGroup(itemExclusionGroup);
                    item.classList.add(OPTION_BUTTON_SELECTED_CLASS);
                }

                // alwaysVisibleInLobby checked when correspondence time control is selected
                // alwaysVisibleInLobby unchecked otherwise
                if (item.classList.contains(TIME_CONTROL_OPTION_GROUP)) {
                    this.#alwaysVisibleInLobbyCheckBox.checked = correspondenceTimeControls.includes(item.id);
                }
            });
        });

        makeCheckboxesClickable();

        // private game checkbox listener
        this.#privateInvite.addEventListener('change', () => {
            if (this.#privateInvite.checked) {
                // When private game is checked, uncheck and disable "always visible in lobby"
                this.#alwaysVisibleInLobbyCheckBox.checked = false;
                this.#disableCheckbox('always-visible-in-lobby-container');
            } else {
                // When private game is unchecked, enable "always visible in lobby"
                this.#enableCheckbox('always-visible-in-lobby-container');
            }
        });

        // button click listener
        this.#newGameButton.addEventListener('click', () => {
            this.#handleClickCreateNewGame();
        });

        // if authenticated, enable the "allow guests" checkbox
        if (isUserAuthenticated()) {
            this.#enableCheckbox('allow-guests-container');

            correspondenceTimeControls.forEach(tcId => {
                document.getElementById(tcId).classList.remove('option-button-div-disabled');
            });
        } else {
            correspondenceTimeControls.forEach(tcId => {
                addToolTip(document.getElementById(tcId), 'This option is not available for guest users.');
            });
        }

        addToolTip(
            document.getElementById('is-rated-container'),
            'If playing "Rated" (instead of "Casual"), your rating will be affected by the outcome of the game.'
        );

        addToolTip(
            document.getElementById('allow-guests-container'),
            'Let guest users (i.e. users without a registered account) join this game. ' +
            'This option is not available for guest users.'
        );

        addToolTip(
            document.getElementById('always-visible-in-lobby-container'),
            'If checked, the game will be visible to other players in the lobby even when you are offline. ' +
            'Not recommended for shorter games.'
        );

        addToolTip(
            document.getElementById('is-private-container'),
            'Private games are not listed in the lobby and can only be joined by players with the direct link.'
        );
    }

    /**
     * Disable a checkbox by its container ID
     * @param containerId {string}
     */
    #disableCheckbox(containerId) {
        const container = document.getElementById(containerId);
        container.classList.add('standard-checkbox-disabled');
        // Find the checkbox inside the container
        const checkbox = container.querySelector('input[type="checkbox"]');
        if (checkbox) {
            checkbox.disabled = true;
        }
    }

    /**
     * Enable a checkbox by its container ID
     * @param containerId {string}
     */
    #enableCheckbox(containerId) {
        const container = document.getElementById(containerId);
        container.classList.remove('standard-checkbox-disabled');
        // Find the checkbox inside the container
        const checkbox = container.querySelector('input[type="checkbox"]');
        if (checkbox) {
            checkbox.disabled = false;
        }
    }

    /**
     * @param item {Element}
     * @return {string|null}
     */
    #findExclusionGroup(item) {
        return this
            .#exclusionGroups
            .find(group => item.classList.contains(group));
    }

    /**
     * @param group {string}
     */
    #unselectedForGroup(group) {
        this.#optionsDivs
            .filter(item => item.classList.contains(group))
            .forEach(item => item.classList.remove(OPTION_BUTTON_SELECTED_CLASS));
    }

    /**
     * @return {TimeControl}
     */
    #getSelectedTimeControl() {
        let timeControlId = null;
        this.#optionsDivs
            .filter(item => item.classList.contains(OPTION_BUTTON_SELECTED_CLASS))
            .filter(item => item.classList.contains(TIME_CONTROL_OPTION_GROUP))
            .forEach(item => timeControlId = item.id);

        return TimeControl.fromTcFormatId(timeControlId);
    }

    /**
     * @return {string|null}
     */
    #getSelectedColor() {
        let colorId = null;
        this.#optionsDivs
            .filter(item => item.classList.contains(OPTION_BUTTON_SELECTED_CLASS))
            .filter(item => item.classList.contains(COLOR_OPTION_GROUP))
            .forEach(item => colorId = item.id);

        if (colorId === null) {
            throw new Error('No color selected');
        } else {
            switch (colorId) {
                case 'option-red':
                    return Color.RED;
                case 'option-black':
                    return Color.BLACK;
                default:
                    return null;
            }
        }
    }

    #handleClickCreateNewGame() {
        const color = this.#getSelectedColor();
        const isRated = this.#isRatedCheckbox.checked;
        const timeControl = this.#getSelectedTimeControl();
        const base = timeControl.base.toSeconds();

        let increment = null;
        if (timeControl.increment != null) {
            increment = timeControl.increment.toSeconds();
        }

        let timeControlMode = TimeControlMode.GAME_TIME;
        if (base >= SECONDS_IN_ONE_DAY) {
            timeControlMode = TimeControlMode.MOVE_TIME;
        }

        const allowGuests = this.#allowGuestsCheckBox.checked;
        const alwaysVisibleInLobby = this.#alwaysVisibleInLobbyCheckBox.checked;
        const privateInvite = this.#privateInvite.checked;

        const request = {
            'inviterColor': color,
            'isRated': isRated,
            'timeControlBase': base,
            'timeControlIncrement': increment,
            'timeControlMode': timeControlMode,
            'allowGuests': allowGuests,
            'alwaysVisibleInLobby': alwaysVisibleInLobby,
            'privateInvite': privateInvite
        };

        // TODO: also check color that comes from the response and event type
        postAndHandle('/api/game/create', request, (json) => {
            let url = '/game?id=' + json.gameId;
            if (color != null) {
                url = url + '&color=' + color;
            }
            window.open(url, '_self');
        });
    }

}
