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

class AbstractBarIndicator {

    #value1;
    #value2;
    #leftOver;

    constructor(value1, value2) {
        this.#value1 = value1;
        this.#value2 = value2;
        this.#leftOver = 1 - value1 - value2;
    }

    /**
     * @return {number}
     */
    get value1() {
        return this.#value1;
    }

    /**
     * @return {number}
     */
    get value2() {
        return this.#value2;
    }

    getCssClasses() {
        throw new Error('Abstract');
    }

    mustWriteValue() {
        throw new Error('Abstract');
    }

    render() {
        let cssClasses = this.getCssClasses();

        let div1 = document.createElement('div');
        let div2 = document.createElement('div');
        let div3 = document.createElement('div');

        if (this.mustWriteValue()) {
            let valueLabel = document.createElement('div');
            valueLabel.innerText = ((this.#value1) * 100).toFixed(0) + '%'
            div1.append(valueLabel);
        }

        div1.classList.add('bar-indicator', cssClasses[0]);
        div2.classList.add('bar-indicator', cssClasses[1]);
        div3.classList.add('bar-indicator', cssClasses[2]);

        div1.style.width = ((this.#value1) * 100) + '%';
        div2.style.width = ((this.#leftOver) * 100) + '%';
        div3.style.width = ((this.#value2) * 100) + '%';

        let container = document.createElement('div');
        container.classList.add('bar-indicator-container');
        container.append(div1, div2, div3);

        return container;
    }

}

class SuccessIndicator extends AbstractBarIndicator {

    constructor(solved, failed) {
        super(solved, failed);
    }

    getCssClasses() {
        return [
            'bar-indicator-solved',
            'bar-indicator-skipped',
            'bar-indicator-failed'
        ];
    }

    mustWriteValue() {
        return true;
    }

    render() {
        let container = super.render();
        if (this.value2 === 0) {
            container.getElementsByTagName('div')[0].classList.add('bar-indicator-solved-100-percent');
        }
        return container;
    }

}

class GameOutcomeIndicator extends AbstractBarIndicator {

    #redWinsRate;
    #blackWinsRate;

    constructor(redWins, blackWins) {
        super(redWins, blackWins);
        this.#redWinsRate = redWins;
        this.#blackWinsRate = blackWins;
    }

    getCssClasses() {
        return [
            'bar-indicator-red-wins',
            'bar-indicator-draw',
            'bar-indicator-black-wins'
        ];
    }

    mustWriteValue() {
        return false;
    }

    render() {
        let container = super.render();
        if (this.#redWinsRate === 0) {
            container
                .getElementsByClassName('bar-indicator')[1]
                .classList
                .add('bar-indicator-rounded-left');
        }
        if (this.#blackWinsRate === 0) {
            container
                .getElementsByClassName('bar-indicator')[1]
                .classList
                .add('bar-indicator-rounded-right');
        }
        return container;
    }

}

class EvalBarIndicator extends GameOutcomeIndicator {

    #cp;
    #mate;
    #normalizedEval;

    /**
     * @param cp {number|null}
     * @param mate {number|null}
     * @param normalizedEval {number|null}
     */
    constructor(cp, mate, normalizedEval) {
        if ((cp != null && normalizedEval != null) || (mate != null && normalizedEval != null)) {
            if (cp != null) {
                let maxAbsEval = 100;
                let bound = maxAbsEval * 2;
                let value = 0.5;
                if (normalizedEval >= 0) {
                    value = value + (normalizedEval / bound);
                } else {
                    value = value - ((normalizedEval / bound) * -1);
                }
                super(value, 1 - value);
                this.#cp = cp;
                this.#mate = mate;
                this.#normalizedEval = normalizedEval;
            } else if (mate != null) {
                if (normalizedEval >= 0) {
                    super(1, 0);
                } else {
                    super(0, 1);
                }
                this.#cp = cp;
                this.#mate = mate;
                this.#normalizedEval = normalizedEval;
            }
        } else {
            super(0, 0);
            this.#cp = cp;
            this.#mate = mate;
            this.#normalizedEval = normalizedEval;
        }
    }

    render() {
        let container = super.render();
        if (this.#cp != null && this.#normalizedEval != null) {
            let values = document.createElement('div');
            values.id = 'indicator-values';
            values.innerText = 'cp ' + this.#cp + ' | eval ' + formatCp(this.#normalizedEval);
            container.append(values);
        } else if (this.#mate != null && this.#normalizedEval != null) {
            let values = document.createElement('div');
            values.id = 'indicator-values';
            values.innerText = 'mate ' + this.#mate + ' | eval ' + formatCp(this.#normalizedEval);
            container.append(values);
        }
        return container;
    }

}

class ProgressIndicator extends AbstractBarIndicator {

    constructor(progress) {
        super(progress, 1 - progress);
    }

    getCssClasses() {
        return [
            'bar-indicator-progress',
            'bar-indicator-progress-unfinished',
            'bar-indicator-progress-unfinished'
        ];
    }

    mustWriteValue() {
        return true;
    }

}
