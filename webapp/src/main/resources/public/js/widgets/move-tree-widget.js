/*
 * Copyright (C) 2026  Encelade SRL
 * Copyright (C) 2026  elephantchess.io
 * Copyright (C) 2026  Benoît Vleminckx (benckx)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see <https://www.gnu.org/licenses/>.
 */

const COLOR_DELTA_FOR_ONE_LEVEL = 10;
const HOVERING_COLOR = '#01138a';
const MOVE_TREE_WIDGET_SAVE_DEBOUNCE_MS = 150;

const BRANCHES_COLORS = [
    '#01138a',
    '#470076',
    '#aa0000',
    '#a64509',
    '#23580f',
];

function branchColor(childIndex) {
    const colorIndex = childIndex % BRANCHES_COLORS.length;
    return BRANCHES_COLORS[colorIndex];
}

function colorForLevel(level) {
    const colorDelta = level * COLOR_DELTA_FOR_ONE_LEVEL;
    return `#${lightenOrDarkenColor('e0d2bd', -colorDelta)}`;
}

function initialColor(node) {
    return colorForLevel(node.level);
}

/**
 * Holds the eval annotation symbol (??, ?!, etc.) displayed in the move tree, together
 * with the calculation that produced it (eval of the actual move, eval of the engine's
 * best move, centi-pawn delta and resulting symbol).
 *
 * This is what we attach to a {@link MoveTreeNode} instead of only the raw symbol string,
 * so the breakdown can be surfaced as a tooltip.
 */
class AnnotationEvalDetails {

    #symbolEnum;
    #symbol;
    #engineCp;
    #actualMoveCp;
    #delta;

    /**
     * @param symbolEnum {string} one of {@link MoveAnnotationSymbolTypes}
     * @param engineCp {number} heuristic centi-pawn value of the engine's best move
     * @param actualMoveCp {number} heuristic centi-pawn value of the actual move
     * @param delta {number} centi-pawn delta (engineCp - actualMoveCp)
     */
    constructor(symbolEnum, engineCp, actualMoveCp, delta) {
        this.#symbolEnum = symbolEnum;
        this.#symbol = moveAnnotationEnumToSymbol(symbolEnum);
        this.#engineCp = engineCp;
        this.#actualMoveCp = actualMoveCp;
        this.#delta = delta;
    }

    /**
     * @return {string}
     */
    get symbol() {
        return this.#symbol;
    }

    /**
     * @return {string}
     */
    get cssClass() {
        return `${this.#symbolEnum.toLowerCase()}-annotation-label`;
    }

    /**
     * @param value {number}
     * @return {string}
     */
    #formatCpValue(value) {
        if (value == null || Number.isNaN(value)) {
            return '--';
        }
        const rounded = Math.round(value);
        return `${rounded > 0 ? '+' : ''}${rounded}`;
    }

    /**
     * @return {string}
     */
    #thresholdText() {
        switch (this.#symbolEnum) {
            case MoveAnnotationSymbolTypes.BLUNDER:
                return '-300';
            case MoveAnnotationSymbolTypes.MISTAKE:
                return '-100';
            case MoveAnnotationSymbolTypes.INACCURACY:
                return '-50';
            case MoveAnnotationSymbolTypes.INTERESTING:
                return '+50';
            case MoveAnnotationSymbolTypes.GOOD:
                return '+100';
            case MoveAnnotationSymbolTypes.BRILLIANT:
                return '+300';
            default:
                return '';
        }
    }

    /**
     * Multi-line text describing the calculation behind the annotation symbol.
     *
     * @return {string}
     */
    toTooltipText() {
        return [
            `Move: ${this.#formatCpValue(this.#actualMoveCp)}`,
            `Engine best: ${this.#formatCpValue(this.#engineCp)}`,
            `CPL: ${this.#formatCpValue(this.#delta)}`,
            `${moveAnnotationEnumToLabel(this.#symbolEnum)} (${this.#thresholdText()})`,
        ].join('\n');
    }

}

class MoveTreeNodeDto {

    #nodeId;
    #move;
    #level;
    #previous = null;
    #next = null;
    #childNodes = [];
    #annotation = null;

    constructor(nodeId, moveAsUci, level, previous, next, childNodes, annotation) {
        this.#nodeId = nodeId;
        this.#move = moveAsUci;
        this.#level = level;
        this.#previous = previous;
        this.#next = next;
        this.#childNodes = childNodes;
        this.#annotation = annotation;
    }

    get nodeId() {
        return this.#nodeId;
    }

    get move() {
        return this.#move;
    }

    get level() {
        return this.#level;
    }

    /**
     * @return {string|null}
     */
    get previous() {
        return this.#previous;
    }

    /**
     * @return {string|null}
     */
    get next() {
        return this.#next;
    }

    get childNodes() {
        return this.#childNodes;
    }

    get annotation() {
        return this.#annotation;
    }

    toLiteral() {
        return {
            id: this.#nodeId,
            move: this.#move,
            level: this.#level,
            previous: this.#previous,
            next: this.#next,
            childNodes: this.#childNodes,
            annotation: this.#annotation,
        };
    }

}

class MoveTreeNode {

    // attributes
    #nodeId = randomId();

    /**
     * @type {HalfMove}
     */
    #move;
    #level;
    #position;

    /**
     * In other widgets, we set DEFAULT_START_FEN here, so it's easier
     * In this case, null means we use the default start fen
     */
    #customStartFen;

    // graph connections
    #previous = null;
    #next = null;
    #childNodes = [];

    // derived data
    #fen = null;
    #fenKey = null;
    #eval = null;

    /**
     * Calculation behind the eval annotation symbol (if any), used to render a tooltip.
     *
     * @type {AnnotationEvalDetails|null}
     */
    #annotationDetails = null;

    #annotationText = null;

    /**
     * Keep annotation symbol colors in-between renders
     *
     * @type {string[]}
     */
    #evalCssClasses = [];

    constructor(move, level) {
        this.#move = move;
        this.#level = level;
    }

    /**
     * @return {string|null}
     */
    get nodeId() {
        return this.#nodeId;
    }

    set nodeId(value) {
        this.#nodeId = value;
    }

    /**
     * @return {HalfMove}
     */
    get move() {
        return this.#move;
    }

    /**
     * @return {number}
     */
    get level() {
        return this.#level;
    }

    /**
     * @return {number}
     */
    get position() {
        if (this.#position == null) {
            let nodes = this.getAllNodesLeadingUpTo();
            this.#position = nodes.length - 1;
        }
        return this.#position;
    }

    /**
     * @param value {string}
     */
    set startFen(value) {
        if (value !== DEFAULT_START_FEN) {
            this.#customStartFen = value;
        }
    }

    /**
     * @return {string}
     */
    get startFen() {
        if (this.#customStartFen != null) {
            return this.#customStartFen;
        } else {
            return DEFAULT_START_FEN;
        }
    }

    get fullMoveCount() {
        return halfMoveIndexToFullMove(this.position);
    }

    /**
     * @returns {MoveTreeNode|null}
     */
    get previous() {
        return this.#previous;
    }

    /**
     * @param node {MoveTreeNode}
     */
    set previous(node) {
        this.#previous = node;
    }

    /**
     * @return {MoveTreeNode|null}
     */
    get next() {
        return this.#next;
    }

    /**
     * @param node {MoveTreeNode}
     */
    set next(node) {
        this.#next = node;
    }

    hasChildNodes() {
        return this.#childNodes.length > 0;
    }

    /**
     * @return {MoveTreeNode[]}
     */
    get childNodes() {
        return this.#childNodes;
    }

    set childNodes(value) {
        this.#childNodes = value;
    }

    /**
     * @returns {string}
     */
    get fen() {
        if (this.#fen == null) {
            this.#fen = this.#calculateFen();
        }
        return this.#fen;
    }

    /**
     * @returns {string}
     */
    get fenKey() {
        if (this.#fenKey == null) {
            this.#fenKey = resetFenFullMovesCount(this.fen);
        }
        // noinspection JSValidateTypes
        return this.#fenKey;
    }

    /**
     * @return {string|null}
     */
    get eval() {
        return this.#eval;
    }

    /**
     * @param value {string|null}
     */
    set eval(value) {
        this.#eval = value;
    }

    /**
     * @return {AnnotationEvalDetails|null}
     */
    get annotationDetails() {
        return this.#annotationDetails;
    }

    /**
     * @param value {AnnotationEvalDetails|null}
     */
    set annotationDetails(value) {
        this.#annotationDetails = value;
    }

    get annotation() {
        if (this.hasAnnotation()) {
            return this.#annotationText;
        } else {
            return null;
        }
    }

    set annotation(value) {
        this.#annotationText = value;
    }

    hasAnnotation() {
        return this.#annotationText != null && this.#annotationText.trim().length > 0;
    }

    /**
     * @return {string[]}
     */
    get evalCssClasses() {
        return this.#evalCssClasses;
    }

    /**
     * @param cssClass {string}
     */
    addEvalCssClass(cssClass) {
        this.#evalCssClasses.push(cssClass);
    }

    clearEvalCssClasses() {
        this.#evalCssClasses = [];
    }

    hasPrevious() {
        return this.#previous != null;
    }

    hasNext() {
        return this.#next != null;
    }

    /**
     * Calculate and returns the label, but doesn't update UI
     *
     * @param moveFormat {string}
     * @return {string}
     */
    renderLabel(moveFormat) {
        const moves = this.getAllNodesLeadingUpTo().map(node => node.move);
        const translated = translateMovesFormatTakeLast(moves, moveFormat.toString(), this.startFen);
        if (translated != null) {
            return translated;
        } else {
            return this.move.toAlgebraic();
        }
    }

    /**
     * Excludes child nodes, includes this node
     *
     * @return {MoveTreeNode[]}
     */
    getAllNodesDownstream() {
        let nodes = [];
        let currentNode = this;
        while (currentNode != null) {
            nodes.push(currentNode);
            currentNode = currentNode.#next;
        }
        return nodes;
    }

    /**
     * Get all the nodes of the branch leading up to this node, including this node
     *
     * @return {MoveTreeNode[]}
     */
    getAllNodesLeadingUpTo() {
        const nodes = [];
        nodes.push(this);
        let currentNode = this.#previous;
        while (currentNode != null) {
            nodes.push(currentNode);
            currentNode = currentNode.#previous;
        }
        return nodes.reverse();
    }

    getLastNodeOfBranch() {
        let node = this;
        while (node.hasNext()) {
            node = node.#next;
        }
        return node;
    }

    /**
     * Find the parent Node where the branch starts
     */
    getBranchOffParentNode() {
        if (this.#level > 0) {
            let currentNode = this.#previous;
            while (currentNode != null) {
                if (currentNode.#level === this.#level - 1) {
                    return currentNode;
                }
                currentNode = currentNode.#previous;
            }
        } else {
            return null;
        }
    }

    /**
     * @return {MoveTreeNode[]}
     */
    getAllNodes() {
        let nodes = [];
        let currentNode = this;
        while (currentNode != null) {
            nodes.push(currentNode);
            for (let i = 0; i < currentNode.#childNodes.length; i++) {
                let childNode = currentNode.#childNodes[i];
                nodes = nodes.concat(childNode.getAllNodes());
            }
            currentNode = currentNode.next;
        }
        return nodes;
    }

    /**
     * Append to the end or branch off
     */
    appendOrBranchOff(moves) {
        if (this.hasNext()) {
            return this.#createBranchWithMoves(moves);
        } else {
            return this.#appendAtTheEnd(moves);
        }
    }

    #createBranchWithMoves(moves) {
        let nodes = MoveTreeNode.mapMovesToNodes(moves, this.#level + 1);
        if (this.#customStartFen != null) {
            nodes.forEach(node => node.startFen = this.#customStartFen);
        }
        this.#childNodes.push(nodes[0]);
        nodes[0].#previous = this;
        return nodes[nodes.length - 1];
    }

    #appendAtTheEnd(moves) {
        let nodes = MoveTreeNode.mapMovesToNodes(moves, this.#level);
        if (this.#customStartFen != null) {
            nodes.forEach(node => node.startFen = this.#customStartFen);
        }
        this.#next = nodes[0];
        nodes[0].#previous = this;
        return nodes[nodes.length - 1];
    }

    #calculateFen() {
        let moves = this.getAllNodesLeadingUpTo().map(node => node.move);
        return calculateFen(moves, this.startFen);
    }

    /**
     * Delete the branch this node belongs to
     * @returns {string|null} branchId of the deleted branch
     */
    deleteBranch() {
        if (this.#level > 0) {
            let branchOffNode = null;
            let firstNodeOfBranch = null;

            // it's the 'previous' node in the iteration (we're going backwards/up from the selected node),
            // it's actually 'next' or a child node in the graph
            let previousCurrentNode = this;
            let currentNode = this.#previous;

            while (currentNode != null) {
                if (currentNode.#level === this.#level - 1) {
                    branchOffNode = currentNode;
                    firstNodeOfBranch = previousCurrentNode;
                    break;
                }
                previousCurrentNode = currentNode;
                currentNode = currentNode.#previous;
            }

            if (branchOffNode != null && firstNodeOfBranch != null) {
                let index = branchOffNode.#childNodes.map(node => node.#nodeId).indexOf(firstNodeOfBranch.#nodeId);
                if (index >= 0) {
                    branchOffNode.#childNodes = branchOffNode.#childNodes.splice(index + 1, 1);
                    return branchOffNode.#nodeId + '-' + index;
                }
            }
        }

        return null;
    }

    deleteAllNodesBelow() {
        if (this.hasNext()) {
            this.#next = null;
            return true;
        }

        return false;
    }

    toDto() {
        let previous = null;
        if (this.#previous != null) {
            previous = this.#previous.nodeId;
        }

        let next = null;
        if (this.#next != null) {
            next = this.#next.nodeId;
        }

        return new MoveTreeNodeDto(
            this.#nodeId,
            this.#move.toUci(),
            this.#level,
            previous,
            next,
            this.childNodes.map(node => node.nodeId),
            this.#annotationText
        );
    }

    /**
     * @param startFen {string}
     * @return {string}
     */
    toPgn(startFen) {
        let nodes = this.getAllNodesLeadingUpTo();
        let movesAsPgn = translateMovesToPgn(nodes.map(node => node.move), true, startFen);
        let lastPgn = movesAsPgn[movesAsPgn.length - 1];
        let lastNode = nodes[nodes.length - 1];
        let position = lastNode.position;
        if (position % 2 === 0) {
            lastPgn = lastNode.fullMoveCount + '. ' + lastPgn;
        }
        if (this.hasAnnotation()) {
            lastPgn += ' {' + this.#annotationText + '}';
        }
        return lastPgn;
    }

    toString() {
        return 'Node[(' + this.nodeId + ') ' + this.move.toAlgebraic() + ']';
    }

    static areNotEquals(n1, n2) {
        return !MoveTreeNode.areEquals(n1, n2);
    }

    static areEquals(n1, n2) {
        return n1 != null && n2 != null && n1.nodeId === n2.nodeId;
    }

    static mapMovesToNodes(moves, level) {
        function connectNodes(nodes) {
            nodes.forEach((node, i) => {
                if (i < nodes.length - 1) {
                    node.next = nodes[i + 1];
                }
                if (i > 0) {
                    node.previous = nodes[i - 1];
                }
            });
        }

        let nodes = moves.map(move => new MoveTreeNode(move, level));
        connectNodes(nodes);
        return nodes;
    }

}

class MoveTree {

    #rootNode = null;
    #startFen = DEFAULT_START_FEN;
    #useDefaultFen = true;

    get rootNode() {
        return this.#rootNode;
    }

    /**
     * @param value {string}
     */
    set startFen(value) {
        this.#startFen = value;
        this.#useDefaultFen = (value === DEFAULT_START_FEN);
    }

    get startFen() {
        if (this.#useDefaultFen) {
            return DEFAULT_START_FEN;
        } else {
            return this.#startFen;
        }
    }

    isEmpty() {
        return this.#rootNode == null;
    }

    /**
     * @returns {MoveTreeNode[]}
     */
    getAllNodes() {
        if (this.#rootNode == null) {
            return [];
        } else {
            return this.#rootNode.getAllNodes();
        }
    }

    /**
     * @returns {MoveTreeNode[]}
     */
    getAllNodesMatchingFenKey(fenKey) {
        return this.getAllNodes().filter(node => node.fenKey === fenKey);
    }

    /**
     * @returns {[MoveTreeNode]}
     */
    getMainBranchNodes() {
        let mainLineNodes = [];
        let node = this.#rootNode;
        while (node != null) {
            mainLineNodes.push(node);
            node = node.next;
        }
        return mainLineNodes;
    }

    setMoves(moves) {
        let nodes = MoveTreeNode.mapMovesToNodes(moves, 0);
        if (!this.#useDefaultFen) {
            nodes.forEach(node => node.startFen = this.#startFen);
        }
        this.#rootNode = nodes[0];
        return nodes[nodes.length - 1];
    }

    addMovesAtTheBottom(moves) {
        let newNodes = moves.map(move => this.#addMoveAtTheBottom(move));
        return newNodes[newNodes.length - 1];
    }

    #addMoveAtTheBottom(move) {
        let lastNode = this.getMainBranchNodes().pop();
        if (lastNode != null) {
            let nextNode = new MoveTreeNode(move, 0);
            nextNode.startFen = this.#startFen;
            nextNode.previous = lastNode;
            lastNode.next = nextNode;
            return nextNode;
        } else {
            this.#rootNode = new MoveTreeNode(move, 0);
            this.#rootNode.startFen = this.#startFen;
            return this.#rootNode;
        }
    }

    /**
     * @return {MoveTreeNodeDto[]}
     */
    serializeToDtos() {
        return this
            .getAllNodes()
            .map(node => node.toDto());
    }

    /**
     * @param nodeDtos {MoveTreeNodeDto[]}
     */
    deserializeNodeDtos(nodeDtos) {
        /**
         * Just to render type
         *
         * @return {Map<string, MoveTreeNode>}
         */
        function makeNodeMap() {
            return new Map();
        }

        // DTO -> Nodes
        let nodesMap = makeNodeMap();
        for (let i = 0; i < nodeDtos.length; i++) {
            let move = HalfMove.parseUci(nodeDtos[i].move);
            let node = new MoveTreeNode(move, nodeDtos[i].level);
            node.nodeId = nodeDtos[i].nodeId;
            node.annotation = nodeDtos[i].annotation;
            nodesMap.set(node.nodeId, node);
        }

        // connect nodes
        for (let i = 0; i < nodeDtos.length; i++) {
            let node = nodesMap.get(nodeDtos[i].nodeId);
            if (nodeDtos[i].previous != null) {
                node.previous = nodesMap.get(nodeDtos[i].previous);
            }
            if (nodeDtos[i].next != null) {
                node.next = nodesMap.get(nodeDtos[i].next);
            }
            if (nodeDtos[i].childNodes != null) {
                node.childNodes = nodeDtos[i].childNodes.map(childNodeId => nodesMap.get(childNodeId));
            }

            // TODO: move to its own loop?
            node.startFen = this.#startFen;
        }

        // find root node
        let rootNode = null;
        for (let i = 0; i < nodeDtos.length; i++) {
            let node = nodesMap.get(nodeDtos[i].nodeId);
            if (node.previous == null && node.level === 0) {
                rootNode = node;
                break;
            }
        }

        this.#rootNode = rootNode;
    }

}

/**
 * Manage the [-] and [+] buttons as well as well as the container for the branch
 */
class BranchGui {

    #parentNode;
    #childIndex;
    #color;

    #buttonContainer = document.createElement('div');
    #branchContainer = document.createElement('div');

    constructor(parentNode, childIndex) {
        this.#parentNode = parentNode;
        this.#childIndex = childIndex;
        this.#color = branchColor(this.#childIndex);

        this.#buttonContainer.id = 'branch-toggle-placeholder-' + this.branchId;
        this.#buttonContainer.className = 'branch-toggle-placeholder';
        this.#buttonContainer.style.color = this.#color;

        this.#branchContainer.id = 'sub-branch-container-' + this.branchId;
        this.#branchContainer.className = 'sub-branch-container';
        this.#branchContainer.style.color = this.#color;
    }

    get buttonContainer() {
        return this.#buttonContainer;
    }

    get subBranchContainer() {
        return this.#branchContainer;
    }

    get parentNodeId() {
        return this.#parentNode.nodeId;
    }

    get branchId() {
        return this.#parentNode.nodeId + '-' + this.#childIndex.toString();
    }

    get rootNodeOfBranch() {
        return this.#parentNode.childNodes[this.#childIndex];
    }

    isClosed() {
        return this.#branchContainer.style.display === 'none'
    }

    isOpen() {
        return !this.isClosed();
    }

    open() {
        this.#branchContainer.style.display = 'block';
        this.#buttonContainer.innerHTML = '-';
    }

    close() {
        this.#branchContainer.style.display = 'none';
        this.#buttonContainer.innerHTML = '+';
    }

    forceColor(color) {
        this.#buttonContainer.style.color = color;
    }

    revertForcedColor() {
        this.#buttonContainer.style.color = this.#color;
    }

}

/**
 * Generic class for buttons 'beginning', 'previous', 'next', 'end'
 */
class NavigationPanelButtonGui {

    #container = document.createElement('div');
    #listeners = [];

    /**
     * @param id {string}
     * @param float {string}
     * @param imgSrc {string}
     * @param alt {string}
     */
    constructor(id, float, imgSrc, alt) {
        const img = document.createElement('img');
        img.setAttribute('src', `${ICON_PATH}/${imgSrc}`);
        img.setAttribute('alt', alt);

        this.#container.id = id;
        this.#container.style.float = float;
        if (float === 'right') {
            this.#container.style.transform = 'rotate(180deg)';
        }
        this.#container.classList.add('navigation-panel-button', 'disabled');
        this.#container.addEventListener('click', (e) => {
            if (e.target.classList.contains('enabled')) {
                this.#listeners.forEach(listener => listener(e));
            }
        });
        img.addEventListener('click', (e) => {
            if (e.target.parentElement.classList.contains('enabled')) {
                this.#listeners.forEach(listener => listener(e));
            }
        });
        this.#container.append(img);
    }

    /**
     * @return {HTMLDivElement}
     */
    get container() {
        return this.#container;
    }

    /**
     * @param value {boolean}
     */
    set enabled(value) {
        if (value) {
            this.#container.classList.add('enabled');
            this.#container.classList.remove('disabled');
        } else {
            this.#container.classList.remove('enabled');
            this.#container.classList.add('disabled');
        }
    }

    addListener(listener) {
        this.#listeners.push(listener);
    }

}

/**
 * Panel that contains the buttons 'beginning', 'previous', 'next', 'end'
 */
class NavigationPanelGui {

    #container = null;

    /**
     * @type {MoveTreeWidget}
     */
    #parent;

    /**
     * @type {NavigationPanelButtonGui}
     */
    #beginningButton;

    /**
     * @type {NavigationPanelButtonGui}
     */
    #previousButton;

    /**
     * @type {NavigationPanelButtonGui}
     */
    #nextButton;

    /**
     * @type {NavigationPanelButtonGui}
     */
    #endButton;

    #isDownloadEnabled = false;
    #downloadButtonContainer = null;

    /**
     *
     * @param {MoveTreeWidget} parent
     * @param {object}  options
     */
    constructor(parent, options) {
        const containerId = options.containerId;

        if (containerId === undefined) {
            throw new Error('containerId of NavigationPanelGui is undefined');
        }

        if (options.isDownloadButtonEnabled !== undefined) {
            this.#isDownloadEnabled = options.isDownloadButtonEnabled;
        }

        this.#container = document.getElementById(containerId);
        if (this.#container == null) {
            throw new Error(`NavigationPanelGui container with id ${containerId} not found`);
        }
        if (!this.#container.classList.contains('navigation-panel')) {
            throw new Error(`${containerId} does not have class 'navigation-panel'`);
        }
        this.#container.classList.add('navigation-panel');
        this.#container.innerHTML = '';

        this.#parent = parent;
        const parentContainerId = this.#parent.containerId;

        this.#beginningButton =
            new NavigationPanelButtonGui(
                `${parentContainerId}-navigation-beginning-button`,
                'left',
                'navigation-beginning.png',
                'Navigate to the beginning'
            );

        this.#previousButton =
            new NavigationPanelButtonGui(
                `${parentContainerId}-navigation-previous-button`,
                'left',
                'navigation-previous.png',
                'Navigate to previous'
            );

        this.#nextButton =
            new NavigationPanelButtonGui(
                `${parentContainerId}-navigation-next-button`,
                'right',
                'navigation-previous.png',
                'Navigate to previous'
            );

        this.#endButton =
            new NavigationPanelButtonGui(
                `${parentContainerId}-navigation-end-button`,
                'right',
                'navigation-beginning.png',
                'Navigate to the end'
            );

        // render
        const backwardCell = document.createElement('td');
        backwardCell.className = 'backward-navigation';

        const indexCell = document.createElement('td');
        indexCell.className = 'indexes-cell';

        const forwardCell = document.createElement('td');
        forwardCell.className = 'forward-navigation';

        const row = document.createElement('tr');
        row.append(backwardCell, indexCell, forwardCell);

        const table = document.createElement('table');
        table.className = 'move-history-navigation-table';
        table.append(row);

        this.#container.append(table);

        backwardCell.append(
            this.#beginningButton.container,
            this.#previousButton.container
        );

        forwardCell.append(
            this.#endButton.container,
            this.#nextButton.container
        );

        // download PGN button
        if (this.#isDownloadEnabled) {
            indexCell.append(this.#buildDownloadButton());
        }
    }

    addClickedBeginningListener(listener) {
        this.#beginningButton.addListener(listener);
    }

    addClickedPreviousListener(listener) {
        this.#previousButton.addListener(listener);
    }

    addClickedNextListener(listener) {
        this.#nextButton.addListener(listener);
    }

    addClickedEndListener(listener) {
        this.#endButton.addListener(listener);
    }

    /**
     * @param value {boolean}
     */
    setBackwardButtonsEnabled(value) {
        this.#beginningButton.enabled = value;
        this.#previousButton.enabled = value;
    }

    /**
     * @param value {boolean}
     */
    setForwardButtonsEnabled(value) {
        this.#nextButton.enabled = value;
        this.#endButton.enabled = value;
    }

    /**
     * @param value {boolean}
     */
    setDownloadButtonEnabled(value) {
        if (this.#isDownloadEnabled && this.#downloadButtonContainer != null) {
            if (value) {
                this.#downloadButtonContainer.classList.add('enabled');
                this.#downloadButtonContainer.classList.remove('disabled');
            } else {
                this.#downloadButtonContainer.classList.remove('enabled');
                this.#downloadButtonContainer.classList.add('disabled');
            }
        }
    }

    #buildDownloadButton() {
        function isButtonEnabled(element) {
            if (element == null) {
                return false;
            } else if (element.tagName.toLowerCase() === 'div') {
                if (element.classList.contains('download-pgn-button')) {
                    if (element.classList.contains('disabled')) {
                        return false;
                    } else if (element.classList.contains('enabled')) {
                        return true;
                    }
                }
            }

            return isButtonEnabled(element.parentElement);
        }

        const img = document.createElement('img');
        img.setAttribute('src', `${ICON_PATH}/direct-download.png`);

        const link = document.createElement('a');
        link.href = '#';
        link.append(img);

        this.#downloadButtonContainer = document.createElement('div');
        this.#downloadButtonContainer.id = 'download-button-container';
        this.#downloadButtonContainer.classList.add('navigation-panel-button', 'disabled', 'download-pgn-button');
        this.#downloadButtonContainer.append(link);

        link.addEventListener('click', (e) => {
            if (isButtonEnabled(e.target)) {
                let file = new File([this.#parent.exportToPgnNested()], 'game.pgn', {type: 'text/plain'});
                link.href = URL.createObjectURL(file);
                link.download = file.name;
            }
        });

        this.#downloadButtonContainer.addEventListener('click', (e) => {
            // skip if the click already happened on the inner <a> (its own handler + browser default already triggers the download)
            if (e.target.closest('a') === link) {
                return;
            }
            if (isButtonEnabled(e.target)) {
                link.click();
            }
        });

        addToolTip(this.#downloadButtonContainer, 'Download moves as PGN');

        return this.#downloadButtonContainer;
    }

}

class MoveTreeContextualMenu extends ContextualMenu {

    /**
     * @type {MoveTreeWidget}
     */
    #moveTreeWidget;

    /**
     * @type {null|AnnotationBox}
     */
    #annotationBox;

    /**
     * @type {MoveTreeNode}
     */
    #node;

    #expandAllItem;
    #collapseAllItem;
    #annotationItem;
    #selectMoveNodeEvalFormatSymbols;
    #selectMoveNodeEvalFormatEval;
    #importMovesItem;
    #deleteBranchItem;
    #deleteAllBelowItem;

    #selectMoveNodeEvalFormatSymbolsLabel = 'Show symbols';
    #selectMoveNodeEvalFormatEvalLabel = 'Show eval';

    #settingsManager = new SettingsManager();

    constructor(moveTreeWidget) {
        super('move-tree-container-contextual-menu');
        this.#moveTreeWidget = moveTreeWidget;
        this.#expandAllItem = this.addSimpleItem('Expand all', () => moveTreeWidget.openAllBranches());
        this.#collapseAllItem = this.addSimpleItem('Collapse all', () => moveTreeWidget.closeAllBranches());
        this.#annotationItem = this.addItemWithTopSeparator('Annotation', () => this.#annotationBox.showForNode(this.#node));
        this.#selectMoveNodeEvalFormatSymbols = this.addItemWithTopSeparator(this.#selectMoveNodeEvalFormatSymbolsLabel, () => {
            this.#settingsManager.moveNodeEvalFormat = MoveNodeEvalFormat.ANNOTATION_SYMBOLS;
            this.#moveTreeWidget.refreshAllMoveNodeEval();
        });
        this.#selectMoveNodeEvalFormatEval = this.addSimpleItem(this.#selectMoveNodeEvalFormatEvalLabel, () => {
            this.#settingsManager.moveNodeEvalFormat = MoveNodeEvalFormat.NORMALIZED_CENTI_PAWNS;
            this.#moveTreeWidget.refreshAllMoveNodeEval();
        });
        this.#importMovesItem = this.addItemWithTopSeparator('Import moves', () => this.#moveTreeWidget.importMovesCallback());
        this.#deleteBranchItem = this.addItemWithTopSeparator('Delete branch', () => moveTreeWidget.deleteBranchOf(this.#node));
        this.#deleteAllBelowItem = this.addSimpleItem('Delete all below', () => moveTreeWidget.deleteAllNodesBelow(this.#node));
    }

    set annotationBox(value) {
        this.#annotationBox = value;
    }

    showAtForNode(x, y, node) {
        console.log('showAtForNode at ' + x + ', ' + y);

        this.#node = node;
        this.disableAllItems();

        if (this.#moveTreeWidget.hasClosedBranches()) {
            this.enableItem(this.#expandAllItem);
        }

        if (this.#moveTreeWidget.hasOpenBranches()) {
            this.enableItem(this.#collapseAllItem);
        }

        if (node !== null) {
            if (this.#annotationBox != null) {
                this.enableItem(this.#annotationItem);
            }
            if (node.level > 0) {
                this.enableItem(this.#deleteBranchItem);
            }
            if (node.hasNext()) {
                this.enableItem(this.#deleteAllBelowItem);
            }
        }

        this.enableItem(this.#selectMoveNodeEvalFormatSymbols);
        this.enableItem(this.#selectMoveNodeEvalFormatEval);
        this.enableItem(this.#importMovesItem);

        // render which move node eval format is selected
        switch (this.#settingsManager.moveNodeEvalFormat) {
            case MoveNodeEvalFormat.ANNOTATION_SYMBOLS:
                this.#selectMoveNodeEvalFormatSymbols.innerText = `✓ ${this.#selectMoveNodeEvalFormatSymbolsLabel}`;
                this.#selectMoveNodeEvalFormatEval.innerText = this.#selectMoveNodeEvalFormatEvalLabel;
                break;
            case MoveNodeEvalFormat.NORMALIZED_CENTI_PAWNS:
                this.#selectMoveNodeEvalFormatSymbols.innerText = this.#selectMoveNodeEvalFormatSymbolsLabel;
                this.#selectMoveNodeEvalFormatEval.innerText = `✓ ${this.#selectMoveNodeEvalFormatEvalLabel}`;
                break;
        }

        super.showAt(x, y);
    }

}

class MoveTreeWidget {

    /**
     * @type {HTMLElement}
     */
    #mainContainer;

    #moveTree = new MoveTree();

    /**
     * @type {null|MoveTreeNode}
     */
    #previouslySelectedNode = null;

    /**
     * @type {null|MoveTreeNode}
     */
    #selectedNode = null;

    #clickedNodeListeners = [];
    #navigationListeners = [];

    /**
     * Map of nodeId -> BranchGui
     *
     * @type {Map<string, BranchGui>}
     */
    #branchGuis = new Map();

    // nodeId of branches that are open
    // remember the state from one render to another
    #openedBranches = new Set();

    /**
     * @type {BoardGui}
     */
    #boardWidget = null;

    /**
     * @type {null|AnnotationBox}
     */
    #annotationBox = null;

    /**
     *
     * @type {NavigationPanelGui[]}
     */
    #navigationPanels = [];

    #isContextualMenuEnabled = false;
    #isLoadingAnimationEnabled = false;
    #contextualMenu = null;
    #importMovesCallback = () => console.warn('importMovesCallback not set');

    #metadataFetcher = () => new Map();
    #startFen = DEFAULT_START_FEN;
    #useDefaultFen = true;

    #settingsManager = new SettingsManager();
    #analysisCache = new Map();

    #stopLoadingAnimationTimeout = null;

    #keyboardNavigation = true;

    /**
     * ResizeObserver instance watching the move-tree container so drag-resize changes
     * can be detected immediately when the browser supports ResizeObserver.
     * @type {null|ResizeObserver}
     */
    #resizeObserver = null;

    /**
     * Debounce timer id used to avoid writing the cookie on every single resize tick.
     * @type {null|number}
     */
    #resizeSaveTimeout = null;

    /**
     * Last persisted pixel height so unchanged values are skipped and don't trigger
     * redundant cookie writes.
     * @type {null|number}
     */
    #lastSavedHeight = null;

    /**
     * Mouse/touch fallback listener for older browsers where ResizeObserver is not
     * available, so resize persistence still works after drag release.
     * @type {null|(() => void)}
     */
    #fallbackResizeListener = null;

    /**
     * MutationObserver used to detect widget removal from DOM and clean up all
     * resize-related listeners/observers.
     * @type {null|MutationObserver}
     */
    #disposalObserver = null;

    /**
     * Optional callback provided by the host page to load a previously saved height
     * (for example from cookies).
     * @type {null|(() => number)}
     */
    #loadPersistedHeight = null;

    /**
     * Optional callback provided by the host page to persist new heights while this
     * widget remains storage-agnostic.
     * @type {null|((height: number) => void)}
     */
    #persistHeight = null;

    constructor(options) {
        // options
        if (options.isContextualMenuEnabled !== undefined) {
            this.#isContextualMenuEnabled = options.isContextualMenuEnabled;
        }

        if (options.isLoadingAnimationEnabled !== undefined) {
            this.#isLoadingAnimationEnabled = options.isLoadingAnimationEnabled;
        }
        if (options.loadPersistedHeight !== undefined) {
            this.#loadPersistedHeight = options.loadPersistedHeight;
        }
        if (options.persistHeight !== undefined) {
            this.#persistHeight = options.persistHeight;
        }

        const containerId = options.containerId;
        if (containerId === undefined) {
            throw new Error('containerId is undefined');
        }

        // init
        this.#mainContainer = document.getElementById(containerId);
        this.#mainContainer.innerHTML = '';
        this.#applySavedHeight();
        this.#setUpResizePersistence();

        // contextual menu if enabled
        if (this.#isContextualMenuEnabled) {
            this.#contextualMenu = new MoveTreeContextualMenu(this);
            DropDownMenuManager.getInstance().registerDropDownMenu(this.#contextualMenu, [], this.#mainContainer.id);

            // contextual menu listener for when we click inside the Move History Tree container, but not on a node container
            let excludedClasses = [
                'move-container',
                'move-label-placeholder',
                'eval-placeholder',
                'branch-toggle-placeholder',
                'move-container-selected'
            ];

            this.#mainContainer.addEventListener('contextmenu', (e) => {
                let targetClasses = e.target.classList;
                let intersects = excludedClasses.filter(value => targetClasses.contains(value)).length > 0;
                if (!intersects) {
                    this.#handleRightClientEvent(e, null);
                }
            });

            this.#mainContainer.addEventListener('scroll', () => this.#contextualMenu.hide());
        }

        this.#addLoadingIconIfNeeded();
    }

    // Restores a previously persisted move-tree height when it is valid.
    // Invalid, missing, or too-small values are ignored.
    #applySavedHeight() {
        if (this.#loadPersistedHeight === null) {
            return;
        }

        const persistedHeight = this.#loadPersistedHeight();
        if (persistedHeight === null) {
            return;
        }

        if (
            !Number.isFinite(persistedHeight)
            || !Number.isInteger(persistedHeight)
            || persistedHeight < this.#minResizeHeight()
        ) {
            return;
        }

        this.#mainContainer.style.height = `${persistedHeight}px`;
        this.#lastSavedHeight = persistedHeight;
    }

    // Reads the widget's CSS min-height and returns it as a pixel integer.
    // Falls back to 0 when parsing is not possible.
    #minResizeHeight() {
        const parsed = Number.parseInt(window.getComputedStyle(this.#mainContainer).minHeight, 10);
        if (Number.isFinite(parsed) && parsed > 0) {
            return parsed;
        }
        return 0;
    }

    // Watches resize changes and saves height through the persistence callback.
    // Cleans up observers/listeners when the widget leaves the DOM.
    #setUpResizePersistence() {
        const saveHeight = () => {
            if (this.#resizeSaveTimeout !== null) {
                clearTimeout(this.#resizeSaveTimeout);
            }

            this.#resizeSaveTimeout = setTimeout(() => {
                const height = this.#mainContainer.offsetHeight;
                if (height >= this.#minResizeHeight() && height !== this.#lastSavedHeight) {
                    if (this.#persistHeight !== null) {
                        this.#persistHeight(height);
                    }
                    this.#lastSavedHeight = height;
                }
            }, MOVE_TREE_WIDGET_SAVE_DEBOUNCE_MS);
        };

        // Prefer ResizeObserver when available; keep a listener fallback for older browsers.
        if (typeof ResizeObserver !== 'undefined') {
            this.#resizeObserver = new ResizeObserver(() => saveHeight());
            this.#resizeObserver.observe(this.#mainContainer);
        } else {
            this.#fallbackResizeListener = saveHeight;
            this.#mainContainer.addEventListener('mouseup', this.#fallbackResizeListener);
            this.#mainContainer.addEventListener('touchend', this.#fallbackResizeListener);
        }

        if (document.body !== null) {
            // Stop persistence observers if this widget container gets detached from the DOM.
            this.#disposalObserver = new MutationObserver(() => {
                if (!document.body.contains(this.#mainContainer)) {
                    this.#teardownResizePersistence();
                }
            });
            this.#disposalObserver.observe(document.body, {childList: true, subtree: true});
        }
    }

    #teardownResizePersistence() {
        if (this.#resizeObserver !== null) {
            this.#resizeObserver.disconnect();
            this.#resizeObserver = null;
        }
        if (this.#fallbackResizeListener !== null) {
            this.#mainContainer.removeEventListener('mouseup', this.#fallbackResizeListener);
            this.#mainContainer.removeEventListener('touchend', this.#fallbackResizeListener);
            this.#fallbackResizeListener = null;
        }
        if (this.#resizeSaveTimeout !== null) {
            clearTimeout(this.#resizeSaveTimeout);
            this.#resizeSaveTimeout = null;
        }
        if (this.#disposalObserver !== null) {
            this.#disposalObserver.disconnect();
            this.#disposalObserver = null;
        }
    }

    enableKeyboardNavigation() {
        this.#keyboardNavigation = true;
    }

    disableKeyboardNavigation() {
        this.#keyboardNavigation = false;
    }

    // NATIVE GETTERS/SETTERS

    /**
     * @return {MoveTreeNode|null}
     */
    get selectedNode() {
        return this.#selectedNode;
    }

    /**
     * @return {string}
     */
    get containerId() {
        return this.#mainContainer.id;
    }

    set metadataFetcher(value) {
        this.#metadataFetcher = value;
    }

    addNavigationListener(listener) {
        this.#navigationPanels.forEach(panel => {
            panel.addClickedBeginningListener(listener);
            panel.addClickedPreviousListener(listener);
            panel.addClickedNextListener(listener);
            panel.addClickedEndListener(listener);
        });

        this.#navigationListeners.push(listener);
    }

    addClickedNodeListener(listener) {
        this.#clickedNodeListeners.push(listener);
    }

    /**
     * Create a new {@link NavigationPanelGui} from the options and connecting it to the {@link MoveTreeWidget}
     *
     * @param options {object}
     */
    addNavigationPanel(options) {
        const panel = new NavigationPanelGui(this, options);
        this.#navigationPanels.push(panel);

        panel.addClickedBeginningListener(() => this.#renderStartFenToBoard());
        panel.addClickedPreviousListener(() => {
            const previousNode = this.#navigateToPrevious();
            if (previousNode != null) {
                this.#renderToBoardForNode(previousNode);
            } else {
                this.#renderStartFenToBoard();
            }
        });
        panel.addClickedNextListener(() => this.#renderToBoardForNode(this.#navigateToNext()));
        panel.addClickedEndListener(() => this.#renderToBoardForNode(this.#navigateToEnd()));
    }

    /**
     * @param boardWidget {BoardGui}
     */
    set boardWidget(boardWidget) {
        this.#boardWidget = boardWidget;

        // click listener
        this.addClickedNodeListener((node) => this.#renderToBoardForNode(node));

        // key listeners
        document.addEventListener('keydown', (e) => {
            if (!this.#keyboardNavigation || this.#boardWidget.isInPlaceHolderMode()) {
                return;
            }

            switch (e.key) {
                case 'ArrowLeft':
                    const previous = this.#navigateToPrevious();
                    if (previous == null) {
                        this.#renderStartFenToBoard();
                    } else {
                        this.#renderToBoardForNode(previous);
                    }
                    this.#navigationListeners.forEach(listener => listener());
                    break;
                case 'ArrowRight':
                    const next = this.#navigateToNext();
                    if (next == null) {
                        UI.pushErrorNotification('Can not go any further');
                    } else {
                        this.#renderToBoardForNode(next);
                        this.#navigationListeners.forEach(listener => listener());
                    }
                    break;
                case 'ArrowUp':
                    // move left by 2
                    this.#navigateToPrevious();
                    let upNode = this.#navigateToPrevious();
                    if (upNode == null) {
                        this.#renderStartFenToBoard();
                    } else {
                        this.#renderToBoardForNode(upNode);
                    }
                    this.#navigationListeners.forEach(listener => listener());
                    break;
                case 'ArrowDown':
                    // move right by 2
                    const downNode1 = this.#navigateToNext();
                    const downNode2 = this.#navigateToNext();
                    if (downNode1 == null) {
                        UI.pushErrorNotification('Can not go any further');
                    }
                    if (downNode2 != null) {
                        this.#renderToBoardForNode(downNode2);
                        this.#navigationListeners.forEach(listener => listener());
                    }
                    break;
                case 'PageUp':
                    this.#renderStartFenToBoard();
                    this.#navigationListeners.forEach(listener => listener());
                    break;
                case 'PageDown':
                    this.#renderToBoardForNode(this.#navigateToEnd());
                    this.#navigationListeners.forEach(listener => listener());
                    break;
            }
        });
    }

    /**
     * @param annotationBox {AnnotationBox}
     */
    set annotationBox(annotationBox) {
        this.#annotationBox = annotationBox;

        if (this.#isContextualMenuEnabled) {
            this.#contextualMenu.annotationBox = annotationBox;
        }

        this.addClickedNodeListener(node => {
            if (this.#annotationBox.isVisible()) {
                this.#annotationBox.showForNode(node);
            }
        });

        this.addNavigationListener(() => {
            if (this.#annotationBox.isVisible()) {
                if (this.#selectedNode != null) {
                    this.#annotationBox.showForNode(this.#selectedNode);
                } else {
                    this.#annotationBox.hide();
                }
            }
        });
    }

    /**
     * @return {function}
     */
    get importMovesCallback() {
        return this.#importMovesCallback;
    }

    /**
     * @param value {function}
     */
    set importMovesCallback(value) {
        this.#importMovesCallback = value;
    }

    #renderStartFenToBoard() {
        this.#unselectNode();
        this.#boardWidget.loadFen(this.#startFen, true);
        this.#updateNavigationPanelButtons();
    }

    /**
     * @param node {MoveTreeNode}
     */
    #renderToBoardForNode(node) {
        if (node != null) {
            this.#boardWidget.loadFen(node.fen, true);
            this.#updateNavigationPanelButtons();
        }
    }

    #unselectNode() {
        if (this.#selectedNode != null) {
            let container = document.getElementById('move-container-' + this.#selectedNode.nodeId);
            container.classList.remove('move-container-selected');
            this.#selectedNode = null;
        }
    }

    set startFen(fen) {
        this.#startFen = fen;
        this.#useDefaultFen = (fen === DEFAULT_START_FEN);
        this.#moveTree.startFen = fen;
    }

    get startFen() {
        return this.#moveTree.startFen;
    }

    // COMPLEX GETTERS/SETTERS

    /**
     * @returns {string[]}
     */
    getAllFenKeys() {
        return this.#moveTree.getAllNodes().map(node => node.fenKey);
    }

    // FIXME: seems like we keep too many values in 'openedBranches',
    //  this needs more investigation
    /**
     * @return {string[]}
     */
    listOpenBranchIds() {
        let nodesWithChildNodes = this
            .#moveTree
            .getAllNodes()
            .filter(node => node.hasChildNodes())
            .map(node => node.nodeId);

        return Array.from(this.#openedBranches).filter(branchId => {
            let parentNodeId = branchId.split('-')[0];
            return nodesWithChildNodes.includes(parentNodeId);
        });
    }

    /**
     * @return {null|string}
     */
    getFenAtSelection() {
        if (this.selectedNode === null) {
            return this.#startFen;
        } else {
            return this.selectedNode.fen;
        }
    }

    /**
     * The nodes without any branches
     *
     * @returns {MoveTreeNode[]}
     */
    getMainBranchNodes() {
        return this.#moveTree.getMainBranchNodes();
    }

    getMovesUpToSelection() {
        if (this.#selectedNode === null) {
            return [];
        } else {
            return this.#selectedNode.getAllNodesLeadingUpTo().map(node => node.move);
        }
    }

    /**
     * @return {boolean}
     */
    isEmpty() {
        return this.#moveTree.isEmpty();
    }

    /**
     * Append to the end or branch off (depending on which node is selected)
     *
     * @param moves {HalfMove[]}
     * @return {MoveTreeNode}
     */
    addToTree(moves) {
        let newSelectedNode;
        let node = this.#selectedNode;
        if (node !== null) {
            newSelectedNode = node.appendOrBranchOff(moves);
            if (newSelectedNode.level > 0) {
                let newBranchIndex = (newSelectedNode.getBranchOffParentNode().childNodes.length - 1).toString();
                let branchId = node.nodeId + '-' + newBranchIndex;
                this.#closeAllOtherBranchesAtSameLevel(branchId);
                this.#openedBranches.add(branchId);
            }
        } else {
            newSelectedNode = this.#moveTree.addMovesAtTheBottom(moves);
        }
        this.#render();
        return this.#selectNode(newSelectedNode);
    }

    /**
     * @param move {HalfMove}
     */
    addMoveAtTheEnd(move) {
        let newNode;

        if (this.#moveTree.isEmpty()) {
            newNode = this.#moveTree.setMoves([move]);
        } else {
            newNode = this.#findLastNode().appendOrBranchOff([move]);
        }

        this.#render();
        return this.#selectNode(newNode);
    }

    /**
     * @param moves {HalfMove[]}
     */
    setMoves(moves) {
        this.#selectedNode = null;
        this.#previouslySelectedNode = null;
        this.#moveTree = new MoveTree();
        this.#moveTree.startFen = this.#startFen;

        if (moves.length > 0) {
            const lastNode = this.#moveTree.setMoves(moves);
            this.#render();
            this.#selectNode(lastNode);
        }
    }

    /**
     * Remove all nodes but doesn't override startFen
     */
    clear() {
        this.setMoves([]);
        this.#render();
    }

    /**
     *  To be called when we change the eval label format
     *
     *  @param analysisCache {Map<string, InfoLineResult>|null}
     */
    refreshAllMoveNodeEval(analysisCache = null) {
        if (analysisCache != null) {
            this.#syncAnalysisCache(analysisCache);
        }
        this.#moveTree.getAllNodes().forEach((node) => {
            this.#renderNodeEval(node);
        });
    }

    /**
     * @param fenKey {string}
     * @param analysisCache {Map<string, InfoLineResult>}
     */
    updateEvalForInfoLineResult(fenKey, analysisCache) {
        this.#syncAnalysisCache(analysisCache);

        switch (this.#settingsManager.moveNodeEvalFormat) {
            case MoveNodeEvalFormat.NORMALIZED_CENTI_PAWNS:
                const infoLineResult = analysisCache.get(fenKey);
                if (infoLineResult != null) {
                    this.#moveTree.getAllNodesMatchingFenKey(fenKey).forEach((node) => {
                        this.#renderNodeEval(node, infoLineResult);
                    });
                }
                break;
            case MoveNodeEvalFormat.ANNOTATION_SYMBOLS:
                this.#moveTree.getAllNodesMatchingFenKey(fenKey).forEach((node) => {
                    this.#renderNodeEval(node);
                });
                break;
            default:
                break;
        }
    }

    /**
     * Apply backend-provided annotation details to every node in the move tree.
     *
     * @param moveAnnotations {GameMoveAnnotationDto[]}
     */
    applyAnnotationSymbols(moveAnnotations) {
        const moveAnnotationsByMoveIndex = new Map(moveAnnotations.map(annotation => [annotation.moveIndex, annotation]));
        this.#moveTree.getAllNodes().forEach((node) => {
            const moveAnnotation = moveAnnotationsByMoveIndex.get(node.position);
            if (moveAnnotation != null) {
                node.annotationDetails = new AnnotationEvalDetails(
                    moveAnnotation.annotation,
                    moveAnnotation.engineCp,
                    moveAnnotation.actualMoveCp,
                    moveAnnotation.cpl
                );
            } else {
                node.annotationDetails = null;
            }
            this.#renderNodeEval(node);
        });
    }

    /**
     * @param node {MoveTreeNode}
     * @param infoLineResult {InfoLineResult|null}
     */
    #renderNodeEval(node, infoLineResult = null) {
        switch (this.#settingsManager.moveNodeEvalFormat) {
            case MoveNodeEvalFormat.NORMALIZED_CENTI_PAWNS:
                node.clearEvalCssClasses();
                node.eval = this.#getNormalizedEvalString(node, infoLineResult);
                this.#updateEvalPlaceholder(node);
                break;
            case MoveNodeEvalFormat.ANNOTATION_SYMBOLS:
                this.#renderAnnotationDetails(node);
                break;
            default:
                break;
        }
    }

    /**
     * Persist the latest streamed analysis cache so eval display can be refreshed
     * consistently when the user changes formats.
     *
     * @param analysisCache {Map<string, InfoLineResult>}
     */
    #syncAnalysisCache(analysisCache) {
        if (analysisCache != null) {
            this.#analysisCache = new Map(analysisCache);
        }
    }

    /**
     * @param node {MoveTreeNode}
     * @param infoLineResult {InfoLineResult|null}
     * @return {string|null}
     */
    #getNormalizedEvalString(node, infoLineResult = null) {
        if (infoLineResult != null) {
            return infoLineResult.evalAsString;
        }

        const cachedInfoLineResult = this.#analysisCache.get(node.fenKey);
        if (cachedInfoLineResult != null) {
            return cachedInfoLineResult.evalAsString;
        }

        return null;
    }

    /**
     * @param node {MoveTreeNode}
     */
    #renderAnnotationDetails(node) {
        node.clearEvalCssClasses();

        if (node.annotationDetails != null) {
            node.eval = node.annotationDetails.symbol;
            node.addEvalCssClass(node.annotationDetails.cssClass);
        } else {
            node.eval = null;
        }

        this.#updateEvalPlaceholder(node);
    }

    /**
     * @param node {MoveTreeNode}
     */
    #updateEvalPlaceholder(node) {
        const evalPlaceholder = document.getElementById(`eval-placeholder-${node.nodeId}`);
        const moveContainer = document.getElementById(`move-container-${node.nodeId}`);

        // clear all annotation label CSS classes
        moveAnnotationSymbolTypesArray
            .map((annotationSymbolType) => `${annotationSymbolType.toLowerCase()}-annotation-label`)
            .forEach((cssClass) => evalPlaceholder.classList.remove(cssClass));

        if (node.eval == null) {
            evalPlaceholder.innerHTML = '';
        } else {
            evalPlaceholder.innerHTML = node.eval;
        }

        node.evalCssClasses.forEach(cssClass => evalPlaceholder.classList.add(cssClass));

        // tooltip describing the calculation behind the eval annotation symbol
        // should be available on the whole move cell (not only the eval label)
        const annotationTooltipText = this.#annotationTooltipText(node);
        if (annotationTooltipText != null) {
            this.#removeEvalTooltip(evalPlaceholder);
            if (moveContainer != null) {
                addToolTip(moveContainer, annotationTooltipText);
            }
        } else {
            this.#removeEvalTooltip(evalPlaceholder);
            if (moveContainer != null) {
                this.#removeEvalTooltip(moveContainer);
            }
        }
    }

    /**
     * Remove a previously added tooltip from the eval placeholder (if any).
     *
     * @param evalPlaceholder {HTMLElement}
     */
    #removeEvalTooltip(evalPlaceholder) {
        const tooltip = document.getElementById(`${evalPlaceholder.id}-tooltip`);
        if (tooltip != null) {
            tooltip.remove();
        }
    }

    /**
     * @param node {MoveTreeNode}
     * @return {string|null}
     */
    #annotationTooltipText(node) {
        if (this.#settingsManager.moveNodeEvalFormat !== MoveNodeEvalFormat.ANNOTATION_SYMBOLS) {
            return null;
        }

        return node.annotationDetails?.toTooltipText() ?? null;
    }

    /**
     * @param format {string}
     */
    updateMoveFormat(format) {
        this.#moveTree.getAllNodes().forEach(node => {
            document
                .getElementById(`move-label-placeholder-${node.nodeId}`)
                .innerText = node.renderLabel(format);
        });
    }

    setAnnotationIconVisible(node, visible) {
        let annotationIcon = document.getElementById('annotation-icon-' + node.nodeId);
        if (visible) {
            annotationIcon.style.display = 'block';
        } else {
            annotationIcon.style.display = 'none';
        }
    }

    /**
     * Delete branch which 'node' is part of
     *
     * @param {MoveTreeNode} node
     */
    deleteBranchOf(node) {
        if (node.level > 0) {
            let branchOffNode = node.getBranchOffParentNode();
            let deletedBranchId = node.deleteBranch();
            if (deletedBranchId != null) {
                this.#openedBranches.delete(deletedBranchId);
                this.#branchGuis.delete(deletedBranchId);
                this.#render();
                this.#closeAllBranchesAtSameLevel(deletedBranchId);
                this.#selectNode(branchOffNode);
            }
        }
    }

    /**
     * @param {MoveTreeNode} node
     */
    deleteAllNodesBelow(node) {
        node.deleteAllNodesBelow();
        this.#render();
        this.#selectNode(node);
    }

    selectLastNode() {
        let node = this.#findLastNode();
        if (node) {
            this.#handleClickEvent(node);
        }
    }

    /**
     * @return {boolean}
     */
    isLastMoveSelected() {
        return MoveTreeNode.areEquals(this.#selectedNode, this.#findLastNode());
    }

    selectNodeById(nodeId) {
        let node = this.#findNodeById(nodeId);
        if (node) {
            this.#handleClickEvent(node);
        }
    }

    navigateToStart() {
        this.#renderStartFenToBoard();
        this.#navigationListeners.forEach(listener => listener());
    }

    /**
     * @param {number} position
     * @return {null|MoveTreeNode}
     */
    selectMoveAt(position) {
        if (this.#selectedNode != null) {
            let nodes = this.#selectedNode.getAllNodesLeadingUpTo();
            if (position < nodes.length) {
                let node = nodes[position];
                if (node) {
                    return this.#selectNode(node);
                }
            }
        } else {
            let nodes = this.#moveTree.getMainBranchNodes();
            if (position < nodes.length) {
                let node = nodes[position];
                if (node) {
                    return this.#selectNode(node);
                }
            }
        }

        return null;
    }

    startLoadingAnimation() {
        const icon = document.getElementById('move-history-loading-icon');
        icon.style.visibility = 'visible';
        clearInterval(this.#stopLoadingAnimationTimeout);
        this.#stopLoadingAnimationTimeout = setTimeout(() => {
            icon.style.visibility = 'hidden';
        }, 10_000);
    }

    /**
     * @return {null|MoveTreeNode}
     */
    #findLastNode() {
        return this.#moveTree.getAllNodes().find(node => node.level === 0 && !node.hasNext());
    }

    /**
     * @return {null|MoveTreeNode}
     */
    #findNodeById(nodeId) {
        return this.#moveTree.getAllNodes().find(node => node.nodeId === nodeId);
    }

    // CONTEXTUAL MENU ACTIONS

    hasClosedBranches() {
        return Array
            .from(this.#branchGuis.keys())
            .filter(branchId => !this.#openedBranches.has(branchId))
            .length > 0;
    }

    hasOpenBranches() {
        return this.#openedBranches.size > 0;
    }

    openAllBranches() {
        this.#moveTree.getAllNodes().map(node => node.nodeId).forEach(nodeId => {
            let allBranchesAtNode = this.#getAllBranchGuisAtNodeId(nodeId);
            if (allBranchesAtNode.length > 0) {
                let allBranchesIdsAtNode = allBranchesAtNode.map(branchGui => branchGui.branchId);
                let alreadyHasOneBranchOpened = allBranchesIdsAtNode.filter(value => this.#openedBranches.has(value)).length > 0;
                if (!alreadyHasOneBranchOpened) {
                    allBranchesAtNode[0].open();
                    this.#openedBranches.add(allBranchesAtNode[0].branchId);
                }
            }
        });
    }

    openBranchesByIds(branchIds) {
        branchIds.forEach(branchId => {
            let branchGui = this.#branchGuis.get(branchId);
            if (branchGui != null) {
                branchGui.open();
                this.#openedBranches.add(branchId);
            }
        });
    }

    closeAllBranches() {
        this.#branchGuis.forEach(branchGui => branchGui.close());
        this.#openedBranches.clear();
    }

    // SERIALIZATION AND EXPORT

    /**
     * @return {MoveTreeNodeDto[]}
     */
    serializeToDtos() {
        return this.#moveTree.serializeToDtos();
    }

    /**
     * @param jsonNodes {MoveTreeNodeDto[]}
     */
    deserializeNodeDtos(jsonNodes) {
        this.#moveTree.deserializeNodeDtos(jsonNodes);
        this.#render();
    }

    /**
     * @return {string[]} each String is the PGN of the variation
     */
    exportToPgnFlattened() {
        let allLines = this.#getAllLines();
        let hasMultipleLines = allLines.length > 1;
        let metadata = this.#metadataFetcher();

        return allLines.map(nodes => {
            let isMainLine = nodes.every(node => node.level === 0);
            let moves = nodes.map(node => node.move);
            let exportForLine = [];

            // metadata
            metadata.forEach((value, key) => {
                exportForLine.push('[' + key + ' "' + value + '"]');
            });
            if (hasMultipleLines) {
                if (isMainLine) {
                    exportForLine.push('[Variation "no"]');
                } else {
                    exportForLine.push('[Variation "yes"]');
                }
            }

            // data
            exportForLine.push(exportMovesToPgnLine(moves));
            exportForLine.push('');
            exportForLine.push('');
            return exportForLine.join('\n');
        });
    }

    #getAllLines() {
        // find all terminal nodes
        let terminalNodes = this.#moveTree.getAllNodes().filter(node => !node.hasNext());

        // for each terminal node, find all nodes leading up to it
        let lines = [];
        for (let terminalNode of terminalNodes) {
            lines.push(terminalNode.getAllNodesLeadingUpTo());
        }

        // sort lines by level
        lines.sort((line1, line2) => {
            let level1 = line1[line1.length - 1].level;
            let level2 = line2[line2.length - 1].level;
            return level1 - level2;
        });

        return lines;
    }

    /**
     * @return {string}
     */
    exportToPgnNested() {
        let lines = [];
        let metadata = this.#metadataFetcher();
        metadata.forEach((value, key) => {
            lines.push('[' + key + ' "' + value + '"]');
        });
        lines.push('');
        lines.push(this.#exportNodesToPgnNested(this.#moveTree.getMainBranchNodes()));
        lines.push('');
        lines.push('');
        return lines.join('\n');
    }

    #exportNodesToPgnNested(nodes) {
        let result = [];
        for (let node of nodes) {
            result.push(node.toPgn(this.#startFen));
            for (let i = 0; i < node.childNodes.length; i++) {
                let childNode = node.childNodes[i];
                let continuationToken = '';
                if (childNode.position % 2 !== 0) {
                    continuationToken = childNode.fullMoveCount + '... ';
                }
                result.push('(' + continuationToken + this.#exportNodesToPgnNested(childNode.getAllNodesDownstream()) + ')');
            }
        }

        return result.join(' ');
    }

    // NAVIGATION

    #updateNavigationPanelButtons() {
        this.#navigationPanels.forEach(panel => {
            panel.setBackwardButtonsEnabled(this.#canNavigateBackward());
            panel.setForwardButtonsEnabled(this.#canNavigateForward());
            panel.setDownloadButtonEnabled(!this.#moveTree.isEmpty());
        });
    }

    #canNavigateBackward() {
        return this.#selectedNode !== null && (this.#selectedNode.hasPrevious()
            || MoveTreeNode.areEquals(this.#selectedNode, this.#moveTree.rootNode));
    }

    #canNavigateForward() {
        return this.#findNextNavigationNode() !== null;
    }

    /**
     * @return {null|MoveTreeNode}
     */
    #navigateToPrevious() {
        if (this.#selectedNode != null) {
            let previousNode = this.#selectedNode.previous;
            if (previousNode != null) {
                return this.#selectNode(previousNode);
            }
        }

        return null;
    }

    /**
     * @return {null|MoveTreeNode}
     */
    #navigateToNext() {
        let nextNode = this.#findNextNavigationNode();
        if (nextNode != null) {
            return this.#selectNode(nextNode);
        } else {
            return null;
        }
    }

    /**
     * @return {null|MoveTreeNode}
     */
    #findNextNavigationNode() {
        if (this.#selectedNode == null) {
            let rootNode = this.#moveTree.rootNode;
            if (rootNode != null) {
                return rootNode;
            }
        } else {
            let nextOpenedBranchNode = this.#getNextOpenedBranchNode();
            if (nextOpenedBranchNode !== null) {
                return nextOpenedBranchNode;
            } else if (this.#selectedNode.hasNext()) {
                return this.#selectedNode.next;
            } else if (this.#selectedNode.level > 0) {
                let branchOffNode = this.#selectedNode.getBranchOffParentNode();
                if (branchOffNode !== null && branchOffNode.hasNext()) {
                    return branchOffNode.next;
                }
            }
        }

        return null;
    }

    /**
     * @return {null|MoveTreeNode}
     */
    #navigateToEnd() {
        let lastNode = null;
        if (this.#selectedNode != null) {
            lastNode = this.#selectedNode.getLastNodeOfBranch();
        } else {
            let nodes = this.#moveTree.getMainBranchNodes();
            if (nodes.length > 0) {
                lastNode = nodes.pop();
            }
        }

        if (lastNode != null) {
            return this.#selectNode(lastNode);
        } else {
            return null;
        }
    }

    /**
     * If selected node has at least one sub-branch that is open,
     * return the first node of the that branch
     */
    #getNextOpenedBranchNode() {
        if (this.selectedNode != null) {
            let branchGuis = this
                .#getAllBranchGuisAtNodeId(this.#selectedNode.nodeId)
                .filter(branchGui => branchGui.isOpen());

            switch (branchGuis.length) {
                case 0:
                    break;
                case 1:
                    return branchGuis[0].rootNodeOfBranch;
                default:
                    console.warn('there should not be >1 open branches (right now there are ' + branchGuis.length + ')');
                    break;
            }
        }

        return null;
    }

    /**
     * @param {MoveTreeNode} node
     * @return {MoveTreeNode}
     */
    #selectNode(node) {
        // logic
        this.#previouslySelectedNode = this.#selectedNode;
        this.#selectedNode = node;

        // css
        const currentlySelected = this.#mainContainer.getElementsByClassName('move-container-selected');
        for (let i = 0; i < currentlySelected.length; i++) {
            currentlySelected[i].classList.remove('move-container-selected');
        }
        if (currentlySelected.length > 1) {
            console.warn('there should not be >1 selected nodes (found ' + currentlySelected.length + ')');
        }

        const moveContainer = document.getElementById('move-container-' + node.nodeId);
        if (moveContainer != null) {
            moveContainer.classList.add('move-container-selected');

            // scroll to the selected element
            // but only if widget is visible
            // (otherwise, on the mobile layout, it scrolls to the bottom of the page)
            if (isInViewport(this.#mainContainer)) {
                moveContainer.scrollIntoView({block: 'nearest', inline: 'start'});
            }
        } else {
            console.error('could not find move container for node ' + node);
        }

        // UI
        this.#updateNavigationPanelButtons();

        return node;
    }

    // RENDERING

    #addLoadingIconIfNeeded() {
        if (this.#isLoadingAnimationEnabled && document.getElementById('move-history-loading-icon') == null) {
            const loadingIconDiv = document.createElement('div');
            loadingIconDiv.id = 'move-history-loading-icon';

            const img = document.createElement('img');
            img.src = `${ICON_PATH}/loading-animation-2.gif`;
            loadingIconDiv.append(img);

            document.getElementsByClassName('flex-triptych-right-container')[0].append(loadingIconDiv);

            // TODO: tooltip too close to the right edge are cut (they should render in the other direction compared to the point)
            //   addToolTip(loadingIconDiv, 'The engine is running');
        }
    }

    #render() {
        this.#mainContainer.innerHTML = '';
        this.#branchGuis = new Map();
        this.#renderNodes(this.#moveTree.getMainBranchNodes(), this.#mainContainer);
        if (this.#isContextualMenuEnabled) {
            this.#mainContainer.append(this.#contextualMenu.menuContainer);
        }

        this.#addLoadingIconIfNeeded();
    }

    /**
     * @param nodes {MoveTreeNode[]}
     * @param parentContainer {HTMLElement}
     */
    #renderNodes(nodes, parentContainer) {
        // create a line in the move tree widget
        function buildMoveLineContainer(node, isFirstLineOfBranch = false, isLastLineOfBranch = false) {
            let lineContainer = document.createElement('div');
            lineContainer.className = 'move-line-container';
            for (let level = 0; level < node.level; level++) {
                let previousLevelFullMoveIndex = buildFullMoveIndex();
                previousLevelFullMoveIndex.classList.add('full-move-index-label-container-previous-levels');
                lineContainer.append(previousLevelFullMoveIndex);
            }
            let fullMoveIndex = buildFullMoveIndex(node.fullMoveCount);
            if (isFirstLineOfBranch && node.level > 0) {
                fullMoveIndex.classList.add('full-move-index-label-container-first');
            }
            if (isLastLineOfBranch && node.level > 0) {
                fullMoveIndex.classList.add('full-move-index-label-container-last');
            }
            lineContainer.append(fullMoveIndex);
            return lineContainer;
        }

        // https://stackoverflow.com/questions/2939914/how-do-i-vertically-align-text-in-a-div
        function buildFullMoveIndex(textContent) {
            let container = document.createElement('div');
            container.className = 'full-move-index-label-container';

            let labelHolder = document.createElement('div');
            labelHolder.className = 'full-move-index-label-holder';

            if (textContent) {
                let text = document.createElement('div');
                text.className = 'full-move-index-label';
                text.innerText = textContent;
                labelHolder.append(text);
            }

            container.append(labelHolder);
            return container;
        }

        /**
         * @param node {MoveTreeNode}
         * @return {HTMLDivElement}
         */
        function buildMoveContainer(node) {
            let container = document.createElement('div');
            container.id = 'move-container-' + node.nodeId;
            container.className = 'move-container';
            container.style.backgroundColor = initialColor(node); // move container color depends on the level
            return container;
        }

        /**
         * @param firstNode {MoveTreeNode}
         * @return {HTMLDivElement}
         */
        function buildEmptyMoveContainer(firstNode) {
            const container = document.createElement('div');
            container.classList.add('move-container', 'empty-move-container');
            container.style.backgroundColor = initialColor(firstNode); // move container color depends on the level
            return container;
        }

        /**
         * @param node {MoveTreeNode}
         * @param moveFormat {string}
         * @return {HTMLDivElement}
         */
        function buildLabelPlaceholder(node, moveFormat) {
            const label = document.createElement('div');
            label.id = `move-label-placeholder-${node.nodeId}`;
            label.className = 'move-label-placeholder';
            label.innerText = node.renderLabel(moveFormat);
            return label;
        }

        /**
         * @param node {MoveTreeNode}
         * @return {HTMLDivElement}
         */
        function buildEvalPlaceHolder(node) {
            const placeholder = document.createElement('div');
            placeholder.id = `eval-placeholder-${node.nodeId}`;
            placeholder.className = 'eval-placeholder';
            node.evalCssClasses.forEach(cssClass => placeholder.classList.add(cssClass));
            return placeholder;
        }

        function buildAnnotationPlaceHolder(node) {
            const img = document.createElement('img');
            img.src = `${ICON_PATH}/writing-icon.png`;

            const placeholder = document.createElement('div');
            placeholder.id = 'annotation-icon-' + node.nodeId;
            placeholder.className = 'annotation-icon';
            placeholder.append(img);

            return placeholder;
        }

        let moveFormat = new SettingsManager().moveFormat;
        let moveLineContainerDiv = null;

        // if branch off from red move, then first move container of sub-branch should be an empty block,
        // this way left column is for red moves and right column is for black moves
        let shouldStartWithEmptyBlock = false;

        if (nodes.length > 0) {
            let isRedBranch = false;
            let startsAsBlack = false;
            const firstNode = nodes[0];

            if (firstNode.level > 0) {
                let branchOffNodePosition = firstNode.getBranchOffParentNode().position;
                if (branchOffNodePosition % 2 === 0) {
                    isRedBranch = true;
                }
            } else {
                startsAsBlack = this.#startFen != null && this.#startFen.includes(' b ');
            }

            shouldStartWithEmptyBlock = isRedBranch || startsAsBlack;

            if (startsAsBlack) {
                console.log('startsAsBlack enabled (custom start FEN)');
            }

            if (shouldStartWithEmptyBlock) {
                moveLineContainerDiv = buildMoveLineContainer(firstNode, true, false);
                moveLineContainerDiv.append(buildEmptyMoveContainer(firstNode));
                parentContainer.append(moveLineContainerDiv);
            }
        }

        // render nodes
        nodes.forEach((node, i) => {
            // move container components
            let moveContainer = buildMoveContainer(node)
            moveContainer.append(buildLabelPlaceholder(node, moveFormat));
            moveContainer.append(buildEvalPlaceHolder(node));
            moveContainer.append(buildAnnotationPlaceHolder(node));

            let isFirstLineOfBranch = i === 0;
            let isLastLineOfBranch = i === nodes.length - 1 || i === nodes.length - 2;

            // create line if necessary
            if ((!shouldStartWithEmptyBlock && i % 2 === 0) || (shouldStartWithEmptyBlock && i % 2 === 1)) {
                // create new line when necessary
                moveLineContainerDiv = buildMoveLineContainer(node, isFirstLineOfBranch, isLastLineOfBranch);
                parentContainer.append(moveLineContainerDiv);
            }
            moveLineContainerDiv.append(moveContainer);

            // render branches recursively
            let branchIdsOfNode = [];
            for (let i = 0; i < node.childNodes.length; i++) {
                let childNode = node.childNodes[i];
                let branchGui = new BranchGui(node, i);
                moveContainer.append(branchGui.buttonContainer);
                parentContainer.append(branchGui.subBranchContainer);
                this.#branchGuis.set(branchGui.branchId, branchGui);
                branchIdsOfNode.push(branchGui.branchId);
                this.#renderNodes(childNode.getAllNodesDownstream(), branchGui.subBranchContainer);
                if (this.#openedBranches.has(branchGui.branchId)) {
                    branchGui.open();
                } else {
                    branchGui.close();
                }
            }

            // update eval
            this.#updateEvalPlaceholder(node);

            // update annotation
            if (node.hasAnnotation()) {
                this.setAnnotationIconVisible(node, true);
            }

            // click event
            moveContainer.addEventListener('click', (e) => {
                // select node
                this.#handleClickEvent(node);

                // open/close branches
                if (e.target.classList.contains('branch-toggle-placeholder')) {
                    let branchId = e.target.id.replace('branch-toggle-placeholder-', '');
                    let branchGui = this.#branchGuis.get(branchId);
                    this.#closeAllOtherBranchesAtSameLevel(branchId);

                    if (branchGui.isClosed()) {
                        branchGui.open();
                        this.#openedBranches.add(branchId);
                    } else {
                        branchGui.close();
                        this.#openedBranches.delete(branchId);
                    }
                }
            });

            // 'hover' background-color is overridden by dynamically changing the background-color
            // so we kinda re-implement 'hover' behavior
            moveContainer.addEventListener('mouseover', () => {
                moveContainer.style.backgroundColor = HOVERING_COLOR;

                // change color to white when hovering over toggle
                for (let i = 0; i < branchIdsOfNode.length; i++) {
                    // TODO: read color from CSS
                    this.#branchGuis.get(branchIdsOfNode[i]).forceColor('#e7e1d9');
                }
            });

            moveContainer.addEventListener('mouseout', () => {
                moveContainer.style.backgroundColor = initialColor(node);

                // change color of toggles back to their original
                for (let i = 0; i < branchIdsOfNode.length; i++) {
                    this.#branchGuis.get(branchIdsOfNode[i]).revertForcedColor();
                }
            });

            // contextual menu
            moveContainer.addEventListener('contextmenu', (e) => {
                this.#handleClickEvent(node);
                this.#handleRightClientEvent(e, node);
            });
        });
    }

    #handleClickEvent(node) {
        if (MoveTreeNode.areNotEquals(this.selectedNode, node)) {
            this.#selectNode(node);
            this.#clickedNodeListeners.forEach(listener => listener(node));
        }
    }

    /**
     * Contextual menu
     */
    #handleRightClientEvent(e, node) {
        if (this.#isContextualMenuEnabled) {
            e.preventDefault();
            let x = e.clientX;
            let y = e.clientY;
            let menuWidth = 120;
            if (x > window.innerWidth - menuWidth) {
                x -= menuWidth;
            }
            this.#contextualMenu.showAtForNode(x, y, node);
        }
    }

    // BRANCH UTILS

    /**
     * nodeId of the Node that sits in the same line as param nodeId, if any
     */
    #getSiblingNodeId(nodeId) {
        let moveContainer = document.getElementById('move-container-' + nodeId);
        let lineContainer = moveContainer.parentElement;
        let allMoveContainers = lineContainer.getElementsByClassName('move-container'); // max. 2
        for (let i = 0; i < allMoveContainers.length; i++) {
            let currentMoveContainer = allMoveContainers[i];
            let currentNodeId = currentMoveContainer.id.replace('move-container-', '');
            if (nodeId !== currentNodeId) {
                return currentNodeId;
            }
        }
        return null;
    }

    /**
     * Collect all nodeId from the line where branchId (i.e. branch parent node + sibling node)
     */
    #getNodeIdsAtSameLevel(branchId) {
        let nodesIds = [];
        nodesIds.push(branchId.split('-')[0]);
        let siblingNodeId = this.#getSiblingNodeId(branchId.split('-')[0]);
        if (siblingNodeId != null) {
            nodesIds.push(siblingNodeId);
        }
        return nodesIds;
    }

    /**
     * Close other BranchGui that are at the same line as branchId
     */
    #closeAllOtherBranchesAtSameLevel(branchId) {
        this.#getNodeIdsAtSameLevel(branchId).forEach(nodeId => {
            this.#getAllBranchGuisAtNodeId(nodeId).forEach(branchGui => {
                if (branchId !== branchGui.branchId && nodeId === branchGui.parentNodeId) {
                    branchGui.close();
                    this.#openedBranches.delete(branchGui.branchId);
                }
            });
        });
    }

    /**
     * Close all BranchGui that are at the same line as branchId
     */
    #closeAllBranchesAtSameLevel(branchId) {
        this.#getNodeIdsAtSameLevel(branchId).forEach(nodeId => {
            this.#getAllBranchGuisAtNodeId(nodeId).forEach(branchGui => {
                if (nodeId === branchGui.parentNodeId) {
                    branchGui.close();
                    this.#openedBranches.delete(branchGui.branchId);
                }
            });
        });
    }

    #getAllBranchGuisAtNodeId(nodeId) {
        let result = [];
        let guis = Array.from(this.#branchGuis.values());

        for (let i = 0; i < guis.length; i++) {
            let gui = guis[i];
            if (nodeId === gui.parentNodeId) {
                result.push(gui);
            }
        }

        return result;
    }

}
