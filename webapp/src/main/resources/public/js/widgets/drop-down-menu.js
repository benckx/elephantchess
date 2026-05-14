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

class DropDownMenu {

    #disabledClass = 'drop-down-menu-item-disabled';
    #container = document.createElement('div');
    #items = [];

    constructor(id) {
        this.#container.id = id;
        this.#container.className = 'drop-down-menu-container';
        this.hide();
    }

    /**
     * @return {HTMLDivElement}
     */
    get menuContainer() {
        return this.#container;
    }

    isVisible() {
        return this.#container.style.display !== 'none';
    }

    show() {
        this.#container.style.display = 'block';
        UI.hideUserTurnToPlayWidgetOnEvent(); // feels like a hack
    }

    hide() {
        this.#container.style.display = 'none';
    }

    toggle() {
        if (this.isVisible()) {
            this.hide();
        } else {
            this.show();
        }
    }

    /**
     * @param label {string}
     * @param optionalCssClasses {string[]}
     * @param cb {function}
     * @return {HTMLDivElement}
     */
    addItem(label, optionalCssClasses, cb) {
        let item = document.createElement('div');

        let defaultCssClasses = this.defaultCssClasses();
        for (let i = 0; i < defaultCssClasses.length; i++) {
            item.classList.add(defaultCssClasses[i]);
        }
        for (let i = 0; i < optionalCssClasses.length; i++) {
            item.classList.add(optionalCssClasses[i]);
        }
        item.innerText = label;
        item.addEventListener('click', (e) => {
            if (this.isEnabled(item)) {
                this.hide();
                cb(e);
            }
        });

        this.#container.append(item);
        this.#items.push(item);
        return item;
    }

    /**
     * @return {HTMLDivElement}
     */
    addSimpleItem(label, cb) {
        return this.addItem(label, [], cb);
    }

    /**
     * @param label {string}
     * @param optionalCssClasses {string[]}
     * @param href {string}
     * @return {HTMLAnchorElement}
     */
    addItemWithHref(label, optionalCssClasses, href) {
        let item = document.createElement('a');
        item.href = href;

        let defaultCssClasses = this.defaultCssClasses();
        for (let i = 0; i < defaultCssClasses.length; i++) {
            item.classList.add(defaultCssClasses[i]);
        }
        for (let i = 0; i < optionalCssClasses.length; i++) {
            item.classList.add(optionalCssClasses[i]);
        }
        item.classList.add('drop-down-menu-item-link');
        item.innerText = label;
        item.addEventListener('click', (e) => {
            if (this.isEnabled(item)) {
                this.hide();
            } else {
                e.preventDefault();
            }
        });

        this.#container.append(item);
        this.#items.push(item);
        return item;
    }

    /**
     * @param label {string}
     * @param href {string}
     * @return {HTMLAnchorElement}
     */
    addSimpleItemWithHref(label, href) {
        return this.addItemWithHref(label, [], href);
    }

    /**
     * @param label {string}
     * @param cb {function}
     * @return {HTMLDivElement}
     */
    addItemWithTopSeparator(label, cb) {
        return this.addItem(label, ['drop-down-menu-item-top-separator'], cb);
    }

    /**
     * @param label {string}
     * @param href {string}
     * @return {HTMLAnchorElement}
     */
    addItemWithTopSeparatorAndHref(label, href) {
        return this.addItemWithHref(label, ['drop-down-menu-item-top-separator'], href);
    }

    defaultCssClasses() {
        return ['drop-down-menu-item'];
    }

    isEnabled(item) {
        return !item.classList.contains(this.#disabledClass);
    }

    enableItem(item) {
        item.classList.remove(this.#disabledClass);
    }

    disableItem(item) {
        item.classList.add(this.#disabledClass);
    }

    disableAllItems() {
        for (let item of this.#items) {
            this.disableItem(item);
        }
    }

}

class ContextualMenu extends DropDownMenu {

    constructor(id) {
        super(id);
    }

    showAt(x, y) {
        // console.log('showing at ' + x + ', ' + y);
        this.menuContainer.style.left = x + 'px';
        this.menuContainer.style.top = y + 'px';
        this.show();
    }

}

class UserNameDropDownMenu extends DropDownMenu {

    /**
     * @param user {User}
     */
    constructor(user) {
        super('username-drop-down-menu');

        switch (user.userType) {
            case UserType.AUTHENTICATED:
                this.addSimpleItemWithHref('Profile', '/@/' + user.username);
                this.addSimpleItemWithHref('Settings', '/user/settings');
                this.addItemWithTopSeparatorAndHref('My Games', '/userdata/games');
                this.addSimpleItemWithHref('My Bot Games', '/userdata/botgames');
                this.addSimpleItemWithHref('My Puzzles', '/userdata/puzzles');
                this.addSimpleItemWithHref('My Analysis', '/userdata/analysis');
                this.addSimpleItemWithHref('My DB Searches', '/userdata/db-searches');
                if (user.isEditor) {
                    this.addItemWithTopSeparatorAndHref('My Edits', '/userdata/db-edits');
                }
                if (user.isAdmin) {
                    this.addItemWithTopSeparatorAndHref('Admin', '/admin');
                }
                this.addItem('Logout', ['drop-down-menu-item-top-separator', 'drop-down-menu-item-logout'], () => {
                    eraseAllIdentificationCookies();
                    window.open('/', '_self');
                });
                break;
            case UserType.GUEST:
                this.addSimpleItemWithHref('My Games', '/userdata/games');
                this.addSimpleItemWithHref('My Bot Games', '/userdata/botgames');
                this.addSimpleItemWithHref('My Puzzles', '/userdata/puzzles');
                this.addItem('Login', ['drop-down-menu-item-top-separator', 'drop-down-menu-item-authenticate'], () => {
                    showLoginModal();
                });
                this.addItem('Sign Up', ['drop-down-menu-item-authenticate'], () => {
                    showSignUpModal();
                });
                break;
        }
    }

    defaultCssClasses() {
        let parent = super.defaultCssClasses();
        parent.push('username-drop-down-menu-item');
        return parent;
    }

}

// TODO: we could probably get rid of eventSourceIds by using event.stopPropagation() properly
class DropDownMenuManager {

    static #instance = null;

    /**
     * @return {DropDownMenuManager}
     */
    static getInstance() {
        if (DropDownMenuManager.#instance == null) {
            DropDownMenuManager.#instance = new DropDownMenuManager();
        }
        return DropDownMenuManager.#instance;
    }

    /**
     * @param menu {DropDownMenu}
     * @param eventSourceIds {string[]} ids of elements that should not hide the menu when processing a 'click' event
     * @param parentId {string|null}
     */
    registerDropDownMenu(menu, eventSourceIds = null, parentId = null) {
        const containerId = menu.menuContainer.id;
        this.#removeIfExists(containerId);
        this.#appendTo(menu, parentId);

        document.addEventListener('click', (e) => {
            this.#hideOnEvent(menu, e, eventSourceIds);
        });

        window.addEventListener('resize', () => menu.hide());
        window.addEventListener('scroll', () => menu.hide());
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                menu.hide();
            }
        });
    }

    /**
     * @param menu {DropDownMenu}
     * @param mouseEvent {MouseEvent|null}
     * @param eventSourceIds {string[]}
     */
    #hideOnEvent(menu, mouseEvent, eventSourceIds) {
        if (mouseEvent == null) {
            menu.hide();
        } else {
            if (!eventSourceIds.includes(mouseEvent.target.id)) {
                menu.hide()
            }
        }
    }

    #removeIfExists(id) {
        let elem = document.getElementById(id);
        if (elem != null) {
            elem.parentNode.removeChild(elem);
        }
    }

    /**
     * @param menu {DropDownMenu}
     * @param parentId {string|null}
     */
    #appendTo(menu, parentId) {
        let parent;
        if (parentId != null) {
            parent = document.getElementById(parentId);
        } else {
            parent = document.getElementsByTagName('body')[0];
        }
        parent.append(menu.menuContainer);
    }

}
