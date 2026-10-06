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

        const palette = [
            '#008FFB', '#00E396', '#FEB019', '#FF4560', '#775DD0',
            '#3F51B5', '#546E7A', '#D4526E', '#8D5B4C', '#F86624',
            '#1B998B', '#2E294E', '#662E9B', '#C5D86D'
        ];
        const colors = labels.map((label, index) => {
            if (label === 'true') {
                return '#00E396';
            } else if (label === 'false') {
                return '#FF4560';
            }
            return palette[index % palette.length];
        });

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
