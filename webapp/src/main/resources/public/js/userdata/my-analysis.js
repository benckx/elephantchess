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

class PreviousVersionsContextualMenu extends ContextualMenu {

    constructor(analysisId, versionNumbers) {
        super('previous-version-contextual-menu-' + analysisId);
        this.menuContainer.classList.add('previous-version-contextual-menu');
        for (let i = 0; i < versionNumbers.length; i++) {
            this.addSimpleItem('Version ' + versionNumbers[i], () => {
                window.location.href = '/analysis?id=' + analysisId + '&version=' + versionNumbers[i];
            });
        }
    }

}

class MyAnalysisPage extends InfiniteScrollPage {

    #myGameItems = document.getElementById('my-game-items');
    #noAnalysisMessage = document.getElementById('no-analysis-message');
    #contextualMenus = new Map();

    constructor() {
        super();
        this.fetchItems();
        UI.preloadModal('confirmation');
    }

    baseUrl() {
        return '/api/analysis/list-user-analysis';
    }

    deserializeJsonEntry(jsonEntry) {
        return new AnalysisEntryDto(jsonEntry);
    }

    extractToken(entry) {
        return entry.lastUpdated.toString();
    }

    showNoItem(value) {
        this.#noAnalysisMessage.style.display = value ? 'block' : 'none';
    }

    /**
     * @param entries {AnalysisEntryDto[]}
     */
    addEntries(entries) {
        const parentPageObject = this;

        /**
         * @param entry {AnalysisEntryDto}
         * @returns {HTMLDivElement}
         */
        function buildIconDiv(entry) {
            const puzzleImg = document.createElement('img');
            puzzleImg.className = 'icon';
            puzzleImg.src = analysisIconByGameType(entry.gameType);
            puzzleImg.alt = 'Analysis';
            return wrapInDiv(puzzleImg);
        }

        /**
         * @param entry {AnalysisEntryDto}
         * @returns {HTMLDivElement|null}
         */
        function buildNumberOfAnnotationsDiv(entry) {
            if (entry.numberOfAnnotations > 0) {
                let numberOfAnnotationsLabel;
                if (entry.numberOfAnnotations === 1) {
                    numberOfAnnotationsLabel = '1 annotation';
                } else if (entry.numberOfAnnotations > 1) {
                    numberOfAnnotationsLabel = entry.numberOfAnnotations + ' annotations';
                } else {
                    numberOfAnnotationsLabel = 'no annotations';
                }

                return buildDivWithTextAndClasses(
                    numberOfAnnotationsLabel,
                    ['default-text', 'number-of-annotations']
                )
            } else {
                return null;
            }
        }

        /**
         * @param entry {AnalysisEntryDto}
         * @returns {HTMLDivElement|null}
         */
        function buildNumberOfVariationsDiv(entry) {
            if (entry.numberOfVariations > 0) {
                let numberOfVariationsLabel;
                if (entry.numberOfVariations === 1) {
                    numberOfVariationsLabel = '1 variation';
                } else if (entry.numberOfVariations > 1) {
                    numberOfVariationsLabel = entry.numberOfVariations + ' variations';
                } else {
                    numberOfVariationsLabel = 'no variations';
                }

                return buildDivWithTextAndClasses(
                    numberOfVariationsLabel,
                    ['default-text', 'number-of-variations']
                )
            } else {
                return null;
            }
        }

        /**
         * @param entry {AnalysisEntryDto}
         */
        function buildPreviousVersionDiv(entry) {
            const previousVersionImg = document.createElement('img');
            previousVersionImg.className = 'icon';
            previousVersionImg.id = `previous-version-icon-${entry.analysisId}`;
            previousVersionImg.src = '/images/icons/history.png';
            if (entry.currentVersion === 1) {
                previousVersionImg.classList.add('icon-disabled');
            }

            const previousVersionAnchor = document.createElement('a');
            previousVersionAnchor.id = `previous-version-cell-${entry.analysisId}`;
            previousVersionAnchor.setAttribute('href', '#');
            previousVersionAnchor.append(previousVersionImg);
            previousVersionAnchor.addEventListener('click', (e) => {
                e.preventDefault();
                parentPageObject.#handleClickedPreviousVersionButton(entry, previousVersionImg);
            });


            // contextual menu to load previous versions
            const contextualMenu = new PreviousVersionsContextualMenu(entry.analysisId, entry.versions);
            DropDownMenuManager.getInstance().registerDropDownMenu(contextualMenu, [previousVersionImg.id]);
            parentPageObject.#contextualMenus.set(entry.analysisId, contextualMenu);

            return previousVersionAnchor;
        }

        function buildDeleteButtonDiv(entry) {
            const deleteImg = document.createElement('img');
            deleteImg.classList.add('icon', 'delete-icon');
            deleteImg.src = `${ICON_PATH}/trash.png`;

            const deleteAnchor = document.createElement('a');
            deleteAnchor.id = `delete-analysis-${entry.analysisId}`;
            deleteAnchor.setAttribute('href', '#');
            deleteAnchor.append(deleteImg);
            deleteAnchor.addEventListener('click', () => parentPageObject.#handleClickedDeleteButton(entry));
            addToolTip(deleteAnchor, 'Delete analysis');
            return deleteAnchor;
        }

        /**
         * @param entry {AnalysisEntryDto}
         * @returns {HTMLDivElement}
         */
        function buildLastUpdatedDiv(entry) {
            const lastUpdatedDiv = document.createElement('div');
            lastUpdatedDiv.id = `last-updated-${entry.analysisId}`;
            lastUpdatedDiv.className = 'last-modified';
            setRelativeTimeAndToolTip(lastUpdatedDiv, entry.lastUpdated);
            return lastUpdatedDiv;
        }

        entries.forEach(entry => {
            const leftPane = document.createElement('div');
            leftPane.className = 'left-pane';

            const middlePane = document.createElement('div');
            middlePane.className = 'middle-pane';

            const previousVersionIndicatorPane = document.createElement('div');
            previousVersionIndicatorPane.className = 'indicator-pane';

            const deleteIndicatorPane = document.createElement('div');
            deleteIndicatorPane.className = 'indicator-pane';

            const rightPane = document.createElement('div');
            rightPane.className = 'right-pane';

            const item = document.createElement('a');
            item.id = entry.rowId;
            item.className = 'my-game-item';
            item.setAttribute('href', entry.url);

            item.append(
                leftPane,
                middlePane,
                previousVersionIndicatorPane,
                deleteIndicatorPane,
                rightPane
            );

            this.#myGameItems.append(item);
            if (entry.selectedNodeFen != null) {
                addMiniboardDiv(item, entry.analysisId, entry.selectedNodeFen, Color.RED);
            }

            // left pane
            leftPane.append(buildIconDiv(entry))

            // indicator panes
            previousVersionIndicatorPane.append(buildPreviousVersionDiv(entry));
            deleteIndicatorPane.append(buildDeleteButtonDiv(entry));

            // middle pane
            middlePane.append(
                buildDivWithTextAndClasses(
                    entry.name,
                    ['default-text', 'analysis-name']
                )
            );

            const numberOfVariationsDiv = buildNumberOfVariationsDiv(entry);
            if (numberOfVariationsDiv != null) {
                middlePane.append(numberOfVariationsDiv);
            }

            const numberOfAnnotationsDiv = buildNumberOfAnnotationsDiv(entry);
            if (numberOfAnnotationsDiv != null) {
                middlePane.append(numberOfAnnotationsDiv);
            }

            // right pane
            rightPane.append(
                buildLastUpdatedDiv(entry),
                buildDivWithTextAndClasses(
                    `version ${entry.currentVersion}`,
                    ['default-text', 'analysis-version']
                ),
            );
        });
    }

    // Keep the original table rendering for reference
    addEntriesTable(entries) {
        entries.forEach(entry => {
            // let tbody = this.#table.tBodies[0];
            let row = document.createElement('tr');
            row.id = entry.rowId;

            let analysisNameCell = row.insertCell();
            analysisNameCell.classList.add('clickable', 'analysis-name-cell');
            analysisNameCell.append(buildLink(entry.url, entry.name));

            let currentVersionCell = row.insertCell();
            currentVersionCell.classList.add('clickable');
            currentVersionCell.innerText = entry.currentVersion.toString();

            let createdCell = row.insertCell();
            createdCell.id = 'created-' + entry.analysisId;
            createdCell.classList.add('clickable', 'date-cell');
            setRelativeTimeAndToolTip(createdCell, entry.created);

            let lastUpdatedCell = row.insertCell();
            lastUpdatedCell.id = 'last-updated-' + entry.analysisId;
            lastUpdatedCell.classList.add('clickable', 'date-cell');
            setRelativeTimeAndToolTip(lastUpdatedCell, entry.lastUpdated);

            let previousVersionImg = document.createElement('img');
            previousVersionImg.className = 'icons';
            previousVersionImg.id = 'previous-version-icon-' + entry.analysisId;
            previousVersionImg.src = '/images/icons/history.png';
            if (entry.currentVersion === 1) {
                previousVersionImg.classList.add('icons-disabled');
            }

            let previousVersionCell = row.insertCell();
            previousVersionCell.id = 'previous-version-cell-' + entry.analysisId;
            previousVersionCell.classList.add('action-cell');
            previousVersionCell.append(previousVersionImg);
            previousVersionCell.addEventListener('click', () => {
                this.#handleClickedPreviousVersionButton(entry, previousVersionImg);
            });
            if (entry.currentVersion !== 1) {
                addToolTip(previousVersionCell, 'Load a previous version');
            }

            const deleteImg = document.createElement('img');
            deleteImg.className = 'icons';
            deleteImg.src = `${ICON_PATH}/trash.png`;

            const deleteCell = row.insertCell();
            deleteCell.id = 'delete-cell-' + entry.analysisId;
            deleteCell.classList.add('action-cell');
            deleteCell.append(deleteImg);
            deleteCell.addEventListener('click', () => this.#handleClickedDeleteButton(entry));
            addToolTip(deleteCell, 'Delete analysis');

            // contextual menu to load previous versions
            let contextualMenu = new PreviousVersionsContextualMenu(entry.analysisId, entry.versions);
            DropDownMenuManager.getInstance().registerDropDownMenu(contextualMenu, [previousVersionImg.id]);
            this.#contextualMenus.set(entry.analysisId, contextualMenu);
        });
    }

    /**
     * @param entry {AnalysisEntryDto}
     * @param img {HTMLImageElement}
     */
    #handleClickedPreviousVersionButton(entry, img) {
        if (entry.currentVersion > 1) {
            const rectangle = img.getBoundingClientRect();
            const x = rectangle.x;
            const y = rectangle.y + rectangle.height;
            this.#contextualMenus.get(entry.analysisId).showAt(x, y);
        }
    }

    /**
     * @param {AnalysisEntryDto} entry
     */
    #handleClickedDeleteButton(entry) {
        let name = sanitizeString(entry.name);
        let text = 'Are you sure you want to delete "' + name + '"? This can not be reverted.';
        let yesCallback = () => this.#deleteAnalysis(entry);
        let yesButtonText = 'delete';
        let noCallback = () => UI.hideModal(null);
        let noButtonText = 'cancel';
        UI.showConfirmationModal(text, yesCallback, yesButtonText, noCallback, noButtonText);
    }

    /**
     * @param {AnalysisEntryDto} entry
     */
    #deleteAnalysis(entry) {
        function deleteItem(rowId) {
            const row = document.getElementById(rowId);
            row.parentNode.removeChild(row);
        }

        const body = {'analysisId': entry.analysisId};

        postAndHandle('/api/analysis/delete', body, () => {
            UI.pushInfoNotification('Analysis "' + entry.name + '" deleted successfully.', 3_000);
            deleteItem(entry.rowId);
        });
    }

}

window.onload = () => new MyAnalysisPage();
