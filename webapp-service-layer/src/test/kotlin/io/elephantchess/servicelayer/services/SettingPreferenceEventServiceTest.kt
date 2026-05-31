package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.SETTING_PREFERENCE_EVENT
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.servicelayer.services.SettingPreferenceEventService.Companion.COLORBLIND_FRIENDLY_BLACK_PIECES_SETTING
import io.elephantchess.servicelayer.services.SettingPreferenceEventService.Companion.COORDINATES_STYLE_SETTING
import io.elephantchess.servicelayer.services.SettingPreferenceEventService.Companion.FLIP_OPPONENT_PIECES_SETTING
import io.elephantchess.servicelayer.services.SettingPreferenceEventService.Companion.MOVE_FORMAT_SETTING
import io.elephantchess.servicelayer.services.SettingPreferenceEventService.Companion.MOVE_NODE_EVAL_FORMAT
import io.elephantchess.servicelayer.services.SettingPreferenceEventService.Companion.PIECE_STYLE_SETTING
import io.elephantchess.servicelayer.services.SettingPreferenceEventService.Companion.PLAY_SOUNDS_SETTING
import io.elephantchess.servicelayer.services.SettingPreferenceEventService.Companion.SHOW_ANALYTICS_ARROWS
import io.elephantchess.servicelayer.services.SettingPreferenceEventService.Companion.SHOW_COORDINATES_SETTING
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingPreferenceEventServiceTest : ServiceTest() {

    private val settingPreferenceEventService by inject<SettingPreferenceEventService>()
    private val dslContext by inject<DSLContext>()

    @AfterTest
    fun afterEach() = runTest {
        dslContext
            .deleteFrom(SETTING_PREFERENCE_EVENT)
            .awaitExecute()
    }

    @Test
    fun samplesAFractionAndStoresCookieValues() = runTest {
        val cookies = mapOf(
            PIECE_STYLE_SETTING to "WOOD",
            SHOW_COORDINATES_SETTING to "false",
            MOVE_FORMAT_SETTING to "WXF_DOT",
            MOVE_NODE_EVAL_FORMAT to "CENTIPAWNS",
            SHOW_ANALYTICS_ARROWS to "true",
            COORDINATES_STYLE_SETTING to "WXF_ARABIC",
            FLIP_OPPONENT_PIECES_SETTING to "true",
            PLAY_SOUNDS_SETTING to "false",
            COLORBLIND_FRIENDLY_BLACK_PIECES_SETTING to "true",
        )

        val attempts = 300
        repeat(attempts) {
            settingPreferenceEventService.sampleSettingPreferences(cookies)
        }

        val records = dslContext
            .selectFrom(SETTING_PREFERENCE_EVENT)
            .awaitRecords()

        // sampling: only a fraction is persisted, but at least one over 300 attempts
        assertTrue(records.isNotEmpty(), "expected at least one sampled record")
        assertTrue(records.size < attempts, "expected only a fraction to be sampled")

        val record = records.first()
        assertEquals("WOOD", record.pieceStyle)
        assertEquals("false", record.showCoordinates)
        assertEquals("WXF_DOT", record.moveFormat)
        assertEquals("CENTIPAWNS", record.moveNodeEvalFormat)
        assertEquals("true", record.showAnalyticsArrows)
        assertEquals("WXF_ARABIC", record.coordinatesStyle)
        assertEquals("true", record.flipOpponentPieces)
        assertEquals("false", record.playSounds)
        assertEquals("true", record.colorblindFriendlyBlackPieces)
    }

    @Test
    fun storesNullWhenCookieMissing() = runTest {
        repeat(300) {
            settingPreferenceEventService.sampleSettingPreferences(emptyMap())
        }

        val records = dslContext
            .selectFrom(SETTING_PREFERENCE_EVENT)
            .awaitRecords()

        assertTrue(records.isNotEmpty(), "expected at least one sampled record")
        val record = records.first()
        assertEquals(null, record.pieceStyle)
        assertEquals(null, record.playSounds)
    }

}
