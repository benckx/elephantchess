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

class DatabaseTableSizesChart extends ApexChartWidget {

    /**
     * @param containerId {string}
     * @param entries {Array} - Array of table entries with totalSize property
     */
    constructor(containerId, entries) {
        super(containerId);

        if (entries.length === 0) {
            return;
        }

        /**
         * Parse size string to GB
         * @param sizeStr {string} - e.g., "1.5 GB", "500 MB"
         * @returns {number} - size in GB
         */
        const parseSizeToGB = (sizeStr) => {
            const sizeMatch = sizeStr.match(/^([\d.]+)\s*(\w+)$/);
            if (sizeMatch) {
                const value = parseFloat(sizeMatch[1]);
                const unit = sizeMatch[2].toUpperCase();
                if (unit === 'GB') {
                    return value;
                } else if (unit === 'MB') {
                    return value / 1024;
                } else if (unit === 'KB') {
                    return value / (1024 * 1024);
                } else if (unit === 'BYTES' || unit === 'B') {
                    return value / (1024 * 1024 * 1024);
                }
            }
            return 0;
        };

        // Parse the sizes to get numeric values for sorting
        const entriesWithNumericSize = entries.map(entry => ({
            ...entry,
            totalSizeInGB: parseSizeToGB(entry.totalSize),
            tableSizeInGB: parseSizeToGB(entry.tableSize),
            indexSizeInGB: parseSizeToGB(entry.indexSize)
        }));

        // Sort by total size and get top 10
        const top10 = entriesWithNumericSize
            .sort((a, b) => b.totalSizeInGB - a.totalSizeInGB)
            .slice(0, 10);

        // Prepare data for chart
        const categories = top10.map(entry => entry.tableName);
        const tableData = top10.map(entry => entry.tableSizeInGB);
        const indexData = top10.map(entry => entry.indexSizeInGB);

        // Create chart options
        this.chartOptions = {
            series: [{
                name: 'Table Data',
                data: tableData
            }, {
                name: 'Index',
                data: indexData
            }],
            chart: {
                height: 400,
                type: 'bar',
                stacked: true,
                toolbar: { show: false },
                animations: { enabled: false }
            },
            plotOptions: {
                bar: {
                    horizontal: false,
                    dataLabels: {
                        total: {
                            enabled: true,
                            offsetY: -5,
                            style: {
                                fontSize: '13px',
                                fontWeight: 600
                            },
                            formatter: function(val) {
                                if (val >= 1) {
                                    return val.toFixed(2) + ' GB';
                                } else {
                                    return (val * 1024).toFixed(2) + ' MB';
                                }
                            }
                        }
                    }
                }
            },
            dataLabels: {
                enabled: false
            },
            colors: ['#008FFB', '#7FC4FF'],
            legend: {
                position: 'top',
                horizontalAlign: 'left'
            },
            xaxis: {
                categories: categories,
                labels: {
                    rotate: -45,
                    rotateAlways: true
                }
            },
            yaxis: {
                title: {
                    text: 'Size (GB)'
                },
                labels: {
                    formatter: function(val) {
                        return val.toFixed(2);
                    }
                }
            },
            tooltip: {
                y: {
                    formatter: function(val) {
                        if (val >= 1) {
                            return val.toFixed(3) + ' GB';
                        } else {
                            return (val * 1024).toFixed(2) + ' MB';
                        }
                    }
                }
            }
        };

        this.enableRender();
    }

}

