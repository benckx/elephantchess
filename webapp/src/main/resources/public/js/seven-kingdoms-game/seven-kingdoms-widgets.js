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

class SevenKingdomsGameMoveHistoryWidget {

    #selectedIndex = -1;
    #maxIndex = 0;

    /**
     * @type {function(number)}
     */
    #clickedMoveCb;

    /**
     * @param clickedMoveCb {function(number)}
     */
    constructor(clickedMoveCb) {
        this.#clickedMoveCb = clickedMoveCb;

        document.addEventListener('keydown', (e) => {
            switch (e.key) {
                case 'ArrowLeft':
                    this.#handlePreviousMoveClick();
                    break;
                case 'ArrowRight':
                    this.#handleNextMoveClick();
                    break;
                default:
                    break;
            }
        });
    }

    get container() {
        return getElementsByClassNameArray('move-history-container')[0];
    }

    /**
     * @param board {Board7k}
     */
    renderMoveHistory(board) {
        // render history
        const mainContainer = this.container;
        mainContainer.innerHTML = '';

        board.historicalMoves.forEach(historicalMove => {
            this.addMove(historicalMove);
        });

        // select the last move
        this.#maxIndex = board.currentIndex - 1;
        this.#selectedIndex = this.#maxIndex;
        this.#selectMove(this.#selectedIndex);
    }

    /**
     * @param historicalMove {HistoricalMove}
     */
    addMove(historicalMove) {
        const i = this.#maxIndex;
        const pieceType = historicalMove.piece.abstractPieceType;

        // index
        const indexLabelDiv = document.createElement('div');
        indexLabelDiv.className = 'index-label';
        indexLabelDiv.textContent = `${i + 1}`;

        const indexContainer = document.createElement('div');
        indexContainer.className = 'index-container';
        indexContainer.appendChild(indexLabelDiv);

        // color indicator
        const colorIndicator = document.createElement('div');
        colorIndicator.classList.add(indicatorClass(), indicatorClass(historicalMove.color));

        const colorIndicatorContainer = document.createElement('div');
        colorIndicatorContainer.className = 'color-indicator-container';
        colorIndicatorContainer.appendChild(colorIndicator);

        // icon indicator
        const pieceIconImg = document.createElement('img');
        pieceIconImg.className = `piece-icon-${pieceType}`;
        pieceIconImg.src = `${PIECE_7K_ICON_PATH}/${darkPieceIcons.get(pieceType)}`;

        const pieceIconContainer = document.createElement('div');
        pieceIconContainer.className = 'piece-icon-container';
        pieceIconContainer.appendChild(pieceIconImg);

        // move label
        const moveLabel = document.createElement('div');
        moveLabel.className = 'move-label';
        moveLabel.textContent = historicalMove.move.algebraic;

        const moveLabelContainer = document.createElement('div');
        moveLabelContainer.className = 'move-label-container';
        moveLabelContainer.appendChild(moveLabel);

        // inner container (i.e. all elements except the index)
        const innerContainer = document.createElement('div');
        innerContainer.className = 'move-item-inner-container';
        innerContainer.append(
            colorIndicatorContainer,
            pieceIconContainer,
            moveLabelContainer
        );

        // main container
        const itemDiv = document.createElement('div');
        itemDiv.id = `move-item-${i}`;
        itemDiv.className = 'move-item';
        itemDiv.append(
            indexContainer,
            innerContainer
        );

        // captured kingdom if any
        if (historicalMove.armyCapturedEvent != null) {
            const armyCapturedEvent = historicalMove.armyCapturedEvent;

            const capturedKingdomIndicator = document.createElement('div');
            capturedKingdomIndicator.classList.add(indicatorClass(), indicatorClass(armyCapturedEvent.capturedColor));
            capturedKingdomIndicator.innerHTML = indicatorCrossSvg(armyCapturedEvent.capturedColor);

            const capturedKingdomContainer = document.createElement('div');
            capturedKingdomContainer.className = 'captured-kingdom-container';
            capturedKingdomContainer.appendChild(capturedKingdomIndicator);

            innerContainer.appendChild(capturedKingdomContainer);
        }

        itemDiv.addEventListener('mouseenter', () => {
            pieceIconImg.src = `${PIECE_7K_ICON_PATH}/${lightPieceIcons.get(pieceType)}`;
        });

        itemDiv.addEventListener('mouseleave', () => {
            pieceIconImg.src = `${PIECE_7K_ICON_PATH}/${darkPieceIcons.get(pieceType)}`;
        });

        itemDiv.addEventListener('click', () => {
            this.#handleMoveSelected(i);
        });

        this.#maxIndex = this.#maxIndex + 1;
        this.container.appendChild(itemDiv);
    }

    #handlePreviousMoveClick() {
        if (this.#selectedIndex > 0) {
            this.#handleMoveSelected(this.#selectedIndex - 1);
        }
    }

    #handleNextMoveClick() {
        if (this.#selectedIndex >= 0 && this.#selectedIndex < this.#maxIndex) {
            this.#handleMoveSelected(this.#selectedIndex + 1);
        }
    }

    /**
     * @param i {number}
     */
    #handleMoveSelected(i) {
        this.#selectedIndex = i;
        this.#selectMove(i);
        this.#clickedMoveCb(i);
    }

    /**
     * @param i {number}
     */
    #selectMove(i) {
        // mark move item as selected
        getElementsByClassNameArray('move-item-inner-container')
            .forEach((item, j) => {
                if (j === i) {
                    item.classList.add('move-item-selected');
                } else {
                    item.classList.remove('move-item-selected');
                }
            });

        // scroll down
        if (isInViewport(this.container)) {
            document
                .getElementById(`move-item-${i}`)
                .scrollIntoView({block: 'nearest', inline: 'start'});
        }
    }

}

class SevenKingdomsDataWidget {

    /**
     * @param board {Board7k}
     */
    update(board) {
        // reset
        this.#clearAllCrosses();
        this.#clearAllCapturedIndicators();
        this.#clearNameFormatting();

        // update the 2 indicators at the end of each row
        for (const [capturing, captured] of board.capturedKingdomsMap.entries()) {
            this.#updateCapturedKingdoms(capturing, captured);
        }

        // update the captures/losses counters
        for (const color of allColors()) {
            const row = this.#findRow(color);

            // green counter
            const capturesCount = board.capturesCount;
            const capturesElement = row.getElementsByClassName('captures-counter-cell')[0];
            capturesElement.textContent = `${capturesCount.get(color)}/30`;

            // red counter
            const lossesCount = board.lossesCount;
            const lossesElement = row.getElementsByClassName('losses-counter-cell')[0];
            if (lossesCount.get(color) >= 17) {
                lossesElement.textContent = `out`;
            } else {
                lossesElement.textContent = `${lossesCount.get(color)}/10`;
            }
        }
    }

    /**
     * @param color {Color7k}
     * @returns {HTMLElement}
     */
    #findRow(color) {
        return document.getElementById(`row-${color.colorName.toLowerCase()}`);
    }

    /**
     * @param color {Color7k}
     */
    #markAsEliminated(color) {
        // update "kingdom data" widget (right side)
        const capturedKingdomRow = this.#findRow(color);
        capturedKingdomRow.getElementsByClassName('kingdom-name-cell')[0].classList.add('eliminated');
        capturedKingdomRow.getElementsByClassName(indicatorClass())[0].innerHTML = indicatorCrossSvg(color);

        // TODO: to be done from elsewhere?
        //   this updates something outside of the block
        // update player indicators (left side)
        for (const playerInfo of getElementsByClassNameArray('player-info')) {
            const indicators = htmlCollectionToArray(playerInfo.getElementsByClassName(indicatorClass(color)));
            for (const indicator of indicators) {
                indicator.innerHTML = indicatorCrossSvg(color);
            }
        }
    }

    /**
     * @param capturing {Color7k}
     * @param captured {Color7k[]}
     */
    #updateCapturedKingdoms(capturing, captured) {
        if (captured.length > 0) {
            const capturedIndicators =
                this.#findRow(capturing).getElementsByClassName('kingdom-color-indicator');

            if (captured.length >= 1) {
                capturedIndicators[1].classList.add(indicatorClass(captured[0]));
                this.#markAsEliminated(captured[0]);
            }
            if (captured.length === 2) {
                capturedIndicators[2].classList.add(indicatorClass(captured[1]));
                this.#markAsEliminated(captured[1]);
            }
        }
    }

    #clearNameFormatting() {
        getElementsByClassNameArray('kingdom-name-cell')
            .forEach(cell => cell.classList.remove('eliminated'));
    }

    /**
     * within the widget
     */
    #clearAllCapturedIndicators() {
        getElementsByClassNameArray('captured-kingdoms-indicator-cell')
            .flatMap(cell => htmlCollectionToArray(cell.getElementsByClassName(indicatorClass())))
            .forEach(indicator => indicator.className = indicatorClass());
    }

    #clearAllCrosses() {
        // TODO: to be done from elsewhere?
        //   this updates something outside of the block
        // update player indicators (left side)
        getElementsByClassNameArray('player-info')
            .flatMap(playerInfo => htmlCollectionToArray(playerInfo.getElementsByClassName(indicatorClass())))
            .forEach(indicator => indicator.innerHTML = '');

        // kingdom data widget (right side)
        getElementsByClassNameArray('indicator-cell')
            .flatMap(cell => htmlCollectionToArray(cell.getElementsByClassName(indicatorClass())))
            .forEach(indicator => indicator.innerHTML = '');
    }

}
