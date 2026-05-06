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

const PERPETUAL_CHECKING_INFO_BOX_SUB_BOXES = 'perpetual-checking-info-box-sub-boxes';
const PERPETUAL_CHECKING_INFO_BOX_LIMIT_LABEL = 'perpetual-checking-info-box-limit-label'
const PERPETUAL_CHECKING_INFO_BOX_PIECES_LABEL = 'perpetual-checking-info-box-pieces-label'

class PerpetualCheckingRule {

    numberOfPieces;
    maxNumberOfChecks;

    constructor(numberOfPieces, numberOfChecks) {
        this.numberOfPieces = numberOfPieces;
        this.maxNumberOfChecks = numberOfChecks;
    }

    toString() {
        return `Rule [${this.numberOfPieces}] pieces -> max ${this.maxNumberOfChecks}`;
    }

}

class PerpetualCheckingRulesSet {

    /**
     * @type {PerpetualCheckingRule[]}
     */
    rules = [];

    constructor(rules) {
        this.rules = rules;
    }

}

class RuleSetLimitIndication {

    /**
     * @type {number}
     */
    #numberOfChecks;

    /**
     * @type {number}
     */
    #maxNumberOfChecks;

    /**
     * @type {PieceAtPosition[]}
     */
    #attackers = [];

    /**
     * @param numberOfChecks {number}
     * @param maxNumberOfChecks {number}
     * @param attackers {PieceAtPosition[]}
     */
    constructor(numberOfChecks, maxNumberOfChecks, attackers) {
        this.#numberOfChecks = numberOfChecks;
        this.#maxNumberOfChecks = maxNumberOfChecks;
        this.#attackers = attackers;
    }

    get numberOfChecks() {
        return this.#numberOfChecks;
    }

    get maxNumberOfChecks() {
        return this.#maxNumberOfChecks;
    }

    get attackers() {
        return this.#attackers;
    }

}

/**
 * If the player makes 6 consecutive "checking" moves with the same piece
 * If the player makes 12 consecutive "checking" moves with the same two pieces
 * If the player makes 18 consecutive "checking" moves with the same three pieces
 *
 * source: // https://www.iggamecenter.com/en/rules/xiangqi#perpetual
 */
const DEFAULT_RULES_SET =
    new PerpetualCheckingRulesSet([
        new PerpetualCheckingRule(1, 6),
        new PerpetualCheckingRule(2, 12),
        new PerpetualCheckingRule(3, 18)
    ]);

class AttackMap {

    /**
     *
     * @type {Map<PieceAtPosition, PieceAtPosition[]>}
     */
    #map = new Map();

    constructor(color, startFen, moves, onlyGenerals) {
        // init
        let board = new Board();
        board.loadFen(startFen);
        moves.forEach(move => board.registerMove(move));
        board.forceColorToPlay(color);

        // calculation
        let allPieces = board.listPiecePositions();
        let playerPieces = [];
        let opponentPieces = [];

        allPieces.forEach(pieceAtPosition => {
            if (pieceAtPosition.isColor(color)) {
                playerPieces.push(pieceAtPosition);
            } else {
                opponentPieces.push(pieceAtPosition);
            }
        });

        if (onlyGenerals) {
            opponentPieces = opponentPieces.filter(pieceAtPosition => pieceAtPosition.piece.pieceChar.toUpperCase() === 'K');
        }

        playerPieces.forEach(playerPiece => {
            let legalMoves = board.listLegalMovesFrom(playerPiece.position);
            let attackedPieces = opponentPieces.filter(opponentPiece => legalMoves.some(move => move.to.isEqualsTo(opponentPiece.position)));
            if (attackedPieces.length > 0) {
                this.#map.set(playerPiece, attackedPieces);
            }
        });
    }

    /**
     * @return {PieceAtPosition[]}
     */
    listAttackers() {
        return Array.from(this.#map.keys());
    }

}

class AttackMapAtIndex {

    /**
     * @type {number}
     */
    #index;

    /**
     * @type {AttackMap}
     */
    #attackMap;

    constructor(index, attackMap) {
        this.#index = index;
        this.#attackMap = attackMap;
    }

    get index() {
        return this.#index;
    }

    get attackMap() {
        return this.#attackMap;
    }

}

class PerpetualCheckingHistoryForColor {

    /**
     * @type {string}
     */
    #color;

    /**
     * @type {AttackMapAtIndex[]}
     */
    #checks = [];

    /**
     *
     * @param color {string} Color of the player who is giving check, not the one being in check.
     */
    constructor(color) {
        this.#color = color;
    }

    get size() {
        return this.#checks.length;
    }

    addCheckAt(index, attackMap) {
        this.#checks.push(new AttackMapAtIndex(index, attackMap));
    }

    /**
     * @return {PieceAtPosition[]}
     */
    listAttackers() {
        let result = [];

        this
            .#checks
            .flatMap(attackMap => attackMap.attackMap.listAttackers())
            .forEach(attacker => {
                let alreadyInResult = result.some(it => it.piece.isEqualsTo(attacker.piece));
                if (!alreadyInResult) {
                    result.push(attacker);
                }
            });

        return result;
    }

    toString() {
        let attackersStr = this.listAttackers().map(attacker => attacker.piece.toString()).join(', ');
        return `[${this.#color}] Perpetual checks given by ${attackersStr} at ${this.#checks.map(check => check.index).join(', ')}`;
    }

}

class PerpetualCheckingTracker {

    #moves = [];
    #startFen;
    #board = new Board();
    #index = 0;
    #rulesSet = DEFAULT_RULES_SET;

    /**
     * @type {Map<string, PerpetualCheckingHistoryForColor>}
     */
    #historyByColor = new Map();

    constructor(startFen = DEFAULT_START_FEN) {
        this.#startFen = startFen;
        this.#board.loadFen(startFen);
        this.#historyByColor.set(Color.RED, new PerpetualCheckingHistoryForColor(Color.RED));
        this.#historyByColor.set(Color.BLACK, new PerpetualCheckingHistoryForColor(Color.BLACK));
    }

    /**
     * @param moves {HalfMove[]}
     */
    addMoves(moves) {
        moves.forEach(move => this.addMove(move));
    }

    /**
     * @param move {HalfMove}
     */
    addMove(move) {
        this.#moves.push(move);
        this.#board.registerMove(move);
        let colorToPlayNext = this.#board.getColorToPlay();
        let moveColor = reverseColor(colorToPlayNext);

        if (this.#board.isInCheck(colorToPlayNext)) {
            this.#addCheckToHistory(moveColor);
        } else {
            this.#resetState(moveColor);
        }

        this.#index++;
    }

    #addCheckToHistory(color) {
        let attackMap = new AttackMap(color, this.#startFen, this.#moves, true);
        this.#historyByColor.get(color).addCheckAt(this.#index, attackMap);
        if (this.#historyByColor.get(color).size > 1) {
            console.log(this.#historyByColor.get(color).toString());
        }
    }

    #resetState(color) {
        this.#historyByColor.set(color, new PerpetualCheckingHistoryForColor(color));
    }

    /**
     * @param color {string}
     * @return {RuleSetLimitIndication|null}
     */
    findCurrentLimitIndicator(color) {
        let history = this.#historyByColor.get(color);
        let attackers = history.listAttackers();
        let rule = this.#rulesSet.rules.find(rule => rule.numberOfPieces === attackers.length);
        if (rule != null) {
            let numberOfChecks = history.size;
            let maxNumberOfChecks = rule.maxNumberOfChecks;
            return new RuleSetLimitIndication(numberOfChecks, maxNumberOfChecks, attackers);
        } else {
            return null;
        }
    }

}

class PerpetualCheckingTrackerWidget {

    #elementId;
    #tracker = new PerpetualCheckingTracker();

    constructor(elementId) {
        this.#elementId = elementId;
    }

    /**
     * @param moves {HalfMove[]}
     */
    addMoves(moves) {
        this.#tracker.addMoves(moves);
    }

    /**
     * @param move {HalfMove}
     */
    addMove(move) {
        this.#tracker.addMove(move);
    }

    render() {
        const element = document.getElementById(this.#elementId);
        const boxes = element.getElementsByClassName(PERPETUAL_CHECKING_INFO_BOX_SUB_BOXES);
        if (boxes.length === 2) {
            this.#renderForColor(Color.RED, boxes[0]);
            this.#renderForColor(Color.BLACK, boxes[1]);
        }
    }

    /**
     * @param color {string}
     * @param box {HTMLElement}
     */
    #renderForColor(color, box) {
        const piecesLabel = box.getElementsByClassName(PERPETUAL_CHECKING_INFO_BOX_PIECES_LABEL)[0];
        const limitLabel = box.getElementsByClassName(PERPETUAL_CHECKING_INFO_BOX_LIMIT_LABEL)[0];

        const limitIndicator = this.#tracker.findCurrentLimitIndicator(color);
        if (limitIndicator != null) {
            const attackers = limitIndicator.attackers.map(it => it.piece.pieceChar).join(', ');
            const counter = `${limitIndicator.numberOfChecks}/${limitIndicator.maxNumberOfChecks}`;
            piecesLabel.innerHTML = 'pieces: ' + attackers;
            limitLabel.innerHTML = 'limit: ' + counter;
        } else {
            piecesLabel.innerHTML = 'pieces: --';
            limitLabel.innerHTML = 'limit: --';
        }
    }

}
