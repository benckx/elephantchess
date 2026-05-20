package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.tables.pojos.*
import io.elephantchess.db.services.AnalysisDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.gameId
import io.elephantchess.db.utils.generateId
import io.elephantchess.model.GameType.*
import io.elephantchess.servicelayer.dto.analysis.*
import io.elephantchess.servicelayer.dto.engines.EngineRequest
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto.Companion.mapToInfoLineResultDto
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.ForbiddenException
import io.elephantchess.servicelayer.exceptions.NotAcceptableException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import io.elephantchess.xiangqi.Board.Companion.validateFen
import io.elephantchess.xiangqi.HalfMove
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Clock
import kotlin.time.Instant

class AnalysisService(
    private val analysisDaoService: AnalysisDaoService,
    private val userDaoService: UserDaoService,
    private val gameDataService: GameDataService,
    private val engineService: EngineService,
    private val logger: KLogger
) {

    suspend fun listUserAnalysis(userId: String, beforeTs: Long?): ListUserAnalysisResponse {
        val analysisIdToVersionsMap = analysisDaoService
            .listAnalysisAndVersionForUser(userId, 30, beforeTs)
            .groupBy { record -> record.analysisId() }

        val pairs = analysisIdToVersionsMap
            .values
            .flatMap { records ->
                records.map { record ->
                    Pair(record.analysisId(), record.versionNumber())
                }
            }

        val annotationsCount = analysisDaoService.countAnnotations(pairs)
        val variationsCount = analysisDaoService.countVariations(pairs)

        return analysisIdToVersionsMap.map { (analysisId, analysisAndVersionRecords) ->
            val latestVersion =
                analysisAndVersionRecords.maxByOrNull { record -> record.versionNumber() }!!
            val allVersionNumbers =
                analysisAndVersionRecords.map { record -> record.versionNumber() }.sortedDescending()
            val numberOfAnnotations =
                annotationsCount[Pair(analysisId, latestVersion.versionNumber())] ?: 0
            val numberOfVariations =
                variationsCount[Pair(analysisId, latestVersion.versionNumber())] ?: 0

            ListUserAnalysisResponse.Entry(
                analysisId = analysisId,
                currentVersion = latestVersion.versionNumber(),
                name = latestVersion.analysisName(),
                created = latestVersion.created().toEpochMilliseconds(),
                lastUpdated = latestVersion.lastUpdated().toEpochMilliseconds(),
                versions = allVersionNumbers,
                gameType = latestVersion.gameType(),
                selectedNodeFen = latestVersion.selectedNodeFen(),
                numberOfAnnotations = numberOfAnnotations,
                numberOfVariations = numberOfVariations
            )
        }
            .sortedByDescending { entry -> entry.lastUpdated }
            .let { entries -> ListUserAnalysisResponse(entries) }
    }

    suspend fun saveOrUpdateAnalysis(userId: String, request: SaveAnalysisRequest): SaveAnalysisResponse {
        suspend fun replaceEngineCacheData(analysisId: String) {
            val pvCacheDto = request.engineCache
            val filteredPvCacheDto = pvCacheDto.filter { isLegal(it) }
            if (filteredPvCacheDto.size < pvCacheDto.size) {
                logger.warn { "filtered out ${pvCacheDto.size - filteredPvCacheDto.size} illegal engine data entries" }
            }

            val engineDataCacheEntryRecords =
                mapEngineDataToRecord(filteredPvCacheDto)
                    .map { cache ->
                        cache.analysisId = analysisId
                        cache
                    }

            analysisDaoService.replaceEngineDataCache(analysisId, engineDataCacheEntryRecords)
        }

        // request validation
        request
            .nodes
            .mapNotNull { node -> node.annotation }
            .forEach { annotation ->
                if (annotation.length > ANNOTATION_MAX_LENGTH) {
                    throw NotAcceptableException("Annotation is too long (max. $ANNOTATION_MAX_LENGTH)")
                }
            }

        request.startFen?.let { validateFen(it) }

        if (request.nodes.isEmpty()) {
            throw BadRequestException("Analysis must contain at least one move")
        }

        return if (request.analysisId == null) {
            // create new analysis
            val version = 1
            val analysisId = generateId()
            val created = Clock.System.now()

            val analysisRecord = Analysis()
            analysisRecord.id = analysisId
            analysisRecord.ownerUserId = userId
            analysisRecord.currentVersionNumber = version
            analysisRecord.analysisName = request.name
            analysisRecord.created = created
            analysisRecord.lastUpdated = created
            request.gameId?.let { gameId ->
                when (gameId.type) {
                    PVP -> analysisRecord.gameId = gameId.id
                    PVB -> analysisRecord.botGameId = gameId.id
                    DB -> analysisRecord.referenceGameId = gameId.id
                }
            }

            val analysisVersionRecord = AnalysisVersion()
            analysisVersionRecord.analysisId = analysisId
            analysisVersionRecord.versionNumber = version
            analysisVersionRecord.created = created
            analysisVersionRecord.selectedNodeId = request.selectedNodeId
            analysisVersionRecord.openBranchIds = request.openBranchIds.joinToString(",")
            analysisVersionRecord.startFen = request.startFen

            val nodeRecords = mapNodesToRecords(request.nodes)
            val childNodeRecords = mapChildNodeRecords(request.nodes)
            val annotationRecords = mapNodesToAnnotationRecords(request.nodes)

            // calculate FEN for the selected node
            analysisVersionRecord.selectedNodeFen =
                request.selectedNodeId?.let { selectedNodeId ->
                    calculateFenForAnalysis(
                        startFen = request.startFen,
                        selectedNodeId = selectedNodeId,
                        nodes = nodeRecords
                    )
                }

            analysisDaoService.createAnalysis(
                analysis = analysisRecord,
                analysisVersion = analysisVersionRecord,
                nodes = nodeRecords,
                childNodes = childNodeRecords,
                annotationRecords = annotationRecords
            )
            replaceEngineCacheData(analysisId)
            SaveAnalysisResponse(analysisId, version, created.toEpochMilliseconds())
        } else {
            // check ACL
            requireWriteAccess(userId, request.analysisId)

            // create new version
            val lastUpdated = Clock.System.now()
            val nodes = mapNodesToRecords(request.nodes)
            val childNodes = mapChildNodeRecords(request.nodes)
            val annotationRecords = mapNodesToAnnotationRecords(request.nodes)
            val selectedNodeFen = request.selectedNodeId?.let { selectedNodeId ->
                calculateFenForAnalysis(
                    startFen = request.startFen,
                    selectedNodeId = selectedNodeId,
                    nodes = nodes
                )
            }

            val version = saveAnalysisVersion(
                analysisId = request.analysisId,
                analysisName = request.name,
                nodes = nodes,
                childNodes = childNodes,
                annotations = annotationRecords,
                lastUpdated = lastUpdated,
                selectedNodeId = request.selectedNodeId,
                selectedNodeFen = selectedNodeFen,
                openBranchIds = request.openBranchIds,
                startFen = request.startFen
            )

            replaceEngineCacheData(request.analysisId)
            SaveAnalysisResponse(request.analysisId, version, lastUpdated.toEpochMilliseconds())
        }
    }

    // visible for script to backfill selected nodes FENs
    private fun calculateFenForAnalysis(
        startFen: String?,
        selectedNodeId: String,
        nodes: List<AnalysisNode>
    ): String? {
        fun findChainOfMoves(nodes: List<AnalysisNode>, selectedNodeId: String): List<String> {
            val chain = mutableListOf<String>()
            var currentNode: AnalysisNode? = nodes.find { it.nodeId == selectedNodeId }

            while (currentNode != null) {
                chain.add(currentNode.move)
                currentNode = nodes.find { it.nodeId == currentNode.previous }
            }

            return chain.reversed()
        }

        try {
            val chainOfMoves = findChainOfMoves(nodes, selectedNodeId)
            return if (chainOfMoves.isEmpty()) {
                null
            } else {
                val board = Board()
                if (startFen != null) {
                    board.loadFen(startFen)
                }
                board.registerMoves(chainOfMoves.map { uci -> HalfMove.parseMoveFromUci(uci) })
                board.outputFen()
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error calculating FEN for analysis with selected node $selectedNodeId" }
            return null
        }
    }

    suspend fun findByIdAndVersion(analysisId: String, version: Int?): GetAnalysisResponse {
        val versionToRetrieve = version ?: analysisDaoService.getLastVersionNumberOf(analysisId)
        ?: throw NotFoundException("Analysis not found")

        val analysis = analysisDaoService.fetchAnalysis(analysisId)
            ?: throw NotFoundException("Analysis not found")

        val versionRecord = analysisDaoService.fetchVersion(analysisId, versionToRetrieve)
            ?: throw NotFoundException("Analysis version not found")

        val nodesRecords = analysisDaoService.getAllNodes(analysisId, versionToRetrieve)
        val childNodeRecords = analysisDaoService.getAllChildNodes(analysisId, versionToRetrieve)
        val annotationRecords = analysisDaoService.getAllAnnotations(analysisId, versionToRetrieve)
        val nodeDtos =
            nodesRecords
                .map { node ->
                    val annotation = annotationRecords.firstOrNull { annotation -> annotation.nodeId == node.nodeId }
                    mapRecordNodeToDto(node, annotation)
                }
                .map { node ->
                    val childNodeIds = childNodeRecords
                        .filter { childNode -> childNode.parentNodeId == node.id }
                        .map { childNode -> childNode.childNodeId }

                    node.copy(childNodes = childNodeIds)
                }

        return GetAnalysisResponse(
            analysisId = analysisId,
            version = versionToRetrieve,
            userId = analysis.ownerUserId,
            username = userDaoService.findUserNameById(analysis.ownerUserId).orEmpty(),
            name = analysis.analysisName,
            nodes = nodeDtos,
            lastUpdated = analysis.lastUpdated.toEpochMilliseconds(),
            gameId = analysis.gameId(),
            startFen = versionRecord.startFen,
            selectedNodeId = versionRecord.selectedNodeId,
            openBranchIds = versionRecord.openBranchIds.split(",")
        )
    }

    suspend fun fetchAnalysisEngineDataCache(analysisId: String): GetAnalysisEngineDataResponse {
        val entries = mutableListOf<InfoLineResultDto>()

        // from pre-analyzed reference game data
        val gameId = analysisDaoService.fetchReferenceGameId(analysisId)
        if (gameId != null) {
            entries += gameDataService.fetchAnalysisData(gameId).entries
        }

        // specific to the analysis
        entries += analysisDaoService
            .fetchEngineDataCache(analysisId)
            .map { record -> mapRecordToInfoLineResultDto(record) }

        return GetAnalysisEngineDataResponse(entries.distinctBy { it.fen })
    }

    @Suppress("FoldInitializerAndIfToElvis")
    suspend fun renameAnalysis(userId: String, request: RenameAnalysisRequest): SaveAnalysisResponse {
        // check ACL
        requireWriteAccess(userId, request.analysisId)

        val lastUpdated = Clock.System.now()
        val version = analysisDaoService.getLastVersionNumberOf(request.analysisId)

        if (version == null) {
            throw BadRequestException("Could not find version of the analysis")
        }

        analysisDaoService.renameAnalysis(request.analysisId, request.name, lastUpdated)
        return SaveAnalysisResponse(request.analysisId, version, lastUpdated.toEpochMilliseconds())
    }

    suspend fun updateStartFen(userId: String, request: UpdateStartFenRequest): SaveAnalysisResponse {
        requireWriteAccess(userId, request.analysisId)
        validateFen(request.startFen)

        // TODO: check the current start FEN isn't the same already

        val lastUpdated = Clock.System.now()

        val version = saveAnalysisVersion(
            analysisId = request.analysisId,
            analysisName = analysisDaoService.fetchAnalysisName(request.analysisId)!!,
            nodes = emptyList(),
            childNodes = emptyList(),
            annotations = emptyList(),
            selectedNodeId = null,
            selectedNodeFen = null,
            openBranchIds = emptyList(),
            startFen = request.startFen,
            lastUpdated = lastUpdated
        )

        return SaveAnalysisResponse(request.analysisId, version, lastUpdated.toEpochMilliseconds())
    }

    suspend fun deleteAnalysis(userId: String, request: DeleteAnalysisRequest) {
        requireWriteAccess(userId, request.analysisId)
        analysisDaoService.deleteAnalysis(request.analysisId)
    }

    suspend fun queryEngine(engineRequest: EngineRequest, analysisId: String?): InfoLineResultDto {
        val infoLineResult = engineService.principalVariation(
            fen = engineRequest.fen,
            engine = engineRequest.engine,
            depth = engineRequest.validatedDepth()
        )

        if (infoLineResult == null) {
            throw BadRequestException("No engine data was found")
        }

        if (analysisId != null) {
            val wasInserted =
                analysisDaoService.replaceEngineEntryIfNecessary(
                    analysisId = analysisId,
                    record = mapInfoLineResultDtoToRecord(analysisId, infoLineResult)
                )

            if (wasInserted) {
                logger.debug { "inserted engine data for analysis $analysisId" }
            }
        }

        return infoLineResult
    }

    // TODO: caching
    private suspend fun requireWriteAccess(userId: String, analysisId: String) {
        if (!analysisDaoService.isOwner(userId, analysisId)) {
            throw ForbiddenException("Not the owner of this analysis")
        }
    }

    private suspend fun saveAnalysisVersion(
        analysisId: String,
        analysisName: String,
        nodes: List<AnalysisNode>,
        childNodes: List<AnalysisChildNode>,
        annotations: List<AnalysisNodeAnnotation>,
        selectedNodeId: String?,
        selectedNodeFen: String?,
        openBranchIds: List<String>,
        startFen: String?,
        lastUpdated: Instant = Clock.System.now(),
    ): Int {
        val version = analysisDaoService.createAnalysisVersion(
            analysisId = analysisId,
            analysisName = analysisName,
            nodes = nodes,
            childNodes = childNodes,
            annotationRecords = annotations,
            lastUpdated = lastUpdated,
            selectedNodeId = selectedNodeId,
            selectedNodeFen = selectedNodeFen,
            openBranchIds = openBranchIds.joinToString(","),
            startFen = startFen
        )

        if (version == null) {
            throw BadRequestException("Could not create new version of the analysis")
        }

        // delete out of date versions
        val allVersions = analysisDaoService.listAllVersions(analysisId)
        val versionsToDelete = allVersions.drop(MAX_VERSIONS_TO_KEEP)
        if (versionsToDelete.isNotEmpty()) {
            logger.info { "deleting version(s) ${versionsToDelete.joinToString(", ")} of analysis $analysisId" }
            analysisDaoService.deleteAnalysisVersions(analysisId, versionsToDelete)
        }

        return version
    }

    companion object {

        const val MAX_VERSIONS_TO_KEEP = 10
        const val ANNOTATION_MAX_LENGTH = 2000

        // for persisting
        private fun mapChildNodeRecords(nodes: List<MoveTreeNode>): List<AnalysisChildNode> {
            return nodes.flatMap { node ->
                node.childNodes.map { childNodeId ->
                    val childNode = AnalysisChildNode()
                    childNode.childNodeId = childNodeId
                    childNode.parentNodeId = node.id
                    childNode
                }
            }
        }

        // for persisting
        private fun mapNodesToRecords(nodes: List<MoveTreeNode>): List<AnalysisNode> {
            return nodes.map { node ->
                val record = AnalysisNode()
                record.nodeId = node.id
                record.move = node.move
                record.level = node.level
                record.previous = node.previous
                record.next = node.next
                record
            }
        }

        // for persisting
        private fun mapNodesToAnnotationRecords(nodes: List<MoveTreeNode>): List<AnalysisNodeAnnotation> {
            return nodes
                .filter { node -> node.annotation != null }
                .map { node ->
                    val record = AnalysisNodeAnnotation()
                    record.nodeId = node.id
                    record.annotationContent = node.annotation
                    record
                }
        }

        // for persisting
        private fun mapEngineDataToRecord(pvCaches: List<PvCache>): List<AnalysisEngineDataCache> {
            return pvCaches
                .filter { dto -> dto.depth != null }
                .filter { dto -> dto.pv.isNotEmpty() }
                .map { dto ->
                    val record = AnalysisEngineDataCache()
                    record.fenKey = dto.fenKey
                    record.rawLine = dto.line
                    record.depth = dto.depth
                    record.cp = dto.cp
                    record.mateIn = dto.mate
                    record.pv = dto.pv.joinToString(",")
                    record.inCheckmate = dto.isCheckmate
                    record
                }
        }

        // for fetching
        private fun mapRecordNodeToDto(node: AnalysisNode, annotation: AnalysisNodeAnnotation?): MoveTreeNode {
            return MoveTreeNode(
                node.nodeId,
                node.move,
                node.level,
                node.previous,
                node.next,
                listOf(),
                annotation?.annotationContent,
            )
        }


        private fun mapRecordToInfoLineResultDto(record: AnalysisEngineDataCache): InfoLineResultDto {
            return if (record.rawLine != null && record.rawLine.isNotBlank()) {
                mapToInfoLineResultDto(record.fenKey, record.rawLine)
            } else {
                val pv = record.pv.split(",")
                InfoLineResultDto(
                    line = record.rawLine,
                    fen = record.fenKey,
                    depth = record.depth,
                    cp = record.cp,
                    mate = record.mateIn,
                    pv = pv,
                    bestMove = pv.firstOrNull(),
                    isCheckmate = record.inCheckmate
                )
            }
        }

        private fun mapInfoLineResultDtoToRecord(
            analysisId: String,
            infoLineResultDto: InfoLineResultDto,
        ): AnalysisEngineDataCache {
            val record = AnalysisEngineDataCache()
            record.analysisId = analysisId
            record.fenKey = resetFullMoveCount(infoLineResultDto.fen)
            record.depth = infoLineResultDto.depth
            record.rawLine = infoLineResultDto.line
            record.inCheckmate = infoLineResultDto.isCheckmate
            return record
        }

        // TODO: this can be improved and move to Board
        private fun isLegal(pvCache: PvCache): Boolean {
            return try {
                if (pvCache.pv.isNotEmpty()) {
                    val board = Board(pvCache.fenKey)
                    val nextMove = pvCache.pv.first()
                    val nextHalfMove = HalfMove.parseMoveFromUci(nextMove)
                    board.isLegalMove(nextHalfMove)
                } else {
                    pvCache.isCheckmate
                }
            } catch (_: Exception) {
                false
            }
        }

    }

}
