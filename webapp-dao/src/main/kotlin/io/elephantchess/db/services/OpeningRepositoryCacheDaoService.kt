package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.OPENING_PRE_CALCULATION_CACHE
import io.elephantchess.db.dao.codegen.tables.pojos.OpeningPreCalculationCache
import io.elephantchess.db.utils.awaitMappedRecords
import org.jooq.DSLContext

class OpeningRepositoryCacheDaoService(private val dslContext: DSLContext) {

    suspend fun fetchNextMovesData(moves: List<String>): List<OpeningPreCalculationCache> {
        val movesKey = movesToKey(moves)

        return dslContext
            .select()
            .from(OPENING_PRE_CALCULATION_CACHE)
            .where(OPENING_PRE_CALCULATION_CACHE.NUMBER_OF_MOVES.eq(moves.size + 1))
            .and(OPENING_PRE_CALCULATION_CACHE.MOVES.like("$movesKey%"))
            .awaitMappedRecords()
    }

    companion object {

        fun movesToKey(moves: List<String>): String {
            return moves.joinToString(separator = ",")
        }

    }

}
