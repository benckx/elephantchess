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

const MAX_USERNAME_LENGTH_PVP_GAME_THUMB = 12;
const MAX_USERNAME_LENGTH_PVB_GAME_THUMB = 18;
const MAX_USERNAME_LENGTH_DB_GAME_THUMB = 24;

const LIVE_HTML =
    '<div>live</div><div><img alt="winner" class="live-icon" src="/images/icons/record-button.png"></div>';

/**
 * @param status {string}
 * @return {string}
 */
function formatGameStatus(status) {
    switch (status) {
        case GameEventType.CANCELED:
        case GameEventType.AUTO_CANCELED:
            return 'canceled';
        case GameEventType.CREATED:
        case GameEventType.JOINED:
        case GameEventType.DRAW_PROPOSED:
        case GameEventType.DRAW_DECLINED:
            return 'ongoing';
        case GameEventType.DRAW_ACCEPTED:
            return 'draw';
        case GameEventType.RESIGNED:
            return 'resigned';
        case GameEventType.AUTO_RESIGNED:
            return 'resigned (auto)';
        case GameEventType.CHECKMATED:
            return 'checkmate';
        case GameEventType.STALEMATED:
            return 'stalemate';
        case GameEventType.FLAGGED:
            return 'flagged';
        case GameEventType.PERPETUAL_CHECKING:
            return 'perpetual';
        default:
            return '??';
    }
}

/**
 * Wraps a game-thumb DOM element plus its associated {@link BoardGui} and the
 * last rendered {@link GameMetadataDto}. Provides cleaner render/refresh/clear
 * operations than the previous free functions.
 */
class GameThumb {

    /**
     * @type {HTMLDivElement}
     */
    divElement;

    /**
     * @type {BoardGui}
     */
    boardGui;

    /**
     * @type {GameMetadataDto|null}
     */
    metadata = null;

    /**
     * @param divElement {HTMLDivElement}
     * @param boardGui {BoardGui}
     */
    constructor(divElement, boardGui) {
        this.divElement = divElement;
        this.boardGui = boardGui;
    }

    /**
     * Clone this thumb's DOM, append the clone to {@code parentContainer},
     * wire up a fresh {@link BoardGui} on it and return a ready-to-render
     * (and already cleared) {@link GameThumb}.
     *
     * Cloning is done from an existing thumb because game thumbs are
     * server-rendered HTML; this guarantees the new thumb keeps the exact
     * same structure/classes without duplicating that markup in JS.
     *
     * @param parentContainer {HTMLElement} where to append the new thumb
     * @param boardId {string} unique id for the new board container
     * @returns {GameThumb}
     */
    cloneInto(parentContainer, boardId) {
        const thumbDiv = /** @type {HTMLDivElement} */ (this.divElement.cloneNode(true));

        // gray out the cloned thumb until it gets rendered
        thumbDiv.classList.add('game-thumb-placeholder');

        // re-target the board container: new unique id, placeholder look,
        // and drop the cloned board's inner DOM (BoardGui will rebuild it)
        const boardContainer = thumbDiv.querySelector('.board-container');
        boardContainer.id = boardId;
        boardContainer.classList.add('board-container-placeholder');
        boardContainer.innerHTML = '';

        // must be in the DOM before BoardGui's constructor looks it up by id
        parentContainer.appendChild(thumbDiv);

        const boardGui = createWebappBoardGui({
            elementId: boardId,
            showCoordinates: false,
            mini: true,
        });

        const thumb = new GameThumb(thumbDiv, boardGui);
        thumb.clear();
        return thumb;
    }

    /**
     * @param className {string}
     * @returns {HTMLElement}
     */
    #findFirst(className) {
        return this.divElement.getElementsByClassName(className)[0];
    }

    /**
     * Render a {@link GameMetadataDto} into this thumb (and its board).
     *
     * @param gameMetadataDto {GameMetadataDto}
     * @param utmMedium {string|null}
     * @param playerNameHighlight {string|null}
     */
    render(gameMetadataDto, utmMedium = null, playerNameHighlight = null) {
        this.metadata = gameMetadataDto;

        let maxUserNameLength;
        switch (gameMetadataDto.gameId.type) {
            case GameType.PVP:
                maxUserNameLength = MAX_USERNAME_LENGTH_PVP_GAME_THUMB;
                break;
            case GameType.PVB:
                maxUserNameLength = MAX_USERNAME_LENGTH_PVB_GAME_THUMB;
                break;
            case GameType.DB:
                maxUserNameLength = MAX_USERNAME_LENGTH_DB_GAME_THUMB;
                break;
            default:
                maxUserNameLength = MAX_USERNAME_LENGTH_PVP_GAME_THUMB;
                break;
        }

        const buildRedUsername = () => buildUsernameSpan(
            gameMetadataDto.redPlayerId,
            gameMetadataDto.redPlayerName,
            gameMetadataDto.redUserType,
            maxUserNameLength
        );

        const buildBlackUsername = () => buildUsernameSpan(
            gameMetadataDto.blackPlayerId,
            gameMetadataDto.blackPlayerName,
            gameMetadataDto.blackUserType,
            maxUserNameLength
        );

        // Safari
        if (isSafari) {
            const miniBoardContainer = this.#findFirst('mini-board-container');
            miniBoardContainer.classList.add('safari-mini-board-container');
        }

        // names
        const redPlayerNameElement = this.#findFirst('red-player-name');
        const blackPlayerNameElement = this.#findFirst('black-player-name');
        redPlayerNameElement.innerHTML = '';
        blackPlayerNameElement.innerHTML = '';

        switch (gameMetadataDto.userColor) {
            case Color.RED:
                // PvB game, user plays RED
                redPlayerNameElement.append(buildRedUsername());
                blackPlayerNameElement.innerText = gameMetadataDto.blackPlayerName;
                break;
            case Color.BLACK:
                // PvB game, user plays BLACK
                redPlayerNameElement.innerText = gameMetadataDto.redPlayerName;
                blackPlayerNameElement.append(buildBlackUsername());
                break;
            default:
                switch (gameMetadataDto.gameId.type) {
                    case GameType.PVP:
                        redPlayerNameElement.append(buildRedUsername());
                        blackPlayerNameElement.append(buildBlackUsername());
                        break;
                    case GameType.DB:
                        if (gameMetadataDto.redPlayerName != null && gameMetadataDto.redPlayerName.length > 0) {
                            redPlayerNameElement.append(
                                buildLink(`/database/player/${encodePlayerNameForUrl(gameMetadataDto.redPlayerName)}`, gameMetadataDto.redPlayerName)
                            );
                        } else {
                            redPlayerNameElement.append(document.createTextNode('<unknown>'));
                        }

                        if (gameMetadataDto.blackPlayerName != null && gameMetadataDto.blackPlayerName.length > 0) {
                            blackPlayerNameElement.append(
                                buildLink(`/database/player/${encodePlayerNameForUrl(gameMetadataDto.blackPlayerName)}`, gameMetadataDto.blackPlayerName)
                            );
                        } else {
                            blackPlayerNameElement.append(document.createTextNode('<unknown>'));
                        }

                        break;
                }
        }

        // link
        let utmMediumParam = '';
        if (utmMedium != null) {
            utmMediumParam = `&medium=${utmMedium}`;
        }

        this.#findFirst('board-outer-container-link-mask').href =
            gameIdToPageLink(gameMetadataDto.gameId) + utmMediumParam;

        // ratings
        const redPlayerRatingElement = this.#findFirst('red-player-rating');
        const blackPlayerRatingElement = this.#findFirst('black-player-rating');
        redPlayerRatingElement.innerHTML = '';
        blackPlayerRatingElement.innerHTML = '';
        if (gameMetadataDto.redPlayerRating != null) {
            redPlayerRatingElement.innerHTML = ` (${gameMetadataDto.redPlayerRating})`;
        }
        if (gameMetadataDto.blackPlayerRating != null) {
            blackPlayerRatingElement.innerHTML = ` (${gameMetadataDto.blackPlayerRating})`;
        }

        // outcome
        const redPlayerWinsElement = this.#findFirst('red-player-wins');
        const blackPlayerWinsElement = this.#findFirst('black-player-wins');
        redPlayerWinsElement.innerHTML = '';
        blackPlayerWinsElement.innerHTML = '';

        switch (gameMetadataDto.outcome) {
            case Outcome.RED_WINS:
                redPlayerWinsElement.innerHTML = WINNER_BLUE_STAR_HTML;
                break;
            case Outcome.BLACK_WINS:
                blackPlayerWinsElement.innerHTML = WINNER_BLUE_STAR_HTML;
                break;
            default:
                break;
        }

        // game status
        const statusDiv = this.#findFirst('game-thumb-status');
        if (gameMetadataDto.isLive()) {
            statusDiv.innerHTML = LIVE_HTML;
        } else if (gameMetadataDto.gameId.type === GameType.DB) {
            // DB games
            if (gameMetadataDto.outcome === Outcome.DRAW) {
                statusDiv.innerHTML = 'draw';
            } else {
                if (playerNameHighlight != null) {
                    if (playerNameHighlight === gameMetadataDto.redPlayerName) {
                        if (gameMetadataDto.outcome === Outcome.RED_WINS) {
                            statusDiv.innerHTML = 'win';
                        } else {
                            statusDiv.innerHTML = 'loss';
                        }
                    } else if (playerNameHighlight === gameMetadataDto.blackPlayerName) {
                        if (gameMetadataDto.outcome === Outcome.BLACK_WINS) {
                            statusDiv.innerHTML = 'win';
                        } else {
                            statusDiv.innerHTML = 'loss';
                        }
                    }
                } else {
                    switch (gameMetadataDto.outcome) {
                        case Outcome.RED_WINS:
                            statusDiv.innerHTML = 'red wins';
                            break;
                        case Outcome.BLACK_WINS:
                            statusDiv.innerHTML = 'black wins';
                            break;
                        default:
                            statusDiv.innerHTML = '??';
                            break;
                    }
                }
            }
        } else {
            // PvP and PvB games
            statusDiv.innerHTML = formatGameStatus(gameMetadataDto.status);
        }

        const lastUpdatedDiv = this.#findFirst('game-thumb-last-updated');
        if (lastUpdatedDiv && gameMetadataDto.lastUpdated != null && gameMetadataDto.lastUpdated > 0) {
            lastUpdatedDiv.innerHTML = formatTimestampToRelativeTime(gameMetadataDto.lastUpdated);
        } else {
            lastUpdatedDiv.innerHTML = '--';
        }

        // variant indicator
        const variantIndicator = this.#findFirst('game-thumb-variant');
        if (variantIndicator) {
            variantIndicator.innerHTML = gameMetadataDto.variant === 'MANCHU' ? '统' : '象';
        }

        // is online
        const redPlayerStatusIndicator = this.#findFirst('red-player-status-indicator');
        const blackPlayerStatusIndicator = this.#findFirst('black-player-status-indicator');

        if (gameMetadataDto.isRedOnline != null && gameMetadataDto.isBlackOnline != null) {
            if (gameMetadataDto.isRedOnline) {
                redPlayerStatusIndicator.classList.add(IS_ONLINE_CSS_CLASS);
            } else {
                redPlayerStatusIndicator.classList.remove(IS_ONLINE_CSS_CLASS);
            }

            if (gameMetadataDto.isBlackOnline) {
                blackPlayerStatusIndicator.classList.add(IS_ONLINE_CSS_CLASS);
            } else {
                blackPlayerStatusIndicator.classList.remove(IS_ONLINE_CSS_CLASS);
            }
        } else {
            redPlayerStatusIndicator.style.display = 'none';
            blackPlayerStatusIndicator.style.display = 'none';
        }

        // position
        this.#findFirst('board-container')
            .classList
            .remove('board-container-placeholder');

        this.boardGui.loadFen(gameMetadataDto.finalFen);

        this.divElement
            .classList
            .remove('game-thumb-placeholder');
    }

    /**
     * Refresh this thumb in-place from a {@link LatestGamesUpdateResponse} entry,
     * without re-rendering player names, ratings, ...
     *
     * @param update {{gameId: GameId, status: string, fen: string, lastUpdated: number}}
     */
    refresh(update) {
        this.boardGui.loadFen(update.fen, true);

        const lastUpdatedDiv = this.#findFirst('game-thumb-status');
        if (lastUpdatedDiv != null) {
            if (this.metadata != null && this.metadata.isLive()) {
                lastUpdatedDiv.innerHTML = LIVE_HTML;
            } else {
                lastUpdatedDiv.innerHTML = formatGameStatus(update.status);
            }
        }

        const lastUpdatedEl = this.#findFirst('game-thumb-last-updated');
        if (lastUpdatedEl != null && update.lastUpdated > 0) {
            lastUpdatedEl.innerHTML = formatTimestampToRelativeTime(update.lastUpdated);
        }
    }

    /**
     * Reset all dynamic content of the thumb (used when cloning a thumb
     * for infinite scroll, before re-rendering with new metadata).
     */
    clear() {
        const div = this.divElement;

        // Clear winner indicators
        div.querySelector('.red-player-wins').innerHTML = '';
        div.querySelector('.black-player-wins').innerHTML = '';

        // Clear player names and ratings (will be repopulated by render())
        div.querySelector('.red-player-name').innerHTML = '';
        div.querySelector('.black-player-name').innerHTML = '';
        div.querySelector('.red-player-rating').innerHTML = '';
        div.querySelector('.black-player-rating').innerHTML = '';

        // Reset status and timestamp
        div.querySelector('.game-thumb-status').innerHTML = '--';
        div.querySelector('.game-thumb-last-updated').innerHTML = '--';
        const variantEl = div.querySelector('.game-thumb-variant');
        if (variantEl) variantEl.innerHTML = '';

        // Reset online indicators
        div.querySelector('.red-player-status-indicator').classList.remove(IS_ONLINE_CSS_CLASS);
        div.querySelector('.black-player-status-indicator').classList.remove(IS_ONLINE_CSS_CLASS);

        this.metadata = null;
    }

}
