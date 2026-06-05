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

const ANALYSIS_NOTIFICATION_TIMEOUT = 4_000;
const PROGRESS_BAR_DISAPPEAR_TIMEOUT = 4_000;

class AnalysisBoardPage extends BasePage {

    /**
     * @type {GameDataClient|null}
     */
    #gameDataClient;

    #client = new AnalysisBoardClient();

    /**
     * @type {AnalysisDto|null}
     */
    #analysis;

    /**
     * @type {GameMetadataDto|null}
     */
    #gameMetadata;

    // widgets

    #moveTreeWidget = new MoveTreeWidget({
        containerId: 'move-tree-container',
        isContextualMenuEnabled: true,
        isLoadingAnimationEnabled: true,
        ...moveTreeResizeCookiePersistence('analysis', 'move-tree-container')
    });

    #boardGui = createWebappBoardGui({ svg: true });
    #openingRepositoryWidget = new OpeningRepositoryWidget(this.#boardGui);
    #analysisCache = new AnalysisCache();
    #engineAnalysisWidget = new EngineAnalysisWidget(
        this.#analysisCache,
        this.#boardGui,
        this.#moveTreeWidget,
        (node) => {
            this.#selectedNode = node;
            this.#handleNodeSelected();
        }
    );

    #settingsManager = new SettingsManager();
    #settingsGui = new SettingsGui(this.#boardGui, this.#moveTreeWidget, true);

    #annotationBox = new AnnotationBox('annotation-box-analysis');

    /**
     * @type {HTMLInputElement}
     */
    #fenExportInput = document.getElementById('fen-export-input');

    /**
     * @type {HTMLImageElement}
     */
    #fenCopyIcon = document.getElementById('fen-copy-icon');

    /**
     * @type {MoveTreeNode}
     */
    #selectedNode = null;

    /**
     * @type {string|null}
     */
    #analysisId = null;

    /**
     * @type {number|null}
     */
    #analysisVersion = null;

    #persistOptionPanel = document.getElementById('persist-options');
    #saveAnalysisButton = document.getElementById('save-analysis-button');
    #renameAnalysisButton = document.getElementById('rename-analysis-button');
    #analysisNameField = document.getElementById('analysis-name');
    #analysisVersionLabel = document.getElementById('analysis-version-label');
    #analysisVersionLastUpdatedLabel = document.getElementById('analysis-version-last-updated-label');

    #gameTypeIcon = document.getElementById('game-type-icon');

    #startFen = DEFAULT_START_FEN;
    #analysisSummaryRenderTimeout = null;
    #moveTreeEvalRefreshTimeout = null;

    constructor() {
        super();

        // fen export
        this.#fenExportInput.value = this.#startFen;

        // add navigation panel to move tree widget
        this.#moveTreeWidget.addNavigationPanel({
            containerId: 'mobile-navigation-panel',
            isDownloadButtonEnabled: true
        });
        this.#moveTreeWidget.addNavigationPanel({
            containerId: 'move-history-navigation-panel',
            isDownloadButtonEnabled: true
        });

        // needs to be on top and not in 'createListeners', so we can display eval data when it's been loaded below
        this.#analysisCache.addNewPvListener(pv => {
            this.#pushNewInfoLineResultToMoveTreeWidget(pv);
            this.#scheduleRenderAnalysisSummaryIfPossible();
            this.#scheduleRefreshMoveTreeEvalFromCache();
            this.#renderEngineArrows();

            // enable the loading animation when receiving new evaluation data in the background
            // but not if the progress bar is visible (cause then it's the game that's being analyzed and not user-inputted moves)
            function isProgressBarVisible() {
                return document.getElementById('analysis-progress-bar-box').style.display !== 'none';
            }

            if (this.#analysisCache.size > 1 && !isProgressBarVisible()) {
                this.#moveTreeWidget.startLoadingAnimation();
            }

        });

        // 1. attempt to load persisted analysis
        // - if not set, attempt to load reference game
        // - if not set, start empty analysis board

        const analysisIdParam = getQueryParam('id');
        const versionParam = getQueryParam('version');
        const gameIdParam = getQueryParam('gameId');
        let gameTypeParam = getQueryParam('gameType');
        if (gameTypeParam == null && gameIdParam != null) {
            // default type for consistency, since it used to be the only option
            gameTypeParam = GameType.DB;
        }

        if (analysisIdParam != null) {
            // load persisted analysis
            this.#loadExistingAnalysis(analysisIdParam, versionParam);
        } else if (gameIdParam != null && gameTypeParam != null) {
            // load reference game data if gameId parameter is set
            this.#loadGenericGameAnalysisData(new GameId(gameTypeParam, gameIdParam));
        } else {
            // by default (i.e. we're not loading a persisted analysis or a reference game)
            this.#boardGui.loadFen(DEFAULT_START_FEN);
            this.#startUpWidgets();
            this.#showPersistOptionsBlockIfAllowed();
        }

        // init import moves modal
        UI.preloadModal(Modals.IMPORT_MOVES);
        this.#moveTreeWidget.importMovesCallback = () => {
            UI.showModalByName(Modals.IMPORT_MOVES, () => {
                new ImportMovesHandler(this.#boardGui, this.#moveTreeWidget, () => {
                    this.#handleNodeSelected();
                    this.#scheduleRenderAnalysisSummaryIfPossible();
                });
            });
        };

        // move history drop down menu (to import moves, etc.)
        new MoveHistoryDropDownMenuWidget(
            'move-history-drop-down-menu',
            this.#boardGui,
            this.#moveTreeWidget,
            () => this.#getCurrentStartFen(),
            (fen) => this.#handleSelectedStartFenFromPositionEditor(fen),
            () => {
                this.#handleNodeSelected();
                this.#scheduleRenderAnalysisSummaryIfPossible();
            }
        );

        // enable "edit position" button
        this.#settingsGui.enableEditPositionButton(
            () => this.#getCurrentStartFen(),
            (fen) => this.#handleSelectedStartFenFromPositionEditor(fen)
        );
    }

    /**
     * @param analysisId {string}
     * @param version {string|null}
     */
    #loadExistingAnalysis(analysisId, version) {
        this.#analysisId = analysisId;
        if (version != null && isNumber(version)) {
            this.#analysisVersion = Number(version);
        }

        // must be called after the custom startFen (if any) has been set in all widgets
        const afterMetadataLoading = (analysis) => {
            this.#moveTreeWidget.deserializeNodeDtos(analysis.nodes);
            this.#startUpWidgets();

            // we do this after listeners have been created, so it load positions on the board
            if (analysis.selectedNodeId != null) {
                this.#moveTreeWidget.selectNodeById(analysis.selectedNodeId);
            } else {
                this.#moveTreeWidget.selectLastNode();
            }

            // if user saved analysis with 0 node, the board will be empty
            if (analysis.nodes.length === 0) {
                this.#boardGui.loadFen(this.#startFen);
            }

            this.#client.fetchAnalysisEngineDataCache(analysisId, entries => {
                this.#analysisCache.populateCache(entries);
                this.#renderAnalysisSummaryIfLongEnough();
            });

            this.#moveTreeWidget.openBranchesByIds(analysis.openedBranchIds);
        }

        this.#client.fetchAnalysis(this.#analysisId, this.#analysisVersion, analysis => {
            this.#analysis = analysis;
            this.#analysisCache.analysisId = analysisId;

            if (analysis.hasGameId()) {
                this.#gameDataClient = new GameDataClient(analysis.gameId);
                this.#gameDataClient.fetchMetadata(gameMetadata => {
                    this.#gameMetadata = gameMetadata;
                    if (gameMetadata.startFen != null && gameMetadata.startFen !== DEFAULT_START_FEN) {
                        this.#loadStartFen(gameMetadata.startFen);
                    }
                    afterMetadataLoading(analysis);
                    this.#renderGameMetaDataInfoPanel();
                });
            } else {
                if (analysis.startFen != null) {
                    this.#loadStartFen(analysis.startFen);
                }
                afterMetadataLoading(analysis);
            }

            // update UI
            this.#showUpdateAnalysis();
            this.#analysisNameField.value = analysis.name;
            this.#analysisVersionLabel.innerText = analysis.version.toString();
            this.#analysisVersionLastUpdatedLabel.innerText = formatTimestampToDateTime(analysis.lastUpdated);

            if (analysis.isOwner) {
                this.#showPersistOptionsBlockIfAllowed();
            } else {
                this.#renderVisitorInfoBox();
            }
        });
    }

    /**
     * Load data related to a referenced PVP, PVB or DB game
     *
     * @param gameId {GameId}
     */
    #loadGenericGameAnalysisData(gameId) {
        this.#gameDataClient = new GameDataClient(gameId);
        this.#gameDataClient.fetchMetadata(gameMetadata => {
            this.#gameMetadata = gameMetadata;
            this.#loadStartFen(gameMetadata.startFen);

            this.#gameDataClient.fetchMoves(moves => {
                this.#moveTreeWidget.setMoves(moves);

                this.#gameDataClient.fetchAnalysisData(entries => {
                    this.#analysisCache.populateCache(entries);
                    this.#renderAnalysisSummaryIfLongEnough();
                    this.#startUpWidgets();
                });

                this.#gameDataClient.startAnalysis(response => {
                    // either has started just now, or was already started upon loading the page
                    if (response.hasStarted || gameMetadata.analysisStatus === AnalysisStatus.STARTED) {
                        this.#startToPollAnalysisStatus();
                    }
                });
            });

            this.#analysisCache.analysisStatus = gameMetadata.analysisStatus;
            this.#boardGui.hideAllHighlightedDynamicMoves();
            this.#boardGui.loadFen(gameMetadata.finalFen);
            this.#updateFenField();
            this.#renderGameMetaDataInfoPanel();

            this.#showPersistOptionsBlockIfAllowed()
        });
    }

    /**
     * When loading start FEN from a persisted analysis or a reference game
     *
     * @param startFen {string|null}
     */
    #loadStartFen(startFen) {
        if (startFen != null) {
            this.#startFen = startFen;
            this.#moveTreeWidget.startFen = startFen;
            this.#openingRepositoryWidget.startFen = startFen;
            this.#engineAnalysisWidget.startFen = startFen;
            console.log('loaded custom startFen: ' + startFen);
        }
    }

    /**
     * When updating start FEN from the position editor
     *
     * @param startFen {string|null}
     */
    #updateStartFen(startFen) {
        if (startFen != null) {
            this.#loadStartFen(startFen);
            this.#moveTreeWidget.clear();
            this.#boardGui.loadFen(startFen);
            this.#updateWidgets([]);
            this.#updateFenField();
            this.#renderEngineArrows();
        }
    }

    #getCurrentStartFen() {
        if (this.#startFen != null) {
            return this.#startFen;
        } else {
            return DEFAULT_START_FEN;
        }
    }

    #handleSelectedStartFenFromPositionEditor(startFen) {
        if (this.#getCurrentStartFen() !== startFen) {
            if (this.#isAnalysisPersisted()) {
                this.#saveAnalysisButton.classList.add('app-buttons-disabled');
                this.#client.updateStartFen(this.#analysisId, startFen, (response) => {
                    this.#updateStartFen(startFen);
                    this.#updateUiForSaveAnalysisResponse(response);
                    this.#saveAnalysisButton.classList.remove('app-buttons-disabled');
                    UI.pushInfoNotification(`Analysis saved with version ${response.version} and new starting position`, ANALYSIS_NOTIFICATION_TIMEOUT);
                });
            } else {
                this.#updateStartFen(startFen);
                UI.pushInfoNotification(`Starting position updated`, ANALYSIS_NOTIFICATION_TIMEOUT);
            }
        }
    }

    #showPersistOptionsBlockIfAllowed() {
        if (isUserAuthenticated()) {
            if (this.#gameMetadata != null && this.#analysisId == null) {
                // default title
                this.#analysisNameField.value = this.#gameMetadata.toStringPlayerNames();
            }

            this.#persistOptionPanel.style.display = 'block';
        }
    }

    /**
     * For 'visitors' of the analysis (i.e. not the owner of the analysis)
     */
    #renderVisitorInfoBox() {
        const analysisName = document.createElement('span');
        analysisName.className = 'info-about-analysis-parts';
        analysisName.innerHTML = this.#analysis.name;
        analysisName.style.fontStyle = 'italic';

        const createdByLabel = document.createElement('span');
        createdByLabel.className = 'info-about-analysis-parts';
        createdByLabel.innerHTML = HTML_WHITE_SPACE + 'created by' + HTML_WHITE_SPACE;

        const username = buildUserLinkDiv(this.#analysis.username);
        username.className = 'info-about-analysis-parts';

        const content = document.createElement('div');
        content.style.width = '100%';
        content.append(analysisName);
        content.append(createdByLabel);
        content.append(username);

        const centerContainer = document.createElement('div');
        centerContainer.className = 'center-content-div';
        centerContainer.append(content);

        const box = document.getElementById('info-about-analysis');
        box.append(centerContainer);
        box.style.display = 'block';
    }

    /**
     * Once the analysis has been persisted for the first time, we show 'update'
     */
    #showUpdateAnalysis() {
        this.#saveAnalysisButton.value = 'update analysis';
        this.#renameAnalysisButton.classList.remove('app-buttons-disabled');
    }

    #renderGameMetaDataInfoPanel() {
        document.getElementById('game-info-box').style.display = 'block';
        document.getElementById('game-info').innerText = this.#gameMetadata.toString();
        this.#gameTypeIcon.src = analysisIconByGameType(this.#gameMetadata.gameId.type);
    }

    #startUpWidgets() {
        this.#createListeners();
        // to enable background engine querying
        this.#analysisCache.allNodesPositions = () => this.#moveTreeWidget.getAllFenKeys();
        this.#updateWidgets(this.#moveTreeWidget.getMovesUpToSelection());
    }

    #createListeners() {
        // board listeners
        this.#boardGui.addAfterMoveListener((move) => {
            // TODO: in the openings repo box, add a little arrow (or indicator) next to the move if it's the next move actually played

            // update move tree
            this.#selectedNode = this.#moveTreeWidget.addToTree([move]);
            this.#updateWidgets(this.#moveTreeWidget.getMovesUpToSelection());

            // update move history gui with cache analysis data
            this.#analysisCache.iterateAllEntries((fenKey, pv) => {
                this.#pushNewInfoLineResultToMoveTreeWidget(pv);
            });

            // update FEN
            this.#updateFenField();
        });

        this.#boardGui.addAfterDrawPositionsListener(() => {
            this.#renderEngineArrows();
        });

        // TODO: the board could be pass in the constructor of the move tree widget
        // connect board to move tree widget
        this.#moveTreeWidget.boardWidget = this.#boardGui;

        // connect annotation box to move tree widget
        this.#moveTreeWidget.annotationBox = this.#annotationBox;
        this.#annotationBox.moveTreeWidget = this.#moveTreeWidget;

        // move history listeners
        this.#moveTreeWidget.metadataFetcher = () => this.#buildPgnMetadata();
        this.#moveTreeWidget.addClickedNodeListener(() => this.#handleNodeSelected());
        this.#moveTreeWidget.addNavigationListener(() => this.#handleNodeSelected());

        // update widget move format when move format is updated
        this.#settingsGui.addMoveFormatUpdateListener(() => {
            this.#updateWidgets(this.#moveTreeWidget.getMovesUpToSelection());
        });

        // update board when 'show analytics arrows' setting is toggled
        this.#settingsGui.addShowAnalyticsArrowsListener((value) => {
            if (value) {
                this.#renderEngineArrows();
            } else {
                this.#boardGui.clearSvg();
            }
        });

        // persistence UI listeners
        this.#saveAnalysisButton.addEventListener('click', () => {
            this.#saveAnalysis();
        });

        // TODO: create a AppButton class and move all that logic there
        this.#renameAnalysisButton.addEventListener('click', (e) => {
            let isEnabled = !e.target.classList.contains('app-buttons-disabled');
            if (isEnabled) {
                this.#renameAnalysis();
            }
        });

        // copy FEN to clipboard button
        this.#fenCopyIcon.addEventListener('click', () => {
            copyTextToClipboardAndNotify(
                this.#fenExportInput.value,
                'FEN copied to clipboard!'
            );
        })
    }

    #handleNodeSelected() {
        // if selectNode == null, it means we're rendering the START_FEN
        if (this.#moveTreeWidget.selectedNode != null) {
            this.#boardGui.enablePlayerMove();
            this.#updateWidgets(this.#moveTreeWidget.getMovesUpToSelection());
            this.#updateFenField();
        } else {
            // disable board, because we can not branch off from before the first node
            this.#boardGui.disablePlayerMove();
            this.#updateWidgets([]);
            // TODO: updateFenField() instead?
            this.#fenExportInput.value = this.#startFen;
        }

        this.#renderEngineArrows();
    }

    #startToPollAnalysisStatus() {
        const progressBarBox = document.getElementById('analysis-progress-bar-box');
        const progressBarContainer = document.getElementById('analysis-progress-bar-container');

        // fetch updated data every 2 sec
        const fetchAnalysisDataInterval = setInterval(() => {
            this.#gameDataClient.fetchAnalysisData(entries => {
                this.#analysisCache.populateCache(entries)
            });
        }, 2_000);

        // TODO: replace by long polling?
        // update progress every 1 sec
        const fetchAnalysisProgressInterval = setInterval(() => {
            this.#gameDataClient.fetchAnalysisStatus(response => {
                progressBarBox.style.display = 'block';
                progressBarContainer.innerHTML = '';

                switch (response.status) {
                    case AnalysisStatus.NOT_STARTED:
                    case AnalysisStatus.STARTED:
                    case AnalysisStatus.PARTIALLY_COMPLETED:
                        progressBarContainer.append(new ProgressIndicator(response.progress).render());
                        break;
                    case AnalysisStatus.COMPLETED:
                    case AnalysisStatus.CANCELLED:
                        progressBarContainer.append(new ProgressIndicator(1).render());
                        clearInterval(fetchAnalysisDataInterval);
                        clearInterval(fetchAnalysisProgressInterval);

                        // make the progress bar disappear with a delay
                        setTimeout(() => {
                            progressBarBox.style.display = 'none';
                        }, PROGRESS_BAR_DISAPPEAR_TIMEOUT);

                        // fetch the final analysis data
                        this.#gameDataClient.fetchAnalysisData(entries => {
                            this.#analysisCache.populateCache(entries);
                            this.#renderAnalysisSummaryIfLongEnough();
                        });
                        break;
                    default:
                        console.warn('Unknown analysis status: ' + response.status);
                }
                this.#analysisCache.analysisStatus = response.status;
            });
        }, 1_000);
    }

    /**
     * @param movesUpToSelection {HalfMove[]} Including the selected one
     */
    #updateWidgets(movesUpToSelection) {
        this.#openingRepositoryWidget.fetchOpeningsNextMoves(movesUpToSelection);
        this.#engineAnalysisWidget.update(movesUpToSelection);
    }

    #saveAnalysis() {
        this.#saveAnalysisButton.classList.add('app-buttons-disabled');

        this.#client.saveAnalysis(
            this.#analysisId,
            this.#analysisNameField.value,
            this.#gameMetadata?.gameId,
            this.#moveTreeWidget.serializeToDtos(),
            this.#moveTreeWidget.selectedNode?.nodeId,
            this.#moveTreeWidget.listOpenBranchIds(),
            this.#analysisCache.serializeToDtos(),
            this.#startFen,
            response => {
                this.#analysisId = response.analysisId;
                this.#analysisVersion = response.version;
                this.#analysisCache.analysisId = response.analysisId;

                UI.pushInfoNotification('Analysis saved with version ' + response.version, ANALYSIS_NOTIFICATION_TIMEOUT);
                this.#saveAnalysisButton.classList.remove('app-buttons-disabled');

                if (response.version === 1) {
                    this.#showUpdateAnalysis();
                    updateUrl('analysis?id=' + response.analysisId);
                }

                this.#updateUiForSaveAnalysisResponse(response);
            });
    }

    #renameAnalysis() {
        this.#renameAnalysisButton.classList.add('app-buttons-disabled');

        if (this.#analysisId != null) {
            this.#client.renameAnalysis(this.#analysisId, this.#analysisNameField.value, response => {
                let html = `Analysis has been renamed to " ${this.#analysisNameField.value}"`;
                UI.pushInfoNotification(html, ANALYSIS_NOTIFICATION_TIMEOUT);
                this.#renameAnalysisButton.classList.remove('app-buttons-disabled');
                this.#analysisVersionLastUpdatedLabel.innerText = formatTimestampToDateTime(response.lastUpdated);
            });
        }
    }

    /**
     * @param response {SaveAnalysisResponseDto}
     */
    #updateUiForSaveAnalysisResponse(response) {
        this.#analysisVersionLabel.innerText = response.version.toString();
        this.#analysisVersionLastUpdatedLabel.innerText = formatTimestampToDateTime(response.lastUpdated);
    }

    #buildPgnMetadata() {
        const metadata = new Map();

        // site metadata
        let url = `${SITE_URL}/analysis`;
        if (this.#analysisId != null) {
            url += '?id=' + this.#analysisId;
        } else if (this.#gameMetadata != null) {
            url += '?' + this.#gameMetadata.gameId.urlParams;
        }
        metadata.set('Site', url);

        // reference game metadata
        if (this.#gameMetadata != null) {
            this
                .#gameMetadata
                .buildPgnMetadata()
                .forEach((value, key) => {
                    metadata.set(key, value);
                });
        }

        return metadata;
    }

    #updateFenField() {
        this.#fenExportInput.value = this.#boardGui.outputFen();
    }

    #renderEngineArrows() {
        this.#boardGui.clearSvg();

        if (this.#settingsManager.isShowAnalyticsArrowsEnabled) {
            const fenKey = resetFenFullMovesCount(this.#boardGui.outputFen());
            const analysisMap = this.#analysisCache.asMap();
            if (analysisMap.has(fenKey)) {
                const pv = analysisMap.get(fenKey).pv;
                if (pv.length > 0) {
                    this.#boardGui.addEngineArrow(pv[0], EngineArrowType.PRIMARY);
                }
                if (pv.length > 1) {
                    this.#boardGui.addEngineArrow(pv[1], EngineArrowType.SECONDARY);
                }
            }
        }
    }

    /**
     * @return {boolean}
     */
    #isAnalysisPersisted() {
        return this.#analysisId != null;
    }

    #renderAnalysisSummaryIfLongEnough() {
        const nodes = this.#moveTreeWidget.getMainBranchNodes();
        if (nodes.length > 6) {
            renderAnalysisSummaryReport(
                this.#analysisCache.asMap(),
                this.#gameMetadata?.redPlayerName,
                this.#gameMetadata?.blackPlayerName,
                this.#gameMetadata?.outcome,
                this.#moveTreeWidget
            );
        }
    }

    #scheduleRenderAnalysisSummaryIfPossible() {
        if (this.#analysisSummaryRenderTimeout !== null) {
            clearTimeout(this.#analysisSummaryRenderTimeout);
        }
        // Coalesce rapid cache updates during startup into a single summary recomputation.
        this.#analysisSummaryRenderTimeout = setTimeout(() => {
            this.#analysisSummaryRenderTimeout = null;
            this.#renderAnalysisSummaryIfLongEnough();
        }, 120);
    }

    #scheduleRefreshMoveTreeEvalFromCache() {
        if (this.#moveTreeEvalRefreshTimeout !== null) {
            clearTimeout(this.#moveTreeEvalRefreshTimeout);
        }
        this.#moveTreeEvalRefreshTimeout = setTimeout(() => {
            this.#moveTreeEvalRefreshTimeout = null;
            this.#moveTreeWidget.refreshAllMoveNodeEval(this.#analysisCache.asMap());
        }, 120);
    }

    /**
     * @param infoLineResult {InfoLineResult}
     */
    #pushNewInfoLineResultToMoveTreeWidget(infoLineResult) {
        const fenKey = resetFenFullMovesCount(infoLineResult.fen);
        const analysisCache = this.#analysisCache.asMap();
        this.#moveTreeWidget.updateEvalForInfoLineResult(fenKey, analysisCache);
    }

}

window.onload = () => new AnalysisBoardPage();
