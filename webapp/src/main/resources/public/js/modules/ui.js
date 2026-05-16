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

const ICON_PATH = '/images/icons';
const RE_CAPTCHA_SITE_KEY = '6LcA3vgkAAAAAID0oAscfvbOYwlZMdxXikfkrEi7';
const PASSWORD_MIN_LENGTH = 4;
const PASSWORD_MAX_LENGTH = 50;
const DEFAULT_NOTIFICATION_TIMEOUT = 2_000;
const VALIDATION_NOTIFICATION_TIMEOUT = 5_000;

// TODO: make a little function out of this
const IS_ONLINE_CSS_CLASS = 'online-status-indicator-is-online';

const WINNER_BLUE_STAR_HTML =
    '<img alt="winner" class="winner-icon" src="/images/icons/blue-star-small.png">';

const Modals = Object.freeze({
    CONFIRMATION: 'confirmation',
    GAME_CANCELED: 'game-canceled',
    GAME_LOSS: 'game-loss',
    GAME_WIN: 'game-win',
    IMPORT_MOVES: 'import-moves',
    LOGIN: 'login',
    NEW_GAME: 'new-game',
    OPPONENT_ACCEPTED_DRAW: 'opponent-accepted-draw',
    OPPONENT_DECLINED_DRAW: 'opponent-declined-draw',
    OPPONENT_RESIGNED: 'opponent-resigned',
    PLAY_BOT: 'play-bot',
    POSITION_EDITOR: 'position-editor',
    SIGN_UP: 'signup',
    JOIN_GAME_CONFIRMATION: 'join-game-confirmation'
});

let notificationTimeoutId = null;

window.mobileCheck = function () {
    let check = false;
    (function (a) {
        if (/(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino/i.test(a) || /1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i.test(a.substr(0, 4))) check = true;
    })(navigator.userAgent || navigator.vendor || window.opera);
    return check;
};

let isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);

const modalCache = new Map();

/**
 * @param element {HTMLElement}
 * @param className {string|null}
 * @return {HTMLDivElement}
 */
function wrapInDiv(element, className = null) {
    const div = document.createElement('div');
    div.append(element);
    if (className != null) {
        div.className = className;
    }
    return div;
}

/**
 * @param element {HTMLElement}
 * @return {HTMLDivElement}
 */
function wrapInBold(element) {
    const b = document.createElement('b');
    b.append(element);
    return b;
}

/**
 * @param url {string}
 * @param label {string}
 * @param target {string|null}
 * @returns {HTMLAnchorElement}
 */
function buildLink(url, label, target = null) {
    const a = document.createElement('a');
    a.href = url;
    a.innerText = label;
    if (target != null) {
        a.target = target;
    }
    return a;
}

/**
 * @param url {string}
 * @param label {string|null}
 * @param className {string}
 * @returns {HTMLAnchorElement}
 */
function buildAnchorWithClass(url, label, className) {
    const a = document.createElement('a');
    a.href = url;
    a.className = className;
    if (label != null) {
        a.innerText = label;
    }
    return a;
}

/**
 * @param text {string}
 * @returns {HTMLSpanElement}
 */
function buildSimpleSpan(text) {
    const spanElement = document.createElement('span');
    spanElement.innerText = text;
    return spanElement;
}

/**
 * @param src {string}
 * @param className {string|null}
 * @returns {HTMLImageElement}
 */
function buildImg(src, className = null) {
    const img = document.createElement('img');
    img.src = src;
    if (className != null) {
        img.className = className;
    }
    return img;
}

/**
 * @param text {string}
 * @returns {HTMLDivElement}
 */
function buildSimpleTextDiv(text) {
    return buildDivWithTextAndClasses(text, []);
}

/**
 * @param className {string}
 * @returns {HTMLDivElement}
 */
function buildDivWithClass(className) {
    const div = document.createElement('div');
    div.className = className;
    return div;
}

/**
 * @param classNames {string[]}
 * @param textContent {string}
 * @returns {HTMLDivElement}
 */
function buildDivWithTextAndClasses(textContent, classNames) {
    const div = document.createElement('div');
    div.innerText = textContent;
    classNames.forEach(className => div.classList.add(className));
    return div;
}

/**
 * @param className {string}
 * @param textContent {string}
 * @returns {HTMLDivElement}
 */
function buildDivWithTextAndClass(textContent, className) {
    return buildDivWithTextAndClasses(textContent, [className]);
}

/**
 *
 * @param username {string}
 * @param maxLength {number|null}
 * @return {HTMLAnchorElement}
 */
function buildUserLinkAnchor(username, maxLength = null) {
    const a = document.createElement('a');
    a.href = '/@/' + username;
    if (maxLength != null) {
        a.innerText = cropText(username, maxLength);
    } else {
        a.innerText = username;
    }
    return a;
}

/**
 * @param username {string}
 * @param maxLength {number|null}
 * @return {HTMLDivElement}
 */
function buildUserLinkDiv(username, maxLength = null) {
    const div = document.createElement('div');
    div.append(buildUserLinkAnchor(username, maxLength));
    return div;
}

/**
 * @param id {string}
 * @param maxLength {number|null}
 * @returns {HTMLSpanElement}
 */
function buildGuestUserSpan(id, maxLength = null) {
    const span = document.createElement('span');
    span.className = 'guest-name';
    const guestName = `guest #${id}`;
    if (maxLength != null) {
        span.innerText = cropText(guestName, maxLength);
    } else {
        span.innerText = guestName;
    }
    return span;
}

/**
 * Whether it's a guest or an authenticated user
 *
 * @param userId {string|null}
 * @param username {string|null}
 * @param userType {string}
 * @param maxLength {number|null}
 * @returns {HTMLElement}
 */
function buildUsernameSpan(userId, username, userType, maxLength = null) {
    switch (userType) {
        case UserType.AUTHENTICATED:
            if (username != null) {
                return buildUserLinkAnchor(username, maxLength);
            }
            break;
        case UserType.GUEST:
            if (userId != null) {
                return buildGuestUserSpan(userId, maxLength);
            }
            break;
    }

    console.warn('can not render username span: ' + userId + ', ' + username + ', ' + userType);
    const span = document.createElement('span');
    span.innerText = '??';
    return span;
}

/**
 * @param color {string|null}
 * @return {HTMLSpanElement}
 */
function buildColorSpan(color) {
    const colorSpan = document.createElement('span');
    switch (color) {
        case Color.RED:
            colorSpan.className = 'red-color';
            colorSpan.innerText = 'Red';
            break;
        case Color.BLACK:
            colorSpan.className = 'black-color';
            colorSpan.innerText = 'Black';
            break;
        default:
            colorSpan.className = 'any-color';
            colorSpan.innerText = 'Any';
            break;
    }
    return colorSpan;
}

/**
 * Info box large buttons
 */
function setInfoBoxButtonEnabled(element, value) {
    if (value) {
        element.classList.add('enabled-button');
        element.classList.remove('disabled-button');
    } else {
        element.classList.add('disabled-button');
        element.classList.remove('enabled-button');
    }
}

/**
 * @param {MouseEvent} e
 * @returns {boolean}
 */
function isInfoBoxButtonEnabled(e) {
    // noinspection JSUnresolvedReference
    return e.target.classList.contains('enabled-button') || // tr
        e.target.parentNode.classList.contains('enabled-button') || // td
        e.target.parentNode.parentNode.classList.contains('enabled-button'); // img
}

/**
 * @param {MouseEvent} e
 * @returns {boolean}
 */
function isAppButtonEnabled(e) {
    return !e.target.classList.contains('app-buttons-disabled');
}

/**
 * @param element {HTMLInputElement}
 */
function enableAppButton(element) {
    element.classList.remove('app-buttons-disabled');
}

/**
 * @param element {HTMLInputElement}
 */
function disableAppButton(element) {
    element.classList.add('app-buttons-disabled');
}

/**
 * @param element {HTMLElement}
 */
function enableDivButton(element) {
    element.classList.remove('div-button-disabled');
    element.classList.add('div-button-enabled');
}

/**
 * @param element {HTMLElement}
 */
function disableDivButton(element) {
    element.classList.remove('div-button-enabled');
    element.classList.add('div-button-disabled');
}

/**
 * @param element {HTMLElement}
 * @return {boolean}
 */
function isDivButtonEnabled(element) {
    return element.classList.contains('div-button-enabled') &&
        !element.classList.contains('div-button-disabled');
}

/**
 * @param circleElement {HTMLElement}
 * @param counterElement {HTMLElement}
 * @param length {number}
 */
function formatNotificationCircleCounter(circleElement, counterElement, length) {
    if (length === 0) {
        counterElement.innerText = '';
        circleElement.style.visibility = 'hidden';
    } else if (length > 0 && length <= 9) {
        counterElement.innerText = length.toString();
        circleElement.style.visibility = 'visible';
    } else if (length > 9) {
        counterElement.innerText = '9+';
        circleElement.style.visibility = 'visible';
    }
}

function showGuestMessages() {
    if (isUserIdentifiedAsGuest()) {
        getElementsByClassNameArray('only-guest-hidden-by-default')
            .forEach(element => {
                element.style.display = 'block';
            });

        const bannerGuestId = document.getElementById('banner-guest-id');
        if (bannerGuestId != null) {
            bannerGuestId.innerText = getCookie(GUEST_USER_ID_FIELD);
        }

        const bannerExpirationDate = document.getElementById('banner-guest-expiration-date');
        if (bannerExpirationDate != null) {
            getAndHandle('/api/token-expiration-date', (json) => {
                const expirationMs = Number(json.expiration);
                bannerExpirationDate.innerText = formatTimestampDefaultDateFormat(expirationMs);
            });
        }
    }
}

function makeCheckboxesClickable() {
    makeFormElementClickable('standard-checkbox');
}

function makeRadioClickable() {
    makeFormElementClickable('standard-radio');
}

function makeFormElementClickable(cssClass) {
    const boxes = document.getElementsByClassName(cssClass);
    for (let i = 0; i < boxes.length; i++) {
        const box = boxes[i];
        const spans = box.getElementsByTagName('span');
        if (spans.length === 2) {
            const checkboxInput = spans[0].getElementsByTagName('input')[0];
            const labelSpan = spans[1];
            const label = labelSpan.getElementsByTagName('label')[0];
            labelSpan.addEventListener('click', () => {
                checkboxInput.checked = !checkboxInput.checked;
            });
            label.addEventListener('click', () => {
                checkboxInput.checked = !checkboxInput.checked;
            });
        }
    }
}

/**
 * Relative time in the text and fully formatted time in the tooltip
 *
 * @param element {HTMLElement}
 * @param timestamp {number}
 */
function setRelativeTimeAndToolTip(element, timestamp) {
    element.innerText = formatTimestampToRelativeTime(timestamp);
    addTimeToolTip(element, timestamp);
}

/**
 * Relative time in the text and shorthand formatted time in the tooltip
 *
 * @param element {HTMLElement}
 * @param timestamp {number}
 */
function setRelativeTimeShorthandAndToolTip(element, timestamp) {
    element.innerText = formatTimestampToRelativeTimeShorthand(timestamp);
    addTimeToolTip(element, timestamp);
}

/**
 * Relative time compared to another timestamp (e.g. 2 months later) in the text and fully formatted time in the tooltip
 *
 * @param element {HTMLElement}
 * @param initialTimestamp {number}
 * @param timestamp {number}
 */
function setRelativeToInitialTimeAndToolTip(element, initialTimestamp, timestamp) {
    element.innerText = millisToRelativeTime(timestamp - initialTimestamp, 'later');
    addTimeToolTip(element, timestamp);
}

/**
 * Tooltip with the fully formatted time
 *
 * @param element {HTMLElement}
 * @param timestamp {number}
 */
function addTimeToolTip(element, timestamp) {
    addToolTip(element, formatTimestampDefaultDateFormat(timestamp));
}

/**
 * @param element {HTMLElement}
 * @param maxChars {number}
 * @return {HTMLElement}
 */
function cropAndAddToolTip(element, maxChars) {
    // add id
    if (element.id == null || element.id === '') {
        element.id = randomId();
    }

    // crop
    const fullText = element.innerText;
    if (element.innerText.length > maxChars) {
        element.innerText = cropText(fullText, maxChars);
    }

    // add tool tip
    addToolTip(element, fullText);

    return element;
}

/**
 * @param element {HTMLElement}
 * @param text {string}
 */
function addToolTip(element, text) {
    if (element.id == null || element.id === '') {
        console.warn('element has no id');
    } else if (text != null && text.trim() !== '') {
        const toolTipElementId = `${element.id}-tooltip`;
        const existingTooltip = document.getElementById(toolTipElementId);

        if (existingTooltip != null) {
            existingTooltip.remove();
            console.log(`tooltip removed ${toolTipElementId}`);
        }

        const toolTip = document.createElement('div');
        toolTip.id = toolTipElementId;
        toolTip.className = 'tooltip';
        toolTip.innerText = text;

        document.getElementsByTagName('body')[0].append(toolTip);

        // we check window.innerWidth,
        // so tool tips don't show up on mobile devices
        element.addEventListener('mouseenter', () => {
            UI.timeoutId = setTimeout(() => {
                if (window.innerWidth >= MOBILE_MAX_WIDTH && UI.cursorX >= 0 && UI.cursorY >= 0) {
                    const toolTipX = UI.cursorX + 8;
                    const toolTipY = UI.cursorY + 8;
                    toolTip.style.left = `${toolTipX}px`;
                    toolTip.style.top = `${toolTipY}px`;
                    toolTip.style.visibility = 'visible';
                }
            }, 1_000);
        });

        element.addEventListener('mouseleave', () => {
            toolTip.style.visibility = 'hidden';
            clearTimeout(UI.timeoutId);
        });
    } else {
        console.warn('empty tooltip text, will not render');
    }
}

/**
 * @param element {HTMLElement}
 * @returns {boolean}
 */
function isInViewport(element) {
    const rect = element.getBoundingClientRect();
    return (
        rect.top >= 0 &&
        rect.left >= 0 &&
        rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
        rect.right <= (window.innerWidth || document.documentElement.clientWidth)
    );
}

function showSignUpModal() {
    UI.showModalByName(Modals.SIGN_UP, () => {
        new SignUpModalHandler();
    });
}

function showLoginModal() {
    UI.showModalByName(Modals.LOGIN, () => {
        new LoginModalHandler();
    });
}

function showNewGameModal() {
    UI.showModalByName(Modals.NEW_GAME, () => {
        new NewGameHandler();
    });
}

function showPlayBotModal() {
    UI.showModalByName(Modals.PLAY_BOT, () => {
        new PlayBotModalHandler();
    });
}

/**
 * @param gameType {string}
 * @returns {string}
 */
function analysisIconByGameType(gameType) {
    switch (gameType) {
        case GameType.PVP:
            return `${ICON_PATH}/swords.png`;
        case GameType.PVB:
            return `${ICON_PATH}/bot-icon.png`;
        case GameType.DB:
            return `${ICON_PATH}/servers.png`;
        default:
            return `${ICON_PATH}/pie-chart.png`;
    }
}

class UI {

    /**
     * @type {UserTurnToPlayIndicatorWidget|null}
     */
    static #turnToPlayWidget;

    /**
     * @type {HTMLDivElement}
     */
    static #notificationBox = document.getElementById('notification-box');

    /**
     * @type {HTMLDivElement}
     */
    static #modalBackground = document.getElementById('modal-background');

    static cursorX = -1;
    static cursorY = -1;
    static timeoutId;
    static #modalHistoryStatePushed = false;

    static init() {
        setTimeout(() => {
            UI.#initNotification();

            window.onclick = (mouseEvent) => {
                UI.hideModal(mouseEvent);
                UI.hideUserTurnToPlayWidgetOnEvent(mouseEvent);
            }

            document.addEventListener('keydown', (e) => {
                if (UI.#isModalVisible()) {
                    switch (e.key) {
                        case 'Escape':
                            UI.hideModal(null);
                            UI.#hideNotification();
                            UI.hideUserTurnToPlayWidgetOnEvent(null);
                            break;
                        case 'Enter':
                            let panels = document.getElementsByClassName('modal-buttons-panel');
                            if (panels.length === 1) {
                                let buttons = panels[0].getElementsByTagName('input');
                                if (buttons.length === 1) {
                                    buttons[0].click();
                                }
                            }
                            break;
                    }
                }
            });

            // Handle browser back button to close modals
            window.addEventListener('popstate', (event) => {
                if (UI.#isModalVisible()) {
                    UI.#modalHistoryStatePushed = false;
                    UI.#modalBackground.style.display = 'none';
                    UI.#modalBackground.innerHTML = '';
                    UI.#hideNotification();
                }
            });

            if (!isUserAuthenticated()) {
                UI.preloadModals(
                    Modals.LOGIN,
                    Modals.SIGN_UP
                );
            }
            UI.preloadModal(
                Modals.PLAY_BOT,
                Modals.NEW_GAME
            );

            const toolTips = [
                ['lobby', 'Lobby where you can join games created by other players, the number in the green circle (if visible) indicates how many games are available'],
                ['new-game', 'Play against other players or a friend'],
                ['play-bot', 'Play against the computer'],
                ['puzzles', 'Train with some exercises to improve your skills'],
                ['database', 'Find (and analyze) games played by professional during tournaments'],
                ['analysis', 'Analysis Board tool where you can import your games and analyze them with the engine'],
                ['global', 'Some statistics about the website'],
                ['merch', 'Support us by purchasing some cool merch with our designs (t-shirts, hoodies, mugs, stickers, etc.)'],
                ['support', 'Send us a tip on Ko-fi to support the project'],
                ['about', 'More information about us and the project'],
            ];

            for (const [id, message] of toolTips) {
                const item = document.getElementById(`top-bar-menu-item-${id}`);
                addToolTip(item, message);
            }
        }, 300);

        // FIXME: should be managed by onclick actions?
        if (!isUserAuthenticated()) {
            const signUpLinks = document.getElementsByClassName('little-message-sign-up-link');
            for (let i = 0; i < signUpLinks.length; i++) {
                signUpLinks[i].addEventListener('click', () => showSignUpModal());
            }
        }

        // hide all tooltips on mouse move
        document.getElementsByTagName('body')[0].addEventListener('mousemove', () => {
            let tooltips = document.getElementsByClassName('tooltip');
            for (let i = 0; i < tooltips.length; i++) {
                tooltips[i].style.visibility = 'hidden';
            }
        });

        document.addEventListener('mousemove', (event) => {
            UI.cursorX = event.pageX;
            UI.cursorY = event.pageY;
        });
    }

    /**
     * Render username, add user menu
     */
    static initLoginPanel() {
        if (isUserIdentified()) {
            const user = new User();

            const userNameDropDownMenu = new UserNameDropDownMenu(user);
            const handleDiv = document.getElementById('handle-div');
            handleDiv.innerText = cropText(user.username, 12);

            // add menu toggle events to all elements that can open the menu
            const eventSourceItems = [
                handleDiv,
                document.getElementById('handle-menu-container'),
                document.getElementById('user-profile-icon'),
                document.getElementById('user-profile-icon-img'),
            ];

            eventSourceItems.forEach(element => {
                element.addEventListener('click', (event) => {
                    event.stopPropagation();
                    userNameDropDownMenu.toggle();
                });
            });

            const eventSourceIds = eventSourceItems.map(div => div.id);
            DropDownMenuManager.getInstance().registerDropDownMenu(userNameDropDownMenu, eventSourceIds, 'login-side');
            UI.#turnToPlayWidget = new UserTurnToPlayIndicatorWidget();
        }
    }

    static #initNotification() {
        UI.#notificationBox.addEventListener('click', () => {
            if (UI.#isNotificationVisible()) {
                UI.#hideNotification();
            }
        });
    }

    static pushInfoNotification(html, timeout) {
        UI.#notificationBox.classList.add('notification-box-info');
        UI.#notificationBox.classList.remove('notification-box-error');
        UI.#pushNotification(html, timeout);
    }

    static pushErrorNotification(html, timeout) {
        UI.#notificationBox.classList.remove('notification-box-info');
        UI.#notificationBox.classList.add('notification-box-error');
        UI.#pushNotification(html, timeout);
    }

    static #pushNotification(html, timeout) {
        UI.#notificationBox.innerHTML = html;
        UI.#showNotification();
        let actualTimeout;
        if (timeout == null) {
            actualTimeout = DEFAULT_NOTIFICATION_TIMEOUT;
        } else {
            actualTimeout = timeout;
        }

        if (notificationTimeoutId != null) {
            clearTimeout(notificationTimeoutId);
        }
        notificationTimeoutId = setTimeout(() => UI.#hideNotification(), actualTimeout);
    }

    static #showNotification() {
        const elements = document.getElementsByClassName('notification-box');
        for (let i = 0; i < elements.length; i++) {
            elements[i].style.visibility = 'visible';
        }
    }

    static #hideNotification() {
        const elements = document.getElementsByClassName('notification-box');
        for (let i = 0; i < elements.length; i++) {
            elements[i].style.visibility = 'hidden';
        }
    }

    static #isNotificationVisible() {
        const elements = document.getElementsByClassName('notification-box');
        for (let i = 0; i < elements.length; i++) {
            if (elements[i].style.visibility === 'visible') {
                return true;
            }
        }
        return false;
    }

    // TODO: update title of modal
    // TODO: move to ModalHandler?
    /**
     * @param labelText {HTMLElement}
     * @param yesCallback {function}
     * @param yesButtonText {string}
     * @param noCallback {function}
     * @param noButtonText {string}
     */
    static showConfirmationModal(labelText, yesCallback, yesButtonText = 'yes', noCallback, noButtonText = 'no') {
        UI.showModalByName(Modals.CONFIRMATION, () => {
            const labelElement = document.getElementById('confirmation-content');
            labelElement.append(labelText);

            const yesButton = document.getElementById('confirmation-yes-button');
            yesButton.addEventListener('click', () => UI.hideModal(null));
            yesButton.addEventListener('click', (e) => yesCallback(e)); // TODO: do we need to pass event?
            yesButton.value = yesButtonText;

            const noButton = document.getElementById('confirmation-no-button');
            noButton.addEventListener('click', noCallback);
            noButton.value = noButtonText;
        });
    }

    /**
     * Open a modal, showing a confirmation dialog first if shouldConfirm is true.
     *
     * @param shouldConfirm {boolean}
     * @param confirmText {string}
     * @param yesButtonText {string}
     * @param openFn {function}
     */
    static openWithConfirmation(shouldConfirm, confirmText, yesButtonText, openFn) {
        if (shouldConfirm) {
            const text = buildSimpleSpan(confirmText);
            UI.showConfirmationModal(text, openFn, yesButtonText, () => UI.hideModal(null), 'cancel');
        } else {
            openFn();
        }
    }

    /**
     * @param modalName {string}
     * @param loadedCallback {function}
     */
    static showModalByName(modalName, loadedCallback = () => console.log(modalName + ' loaded')) {
        if (modalCache.has(modalName)) {
            UI.#modalBackground.innerHTML = modalCache.get(modalName);
            UI.#modalBackground.style.display = 'flex';
            UI.#pushModalHistoryState();
            loadedCallback();
        } else {
            fetch(`/modal/${modalName}`)
                .then((response) => response.text())
                .then((modalHtml) => {
                    UI.#modalBackground.innerHTML = modalHtml;
                    UI.#modalBackground.style.display = 'flex';
                    UI.#pushModalHistoryState();
                    loadedCallback();
                    modalCache.set(modalName, modalHtml);
                    console.log('modal added to cache: ' + modalName);
                })
                .catch((error) => {
                    console.warn(error);
                    UI.#modalBackground.innerHTML = 'Error loading modal: ' + error;
                    UI.#modalBackground.style.display = 'flex';
                    UI.#pushModalHistoryState();
                });
        }
    }

    static preloadModals(...modalNames) {
        for (let i = 0; i < modalNames.length; i++) {
            UI.preloadModal(modalNames[i]);
        }
    }

    static preloadModal(modalName) {
        fetch('/modal/' + modalName)
            .then((response) => response.text())
            .then((modalHtml) => modalCache.set(modalName, modalHtml))
            .catch((error) => {
                console.warn(error);
            });
    }

    static hideModal(mouseEvent) {
        if (mouseEvent == null || mouseEvent.target === UI.#modalBackground) {
            // only close the modal if we click outside the modal form
            if (UI.#isModalVisible()) {
                UI.#modalBackground.style.display = 'none';
                UI.#modalBackground.innerHTML = '';
                UI.#hideNotification();
                UI.#popModalHistoryState();
            }
        }
    }

    static #isModalVisible() {
        return UI.#modalBackground.style.display !== 'none';
    }

    static #pushModalHistoryState() {
        if (!UI.#modalHistoryStatePushed) {
            history.pushState({modal: true}, '');
            UI.#modalHistoryStatePushed = true;
        }
    }

    static #popModalHistoryState() {
        if (UI.#modalHistoryStatePushed) {
            UI.#modalHistoryStatePushed = false;
            history.back();
        }
    }

    /**
     * Hide the menu on click, except if we click on what opens the menu
     */
    static hideUserTurnToPlayWidgetOnEvent(mouseEvent) {
        let widget = UI.#turnToPlayWidget;
        if (widget != null) {
            if (mouseEvent == null) {
                widget.hide();
            } else if (!mouseEvent.target.id.startsWith(TURN_TO_PLAY_GAMES_PREFIX)) {
                widget.hide();
            }
        }
    }

    /**
     *
     * @param update {GamesToPlayUpdateDto}
     */
    static refreshTurnToPlayWidget(update) {
        UI.#turnToPlayWidget?.refresh(update);
    }


    /**
     * @param emailField {HTMLInputElement}
     * @return {boolean}
     */
    static validateEmail(emailField) {
        const email = emailField.value;
        const isValid = email.length > 0 && /^\w+([.-]?\w+)*@\w+([.-]?\w+)*(\.\w{2,3})+$/.test(email);
        if (isValid) {
            emailField.classList.remove('incorrect-data');
        } else {
            emailField.classList.add('incorrect-data');
        }
        return isValid;
    }

    /**
     * @param passwordField {HTMLInputElement}
     * @return {boolean}
     */
    static validatePassword(passwordField) {
        const isValid =
            passwordField.value.length >= PASSWORD_MIN_LENGTH &&
            passwordField.value.length <= PASSWORD_MAX_LENGTH;

        if (isValid) {
            passwordField.classList.remove('incorrect-data');
        } else {
            passwordField.classList.add('incorrect-data');
        }
        return isValid;
    }

    static showValidationErrors(errors) {
        switch (errors.length) {
            case 0:
                // do nothing
                console.warn('no validation errors to render');
                break;
            case 1:
                UI.pushErrorNotification(errors[0], VALIDATION_NOTIFICATION_TIMEOUT);
                break;
            default:
                UI.pushErrorNotification(errors.map(it => '- ' + it).join('<br/>'), VALIDATION_NOTIFICATION_TIMEOUT);
                break;
        }
    }

}
