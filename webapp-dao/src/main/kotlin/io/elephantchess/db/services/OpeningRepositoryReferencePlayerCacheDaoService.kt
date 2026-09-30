package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER
import io.elephantchess.db.dao.codegen.tables.pojos.OpeningPreCalculationCacheReferencePlayer
import io.elephantchess.db.services.OpeningRepositoryCacheDaoService.Companion.movesToKey
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.xiangqi.Color
import org.jooq.DSLContext

class OpeningRepositoryReferencePlayerCacheDaoService(private val dslContext: DSLContext) {

    /**
     * Fetch the pre-calculated opening entries for the next move played by [referencePlayerId]
     * after [moves]. When [color] is `null`, entries for both colors are returned (the caller is
     * expected to aggregate them).
     */
    suspend fun fetchNextMovesData(
        referencePlayerId: String,
        color: Color?,
        moves: List<String>,
    ): List<OpeningPreCalculationCacheReferencePlayer> {
        val movesKey = movesToKey(moves)

        val condition = OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.REFERENCE_PLAYER_ID.eq(referencePlayerId)
            .and(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.NUMBER_OF_MOVES.eq(moves.size + 1))
            .and(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.MOVES.like("$movesKey%"))
            .let { base ->
                if (color != null) {
                    base.and(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.COLOR.eq(color.name))
                } else {
                    base
                }
            }

        return dslContext
            .select()
            .from(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER)
            .where(condition)
            .awaitMappedRecords()
    }

    /**
     * @return `true` if [referencePlayerId] has any pre-calculated opening data.
     */
    suspend fun hasOpeningData(referencePlayerId: String): Boolean {
        return dslContext
            .select(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.ID)
            .from(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER)
            .where(OPENING_PRE_CALCULATION_CACHE_REFERENCE_PLAYER.REFERENCE_PLAYER_ID.eq(referencePlayerId))
            .limit(1)
            .awaitSingleValue<Int>() != null
    }

}
