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

class SevenKingdomsGamePage extends BasePage {

    /**
     * @type {SevenKingdomsController}
     */
    #controller;

    #boardGui = new BoardGui7k({containerId: 'board-container'});

    #moveHistoryWidget =
        new SevenKingdomsGameMoveHistoryWidget(i => this.#handleHistoricalMoveClick(i));

    #kingdomsDataWidget = new SevenKingdomsDataWidget();

    #selectedIndex = -1;

    constructor() {
        super();
        const gameId = getQueryParam('gameId');
        if (gameId != null) {
            this.#controller = new SevenKingdomsController(
                gameId,
                gameDto => this.#handleInitGameData(gameDto),
                moves => this.#handleFetchMoves(moves)
            )
        } else {
            window.location.href = '/404';
        }
    }

    /**
     * @param gameDto {GameDto7k}
     */
    #handleInitGameData(gameDto) {
        // fen
        this.#boardGui.loadFen(gameDto.fen);

        // player-info
        const playerInfoDivs = getElementsByClassNameArray('player-info');
        gameDto.allPlayers.forEach((player, i) => {
            const playerInfoDiv = playerInfoDivs[i];
            playerInfoDiv
                .getElementsByClassName('player-info-placeholder')[0]
                .append(buildUsernameSpan(player.userId, player.userName, UserType.AUTHENTICATED, 30));

            const rightSide = playerInfoDiv.getElementsByClassName('right-side')[0];
            gameDto.colorsOfPlayers(player.userId).forEach(color => {
                const colorDiv = document.createElement('div');
                colorDiv.id = indicatorClass(color);
                colorDiv.classList.add(indicatorClass(), indicatorClass(color));
                addToolTip(colorDiv, `${color.armyName} (${color.armyChineseName}), the ${color.colorName.toLowerCase()} kingdom`);

                rightSide.appendChild(colorDiv);
            });

            playerInfoDiv.style.display = 'flex';
        });
    }

    /**
     * @param moves {Move[]}
     */
    #handleFetchMoves(moves) {
        const board = new Board7k();
        board.registerMoves(moves);
        this.#kingdomsDataWidget.update(board);
        this.#moveHistoryWidget.renderMoveHistory(board);
    }

    /**
     * @param i {number}
     */
    #handleHistoricalMoveClick(i) {
        this.#selectedIndex = i;

        // update widgets
        const boardAtIndex = this.#controller.getBoardForMoveAt(i);
        this.#boardGui.loadFen(boardAtIndex.outputFen());
        this.#kingdomsDataWidget.update(boardAtIndex);
    }
}

window.onload = () => new SevenKingdomsGamePage();
