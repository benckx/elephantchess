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

class PlayerDto7k {

    #userId;
    #userName;

    constructor(json) {
        this.#userId = json.userId;
        this.#userName = json.userName;
    }

    /**
     * @returns {string}
     */
    get userId() {
        return this.#userId;
    }

    /**
     * @returns {string}
     */
    get userName() {
        return this.#userName;
    }

}

class GameDto7k {

    #gameId;
    #whitePlayer;
    #redPlayer;
    #orangePlayer;
    #bluePlayer;
    #greenPlayer;
    #purplePlayer;
    #blackPlayer;
    #currentFen;
    #colorToPlay;

    constructor(gameId, json) {
        this.#gameId = gameId;
        if (json.whitePlayer != null) this.#whitePlayer = new PlayerDto7k(json.whitePlayer);
        if (json.redPlayer != null) this.#redPlayer = new PlayerDto7k(json.redPlayer);
        if (json.orangePlayer != null) this.#orangePlayer = new PlayerDto7k(json.orangePlayer);
        if (json.bluePlayer != null) this.#bluePlayer = new PlayerDto7k(json.bluePlayer);
        if (json.greenPlayer != null) this.#greenPlayer = new PlayerDto7k(json.greenPlayer);
        if (json.purplePlayer != null) this.#purplePlayer = new PlayerDto7k(json.purplePlayer);
        if (json.blackPlayer != null) this.#blackPlayer = new PlayerDto7k(json.blackPlayer);
        this.#currentFen = json.currentFen;
        if (json.colorToPlay != null) {
            this.#colorToPlay = Color7k.findByName(json.colorToPlay);
        } else {
            this.#colorToPlay = null;
        }
    }

    /**
     * @returns {string}
     */
    get fen() {
        return this.#currentFen;
    }

    /**
     * @returns {Color7k|null}
     */
    get colorToPlay() {
        return this.#colorToPlay;
    }

    /**
     * @returns {PlayerDto7k[]}
     */
    get allPlayers() {
        const allPlayers = [];
        if (this.#whitePlayer != null) allPlayers.push(this.#whitePlayer);
        if (this.#redPlayer != null) allPlayers.push(this.#redPlayer);
        if (this.#orangePlayer != null) allPlayers.push(this.#orangePlayer);
        if (this.#bluePlayer != null) allPlayers.push(this.#bluePlayer);
        if (this.#greenPlayer != null) allPlayers.push(this.#greenPlayer);
        if (this.#purplePlayer != null) allPlayers.push(this.#purplePlayer);
        if (this.#blackPlayer != null) allPlayers.push(this.#blackPlayer);

        // distinct
        return allPlayers.filter((value, index, self) =>
            index === self.findIndex((t) => t.userId === value.userId)
        );
    }

    /**
     * @param userId {string}
     * @returns {Color7k[]}
     */
    colorsOfPlayers(userId) {
        const colors = [];
        if (this.#whitePlayer != null && this.#whitePlayer.userId === userId) {
            colors.push(ColorTypes.WHITE);
        }
        if (this.#redPlayer != null && this.#redPlayer.userId === userId) {
            colors.push(ColorTypes.RED);
        }
        if (this.#orangePlayer != null && this.#orangePlayer.userId === userId) {
            colors.push(ColorTypes.ORANGE);
        }
        if (this.#bluePlayer != null && this.#bluePlayer.userId === userId) {
            colors.push(ColorTypes.BLUE);
        }
        if (this.#greenPlayer != null && this.#greenPlayer.userId === userId) {
            colors.push(ColorTypes.GREEN);
        }
        if (this.#purplePlayer != null && this.#purplePlayer.userId === userId) {
            colors.push(ColorTypes.PURPLE);
        }
        if (this.#blackPlayer != null && this.#blackPlayer.userId === userId) {
            colors.push(ColorTypes.BLACK);
        }
        return colors;
    }

}
