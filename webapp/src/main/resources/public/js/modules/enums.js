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

const GameEventType = Object.freeze({
    CREATED: 'CREATED',
    CANCELED: 'CANCELED',
    AUTO_CANCELED: 'AUTO_CANCELED',
    JOINED: 'JOINED',
    RESIGNED: 'RESIGNED',
    AUTO_RESIGNED: 'AUTO_RESIGNED',
    DRAW_PROPOSED: 'DRAW_PROPOSED',
    DRAW_ACCEPTED: 'DRAW_ACCEPTED',
    DRAW_DECLINED: 'DRAW_DECLINED',
    CHECKMATED: 'CHECKMATED',
    STALEMATED: 'STALEMATED',
    FLAGGED: 'FLAGGED',
    PERPETUAL_CHECKING: 'PERPETUAL_CHECKING'
});

const UserOutcome = Object.freeze({
    WIN: 'WIN',
    LOSS: 'LOSS',
    DRAW: 'DRAW'
});

const Outcome = Object.freeze({
    RED_WINS: 'RED_WINS',
    BLACK_WINS: 'BLACK_WINS',
    DRAW: 'DRAW'
});

const PuzzleOutcome = Object.freeze({
    SOLVED: 'SOLVED',
    FAILED: 'FAILED',
    SKIPPED: 'SKIPPED',
});

const AnalysisStatus = Object.freeze({
    NOT_STARTED: 'NOT_STARTED',
    STARTED: 'STARTED',
    CANCELLED: 'CANCELLED',
    PARTIALLY_COMPLETED: 'PARTIALLY_COMPLETED',
    COMPLETED: 'COMPLETED'
});

const GameType = Object.freeze({
    PVP: 'PVP',
    PVB: 'PVB',
    DB: 'DB'
});

const GameJoinSource = Object.freeze({
    DISCORD_NOTIFICATION: 'DISCORD_NOTIFICATION',
    LOBBY: 'LOBBY',
    MATCHED: 'MATCHED',
    LINK: 'LINK'
});

const Role = Object.freeze({
    ADMIN: 'ADMIN',
    EDITOR: 'EDITOR',
});

function oppositeColor(color) {
    switch (color) {
        case Color.RED:
            return Color.BLACK;
        case Color.BLACK:
            return Color.RED;
        default:
            throw new Error('Invalid color ' + color);
    }
}

function isStatusInProgress(status) {
    return status === GameEventType.JOINED ||
        status === GameEventType.DRAW_PROPOSED ||
        status === GameEventType.DRAW_DECLINED;
}

function isStatusFinished(status) {
    return status === GameEventType.CANCELED ||
        status === GameEventType.AUTO_CANCELED ||
        status === GameEventType.CHECKMATED ||
        status === GameEventType.STALEMATED ||
        status === GameEventType.RESIGNED ||
        status === GameEventType.AUTO_RESIGNED ||
        status === GameEventType.DRAW_ACCEPTED ||
        status === GameEventType.FLAGGED ||
        status === GameEventType.PERPETUAL_CHECKING;
}

/**
 *
 * @param userColor {string}
 * @param gameOutcome {string}
 * @returns {string}
 */
function gameOutcomeToUserOutcome(userColor, gameOutcome) {
    switch (gameOutcome) {
        case Outcome.DRAW:
            return UserOutcome.DRAW;
        case Outcome.RED_WINS:
            return userColor === Color.RED ? UserOutcome.WIN : UserOutcome.LOSS;
        case Outcome.BLACK_WINS:
            return userColor === Color.BLACK ? UserOutcome.WIN : UserOutcome.LOSS;
        default:
            return '--';
    }
}

/**
 * @param color {string}
 * @return {string}
 */
function winnerColorToOutcome(color) {
    switch (color) {
        case Color.RED:
            return Outcome.RED_WINS;
        case Color.BLACK:
            return Outcome.BLACK_WINS;
        default:
            throw new Error('Invalid color ' + color);
    }
}

/**
 * @param color {string}
 * @return {string}
 */
function loserColorToOutcome(color) {
    switch (color) {
        case Color.RED:
            return Outcome.BLACK_WINS;
        case Color.BLACK:
            return Outcome.RED_WINS;
        default:
            throw new Error('Invalid color ' + color);
    }
}

/**
 * @param gameType {string}
 */
function validateGameType(gameType) {
    switch (gameType) {
        case GameType.PVP:
        case GameType.PVB:
        case GameType.DB:
            break;
        default:
            throw new Error('Invalid game type ' + gameType);
    }
}
