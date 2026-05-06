package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.*
import io.elephantchess.db.dao.codegen.tables.pojos.*
import io.elephantchess.db.model.AnalysisAndUserRecord
import io.elephantchess.db.model.AnalysisAndVersionRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.GameId
import org.jooq.DSLContext
import org.jooq.Record2
import org.jooq.Record3
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Instant

class AnalysisDaoService(private val dslContext: DSLContext) {

    suspend fun listAnalysisForAllUsers(limit: Int): List<AnalysisAndUserRecord> {
        return dslContext
            .select(
                ANALYSIS.ID,
                ANALYSIS.CREATED,
                ANALYSIS.LAST_UPDATED,
                ANALYSIS.ANALYSIS_NAME,
                DSL.max(ANALYSIS_VERSION.VERSION_NUMBER),
                USER.ID,
                USER.HANDLE
            )
            .from(ANALYSIS, ANALYSIS_VERSION, USER)
            .where(ANALYSIS.ID.eq(ANALYSIS_VERSION.ANALYSIS_ID))
            .and(ANALYSIS.OWNER_USER_ID.eq(USER.ID))
            .groupBy(
                ANALYSIS.ID,
                ANALYSIS.CREATED,
                ANALYSIS.LAST_UPDATED,
                ANALYSIS.ANALYSIS_NAME,
                USER.ID,
                USER.HANDLE
            )
            .orderBy(ANALYSIS.LAST_UPDATED.desc())
            .limit(limit)
            .awaitRecords()
            .map { record7 ->
                AnalysisAndUserRecord(record7)
            }
    }

    suspend fun countAnalysisPerUser(limit: Int): List<Record3<String, String, Int>> {
        return dslContext
            .select(
                USER.ID,
                USER.HANDLE,
                DSL.countDistinct(ANALYSIS.ID)
            )
            .from(ANALYSIS, USER)
            .where(ANALYSIS.OWNER_USER_ID.eq(USER.ID))
            .groupBy(USER.ID, USER.HANDLE)
            .orderBy(DSL.count(ANALYSIS.ID).desc())
            .limit(limit)
            .awaitRecords()
    }

    suspend fun fetchLastUpdatePerUser(): List<Record2<String, Instant>> {
        return dslContext
            .select(ANALYSIS.OWNER_USER_ID, DSL.max(ANALYSIS.LAST_UPDATED))
            .from(ANALYSIS)
            .groupBy(ANALYSIS.OWNER_USER_ID)
            .orderBy(DSL.max(ANALYSIS.LAST_UPDATED).desc())
            .awaitRecords()
    }

    suspend fun listAnalysisAndVersionForUser(
        userId: String,
        limit: Int,
        beforeTs: Long?
    ): List<AnalysisAndVersionRecord> {
        var sql = dslContext
            .select(
                ANALYSIS.ID,
                ANALYSIS.CREATED,
                ANALYSIS.LAST_UPDATED,
                ANALYSIS.ANALYSIS_NAME,
                ANALYSIS_VERSION.VERSION_NUMBER,
                ANALYSIS.GAME_ID,
                ANALYSIS.REFERENCE_GAME_ID,
                ANALYSIS.BOT_GAME_ID,
                ANALYSIS_VERSION.SELECTED_NODE_FEN
            )
            .from(ANALYSIS, ANALYSIS_VERSION)
            .where(ANALYSIS.ID.eq(ANALYSIS_VERSION.ANALYSIS_ID))
            .and(ANALYSIS.OWNER_USER_ID.eq(userId))

        if (beforeTs != null) {
            sql = sql.and(ANALYSIS.LAST_UPDATED.isBeforeEpochMillis(beforeTs))
        }

        return sql
            .orderBy(ANALYSIS.LAST_UPDATED.desc(), ANALYSIS_VERSION.VERSION_NUMBER.desc())
            .limit(limit)
            .awaitRecords()
            .map { record9 ->
                AnalysisAndVersionRecord(record9)
            }
    }

    suspend fun countAnnotations(pairs: List<Pair<String, Int>>): Map<Pair<String, Int>, Int> {
        val conditions = pairs.map { pair ->
            ANALYSIS_NODE_ANNOTATION.ANALYSIS_ID.eq(pair.first)
                .and(ANALYSIS_NODE_ANNOTATION.VERSION_NUMBER.eq(pair.second))
        }

        if (conditions.isEmpty()) {
            return emptyMap()
        }

        val orCombinedCondition = conditions.reduce { acc, condition -> acc.or(condition) }

        return dslContext
            .select(
                ANALYSIS_NODE_ANNOTATION.ANALYSIS_ID,
                ANALYSIS_NODE_ANNOTATION.VERSION_NUMBER,
                DSL.count()
            )
            .from(ANALYSIS_NODE_ANNOTATION)
            .where(orCombinedCondition)
            .groupBy(
                ANALYSIS_NODE_ANNOTATION.ANALYSIS_ID,
                ANALYSIS_NODE_ANNOTATION.VERSION_NUMBER
            )
            .awaitRecords()
            .associate { record ->
                Pair(record.value1(), record.value2()) to record.value3() as Int
            }
    }

    suspend fun countVariations(pairs: List<Pair<String, Int>>): Map<Pair<String, Int>, Int> {
        val conditions = pairs.map { pair ->
            ANALYSIS_CHILD_NODE.ANALYSIS_ID.eq(pair.first)
                .and(ANALYSIS_CHILD_NODE.VERSION_NUMBER.eq(pair.second))
        }

        if (conditions.isEmpty()) {
            return emptyMap()
        }

        val orCombinedCondition = conditions.reduce { acc, condition -> acc.or(condition) }

        return dslContext
            .select(
                ANALYSIS_CHILD_NODE.ANALYSIS_ID,
                ANALYSIS_CHILD_NODE.VERSION_NUMBER,
                DSL.count()
            )
            .from(ANALYSIS_CHILD_NODE)
            .where(orCombinedCondition)
            .groupBy(
                ANALYSIS_CHILD_NODE.ANALYSIS_ID,
                ANALYSIS_CHILD_NODE.VERSION_NUMBER
            )
            .awaitRecords()
            .associate { record ->
                Pair(record.value1(), record.value2()) to record.value3() as Int
            }
    }

    suspend fun listAllVersions(analysisId: String): List<Int> {
        return dslContext
            .selectDistinct(
                ANALYSIS_VERSION.VERSION_NUMBER
            )
            .from(ANALYSIS_VERSION)
            .where(ANALYSIS_VERSION.ANALYSIS_ID.eq(analysisId))
            .orderBy(ANALYSIS_VERSION.VERSION_NUMBER.desc())
            .awaitRecords()
            .map { record1 -> record1.value1() }
    }

    suspend fun createAnalysis(
        analysis: Analysis,
        analysisVersion: AnalysisVersion,
        nodes: List<AnalysisNode>,
        childNodes: List<AnalysisChildNode>,
        annotationRecords: List<AnalysisNodeAnnotation>
    ) {
        val analysisId = analysis.id
        val version = analysisVersion.versionNumber

        val versionedNodes =
            addVersionToNodes(nodes, analysisId, version)

        val versionedChildNodes =
            addVersionToChildNodes(childNodes, analysisId, version)

        val versionedAnnotations =
            addVersionToAnnotations(annotationRecords, analysisId, version)

        dslContext.transactionCoroutine { cfg ->
            AnalysisDao(cfg).insertReactive(analysis)
            AnalysisVersionDao(cfg).insertReactive(analysisVersion)
            AnalysisNodeDao(cfg).insertMultipleReactive(versionedNodes)
            AnalysisChildNodeDao(cfg).insertMultipleReactive(versionedChildNodes)
            AnalysisNodeAnnotationDao(cfg).insertMultipleReactive(versionedAnnotations)
        }
    }

    suspend fun createAnalysisVersion(
        analysisId: String,
        analysisName: String,
        nodes: List<AnalysisNode>,
        childNodes: List<AnalysisChildNode>,
        annotationRecords: List<AnalysisNodeAnnotation>,
        lastUpdated: Instant,
        selectedNodeId: String?,
        selectedNodeFen: String?,
        openBranchIds: String,
        startFen: String?,
    ): Int? {
        var newVersion: Int? = null

        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .select(DSL.max(ANALYSIS_VERSION.VERSION_NUMBER))
                .from(ANALYSIS_VERSION)
                .where(ANALYSIS_VERSION.ANALYSIS_ID.eq(analysisId))
                .groupBy(ANALYSIS_VERSION.ANALYSIS_ID)
                .awaitSingleValue<Int>()
                ?.let { version ->
                    // create new version
                    newVersion = version + 1

                    val versionedNodes = addVersionToNodes(nodes, analysisId, newVersion)
                    val versionedChildNodes = addVersionToChildNodes(childNodes, analysisId, newVersion)
                    val versionedAnnotations = addVersionToAnnotations(annotationRecords, analysisId, newVersion)

                    val analysisVersion = AnalysisVersion()
                    analysisVersion.analysisId = analysisId
                    analysisVersion.versionNumber = newVersion
                    analysisVersion.created = lastUpdated
                    analysisVersion.selectedNodeId = selectedNodeId
                    analysisVersion.selectedNodeFen = selectedNodeFen
                    analysisVersion.openBranchIds = openBranchIds
                    analysisVersion.startFen = startFen

                    // insert new version
                    AnalysisVersionDao(cfg).insertReactive(analysisVersion)
                    AnalysisNodeDao(cfg).insertMultipleReactive(versionedNodes)
                    AnalysisChildNodeDao(cfg).insertMultipleReactive(versionedChildNodes)
                    AnalysisNodeAnnotationDao(cfg).insertMultipleReactive(versionedAnnotations)

                    // update analysis
                    DSL
                        .using(cfg)
                        .update(ANALYSIS)
                        .set(ANALYSIS.CURRENT_VERSION_NUMBER.fixed(), newVersion)
                        .set(ANALYSIS.LAST_UPDATED.fixed(), lastUpdated)
                        .set(ANALYSIS.ANALYSIS_NAME.fixed(), analysisName)
                        .where(ANALYSIS.ID.eq(analysisId))
                        .awaitExecute()
                }
        }

        return newVersion
    }

    suspend fun replaceEngineDataCache(analysisId: String, entries: List<AnalysisEngineDataCache>) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .deleteFrom(ANALYSIS_ENGINE_DATA_CACHE)
                .where(ANALYSIS_ENGINE_DATA_CACHE.ANALYSIS_ID.eq(analysisId))
                .awaitExecute()

            AnalysisEngineDataCacheDao(cfg).insertMultipleReactive(entries)
        }
    }

    suspend fun replaceEngineEntryIfNecessary(analysisId: String, record: AnalysisEngineDataCache): Boolean {
        return dslContext.transactionCoroutine { cfg ->
            val transaction = DSL.using(cfg)

            val currentDepth =
                transaction
                    .select(DSL.max(ANALYSIS_ENGINE_DATA_CACHE.DEPTH))
                    .from(ANALYSIS_ENGINE_DATA_CACHE)
                    .where(ANALYSIS_ENGINE_DATA_CACHE.ANALYSIS_ID.eq(analysisId))
                    .and(ANALYSIS_ENGINE_DATA_CACHE.FEN_KEY.eq(record.fenKey))
                    .awaitSingleValue<Int>()

//            logger.debug { "currentDepth: $currentDepth, record.depth: ${record.depth}, analysis: $analysisId" }

            if (currentDepth == null || (record.depth != null && currentDepth < record.depth)) {
                // replace if not exists or new depth is greater
                transaction
                    .deleteFrom(ANALYSIS_ENGINE_DATA_CACHE)
                    .where(ANALYSIS_ENGINE_DATA_CACHE.ANALYSIS_ID.eq(analysisId))
                    .and(ANALYSIS_ENGINE_DATA_CACHE.FEN_KEY.eq(record.fenKey))
                    .awaitExecute()

                AnalysisEngineDataCacheDao(cfg).insertReactive(record)
                true
            } else {
                false
            }
        }
    }

    suspend fun fetchEngineDataCache(analysisId: String): List<AnalysisEngineDataCache> {
        return dslContext
            .select()
            .from(ANALYSIS_ENGINE_DATA_CACHE)
            .where(ANALYSIS_ENGINE_DATA_CACHE.ANALYSIS_ID.eq(analysisId))
            .awaitMappedRecords()
    }

    suspend fun isOwner(userId: String, analysisId: String): Boolean {
        return dslContext
            .select(DSL.count())
            .from(ANALYSIS)
            .where(ANALYSIS.ID.eq(analysisId))
            .and(ANALYSIS.OWNER_USER_ID.eq(userId))
            .awaitSingleValue<Int>()!! == 1
    }

    suspend fun fetchAnalysis(analysisId: String): Analysis? {
        return dslContext
            .select()
            .from(ANALYSIS)
            .where(ANALYSIS.ID.eq(analysisId))
            .awaitMappedRecords<Analysis>()
            .firstOrNull()
    }

    suspend fun fetchAnalysisName(analysisId: String): String? {
        return dslContext
            .select(ANALYSIS.ANALYSIS_NAME)
            .from(ANALYSIS)
            .where(ANALYSIS.ID.eq(analysisId))
            .awaitSingleValue()
    }

    suspend fun fetchVersion(analysisId: String, versionNumber: Int): AnalysisVersion? {
        return dslContext
            .select()
            .from(ANALYSIS_VERSION)
            .where(ANALYSIS_VERSION.ANALYSIS_ID.eq(analysisId))
            .and(ANALYSIS_VERSION.VERSION_NUMBER.eq(versionNumber))
            .awaitMappedRecords<AnalysisVersion>()
            .firstOrNull()
    }

    suspend fun fetchReferenceGameId(analysisId: String): GameId? {
        return dslContext
            .select(
                ANALYSIS.GAME_ID,
                ANALYSIS.BOT_GAME_ID,
                ANALYSIS.REFERENCE_GAME_ID
            )
            .from(ANALYSIS)
            .where(ANALYSIS.ID.eq(analysisId))
            .awaitMappedRecords<Analysis>()
            .firstOrNull()
            ?.gameId()
    }

    suspend fun getLastVersionNumberOf(analysisId: String): Int? {
        return dslContext
            .select(DSL.max(ANALYSIS_VERSION.VERSION_NUMBER))
            .from(ANALYSIS_VERSION)
            .where(ANALYSIS_VERSION.ANALYSIS_ID.eq(analysisId))
            .groupBy(ANALYSIS_VERSION.ANALYSIS_ID)
            .awaitSingleValue()
    }

    suspend fun getAllNodes(analysisId: String, versionNumber: Int): List<AnalysisNode> {
        return dslContext
            .select()
            .from(ANALYSIS_NODE)
            .where(ANALYSIS_NODE.ANALYSIS_ID.eq(analysisId))
            .and(ANALYSIS_NODE.VERSION_NUMBER.eq(versionNumber))
            .awaitMappedRecords()
    }

    suspend fun getAllChildNodes(analysisId: String, versionNumber: Int): List<AnalysisChildNode> {
        return dslContext
            .select()
            .from(ANALYSIS_CHILD_NODE)
            .where(ANALYSIS_CHILD_NODE.ANALYSIS_ID.eq(analysisId))
            .and(ANALYSIS_CHILD_NODE.VERSION_NUMBER.eq(versionNumber))
            .awaitMappedRecords()
    }

    suspend fun getAllAnnotations(analysisId: String, versionNumber: Int): List<AnalysisNodeAnnotation> {
        return dslContext
            .select()
            .from(ANALYSIS_NODE_ANNOTATION)
            .where(ANALYSIS_NODE_ANNOTATION.ANALYSIS_ID.eq(analysisId))
            .and(ANALYSIS_NODE_ANNOTATION.VERSION_NUMBER.eq(versionNumber))
            .awaitMappedRecords()
    }

    suspend fun renameAnalysis(analysisId: String, name: String, lastUpdated: Instant) {
        dslContext
            .update(ANALYSIS)
            .set(ANALYSIS.ANALYSIS_NAME.fixed(), name)
            .set(ANALYSIS.LAST_UPDATED.fixed(), lastUpdated)
            .where(ANALYSIS.ID.eq(analysisId))
            .awaitExecute()
    }

    suspend fun deleteAnalysisVersions(analysisId: String, versions: List<Int>) {
        dslContext.transactionCoroutine { cfg ->
            val transaction = DSL.using(cfg)

            transaction
                .deleteFrom(ANALYSIS_NODE_ANNOTATION)
                .where(ANALYSIS_NODE_ANNOTATION.ANALYSIS_ID.eq(analysisId))
                .and(ANALYSIS_NODE_ANNOTATION.VERSION_NUMBER.`in`(versions))
                .awaitExecute()

            transaction
                .deleteFrom(ANALYSIS_CHILD_NODE)
                .where(ANALYSIS_CHILD_NODE.ANALYSIS_ID.eq(analysisId))
                .and(ANALYSIS_CHILD_NODE.VERSION_NUMBER.`in`(versions))
                .awaitExecute()

            transaction
                .deleteFrom(ANALYSIS_NODE)
                .where(ANALYSIS_NODE.ANALYSIS_ID.eq(analysisId))
                .and(ANALYSIS_NODE.VERSION_NUMBER.`in`(versions))
                .awaitExecute()

            transaction
                .deleteFrom(ANALYSIS_VERSION)
                .where(ANALYSIS_VERSION.ANALYSIS_ID.eq(analysisId))
                .and(ANALYSIS_VERSION.VERSION_NUMBER.`in`(versions))
                .awaitExecute()

            transaction
                .deleteFrom(ANALYSIS)
                .where(ANALYSIS.ID.`in`(versions))
                .awaitExecute()
        }
    }

    suspend fun deleteAnalysis(analysisId: String) {
        suspend fun deleteNodesAndAnnotations(analysisId: String, transactio: DSLContext) {
            transactio
                .deleteFrom(ANALYSIS_NODE_ANNOTATION)
                .where(ANALYSIS_NODE_ANNOTATION.ANALYSIS_ID.eq(analysisId))
                .awaitExecute()

            transactio
                .deleteFrom(ANALYSIS_CHILD_NODE)
                .where(ANALYSIS_CHILD_NODE.ANALYSIS_ID.eq(analysisId))
                .awaitExecute()

            transactio
                .deleteFrom(ANALYSIS_NODE)
                .where(ANALYSIS_NODE.ANALYSIS_ID.eq(analysisId))
                .awaitExecute()

        }

        dslContext.transactionCoroutine { cfg ->
            val transaction = DSL.using(cfg)
            deleteNodesAndAnnotations(analysisId, transaction)

            transaction
                .deleteFrom(ANALYSIS_VERSION)
                .where(ANALYSIS_VERSION.ANALYSIS_ID.eq(analysisId))
                .awaitExecute()

            transaction
                .deleteFrom(ANALYSIS_ENGINE_DATA_CACHE)
                .where(ANALYSIS_ENGINE_DATA_CACHE.ANALYSIS_ID.eq(analysisId))
                .awaitExecute()

            transaction
                .deleteFrom(ANALYSIS)
                .where(ANALYSIS.ID.eq(analysisId))
                .awaitExecute()
        }
    }

    private companion object {

        fun addVersionToNodes(
            entries: List<AnalysisNode>,
            analysisId: String,
            versionNumber: Int,
        ): List<AnalysisNode> {
            return entries.map { pojo ->
                pojo.analysisId = analysisId
                pojo.versionNumber = versionNumber
                pojo
            }
        }

        fun addVersionToChildNodes(
            entries: List<AnalysisChildNode>,
            analysisId: String,
            versionNumber: Int,
        ): List<AnalysisChildNode> {
            return entries.map { pojo ->
                pojo.analysisId = analysisId
                pojo.versionNumber = versionNumber
                pojo
            }
        }

        private fun addVersionToAnnotations(
            entries: List<AnalysisNodeAnnotation>,
            analysisId: String,
            version: Int,
        ): List<AnalysisNodeAnnotation> {
            return entries.map { pojo ->
                pojo.analysisId = analysisId
                pojo.versionNumber = version
                pojo
            }
        }

    }

}
