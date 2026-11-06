'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {loadParsers} = require('./harness');

function loadPvb() {
    const ctx = loadParsers();
    for (const name of ['dto', 'controller']) {
        const file = path.resolve(__dirname, `../../../main/resources/public/js/player-vs-bot/player-vs-bot-${name}.js`);
        vm.runInContext(fs.readFileSync(file, 'utf8'), ctx);
    }
    vm.runInContext('globalThis.PlayMoveResponseDto = PlayMoveResponseDto; globalThis.PlayerVsBotController = PlayerVsBotController;', ctx);
    return ctx;
}

test('PvB draw response never reports human or bot victory', () => {
    const ctx = loadPvb();
    for (const botMove of [null, 'd5d7']) {
        const response = new ctx.PlayMoveResponseDto({
            statusUpdate: 'STALEMATED', outcome: 'DRAW', position: 1, botMove
        });
        assert.equal(response.hasGameEnded(), true);
        assert.equal(response.isDraw(), true);
        assert.equal(response.isBotVictory(), false);
        assert.equal(response.isUserVictory(), false);
    }
    for (const statusUpdate of ['STALEMATED', 'CHECKMATED']) {
        assert.equal(new ctx.PlayMoveResponseDto({statusUpdate, outcome: 'WHITE_WINS'}).isUserVictory(), true);
        assert.equal(new ctx.PlayMoveResponseDto({statusUpdate, botMove: 'a0a1', outcome: 'BLACK_WINS'}).isBotVictory(), true);
    }
});

test('PvB controller stores draw outcome and disables further play without victory callbacks', () => {
    const ctx = loadPvb();
    vm.runInContext(`
        function formatTimestampToPgnDate() { return '2026.11.06'; }
        function userIdOrNull() { return 'player'; }
        class PlayerVsBotClient {
            fetchGameData(callback) {
                callback(new BotGameDto({
                    userId: 'player', username: 'player', userColor: Color.WHITE,
                    variant: Variant.JANGGI, status: GameEventType.CREATED, moveIndex: 0
                }));
            }
            fetchMovesHistory(callback) { callback([]); }
            playMove(move, callback) {
                callback(new PlayMoveResponseDto({
                    position: 1, statusUpdate: GameEventType.STALEMATED, outcome: Outcome.DRAW
                }));
            }
        }
    `, ctx);
    let wins = 0, losses = 0, draws = 0;
    const noop = () => {};
    const controller = new ctx.PlayerVsBotController(
        'game', noop, noop, noop, noop, () => wins++, () => losses++, () => draws++
    );
    controller.playMove(ctx.HalfMove.parseUci('d5d7'));
    assert.equal(controller.userOutcome(), 'DRAW');
    assert.equal(controller.userCanPlay(), false);
    assert.equal(draws, 1);
    assert.equal(wins, 0);
    assert.equal(losses, 0);
    assert.equal(controller.buildPgnMetadata().get('Result'), '1/2-1/2');
});
