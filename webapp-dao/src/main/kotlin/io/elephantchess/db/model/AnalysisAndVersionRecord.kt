package io.elephantchess.db.model

import io.elephantchess.db.dao.codegen.Tables.ANALYSIS
import io.elephantchess.db.dao.codegen.Tables.ANALYSIS_VERSION
import io.elephantchess.model.GameType
import org.jooq.Record9
import kotlin.time.Instant

data class AnalysisAndVersionRecord(
    private val record: Record9<String, Instant, Instant, String, Int, String?, String?, String?, String?>
) {

    fun analysisId(): String = record.get(ANALYSIS.ID)
    fun created(): Instant = record.get(ANALYSIS.CREATED)
    fun lastUpdated(): Instant = record.get(ANALYSIS.LAST_UPDATED)
    fun analysisName(): String = record.get(ANALYSIS.ANALYSIS_NAME)
    fun versionNumber(): Int = record.get(ANALYSIS_VERSION.VERSION_NUMBER)
    fun gameType(): GameType? {
        return if (record.get(ANALYSIS.GAME_ID) != null) {
            GameType.PVP
        } else if (record.get(ANALYSIS.BOT_GAME_ID) != null) {
            GameType.PVB
        } else if (record.get(ANALYSIS.REFERENCE_GAME_ID) != null) {
            GameType.DB
        } else {
            null
        }
    }

    fun selectedNodeFen(): String? = record.get(ANALYSIS_VERSION.SELECTED_NODE_FEN)

}
