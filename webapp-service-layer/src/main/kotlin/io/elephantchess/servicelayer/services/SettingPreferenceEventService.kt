package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.tables.pojos.SettingPreferenceEvent
import io.elephantchess.db.services.SettingPreferenceEventDaoService
import io.elephantchess.db.utils.generateId
import io.github.oshai.kotlinlogging.KLogger
import kotlin.random.Random.Default.nextDouble

/**
 * Gathers an anonymous sample of the cookie-based user preferences,
 * just to get an idea of what settings users actually use.
 *
 * Only a fraction of the page views are sampled (see [SAMPLE_RATE]).
 */
class SettingPreferenceEventService(
    private val settingPreferenceEventDaoService: SettingPreferenceEventDaoService,
    private val logger: KLogger,
) {

    suspend fun sampleSettingPreferences(cookies: Map<String, String?>) {
        if (nextDouble() > SAMPLE_RATE) {
            return
        }

        val record = SettingPreferenceEvent()
        record.eventId = generateId()
        record.pieceStyle = cookies[PIECE_STYLE_SETTING]
        record.showCoordinates = cookies[SHOW_COORDINATES_SETTING]
        record.moveFormat = cookies[MOVE_FORMAT_SETTING]
        record.moveNodeEvalFormat = cookies[MOVE_NODE_EVAL_FORMAT]
        record.showAnalyticsArrows = cookies[SHOW_ANALYTICS_ARROWS]
        record.coordinatesStyle = cookies[COORDINATES_STYLE_SETTING]
        record.flipOpponentPieces = cookies[FLIP_OPPONENT_PIECES_SETTING]
        record.playSounds = cookies[PLAY_SOUNDS_SETTING]
        record.colorblindFriendlyBlackPieces = cookies[COLORBLIND_FRIENDLY_BLACK_PIECES_SETTING]
        settingPreferenceEventDaoService.save(record)

        logger.debug { "sampled setting preferences ${record.eventId}" }
    }

    companion object {
        private const val SAMPLE_RATE = 1 / 10.0

        const val PIECE_STYLE_SETTING = "setting.piece.style"
        const val SHOW_COORDINATES_SETTING = "setting.show.coordinates"
        const val MOVE_FORMAT_SETTING = "setting.move.format"
        const val MOVE_NODE_EVAL_FORMAT = "setting.move.node.eval.format"
        const val SHOW_ANALYTICS_ARROWS = "setting.show.analytics.arrows"
        const val COORDINATES_STYLE_SETTING = "setting.coordinates.style"
        const val FLIP_OPPONENT_PIECES_SETTING = "setting.flip.opponent.pieces"
        const val PLAY_SOUNDS_SETTING = "setting.play.sounds"
        const val COLORBLIND_FRIENDLY_BLACK_PIECES_SETTING = "setting.colorblind.friendly.black.pieces"

        val SETTING_COOKIE_NAMES = listOf(
            PIECE_STYLE_SETTING,
            SHOW_COORDINATES_SETTING,
            MOVE_FORMAT_SETTING,
            MOVE_NODE_EVAL_FORMAT,
            SHOW_ANALYTICS_ARROWS,
            COORDINATES_STYLE_SETTING,
            FLIP_OPPONENT_PIECES_SETTING,
            PLAY_SOUNDS_SETTING,
            COLORBLIND_FRIENDLY_BLACK_PIECES_SETTING,
        )
    }

}
