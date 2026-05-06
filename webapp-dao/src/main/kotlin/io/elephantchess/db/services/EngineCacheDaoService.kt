package io.elephantchess.db.services

import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.engines.protocol.model.InfoLineResult
import io.elephantchess.engines.protocol.model.InfoLineResult.Companion.parseInfoLine
import org.jooq.DSLContext
import org.jooq.impl.DSL

class EngineCacheDaoService(private val dslContext: DSLContext) {

    suspend fun fetchInfoLine(fenKey: String, minDepth: Int): InfoLineResult? {
        return dslContext
            .select(RAW_LINE)
            .from(ENGINE_CACHE_COMPACT)
            .where(FEN_KEY.eq(fenKey))
            .and(DEPTH.greaterOrEqual(minDepth))
            .orderBy(DEPTH.minus(minDepth).asc())
            .limit(1)
            .awaitRecords()
            .map { record -> parseInfoLine(record.get(RAW_LINE)) }
            .firstOrNull()
    }

    suspend fun fetchInfoLine(fenKey: String): InfoLineResult? {
        return dslContext
            .select(RAW_LINE)
            .from(ENGINE_CACHE_COMPACT)
            .where(FEN_KEY.eq(fenKey))
            .orderBy(DEPTH.desc())
            .limit(1)
            .awaitRecords()
            .map { record -> parseInfoLine(record.get(RAW_LINE)) }
            .firstOrNull()
    }

    private companion object {

        val ENGINE_CACHE_COMPACT = DSL.table("engine_cache_compact_no_duplicate")
        val FEN_KEY = DSL.field("fen_key", String::class.java)
        val DEPTH = DSL.field("depth", Int::class.java)
        val RAW_LINE = DSL.field("raw_line", String::class.java)

    }

}
