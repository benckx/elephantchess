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
 * Horizontal bar chart showing the distribution of the non-null values of a (string) column.
 */
class CategoryDistributionBarChart extends ApexChartWidget {

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
        const categories = sorted.map(entry => entry.value);
        const data = sorted.map(entry => entry.count);

        this.chartOptions = {
            series: [{
                name: 'count',
                data: data
            }],
            chart: {
                height: Math.max(220, 40 + categories.length * 28),
                type: 'bar',
                toolbar: {show: false},
                animations: {enabled: false}
            },
            plotOptions: {
                bar: {
                    horizontal: true,
                    distributed: true
                }
            },
            colors: ['#008FFB'],
            legend: {
                show: false
            },
            dataLabels: {
                enabled: true,
                formatter: (val) => val.toLocaleString()
            },
            xaxis: {
                categories: categories
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
 * Vertical bar chart (histogram) of the bucketed non-null values of a number column.
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
                    columnWidth: '90%'
                }
            },
            colors: ['#008FFB'],
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
