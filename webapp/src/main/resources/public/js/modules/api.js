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

/**
 * @param params {Map<string, any>}
 */
function paramMapToQueryString(params) {
    if (params.size === 0) {
        return '';
    } else {
        const paramListArray = [];
        params.forEach((value, key) => {
            paramListArray.push(`${key}=${value}`);
        });
        return '?' + paramListArray.join('&');
    }
}

function updateUrl(suffix) {
    if (history.pushState) {
        const newUrl = getFullHost() + '/' + suffix;
        window.history.pushState({path: newUrl}, '', newUrl);
        return true;
    } else {
        return false;
    }
}

function removeTrackingParamsFromUrl() {
    const params = new URLSearchParams(window.location.search);
    if (params.has('medium' || params.has('utc_medium'))) {
        params.delete('medium');
        params.delete('utc_medium');

        if (params.size > 0) {
            const newUrl = window.location.pathname + '?' + params.toString();
            window.history.replaceState({path: newUrl}, '', newUrl);
        } else {
            window.history.replaceState({path: window.location.pathname}, '', window.location.pathname);
        }
    }
}

function getFullHost() {
    return window.location.protocol + "//" + window.location.host;
}

function getUrlPort() {
    const hostSplit = window.location.host.split(':');
    if (hostSplit.length === 2) {
        return Number(hostSplit[1])
    } else {
        return 80;
    }
}

function isUsingDevPort() {
    const port = getUrlPort();
    return port === 63342 || port === 8080;
}

/**
 * @returns {null|string}
 */
function getToken() {
    if (isUserAuthenticated()) {
        return getCookie(USER_TOKEN_FIELD);
    } else if (isUserIdentifiedAsGuest()) {
        return getCookie(GUEST_USER_TOKEN_FIELD);
    } else {
        return null;
    }
}

/**
 * @return {object}
 */
function headers() {
    const headers =
        {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        };

    const token = getToken();
    if (token != null) {
        headers.Authorization = `Bearer ${token}`;
    }

    return headers;
}

function getAndHandle(url, cb) {
    getAndHandleWith(url, new SimpleResponseHandler(cb));
}

/**
 * @param url {string}
 * @param handler {SimpleResponseHandler}
 */
function getAndHandleWith(url, handler) {
    fetch(url, {method: 'GET', headers: headers()})
        .then(response => handler.handle(response))
        .catch(error => handler.handleCatchError(error));
}

/**
 * @param url {string}
 * @param body {Object}
 * @param cb {function(Object)}
 */
function postAndHandle(url, body, cb) {
    postAndHandleWith(url, body, new SimpleResponseHandler(cb));
}

/**
 * @param url {string}
 * @param body {Object}
 * @param handler {SimpleResponseHandler}
 */
function postAndHandleWith(url, body, handler) {
    let init;
    if (body != null) {
        init = {method: 'POST', headers: headers(), body: JSON.stringify(body)};
    } else {
        init = {method: 'POST', headers: headers()};
    }

    fetch(url, init)
        .then(response => handler.handle(response))
        .catch(error => handler.handleCatchError(error));
}

/**
 * @param url {string}
 * @param body {Object}
 * @param cb {function(Object)}
 */
function putAndHandle(url, body, cb) {
    putAndHandleWith(url, body, new SimpleResponseHandler(cb));
}

/**
 * @param url {string}
 * @param body {Object}
 * @param handler {SimpleResponseHandler}
 */
function putAndHandleWith(url, body, handler) {
    let init;
    if (body != null) {
        init = {method: 'PUT', headers: headers(), body: JSON.stringify(body)};
    } else {
        init = {method: 'PUT', headers: headers()};
    }

    fetch(url, init)
        .then(response => handler.handle(response))
        .catch(error => handler.handleCatchError(error));
}

class SimpleResponseHandler {

    /**
     * @type {Response}
     */
    #response;

    #callbackJson;

    constructor(callbackJson) {
        this.#callbackJson = callbackJson;
    }

    /**
     * @return {Response}
     */
    get response() {
        return this.#response;
    }

    /**
     * @param response {Response}
     */
    handle(response) {
        this.#response = response;
        switch (this.#response.status) {
            case 200:
            case 201:
                this.handleOk();
                break
            case 204:
                this.handleNoContent();
                break;
            case 400:
                response.text().then(text => {
                    this.handleBadRequest(text);
                });
                break;
            case 401:
                this.handleUnauthorized();
                break;
            case 403:
                this.handleForbidden();
                break;
            case 404:
                this.handleNotFound();
                break;
            case 406:
                this.handleNotAcceptable();
                break;
            case 408:
                this.handleTimeout();
                break;
            case  500:
                this.handleInternalServerError();
                break;
            case 503:
                this.handleServiceUnavailable();
                break;
            case 504:
                this.handleGatewayTimeout();
                break;
            default:
                this.handleDefault(this.#response.status);
                break;
        }
    }

    /**
     * 200 and 201
     */
    handleOk() {
        if (this.#response != null && this.#callbackJson != null) {
            this.#response.json()
                .then(json => this.#callbackJson(json))
                .catch(error => console.error(error));
        }
    }

    /**
     * 204
     */
    handleNoContent() {
        console.warn('no content');
    }

    /**
     * 400
     * @param responseText {string}
     */
    handleBadRequest(responseText) {
        UI.pushErrorNotification(`Error 400: ${responseText}`, 3_000);
    }

    /**
     * 401
     */
    handleUnauthorized() {
        // TODO: refactor: if no authentication cookies -> do nothing
        //  if authentication cookies -> redirect to 401
        //  if guest cookies -> do nothing, let ping handler replace the token
        console.warn('unauthorized');
    }

    /**
     * 403
     */
    handleForbidden() {
        window.open('/403', '_self');
    }

    /**
     * 404
     */
    handleNotFound() {
        console.error('not found');
        // window.open('/404', '_self');
    }

    /**
     * 406
     */
    handleNotAcceptable() {
        this.#response.json()
            .then(json => UI.showValidationErrors(json.errors))
            .catch(() => {
                this.#response.text()
                    .then(text => UI.showValidationErrors([text]))
                    .catch(() => UI.showValidationErrors(['Error 406: Not Acceptable']));
            });
    }

    /**
     * 408
     */
    handleTimeout() {
        UI.pushErrorNotification('Error 408: Request Timeout', 3_000);
    }

    /**
     * 500
     */
    handleInternalServerError() {
        UI.pushErrorNotification('Error 500: Internal Server Error', 3_000);
    }

    /**
     * 503
     */
    handleServiceUnavailable() {
        UI.pushErrorNotification('Error 503: Service Unavailable', 3_000);
    }

    /**
     * 504
     */
    handleGatewayTimeout() {
        UI.pushErrorNotification('Error 504: Gateway Timeout', 3_000);
    }

    handleDefault(code) {
        console.error('not handled for status code ' + code)
        UI.pushErrorNotification('Error ' + code + ' not handled', 3_000);
    }

    handleCatchError(error) {
        // UI.pushErrorNotification('Error when trying to reach the server: ' + error, 5_000);
        console.error(error);
    }

}

class ResponseHandlerWithError extends SimpleResponseHandler {

    #invalidCb;

    /**
     * @param cb {function(Object)}
     * @param invalidCb {function(string)}
     */
    constructor(cb, invalidCb) {
        super(cb);
        this.#invalidCb = invalidCb;
    }

    handleBadRequest(responseText) {
        this.#invalidCb(responseText);
    }

}

class ValidationResponseHandler extends SimpleResponseHandler {

    #validationErrorsCb;

    /**
     * @param cb {function(Object)}
     * @param validationErrorsCb {function(string[])}
     */
    constructor(cb, validationErrorsCb) {
        super(cb);
        this.#validationErrorsCb = validationErrorsCb;
    }

    handleNotAcceptable() {
        this.response.json()
            .then(json => this.#validationErrorsCb(json.errors || []))
            .catch(error => {
                console.error('Failed to parse validation errors:', error);
                this.#validationErrorsCb([]);
            });
    }

}

/**
 * @param endpoint {string} Must not include the conventional '/ws/' prefix
 * @param params {Map<string, *>}
 * @return {WebSocket}
 */
function buildWebSocketEndpoint(endpoint, params) {
    // protocol must be wss in prod, where we use https
    let protocol;
    if (getUrlPort() === 80) {
        protocol = 'wss';
    } else {
        protocol = 'ws'
    }

    // render parameters
    let renderedParams = '';
    if (params.size > 0) {
        renderedParams = '?';
        let paramListArray = [];
        params.forEach((value, key) => {
            paramListArray.push(`${key}=${value}`);
        });
        renderedParams += paramListArray.join('&');
    }

    return new WebSocket(`${protocol}://${window.location.host}/ws/${endpoint}${renderedParams}`);
}

/**
 * Opens a WebSocket that automatically reconnects with exponential backoff.
 * If the WebSocket fails to even handshake several times in a row (typical when an intermediary
 * such as a proxy/VPN/antivirus strips the `Upgrade: websocket` header), shows a one-time
 * notification to the user.
 *
 * @param options {{
 *   endpoint: string,                                  // path passed to buildWebSocketEndpoint (no '/ws/' prefix)
 *   buildParams: function(): (Map<string, *>|null),    // called before each (re)connect; return null to abort
 *   onOpen: function(WebSocket): void,                 // called on every successful open
 *   onMessage: function(MessageEvent): void,           // called for every message
 *   logLabel: string,                                  // used in console logs
 *   networkBlockedMessage?: string                     // override default warning message
 * }}
 * @return {{ getSocket: function(): (WebSocket|null) }} a handle exposing the current socket (or null if not yet connected)
 */
function openReconnectingWebSocket(options) {
    const INITIAL_RECONNECT_DELAY = 1_000;
    const MAX_RECONNECT_DELAY = 60_000;
    const FAILED_HANDSHAKES_BEFORE_WARNING = 3;
    const DEFAULT_NETWORK_BLOCKED_MESSAGE =
        'Your network seems to be blocking real-time connections. ' +
        'Try a different network, or disable your VPN / antivirus / browser extensions.';

    let reconnectDelay = INITIAL_RECONNECT_DELAY;
    let consecutiveFailedHandshakes = 0;
    let hasWarnedUserAboutNetwork = false;
    let currentSocket = null;

    function connect() {
        const params = options.buildParams();
        if (params == null) {
            // caller signaled it's not ready / not authenticated yet
            return;
        }

        const ws = buildWebSocketEndpoint(options.endpoint, params);
        currentSocket = ws;
        let didOpen = false;

        ws.onopen = () => {
            console.log(`[${options.logLabel}] WebSocket connected`);
            didOpen = true;
            reconnectDelay = INITIAL_RECONNECT_DELAY;
            consecutiveFailedHandshakes = 0;
            options.onOpen(ws);
        };

        ws.onmessage = (e) => options.onMessage(e);

        ws.onclose = (e) => {
            if (!didOpen) {
                consecutiveFailedHandshakes++;
                if (consecutiveFailedHandshakes >= FAILED_HANDSHAKES_BEFORE_WARNING && !hasWarnedUserAboutNetwork) {
                    hasWarnedUserAboutNetwork = true;
                    UI.pushErrorNotification(
                        options.networkBlockedMessage || DEFAULT_NETWORK_BLOCKED_MESSAGE,
                        10_000
                    );
                }
            }
            console.log(`[${options.logLabel}] WebSocket closed. Reconnect in ${reconnectDelay} ms.`, e.reason);
            const delay = reconnectDelay;
            reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
            setTimeout(connect, delay);
        };
    }

    connect();

    return {
        getSocket: () => currentSocket
    };
}
