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

const USER_ID_FIELD = 'user.userId';
const USER_TOKEN_FIELD = 'user.token';
const USER_USERNAME_FIELD = 'user.handle';
const USER_ROLES_FIELD = 'user.roles';

const GUEST_USER_ID_FIELD = 'guest.user.userId';
const GUEST_USER_TOKEN_FIELD = 'guest.user.token';

const COOKIE_DEFAULT_DAYS = 120;
const CHROME_COOKIE_MAX_TTL = 400;
const ANONYMOUS_USER_COOKIE_TTL = 30;

function setCookie(name, value, days) {
    let expires = "";
    if (days) {
        let date = new Date();
        date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
        expires = "; expires=" + date.toUTCString();
    }
    document.cookie = name + "=" + (value || "") + expires + "; path=/";
}

function getCookie(name) {
    let nameEQ = name + "=";
    let ca = document.cookie.split(';');
    for (let i = 0; i < ca.length; i++) {
        let c = ca[i];
        while (c.charAt(0) === ' ') c = c.substring(1, c.length);
        if (c.indexOf(nameEQ) === 0) return c.substring(nameEQ.length, c.length);
    }
    return null;
}

function eraseCookie(name) {
    document.cookie = name + '=; Max-Age=-99999999;';
}

/**
 * @returns {string|null}
 */
function userIdOrNull() {
    return new User().userId;
}

/**
 * @returns {UserId}
 */
function compositeUserIdOrNull() {
    return new User().compositeUserId;
}

/**
 * @return {boolean} true if the user is authenticated (i.e. has an account)
 */
function isUserAuthenticated() {
    return getCookie(USER_TOKEN_FIELD) != null;
}

/**
 * @return {boolean} true if the user is identified as guest (uses a temporary guest account)
 */
function isUserIdentifiedAsGuest() {
    return getCookie(GUEST_USER_TOKEN_FIELD) != null;
}

/**
 * @return {boolean} true if the user is either authenticated or identified as guest
 */
function isUserIdentified() {
    return isUserIdentifiedAsGuest() || isUserAuthenticated();
}

function eraseAllIdentificationCookies() {
    eraseCookie(USER_ID_FIELD);
    eraseCookie(USER_TOKEN_FIELD);
    eraseCookie(USER_USERNAME_FIELD);
    eraseCookie(USER_ROLES_FIELD);
    eraseCookie(GUEST_USER_ID_FIELD);
    eraseCookie(GUEST_USER_TOKEN_FIELD);
}

function eraseGuestCookies() {
    eraseCookie(GUEST_USER_ID_FIELD);
    eraseCookie(GUEST_USER_TOKEN_FIELD);
}

function setAuthenticationCookies(json) {
    setCookie(USER_ID_FIELD, json.userId, COOKIE_DEFAULT_DAYS);
    setCookie(USER_USERNAME_FIELD, json.username, COOKIE_DEFAULT_DAYS);
    setCookie(USER_TOKEN_FIELD, json.token, COOKIE_DEFAULT_DAYS);
    if (json.roles && json.roles.length > 0) {
        setCookie(USER_ROLES_FIELD, JSON.stringify(json.roles), COOKIE_DEFAULT_DAYS);
    }
}

class PingResponseHandler extends SimpleResponseHandler {

    constructor() {
        super((json) => this.#okCallback(json));
    }

    handleTimeout() {
        this.#genericHandleServerError();
    }

    handleInternalServerError() {
        this.#genericHandleServerError();
    }

    handleServiceUnavailable() {
        this.#genericHandleServerError();
    }

    handleGatewayTimeout() {
        this.#genericHandleServerError();
    }

    handleForbidden() {
        // normally this only happens locally when we re-use users like 'test4'
        // and the DB has been reset in the meantime (the userId has changed so it's inconsistent)
        const msg = 'Forbidden error, guest will be created';
        const timeout = 3_000;
        UI.pushErrorNotification(msg, timeout);
        setTimeout(() => {
            eraseAllIdentificationCookies();
            window.open('/', '_self');
        }, timeout);
    }

    handleUnauthorized() {
        if (isUserIdentifiedAsGuest()) {
            const msg = 'Unauthorized error 401, guest session may have expired, a new one will be created';
            const timeout = 5_000;
            UI.pushErrorNotification(msg, timeout);
            setTimeout(() => {
                eraseAllIdentificationCookies();
                window.open('/', '_self');
            }, timeout);
        }
    }

    #genericHandleServerError() {
        UI.pushErrorNotification('The server seems unavailable at the moment', 5_000);
    }

    #okCallback(json) {
        if (json.renewedToken != null) {
            setCookie(USER_TOKEN_FIELD, json.renewedToken, CHROME_COOKIE_MAX_TTL);
            setCookie(USER_USERNAME_FIELD, getCookie(USER_USERNAME_FIELD), CHROME_COOKIE_MAX_TTL);
            setCookie(USER_ID_FIELD, getCookie(USER_ID_FIELD), CHROME_COOKIE_MAX_TTL);
        }
    }

}

/**
 * @param userIds string[]
 * @param cb function(string[])
 */
function fetchAreOnline(userIds, cb) {
    // optimize by taking into account the identified user
    const userId = userIdOrNull();
    const areOnlineUserIds = [];
    const userIdsToQuery = [];
    if (userId != null) {
        areOnlineUserIds.push(userId);
        userIds
            .filter(id => id !== userId)
            .forEach(id => userIdsToQuery.push(id));
    } else {
        userIds
            .forEach(id => userIdsToQuery.push(id));
    }

    if (userIdsToQuery.length > 0) {
        const params = userIdsToQuery.map(userId => `userId=${userId}`).join('&');
        const url = '/api/user/info/are-online?' + params;
        getAndHandle(url, json => {
            // noinspection JSUnresolvedReference
            areOnlineUserIds.push(...json.onlineUserIds);
            cb(areOnlineUserIds);
        });
    } else {
        cb(areOnlineUserIds);
    }
}

const UserType = Object.freeze({
    AUTHENTICATED: 'AUTHENTICATED',
    GUEST: 'GUEST',
});

class UserId {

    #userType;
    #userId;

    constructor(userType, userId) {
        this.#userType = userType;
        this.#userId = userId;
    }

    /**
     * @returns {string}
     */
    get userType() {
        return this.#userType;
    }

    /**
     * @returns {string}
     */
    get userId() {
        return this.#userId;
    }

}

class User {

    #userId;
    #username;
    #type;
    #roles;

    constructor() {
        if (isUserAuthenticated()) {
            this.#userId = getCookie(USER_ID_FIELD);
            this.#username = getCookie(USER_USERNAME_FIELD);
            this.#type = UserType.AUTHENTICATED;
            this.#roles = this.#parseRoles();
        } else if (isUserIdentifiedAsGuest()) {
            this.#userId = getCookie(GUEST_USER_ID_FIELD);
            this.#username = 'guest #' + this.#userId;
            this.#type = UserType.GUEST;
            this.#roles = [];
        } else {
            this.#userId = null;
            this.#username = null;
            this.#type = null;
            this.#roles = [];
        }
    }

    #parseRoles() {
        const rolesCookie = getCookie(USER_ROLES_FIELD);
        if (rolesCookie) {
            try {
                return JSON.parse(rolesCookie);
            } catch (e) {
                console.error('Failed to parse roles cookie', e);
                return [];
            }
        }
        return [];
    }

    /**
     * @return {string|null}
     */
    get userId() {
        return this.#userId;
    }

    /**
     * @return {string|null}
     */
    get username() {
        return this.#username;
    }

    /**
     * @return {string|null}
     */
    get userType() {
        return this.#type;
    }

    /**
     * @returns {UserId|null}
     */
    get compositeUserId() {
        if (this.#type != null && this.#userId != null) {
            return new UserId(this.#type, this.#userId);
        } else {
            return null;
        }
    }

    /**
     * @return {boolean}
     */
    get isIdentified() {
        return this.#type != null;
    }

    /**
     * @return {boolean}
     */
    get isAdmin() {
        return this.#roles.includes(Role.ADMIN);
    }

    /**
     * @return {boolean}
     */
    get isEditor() {
        return this.#roles.includes(Role.EDITOR);
    }

    toString() {
        switch (this.#type) {
            case UserType.AUTHENTICATED:
                return this.#username;
            case UserType.GUEST:
                return `guest #${this.#userId}`;
            default:
                return 'Not identified';
        }
    }

    /**
     * @deprecated not a fan of static class methods
     */
    static isAuthenticated() {
        return getCookie(USER_ID_FIELD) != null && getCookie(USER_TOKEN_FIELD) != null;
    }

}
