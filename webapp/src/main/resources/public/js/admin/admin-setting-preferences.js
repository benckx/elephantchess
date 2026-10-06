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

    #segmentTotalsSpan = document.getElementById('segment-totals');
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
        const segments = json.segments || [];

        this.#segmentTotalsSpan.innerText = segments
            .map(segment => `${this.#prettifyUserType(segment.userType)}: ${segment.totalCount.toLocaleString()}`)
            .join(' \u00b7 ');

        const charts = [];

        this.#groupByField(segments, 'stringFields').forEach((perSegment, index) =>
            charts.push(...this.#renderStringField(perSegment, index)));

        this.#groupByField(segments, 'numberFields').forEach((perSegment, index) =>
            charts.push(...this.#renderNumberField(perSegment, index)));

        charts.forEach(chart => chart.render());
    }

    /**
     * Groups the stats of each field across all segments.
     * @param segments {Array}
     * @param collectionKey {string} - 'stringFields' or 'numberFields'
     * @returns {Array<Array<{segment: object, field: object}>>}
     */
    #groupByField(segments, collectionKey) {
        const fieldNames = [];
        segments.forEach(segment => {
            (segment[collectionKey] || []).forEach(field => {
                if (!fieldNames.includes(field.fieldName)) {
                    fieldNames.push(field.fieldName);
                }
            });
        });

        return fieldNames.map(fieldName =>
            segments
                .map(segment => ({
                    segment: segment,
                    field: (segment[collectionKey] || []).find(f => f.fieldName === fieldName)
                }))
                .filter(entry => entry.field)
        );
    }

    /**
     * @param userType {string}
     * @returns {string}
     */
    #prettifyUserType(userType) {
        return userType.toLowerCase();
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
     * Builds a field section containing one sub-block per user type.
     * @param parent {HTMLElement}
     * @param fieldName {string}
     * @param perSegment {Array<{segment: object, field: object}>}
     * @param valuesTitle {string}
     * @param renderBlock {function(HTMLElement, object, object, string): ApexChartWidget[]}
     * @returns {ApexChartWidget[]}
     */
    #buildFieldSection(parent, fieldName, perSegment, valuesTitle, renderBlock) {
        const section = document.createElement('div');
        section.className = 'setting-preference-field';

        const title = document.createElement('h3');
        title.innerText = this.#prettifyFieldName(fieldName);
        section.appendChild(title);

        // Attach the section before building charts so the chart containers are already part of the
        // document when ApexCharts looks them up by id.
        parent.appendChild(section);

        const charts = [];

        perSegment.forEach(({segment, field}) => {
            const block = document.createElement('div');
            block.className = 'sp-segment';

            const blockTitle = document.createElement('div');
            blockTitle.className = 'sp-segment-user-type';
            blockTitle.innerText = this.#prettifyUserType(segment.userType);
            block.appendChild(blockTitle);
            section.appendChild(block);

            charts.push(...renderBlock(block, segment, field, valuesTitle));
        });

        return charts;
    }

    /**
     * Lays out a null-vs-not-null chart next to a values/histogram chart.
     * @param block {HTMLElement}
     * @param nullContainerId {string}
     * @param valuesContainerId {string}
     * @param valuesTitle {string}
     * @param compact {boolean} - when true, constrains the row width (used for pie-chart rows)
     */
    #buildChartRow(block, nullContainerId, valuesContainerId, valuesTitle, compact = false) {
        const chartsRow = document.createElement('div');
        chartsRow.className = compact ? 'sp-chart-row sp-chart-row-compact' : 'sp-chart-row';

        const nullColumn = document.createElement('div');
        nullColumn.className = 'sp-null-col';
        const nullTitle = document.createElement('div');
        nullTitle.className = 'sp-chart-label';
        nullTitle.innerText = 'null vs not null';
        nullColumn.appendChild(nullTitle);
        const nullContainer = document.createElement('div');
        nullContainer.id = nullContainerId;
        nullColumn.appendChild(nullContainer);
        chartsRow.appendChild(nullColumn);

        const valuesColumn = document.createElement('div');
        valuesColumn.className = 'sp-values-col';
        const valuesTitleEl = document.createElement('div');
        valuesTitleEl.className = 'sp-chart-label';
        valuesTitleEl.innerText = valuesTitle;
        valuesColumn.appendChild(valuesTitleEl);
        const valuesContainer = document.createElement('div');
        valuesContainer.id = valuesContainerId;
        valuesColumn.appendChild(valuesContainer);
        chartsRow.appendChild(valuesColumn);

        block.appendChild(chartsRow);
    }

    /**
     * @param perSegment {Array<{segment: object, field: object}>}
     * @param index {number}
     * @returns {ApexChartWidget[]}
     */
    #renderStringField(perSegment, index) {
        const fieldName = perSegment[0].field.fieldName;

        return this.#buildFieldSection(
            this.#stringFieldsContainer,
            fieldName,
            perSegment,
            'non-null value distribution',
            (block, segment, field, valuesTitle) => {
                const nullContainerId = `string-null-${index}-${segment.userType}`;
                const valuesContainerId = `string-values-${index}-${segment.userType}`;

                const summary = document.createElement('p');
                summary.className = 'sp-summary';
                summary.innerHTML =
                    `set: ${this.#formatCountWithPercent(field.nonNullCount, segment.totalCount)}`;
                block.appendChild(summary);

                this.#buildChartRow(block, nullContainerId, valuesContainerId, valuesTitle, true);

                return [
                    new NullVsNotNullChart(nullContainerId, field.nullCount, field.nonNullCount),
                    new CategoryDistributionPieChart(valuesContainerId, field.values || [])
                ];
            }
        );
    }

    /**
     * @param perSegment {Array<{segment: object, field: object}>}
     * @param index {number}
     * @returns {ApexChartWidget[]}
     */
    #renderNumberField(perSegment, index) {
        const fieldName = perSegment[0].field.fieldName;

        return this.#buildFieldSection(
            this.#numberFieldsContainer,
            fieldName,
            perSegment,
            'non-null value histogram',
            (block, segment, field, valuesTitle) => {
                const nullContainerId = `number-null-${index}-${segment.userType}`;
                const bucketsContainerId = `number-buckets-${index}-${segment.userType}`;

                const minMaxAvg =
                    field.min === null || field.min === undefined
                        ? ''
                        : ` &middot; min: ${Math.round(field.min).toLocaleString()}` +
                          ` &middot; max: ${Math.round(field.max).toLocaleString()}` +
                          ` &middot; avg: ${field.avg.toFixed(1)}`;

                const summary = document.createElement('p');
                summary.className = 'sp-summary';
                summary.innerHTML =
                    `set: ${this.#formatCountWithPercent(field.nonNullCount, segment.totalCount)}` +
                    minMaxAvg;
                block.appendChild(summary);

                this.#buildChartRow(block, nullContainerId, bucketsContainerId, valuesTitle);

                return [
                    new NullVsNotNullChart(nullContainerId, field.nullCount, field.nonNullCount),
                    new NumberBucketBarChart(bucketsContainerId, field.buckets || [])
                ];
            }
        );
    }

}

window.onload = () => new AdminSettingPreferencesPage();
