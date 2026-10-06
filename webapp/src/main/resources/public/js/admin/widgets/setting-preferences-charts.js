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

/**
 * Donut chart showing how often a column is set (not null) vs unset (null), as a percentage.
 */
class NullVsNotNullChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param nullCount {number}
     * @param nonNullCount {number}
     */
    constructor(containerId, nullCount, nonNullCount) {
        super(containerId);

        const total = nullCount + nonNullCount;
        if (total === 0) {
            return;
        }

        this.chartOptions = {
            series: [nonNullCount, nullCount],
            labels: ['not null', 'null'],
            chart: {
                height: 260,
                type: 'donut',
                animations: {enabled: false}
            },
            colors: ['#008FFB', '#d0d0d0'],
            legend: {
                position: 'bottom'
            },
            dataLabels: {
                enabled: true,
                formatter: (val) => val.toFixed(1) + '%'
            },
            tooltip: {
                y: {
                    formatter: (val) => {
                        const pct = total === 0 ? 0 : (val / total) * 100;
                        return `${val.toLocaleString()} (${pct.toFixed(1)}%)`;
                    }
                }
            }
        };

        this.enableRender();
    }

}

/**
 * Pie chart showing the distribution of the non-null values of a (string) column.
 */
class CategoryDistributionPieChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param values {Array<{value: string, count: number}>}
     */
    constructor(containerId, values) {
        super(containerId);

        if (!values || values.length === 0) {
            return;
        }

        const sorted = [...values].sort((a, b) => b.count - a.count);
        const labels = sorted.map(entry => entry.value);
        const data = sorted.map(entry => entry.count);

        // Only override the default ApexCharts colors for boolean fields, to keep true green and
        // false red. Everything else uses the default palette.
        const isBooleanField = labels.every(label => label === 'true' || label === 'false');
        const colors = isBooleanField
            ? labels.map(label => (label === 'true' ? '#00E396' : '#FF4560'))
            : undefined;

        this.chartOptions = {
            series: data,
            labels: labels,
            chart: {
                height: Math.max(260, 220 + labels.length * 6),
                type: 'pie',
                animations: {enabled: false}
            },
            colors: colors,
            legend: {
                position: 'bottom'
            },
            dataLabels: {
                enabled: true,
                formatter: (val) => val.toFixed(1) + '%'
            },
            tooltip: {
                y: {
                    formatter: (val) => val.toLocaleString()
                }
            }
        };

        this.enableRender();
    }

}

/**
 * Vertical bar chart rendered as a histogram (no gaps between bars) of the bucketed non-null
 * values of a number column.
 */
class NumberBucketBarChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param buckets {Array<{label: string, count: number}>}
     */
    constructor(containerId, buckets) {
        super(containerId);

        if (!buckets || buckets.length === 0) {
            return;
        }

        const categories = buckets.map(bucket => bucket.label);
        const data = buckets.map(bucket => bucket.count);

        this.chartOptions = {
            series: [{
                name: 'count',
                data: data
            }],
            chart: {
                height: 300,
                type: 'bar',
                toolbar: {show: false},
                animations: {enabled: false}
            },
            plotOptions: {
                bar: {
                    columnWidth: '100%'
                }
            },
            colors: ['#008FFB'],
            stroke: {
                show: true,
                width: 1,
                colors: ['#fff']
            },
            legend: {
                show: false
            },
            dataLabels: {
                enabled: false
            },
            xaxis: {
                categories: categories,
                labels: {
                    rotate: -45,
                    rotateAlways: true
                }
            },
            yaxis: {
                labels: {
                    formatter: (val) => val.toFixed(0)
                }
            },
            tooltip: {
                y: {
                    formatter: (val) => val.toLocaleString()
                }
            }
        };

        this.enableRender();
    }

}

/**
 * Line chart showing the monthly evolution of the percentage share of each non-null value of a
 * (string) column. Shares (not raw counts) are plotted because the sampling rate changes over time.
 */
class CategoryMonthlyShareLineChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param months {Array<string>}
     * @param sampleSizes {Array<number>} - non-null sample size per month, aligned with months
     * @param series {Array<{value: string, shares: Array<number>}>}
     */
    constructor(containerId, months, sampleSizes, series) {
        super(containerId);

        if (!months || months.length === 0 || !series || series.length === 0) {
            return;
        }

        const isBooleanField = series.every(s => s.value === 'true' || s.value === 'false');
        const colors = isBooleanField
            ? series.map(s => (s.value === 'true' ? '#00E396' : '#FF4560'))
            : undefined;

        this.chartOptions = {
            series: series.map(s => ({name: s.value, data: s.shares})),
            chart: {
                height: 220,
                type: 'line',
                toolbar: {show: false},
                animations: {enabled: false}
            },
            colors: colors,
            stroke: {
                width: 2,
                curve: 'straight'
            },
            markers: {
                size: 4
            },
            dataLabels: {
                enabled: false
            },
            legend: {
                position: 'bottom'
            },
            xaxis: {
                categories: months
            },
            yaxis: {
                min: 0,
                max: 100,
                labels: {
                    formatter: (val) => val.toFixed(0) + '%'
                }
            },
            tooltip: {
                x: {
                    formatter: (val, opts) => {
                        const size = (sampleSizes && sampleSizes[opts.dataPointIndex]) || 0;
                        return `${val} (n=${size.toLocaleString()})`;
                    }
                },
                y: {
                    formatter: (val) => val.toFixed(1) + '%'
                }
            }
        };

        this.enableRender();
    }

}
