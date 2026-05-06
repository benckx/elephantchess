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

const ANNOTATION_MAX_LENGTH = 2_000;

class AnnotationBox {

    /**
     * @type {HTMLElement}
     */
    #element;
    #movePlaceholder;

    #textArea;
    #characterCounter = document.getElementById('character-counter');

    /**
     * @type {null|MoveTreeWidget}
     */
    #moveTreeWidget;

    /**
     * @type {null|MoveTreeNode}
     */
    #node;

    constructor(elementId) {
        // container
        this.#element = document.getElementById(elementId);
        makeElementDraggable(this.#element);

        this.#movePlaceholder = document.getElementById('annotation-move-placeholder');

        // text area
        this.#textArea = this.#element.getElementsByTagName('textarea')[0];
        this.#textArea.addEventListener('input', () => {
            // update node
            if (this.#node != null) {
                this.#node.annotation = this.#textArea.value;
                if (this.#moveTreeWidget != null && this.#hasTextValue()) {
                    this.#moveTreeWidget.setAnnotationIconVisible(this.#node, true);
                } else {
                    this.#moveTreeWidget.setAnnotationIconVisible(this.#node, false);
                }
            }

            // counter
            this.#updateCharacterCounter()
        });

        // close button
        this.#element
            .getElementsByClassName('analysis-annotation-close')[0]
            .addEventListener('click', () => this.hide());

        // close on Escape
        this.#textArea.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.hide();
            }
        });

        this.hide();
    }

    set moveTreeWidget(moveTreeWidget) {
        this.#moveTreeWidget = moveTreeWidget;
    }

    showForNode(node) {
        this.#node = node;
        if (node.hasAnnotation()) {
            this.#textArea.value = node.annotation;
        } else {
            this.#textArea.value = '';
        }
        this.#movePlaceholder.innerHTML = '[' + node.move.toAlgebraic() + ']';

        this.#show();
    }

    isVisible() {
        return !this.#element.classList.contains('analysis-annotation-hidden');
    }

    hide() {
        this.#element.classList.add('analysis-annotation-hidden');
    }

    #show() {
        this.#updateCharacterCounter();
        this.#element.classList.remove('analysis-annotation-hidden');
    }

    #hasTextValue() {
        return this.#textArea.value != null && this.#textArea.value.trim().length > 0
    }

    #updateCharacterCounter() {
        let length = 0;
        if (this.#hasTextValue()) {
            length = this.#textArea.value.length;
        }

        this.#characterCounter.innerHTML = length + ' / ' + ANNOTATION_MAX_LENGTH;
    }

}

function makeElementDraggable(element) {
    let xInit = 0;
    let yInit = 0;
    let xDelta = 0;
    let yDelta = 0;

    let draggable = element.getElementsByClassName('draggable');
    if (draggable.length > 0) {
        draggable[0].onmousedown = dragOnMouseDown;
        draggable[0].ontouchstart = dragOnMouseDown;
    }

    function dragOnMouseDown(e) {
        e.preventDefault();
        xInit = e.clientX;
        yInit = e.clientY;
        document.onmouseup = disableDragElement;
        document.onmousemove = elementDrag;
        element.classList.add('analysis-annotation-dragged');
    }

    function elementDrag(e) {
        e.preventDefault();
        xDelta = xInit - e.clientX;
        yDelta = yInit - e.clientY;
        xInit = e.clientX;
        yInit = e.clientY;
        element.style.left = (element.offsetLeft - xDelta) + 'px';
        element.style.top = (element.offsetTop - yDelta) + 'px';
    }

    function disableDragElement() {
        document.onmouseup = null;
        document.onmousemove = null;
        element.classList.remove('analysis-annotation-dragged');
    }
}
