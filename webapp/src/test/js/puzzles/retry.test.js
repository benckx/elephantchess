const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {loadParsers} = require('../parsers/harness');

const puzzlesDir = path.resolve(__dirname, '../../../main/resources/public/js/puzzles');

function setup() {
    const ctx = loadParsers();
    const timers = [];
    const keys = [];
    const outcomes = [];
    let client;
    let state;
    let loads = 0;
    let replies = 0;
    let nextPuzzles = 0;
    let engineCalls = 0;
    ctx.setTimeout = cb => timers.push(cb);
    ctx.document.addEventListener = (name, cb) => keys.push(cb);
    ctx.getAllQueryParam = () => [];
    ctx.getQueryParam = () => null;
    ctx.updateUrl = () => {};
    ctx.PuzzlesClient = class {
        constructor(cb) {
            this.load = cb;
            client = this;
        }
        fetchCurrent() {}
        fetchNext() { nextPuzzles++; }
        postOutcome(...args) { outcomes.push(args); }
        fetchBestMoveAnd(fen, cb) {
            engineCalls++;
            cb(ctx.HalfMove.parseUci('a6a5'));
        }
    };
    for (const file of ['puzzles-dto.js', 'puzzles-state.js', 'puzzles-controller.js']) {
        vm.runInContext(fs.readFileSync(path.join(puzzlesDir, file), 'utf8'), ctx);
    }
    vm.runInContext('globalThis.PuzzleState = PuzzleState; globalThis.PuzzlesController = PuzzlesController;', ctx);
    const controller = new ctx.PuzzlesController(
        loaded => { state = loaded; loads++; },
        () => {}, () => {}, () => {}, () => {},
        () => { replies++; }
    );
    function load() {
        client.load(new ctx.PuzzleState({
            id: 'retry-test', attempts: 10, rating: 1200, categories: [],
            color: ctx.Color.RED, fen: ctx.DEFAULT_START_FEN,
            moves: [], solution: ['a3a4', 'a6a5', 'a4a5'], enabled: true
        }));
    }
    load();
    return {
        ctx, controller, outcomes, load,
        get state() { return state; },
        get loads() { return loads; },
        get replies() { return replies; },
        get nextPuzzles() { return nextPuzzles; },
        get engineCalls() { return engineCalls; },
        play: uci => controller.submitSolution(ctx.HalfMove.parseUci(uci)),
        flush: () => { while (timers.length) timers.shift()(); },
        enter: () => keys.forEach(cb => cb({key: 'Enter'}))
    };
}

test('retry restores the initial FEN, move budget and solution pointer without fetching a puzzle', () => {
    const h = setup();
    h.play('a3a4');
    h.flush();
    assert.notEqual(h.state.outputFen(), h.state.startFen);
    assert.equal(h.state.currentSolutionMove.toUci(), 'a4a5');
    h.controller.retry();
    assert.equal(h.state.outputFen(), h.state.startFen);
    assert.equal(h.state.moveHistoryIndex, -1);
    assert.equal(h.state.currentSolutionMove.toUci(), 'a3a4');
    assert.equal(h.state.hasOutcome(), false);
    assert.equal(h.state.isOutOfMoves(), false);
    assert.equal(h.loads, 2);
    assert.equal(h.nextPuzzles, 0);
    assert.equal(h.outcomes.length, 0);
    h.play('a3a4');
    h.flush();
    assert.equal(h.replies, 2);
});

test('retry leaves the recorded failure intact and never posts a second result', () => {
    const h = setup();
    h.play('a3a4');
    h.flush();
    h.play('a4a5');
    assert.equal(h.state.outcome, 'FAILED');
    assert.equal(h.outcomes.length, 1);
    h.controller.retry();
    h.enter();
    assert.equal(h.nextPuzzles, 0);
    h.state.isCheckmate = () => true;
    h.play('a3a4');
    assert.equal(h.state.outcome, 'SOLVED');
    assert.equal(h.outcomes.length, 1);
    assert.equal(h.outcomes[0][1], 'FAILED');
    h.enter();
    assert.equal(h.nextPuzzles, 1);
});

for (const outcome of ['SOLVED', 'SKIPPED']) {
    test(`retry after ${outcome.toLowerCase()} allows playing again without another rating change`, () => {
        const h = setup();
        if (outcome === 'SOLVED') {
            h.state.isCheckmate = () => true;
            h.play('a3a4');
        } else {
            h.controller.reveal();
        }
        h.controller.retry();
        assert.equal(h.state.hasOutcome(), false);
        assert.equal(h.state.outputFen(), h.state.startFen);
        h.controller.skip();
        assert.equal(h.outcomes.length, 1);
        assert.equal(h.outcomes[0][1], outcome);
        assert.equal(h.nextPuzzles, 1);
    });
}

test('retry exits engine mode and restores prerecorded replies', () => {
    const h = setup();
    h.play('c3c4');
    assert.equal(h.controller.isEngineMode(), true);
    h.controller.retry();
    assert.equal(h.controller.isEngineMode(), false);
    h.play('a3a4');
    h.flush();
    assert.equal(h.engineCalls, 1);
    assert.equal(h.replies, 2);
});

test('loading a new puzzle restores rated outcomes after a retry', () => {
    const h = setup();
    h.controller.reveal();
    h.controller.retry();
    h.controller.reveal();
    assert.equal(h.outcomes.length, 1);
    h.load();
    assert.equal(h.controller.hasRecordedOutcome(), false);
    h.controller.reveal();
    assert.equal(h.outcomes.length, 2);
});

test('Try Again resets page controls and history without accumulating move listeners', () => {
    const h = setup();
    const {ctx} = h;
    const elements = new Map();
    function element(id) {
        if (!elements.has(id)) {
            const classes = new Set();
            elements.set(id, {
                style: {}, dataset: {}, listeners: {},
                classList: {
                    add: (...names) => names.forEach(name => classes.add(name)),
                    remove: (...names) => names.forEach(name => classes.delete(name)),
                    contains: name => classes.has(name)
                },
                addEventListener(name, cb) { this.listeners[name] = cb; }
            });
        }
        return elements.get(id);
    }
    const board = {
        listeners: [], enabled: false,
        flipToColor() {}, loadFen(fen) { this.fen = fen; },
        enablePlayerMove() { this.enabled = true; },
        disablePlayerMove() { this.enabled = false; },
        clearAllAfterMovesListeners() { this.listeners = []; },
        addAfterMoveListener(cb) { this.listeners.push(cb); },
        registerOpponentMove(move, highlight, cb) { ctx.setTimeout(cb); },
        updateHighlightedChecks() {}, hideAllDebugHighlight() {}
    };
    const tree = {
        moves: [], navigationListeners: [], clickedListeners: [],
        addNavigationPanel() {},
        addClickedNodeListener(cb) { this.clickedListeners.push(cb); },
        addNavigationListener(cb) { this.navigationListeners.push(cb); },
        resetFlaggedAsRevealed() {},
        clear() { this.moves = []; },
        setMoves(moves) { this.moves = [...moves]; },
        addSubmittedMove(move) { this.moves.push(move); },
        isLastMoveSelected() { return true; },
        selectLastNode() {}, renderSubmittedMovesAsErroneous() {}
    };
    ctx.BasePage = class {};
    ctx.SettingsGui = class {};
    ctx.PuzzleMoveTreeWidget = class { constructor() { return tree; } };
    ctx.createWebappBoardGui = () => board;
    ctx.isUserIdentified = () => false;
    ctx.document.body = element('body');
    ctx.document.getElementById = element;
    ctx.getElementsByClassNameArray = () => [];
    ctx.setInfoBoxButtonEnabled = (el, enabled) => {
        if (enabled) el.classList.add('enabled-button');
        else el.classList.remove('enabled-button');
    };
    ctx.isInfoBoxButtonEnabled = e => e.target.classList.contains('enabled-button');
    vm.runInContext(fs.readFileSync(path.join(puzzlesDir, 'puzzles.js'), 'utf8'), ctx);
    ctx.window.onload();
    h.load();
    const retry = element('try-again-button');
    const clickRetry = () => retry.listeners.click({target: retry});
    for (let attempt = 0; attempt < 3; attempt++) {
        board.listeners[0](ctx.HalfMove.parseUci('a3a4'));
        assert.equal(retry.classList.contains('enabled-button'), false);
        clickRetry();
        h.flush();
        assert.equal(tree.moves.length, 2);
        assert.equal(retry.classList.contains('enabled-button'), true);
        clickRetry();
        assert.equal(board.fen, ctx.DEFAULT_START_FEN);
        assert.equal(board.enabled, true);
        assert.equal(tree.moves.length, 0);
        assert.equal(board.listeners.length, 1);
        assert.equal(tree.navigationListeners.length, 1);
        assert.equal(tree.clickedListeners.length, 1);
        assert.equal(element('next-puzzle-button-label').innerText, 'Skip Puzzle');
        assert.equal(element('reveal-solution-button').classList.contains('enabled-button'), true);
    }
    assert.equal(h.outcomes.length, 0);
});
