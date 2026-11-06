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

class AdminInsightsUserAutocompleteSearchField extends AutocompleteSearchField {

    constructor() {
        super('insights-user-search', 'insights-user');
    }

    url() {
        return `${ADMIN_URL_PREFIX}/user-insights/autocomplete`;
    }
}

class AdminUserInsightsPage extends BasePage {

    #searchField = new AdminInsightsUserAutocompleteSearchField();
    #searchButton = document.getElementById('admin-user-insights-search-button');
    #identifier = document.body.dataset.userIdentifier;

    constructor() {
        super();

        document
            .getElementsByTagName('html')
            .item(0)
            .addEventListener('click', () => this.#searchField.hideBox());

        this.#searchButton.addEventListener('click', () => this.#search());
        document.getElementById('insights-user-search').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                this.#search();
            }
        });

        this.#loadInsightsDetails();
    }

    #search() {
        const selectedId = this.#searchField.getSelectedId();
        const typedValue = this.#searchField.inputFieldValue.trim();
        const identifier = selectedId != null ? selectedId : typedValue;
        if (identifier !== '') {
            window.location.href = `/admin/user/${encodeURIComponent(identifier)}`;
        }
    }

    #loadInsightsDetails() {
        const url = `${ADMIN_URL_PREFIX}/user-insights/${encodeURIComponent(this.#identifier)}/details`;
        getAndHandle(url, json => this.#renderAllDynamicSections(json));
    }

    #renderAllDynamicSections(data) {
        this.#renderGameActivity(data);
        this.#renderRelatedUsers(data);
        this.#renderSockPuppetGames(data);
        this.#renderSavedAnalysis(data);
        this.#renderLatestChatMessages(data);
        this.#renderLatestPageViews(data);
        this.#renderLatestSessions(data);
    }

    #renderGameActivity(data) {
        const container = document.getElementById('admin-user-game-activity-content');
        this.#clearContainer(container);

        const table = this.#createTable(
            'admin-user-game-activity-table',
            ['mode', 'first played', 'last played', 'total played'],
        );
        this.#appendActivityRow(table, 'puzzles', data.puzzle);
        this.#appendActivityRow(table, 'PvP', data.pvp);
        this.#appendActivityRow(table, 'PvP > 3', data.pvpOver3);
        this.#appendActivityRow(table, 'PvB', data.pvb);
        this.#appendActivityRow(table, 'PvB > 3', data.pvbOver3);
        container.append(table);
    }

    #appendActivityRow(table, label, summary) {
        const row = table.tBodies[0].insertRow();
        row.insertCell().innerText = label;
        row.insertCell().append(this.#buildNaAwareText(this.#formatTimestamp(summary.firstPlayed)));
        row.insertCell().append(this.#buildNaAwareText(this.#formatTimestampWithRelativeAge(summary.lastPlayed)));

        if (summary.totalPlayed === 0 && summary.firstPlayed == null && summary.lastPlayed == null) {
            row.insertCell().append(this.#buildNaAwareText('0'));
        } else {
            row.insertCell().innerText = summary.totalPlayed.toString();
        }
    }

    #renderRelatedUsers(data) {
        const container = document.getElementById('admin-user-related-users-content');
        this.#clearContainer(container);

        const relatedUsers = data.relatedUsers || [];
        if (relatedUsers.length === 0) {
            container.append(this.#buildPlaceholder('No related users detected from recent sessions.'));
            return;
        }

        const table = this.#createTable(
            'admin-user-related-users-table',
            ['user', 'shared IPs', 'PvP together', 'last seen'],
        );
        relatedUsers.forEach(entry => {
            const row = table.tBodies[0].insertRow();
            const userLink = document.createElement('a');
            userLink.href = `/admin/user/${encodeURIComponent(entry.userId)}`;
            userLink.innerText = entry.username === entry.userId ? `#${entry.userId}` : `${entry.username} (${entry.userId})`;
            row.insertCell().append(userLink);
            row.insertCell().innerText = entry.sharedIpCount.toString();
            row.insertCell().innerText = entry.pvpGamesWithUser <= 0 ? 'no' : `yes (${entry.pvpGamesWithUser})`;
            row.insertCell().append(this.#buildNaAwareText(this.#formatTimestamp(entry.lastSeen)));
        });
        container.append(table);
    }

    #renderSockPuppetGames(data) {
        const container = document.getElementById('admin-user-sock-puppet-games-content');
        this.#clearContainer(container);

        const sockPuppetGames = data.sockPuppetGames || [];
        if (sockPuppetGames.length === 0) {
            container.append(this.#buildPlaceholder('No game sock puppet reports found.'));
            return;
        }

        const table = this.#createTable(
            'admin-user-sock-puppet-games-table',
            ['PvP game', 'inviter', 'invitee', 'shared IPs', 'shared session fingerprints', 'shared IP', 'country', 'region', 'city', 'os', 'agent', 'game created'],
        );
        sockPuppetGames.forEach(entry => {
            const row = table.tBodies[0].insertRow();
            row.insertCell().append(this.#buildGameLink(entry.gameId));
            row.insertCell().append(this.#buildUserLink(entry.inviterUserId, entry.inviterUsername));
            row.insertCell().append(this.#buildUserLink(entry.inviteeUserId, entry.inviteeUsername));
            row.insertCell().innerText = entry.sharedRemoteAddressCount.toString();
            row.insertCell().innerText = entry.sharedFingerprintCount.toString();
            row.insertCell().append(this.#buildNaAwareText(entry.sharedRemoteAddress ?? 'n/a'));
            row.insertCell().append(this.#buildNaAwareText(entry.sharedCountryName ?? 'n/a'));
            row.insertCell().append(this.#buildNaAwareText(entry.sharedRegion ?? 'n/a'));
            row.insertCell().append(this.#buildNaAwareText(entry.sharedCity ?? 'n/a'));
            row.insertCell().append(this.#buildNaAwareText(entry.sharedOperatingSystemName ?? 'n/a'));
            row.insertCell().append(this.#buildNaAwareText(entry.sharedAgentName ?? 'n/a'));
            row.insertCell().innerText = this.#formatTimestamp(entry.gameCreated);
        });
        container.append(table);
    }

    #renderSavedAnalysis(data) {
        const container = document.getElementById('admin-user-saved-analysis-content');
        this.#clearContainer(container);

        const savedAnalysis = data.savedAnalysis || [];
        if (savedAnalysis.length === 0) {
            container.append(this.#buildPlaceholder('No saved analysis found.'));
            return;
        }

        const table = this.#createTable(
            'admin-user-saved-analysis-table',
            ['name', 'game type', 'created', 'last updated'],
        );
        savedAnalysis.forEach(entry => {
            const row = table.tBodies[0].insertRow();
            const link = document.createElement('a');
            link.href = `/analysis?id=${encodeURIComponent(entry.analysisId)}`;
            link.innerText = entry.name;
            row.insertCell().append(link);
            row.insertCell().append(this.#buildNaAwareText(entry.gameType?.name ?? 'n/a'));
            row.insertCell().innerText = this.#formatTimestamp(entry.created);
            row.insertCell().append(this.#buildNaAwareText(this.#formatTimestampWithRelativeAge(entry.lastUpdated)));
        });
        container.append(table);
    }

    #renderLatestChatMessages(data) {
        const container = document.getElementById('admin-user-latest-chat-messages-content');
        this.#clearContainer(container);

        const latestChatMessages = data.latestChatMessages || [];
        if (latestChatMessages.length === 0) {
            container.append(this.#buildPlaceholder('No chat messages found.'));
        } else {
            const table = this.#createTable(
                'admin-user-latest-chat-messages-table',
                ['game id', 'message', 'time'],
            );
            latestChatMessages.forEach(entry => {
                const row = table.tBodies[0].insertRow();
                row.insertCell().append(this.#buildGameLink(entry.gameId));
                row.insertCell().innerText = entry.content;
                row.insertCell().innerText = this.#formatTimestamp(entry.messageTime);
            });
            container.append(table);
        }

        this.#renderFullLink(
            'admin-user-chat-messages-full-link-container',
            latestChatMessages.length > 0,
            `/admin/user/${encodeURIComponent(this.#identifier)}/chat-messages`,
            'admin-user-chat-messages-full-link',
            'admin-user-chat-messages-full-link-disabled',
            'view all chat messages',
        );
    }

    #renderLatestPageViews(data) {
        const container = document.getElementById('admin-user-latest-page-views-content');
        this.#clearContainer(container);

        const latestPageViews = data.latestPageViews || [];
        if (latestPageViews.length === 0) {
            container.append(this.#buildPlaceholder('No page views found.'));
        } else {
            const table = this.#createTable(
                'admin-user-latest-page-views-table',
                ['url', 'time'],
            );
            latestPageViews.forEach(entry => {
                const row = table.tBodies[0].insertRow();
                const link = document.createElement('a');
                link.href = entry.url;
                link.innerText = entry.url;
                row.insertCell().append(link);
                row.insertCell().innerText = this.#formatTimestamp(entry.time);
            });
            container.append(table);
        }

        this.#renderFullLink(
            'admin-user-page-views-full-link-container',
            latestPageViews.length > 0,
            `/admin/user/${encodeURIComponent(this.#identifier)}/page-views`,
            'admin-user-page-views-full-link',
            'admin-user-page-views-full-link-disabled',
            'view all page views',
        );
    }

    #renderLatestSessions(data) {
        const container = document.getElementById('admin-user-latest-sessions-content');
        this.#clearContainer(container);

        const latestSessions = data.latestSessions || [];
        if (latestSessions.length === 0) {
            container.append(this.#buildPlaceholder('No sessions found.'));
            return;
        }

        const table = this.#createTable(
            'admin-user-latest-sessions-table',
            ['os', 'agent', 'country', 'region', 'city', 'IP', 'sock puppet', 'created', 'updated'],
        );

        latestSessions.forEach(entry => {
            const row = table.tBodies[0].insertRow();
            row.insertCell().innerText = entry.os;
            row.insertCell().innerText = entry.agentName;
            row.insertCell().append(this.#buildNaAwareText(entry.countryName ?? entry.countryCode ?? 'n/a'));
            row.insertCell().append(this.#buildNaAwareText(entry.region ?? 'n/a'));
            row.insertCell().append(this.#buildNaAwareText(entry.city ?? 'n/a'));
            row.insertCell().innerText = entry.remoteAddress;
            row.insertCell().innerText = this.#formatSockPuppetSignal(entry.otherUsersOnRemoteAddress);
            row.insertCell().append(this.#buildNaAwareText(this.#formatTimestamp(entry.created)));
            row.insertCell().append(this.#buildNaAwareText(this.#formatTimestamp(entry.updated)));
        });
        container.append(table);
    }

    #renderFullLink(containerId, enabled, href, enabledId, disabledId, text) {
        const container = document.getElementById(containerId);
        container.replaceChildren();
        if (enabled) {
            const link = document.createElement('a');
            link.id = enabledId;
            link.href = href;
            link.innerText = text;
            container.append(link);
            return;
        }
        const span = document.createElement('span');
        span.id = disabledId;
        span.className = 'disabled-section-link';
        span.innerText = text;
        container.append(span);
    }

    #buildGameLink(gameId) {
        const link = document.createElement('a');
        link.href = `/game?id=${encodeURIComponent(gameId)}`;
        link.innerText = gameId;
        return link;
    }

    #buildUserLink(userId, username) {
        const link = document.createElement('a');
        link.href = `/admin/user/${encodeURIComponent(userId)}`;
        link.innerText = username === userId ? `#${userId}` : `${username} (${userId})`;
        return link;
    }

    #createTable(id, headers) {
        const table = document.createElement('table');
        table.id = id;
        table.className = 'nice-table nice-table-striped';

        const thead = table.createTHead();
        const headerRow = thead.insertRow();
        headers.forEach(value => {
            const th = document.createElement('th');
            th.innerText = value;
            headerRow.append(th);
        });

        table.createTBody();
        return table;
    }

    #clearContainer(container) {
        container.replaceChildren();
    }

    #buildPlaceholder(text) {
        const paragraph = document.createElement('p');
        paragraph.className = 'empty-block-placeholder';
        paragraph.innerText = text;
        return paragraph;
    }

    #buildNaAwareText(value) {
        if (value === 'n/a' || value === '0') {
            const span = document.createElement('span');
            span.className = 'na-value';
            span.innerText = value;
            return span;
        }
        return document.createTextNode(value);
    }

    #formatTimestamp(value) {
        if (value == null) {
            return 'n/a';
        }
        return formatTimestampDefaultDateFormat(value);
    }

    #formatTimestampWithRelativeAge(value) {
        if (value == null) {
            return 'n/a';
        }
        return `${formatTimestampDefaultDateFormat(value)} (${formatTimestampToRelativeTime(value)})`;
    }

    #formatSockPuppetSignal(otherUsersOnRemoteAddress) {
        if (otherUsersOnRemoteAddress <= 0) {
            return 'no';
        }
        if (otherUsersOnRemoteAddress === 1) {
            return 'possible (1 other user on IP)';
        }
        return `possible (${otherUsersOnRemoteAddress} other users on IP)`;
    }
}

window.onload = () => new AdminUserInsightsPage();
