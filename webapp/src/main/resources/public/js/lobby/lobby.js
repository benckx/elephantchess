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

const YOUTUBE_EMBED = `
            <iframe src="https://www.youtube.com/embed/nApZihrdQGo?si=iUZBitjMJCAQYiTL"
                    title="YouTube video player" frameborder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                    referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>`;

/**
 * @return {HTMLInputElement}
 */
function makeAppButton(id, value) {
    const button = document.createElement('input');
    button.type = 'button';
    button.id = id;
    button.className = 'app-buttons';
    button.value = value;
    return button;
}

const RATING_MODE_ICONS = {
    rated: '/images/icons/trophy-icon-smaller.png',
    casual: '/images/icons/handshake1.png',
};

class LobbyPage extends BasePage {

    #client = new LobbyClient();

    #gameToJoinList = document.getElementById('games-to-join-list');
    #createNewGameButton = document.getElementById('create-new-game-button');

    // no game to join message links
    #noGameToJoinMessage = document.getElementById('no-game-to-join-message');
    #createGameSpan = document.getElementById('create-game-span');
    #playBotSpan = document.getElementById('play-bot-span');
    #puzzleTrainingSpan = document.getElementById('puzzle-training-span');

    // info below the game list
    #onlineUsers = document.getElementById('online-users-and-rating');

    // puzzle training box
    #puzzleBoardGui = createWebappBoardGui({
        elementId: 'puzzle-board-container',
        showCoordinates: false,
        mini: true,
    });

    /**
     *
     * @type {HTMLAnchorElement}
     */
    #puzzleBoardLinkMask = document.getElementById('puzzle-board-link-mask');

    #youTubeDiv = document.getElementById('youtube-embed-container');
    #isYouTubeEmbedRendered = false;

    constructor() {
        super();
        UI.preloadModal(Modals.NEW_GAME);
        this.#createNewGameButton.addEventListener('click', () => showNewGameModal());
        this.#createGameSpan.addEventListener('click', () => showNewGameModal());
        this.#playBotSpan.addEventListener('click', () => showPlayBotModal());
        this.#puzzleTrainingSpan.addEventListener('click', () => {
            window.open('/puzzles', '_self');
        });
        this.#initPuzzleBox();
        this.#loadUpcomingEvents();
        this.#loadSupporters();

        // mostly added this to debug when working on the modals
        // but can also be used in prod to share links to specific modals
        setTimeout(() => {
            if (getQueryParam('pvb') != null) {
                showPlayBotModal();
            } else if (getQueryParam('new-game') != null) {
                showNewGameModal();
            }
        }, 800);

        addEventListener('scroll', (_) => this.#renderYouTubeEmbed());
        addEventListener('resize', (_) => this.#renderYouTubeEmbed());

        const settingsGui = new SettingsGui(this.#puzzleBoardGui, null, false, false);
        new LiveGamesViewer(settingsGui);
    }

    additionalGamesToJoinListeners() {
        return [update => {
            this.#renderGameList(update.gamesToJoin);

            switch (update.totalOnline) {
                case 0:
                    this.#onlineUsers.innerText = 'No user online';
                    break;
                case 1:
                    this.#onlineUsers.innerText = '1 user online';
                    break;
                default:
                    this.#onlineUsers.innerText = `${update.totalOnline} users online`;
            }
        }];
    }

    /**
     * @param entries {GameToPlayDto[]}
     */
    #renderGameList(entries) {
        this.#gameToJoinList.innerHTML = '';

        entries.sort(sortByOnline);
        // TODO: sort by rating most similar to user's

        if (entries.length === 0) {
            this.#noGameToJoinMessage.classList.add('no-game-to-join-message-visible');
        } else {
            this.#noGameToJoinMessage.classList.remove('no-game-to-join-message-visible');
        }

        entries.forEach((entry) => {
            const item = document.createElement('div');
            item.className = 'game-to-join-item';

            const variantPane = document.createElement('div');
            variantPane.className = 'game-to-join-variant-pane';
            item.append(variantPane);

            const variantSymbol = document.createElement('span');
            variantSymbol.className = 'game-to-join-variant-symbol';
            variantSymbol.innerText = '象';
            variantSymbol.title = 'Xiangqi (Chinese chess)';
            variantPane.append(variantSymbol);

            const leftPane = document.createElement('div');
            leftPane.className = 'game-to-join-left-pane';
            item.append(leftPane);

            const middlePane = document.createElement('div');
            middlePane.className = 'game-to-join-middle-pane';
            item.append(middlePane);

            const rightPane = document.createElement('div');
            rightPane.className = 'game-to-join-right-pane';
            item.append(rightPane);

            const ratingPane = document.createElement('div');
            ratingPane.className = 'game-to-join-rating-pane';
            item.append(ratingPane);

            const opponentLine = document.createElement('div');
            opponentLine.className = 'game-to-join-opponent-line';
            middlePane.append(opponentLine);

            const metadataLine = document.createElement('div');
            metadataLine.className = 'game-to-join-metadata-line';
            middlePane.append(metadataLine);

            // is online indicator
            const isOnlineCell = document.createElement('div');
            isOnlineCell.className = 'game-to-join-online-cell';
            opponentLine.append(isOnlineCell);

            const isOnlineIndicator = document.createElement('div');
            isOnlineIndicator.className = 'online-status-indicator';
            if (entry.isOpponentOnline) {
                isOnlineIndicator.classList.add(IS_ONLINE_CSS_CLASS);
            }
            isOnlineCell.append(isOnlineIndicator);

            // username and rating
            const usernameCell = document.createElement('div');
            usernameCell.classList.add('username-cell', 'crop-text-ellipsis');
            opponentLine.append(usernameCell);

            usernameCell.append(
                buildUsernameSpan(
                    entry.opponentUserId,
                    entry.opponentUsername,
                    entry.opponentUserType
                )
            );
            if (entry.opponentUserType === UserType.GUEST && usernameCell.firstChild?.innerText != null) {
                usernameCell.firstChild.innerText = usernameCell.firstChild.innerText.replace(/^guest\s*/i, '').trim();
            }

            const ratingCell = document.createElement('div');
            ratingCell.className = 'rating-cell';
            ratingCell.innerText = `(${entry.opponentRating})`;
            opponentLine.append(ratingCell);

            // color
            const colorCell = document.createElement('div');
            colorCell.className = 'game-to-join-metadata-item color-cell';
            const colorSpan = buildColorSpan(entry.opponentColor);
            if (colorSpan.classList.contains('any-color')) {
                colorSpan.innerText = 'Any color';
                colorSpan.title = 'This player can play either color';
            } else if (entry.opponentColor === Color.RED) {
                colorSpan.title = 'This player picked Red, you would play Black';
            } else if (entry.opponentColor === Color.BLACK) {
                colorSpan.title = 'This player picked Black, you would play Red';
            }
            colorCell.append(colorSpan);
            metadataLine.append(colorCell);

            // time control
            const timeControlIconCell = document.createElement('div');
            timeControlIconCell.className = 'game-to-join-time-icon-cell';
            leftPane.append(timeControlIconCell);

            const imageName = timeControlCategoryIconMap.get(entry.timeControlCategory);
            const img = document.createElement('img');
            img.className = 'time-control-icons';
            img.src = `${ICON_PATH}/${imageName}`;
            timeControlIconCell.append(img);

            let timeControlLabel = '--';
            if (entry.timeControl != null) {
                timeControlLabel = entry.timeControl.printShort(' +');
            }

            const timeControlDurationCell = document.createElement('div');
            timeControlDurationCell.className = 'game-to-join-time-duration-cell';
            timeControlDurationCell.innerText = timeControlLabel;
            leftPane.append(timeControlDurationCell);

            // rating mode
            const ratingModeCell = document.createElement('div');
            ratingModeCell.className = 'game-to-join-rating-mode-cell';
            ratingPane.append(ratingModeCell);

            const ratingModeIcon = document.createElement('img');
            ratingModeIcon.className = 'game-to-join-rating-mode-icon';
            ratingModeCell.append(ratingModeIcon);

            const ratingModeSpan = document.createElement('span');
            ratingModeCell.append(ratingModeSpan);
            if (entry.isRated) {
                ratingModeSpan.innerText = 'Rated';
                ratingModeIcon.src = RATING_MODE_ICONS.rated;
            } else {
                ratingModeSpan.innerText = 'Casual';
                ratingModeIcon.src = RATING_MODE_ICONS.casual;
            }

            // join button
            const joinButtonCell = document.createElement('div');
            joinButtonCell.className = 'join-button-cell';
            rightPane.append(joinButtonCell);

            const joinButton = makeAppButton(`join-game-button-${entry.gameId}`, 'join');
            joinButtonCell.append(joinButton);
            joinButton.classList.add('join-buttons');
            joinButton.addEventListener('click', () => this.#handleClickJoinButton(entry));
            this.#gameToJoinList.append(item);
        });
    }

    /**
     * @param entry {GameToPlayDto}
     */
    #handleClickJoinButton(entry) {
        if (userIdOrNull() === entry.opponentUserId) {
            UI.pushErrorNotification('You can not join your own game ;)');
        } else if (entry.isCorrespondenceGame && !isUserAuthenticated()) {
            UI.pushErrorNotification('Correspondence games are not available for guests. Please sign up or log in.');
        } else {
            this.#joinGame(entry.gameId);
        }
    }

    #joinGame(gameId) {
        this.#client.joinGame(gameId, (color) => {
            window.open(`/game?id=${gameId}&color=${color}`, '_self');
        });
    }

    #initPuzzleBox() {
        this.#puzzleBoardGui.disablePlayerMove();

        // wait until user is identified before loading current puzzle
        // so it can be assigned to the new user
        let interval = null;
        interval = setIntervalNoDelay(() => {
            if (isUserIdentified()) {
                clearInterval(interval);
                this.#client.getCurrentPuzzle((dto) => {
                    this.#puzzleBoardGui.loadFen(dto.fen);
                    this.#puzzleBoardGui.flipToColor(dto.color);
                    this.#puzzleBoardLinkMask.href = `/puzzles?id=${dto.puzzleId}`;
                });
            }
        }, 200);

        if (isSafari) {
            document
                .getElementById('puzzle-board-container')
                .classList.add('safari-mini-board-container');
        }
    }

    #loadUpcomingEvents() {
        this.#client.listUpcomingEvents((events) => {
            const eventsDiv = document.getElementById('upcoming-events');

            if (events.length === 0) {
                eventsDiv.innerText = 'No upcoming events';
            } else {
                events.forEach(event => {
                    const eventDiv = document.createElement('div');
                    eventDiv.className = 'upcoming-event';

                    const datesDiv = document.createElement('div');
                    datesDiv.className = 'dates';
                    datesDiv.innerText = `${formatDayToShortDateFormat(event.start)} - ${formatDayToShortDateFormat(event.end)}`;

                    const description = document.createElement('div');
                    description.className = 'description';
                    description.innerText = event.description;

                    const link = document.createElement('a');
                    link.href = event.link;
                    link.innerText = cropUrl(event.link);
                    link.target = '_blank';

                    const linkDiv = document.createElement('div');
                    linkDiv.className = 'link';
                    linkDiv.appendChild(link);

                    eventDiv.appendChild(datesDiv);
                    eventDiv.appendChild(description);
                    eventDiv.appendChild(linkDiv);
                    eventsDiv.appendChild(eventDiv);
                });

                eventsDiv.style.display = 'block';
            }
        });
    }

    #loadSupporters() {
        // load recurrent supporters
        this.#client.listLatestRecurrentSupporters((response) => {
            const supportersDiv = document.getElementById('monthly-supporters-list');
            if (response.entries.length === 0) {
                supportersDiv.innerText = 'No monthly supporters yet';
            } else {
                supportersDiv.innerHTML = '';
                response.entries.forEach((entry, index) => {
                    const link = document.createElement('a');
                    link.href = `/@/${entry.username}?medium=supporters-lobby`;
                    link.innerText = entry.username;
                    supportersDiv.appendChild(link);

                    if (index < response.entries.length - 1) {
                        supportersDiv.appendChild(document.createTextNode(', '));
                    }
                });
            }
        });

        // load one-time supporters
        this.#client.listLatestTippers((response) => {
            const tippersDiv = document.getElementById('one-time-supporters-list');
            if (response.entries.length === 0) {
                tippersDiv.innerText = 'No one-time supporters yet';
            } else {
                tippersDiv.innerHTML = '';
                response.entries.forEach((entry, index) => {
                    const link = document.createElement('a');
                    link.href = `/@/${entry.username}?medium=supporters-lobby`;
                    link.innerText = entry.username;
                    tippersDiv.appendChild(link);

                    if (index < response.entries.length - 1) {
                        tippersDiv.appendChild(document.createTextNode(', '));
                    }
                });
            }
        });
    }

    #renderYouTubeEmbed() {
        if (!this.#isYouTubeEmbedRendered) {
            if (isInViewport(this.#youTubeDiv)) {
                this.#youTubeDiv.innerHTML = YOUTUBE_EMBED;
                this.#isYouTubeEmbedRendered = true;
            }
        }
    }

}

window.onload = () => new LobbyPage();
