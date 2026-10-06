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

class AdminSettingPreferencesPage extends BasePage {

    #totalEventsSpan = document.getElementById('total-events');
    #stringFieldsContainer = document.getElementById('string-fields');
    #numberFieldsContainer = document.getElementById('number-fields');

    constructor() {
        super();
        this.#fetchStats();
    }

    #fetchStats() {
        getAndHandle(ADMIN_URL_PREFIX + '/setting-preference-stats', json => this.#render(json));
    }

    /**
     * @param json {object}
     */
    #render(json) {
        const total = json.totalCount || 0;
        this.#totalEventsSpan.innerText = total.toLocaleString();

        const charts = [];
        (json.stringFields || []).forEach((field, index) =>
            charts.push(...this.#renderStringField(field, index, total)));
        (json.numberFields || []).forEach((field, index) =>
            charts.push(...this.#renderNumberField(field, index, total)));

        charts.forEach(chart => chart.render());
    }

    /**
     * @param fieldName {string}
     * @returns {string}
     */
    #prettifyFieldName(fieldName) {
        return fieldName.replace(/_/g, ' ');
    }

    /**
     * @param count {number}
     * @param total {number}
     * @returns {string}
     */
    #formatCountWithPercent(count, total) {
        const pct = total === 0 ? 0 : (count / total) * 100;
        return `${count.toLocaleString()} (${pct.toFixed(1)}%)`;
    }

    /**
     * Builds the shared layout for a single field and returns the two chart container ids.
     * @param parent {HTMLElement}
     * @param fieldName {string}
     * @param summaryHtml {string}
     * @param nullContainerId {string}
     * @param valuesContainerId {string}
     * @param valuesTitle {string}
     */
    #buildFieldSection(parent, fieldName, summaryHtml, nullContainerId, valuesContainerId, valuesTitle) {
        const section = document.createElement('div');
        section.className = 'setting-preference-field';
        section.style.marginBottom = '32px';

        const title = document.createElement('h3');
        title.innerText = this.#prettifyFieldName(fieldName);
        title.style.fontFamily = 'monospace';
        section.appendChild(title);

        const summary = document.createElement('p');
        summary.innerHTML = summaryHtml;
        section.appendChild(summary);

        const chartsRow = document.createElement('div');
        chartsRow.style.display = 'flex';
        chartsRow.style.flexWrap = 'wrap';
        chartsRow.style.gap = '24px';
        chartsRow.style.alignItems = 'flex-start';

        const nullColumn = document.createElement('div');
        nullColumn.style.flex = '0 0 280px';
        const nullTitle = document.createElement('div');
        nullTitle.innerText = 'null vs not null';
        nullTitle.style.fontWeight = '600';
        nullTitle.style.marginBottom = '4px';
        nullColumn.appendChild(nullTitle);
        const nullContainer = document.createElement('div');
        nullContainer.id = nullContainerId;
        nullColumn.appendChild(nullContainer);
        chartsRow.appendChild(nullColumn);

        const valuesColumn = document.createElement('div');
        valuesColumn.style.flex = '1 1 420px';
        valuesColumn.style.minWidth = '320px';
        const valuesTitleEl = document.createElement('div');
        valuesTitleEl.innerText = valuesTitle;
        valuesTitleEl.style.fontWeight = '600';
        valuesTitleEl.style.marginBottom = '4px';
        valuesColumn.appendChild(valuesTitleEl);
        const valuesContainer = document.createElement('div');
        valuesContainer.id = valuesContainerId;
        valuesColumn.appendChild(valuesContainer);
        chartsRow.appendChild(valuesColumn);

        section.appendChild(chartsRow);
        parent.appendChild(section);
    }

    /**
     * @param field {object}
     * @param index {number}
     * @param total {number}
     * @returns {ApexChartWidget[]}
     */
    #renderStringField(field, index, total) {
        const nullContainerId = `string-null-${index}`;
        const valuesContainerId = `string-values-${index}`;

        const summary =
            `null: ${this.#formatCountWithPercent(field.nullCount, total)}` +
            ` &middot; not null: ${this.#formatCountWithPercent(field.nonNullCount, total)}` +
            ` &middot; distinct values: ${(field.values || []).length}`;

        this.#buildFieldSection(
            this.#stringFieldsContainer,
            field.fieldName,
            summary,
            nullContainerId,
            valuesContainerId,
            'non-null value distribution'
        );

        return [
            new NullVsNotNullChart(nullContainerId, field.nullCount, field.nonNullCount),
            new CategoryDistributionBarChart(valuesContainerId, field.values || [])
        ];
    }

    /**
     * @param field {object}
     * @param index {number}
     * @param total {number}
     * @returns {ApexChartWidget[]}
     */
    #renderNumberField(field, index, total) {
        const nullContainerId = `number-null-${index}`;
        const bucketsContainerId = `number-buckets-${index}`;

        const minMaxAvg =
            field.min === null || field.min === undefined
                ? ''
                : ` &middot; min: ${Math.round(field.min).toLocaleString()}` +
                  ` &middot; max: ${Math.round(field.max).toLocaleString()}` +
                  ` &middot; avg: ${field.avg.toFixed(1)}`;

        const summary =
            `null: ${this.#formatCountWithPercent(field.nullCount, total)}` +
            ` &middot; not null: ${this.#formatCountWithPercent(field.nonNullCount, total)}` +
            minMaxAvg;

        this.#buildFieldSection(
            this.#numberFieldsContainer,
            field.fieldName,
            summary,
            nullContainerId,
            bucketsContainerId,
            'non-null value histogram'
        );

        return [
            new NullVsNotNullChart(nullContainerId, field.nullCount, field.nonNullCount),
            new NumberBucketBarChart(bucketsContainerId, field.buckets || [])
        ];
    }

}

window.onload = () => new AdminSettingPreferencesPage();
