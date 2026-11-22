package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.servicelayer.dto.database.DatabasePlayer
import io.elephantchess.servicelayer.dto.database.DatabasePlayerProfileEdit
import io.elephantchess.servicelayer.services.DatabaseService
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class DatabasePageRendererTest {

    private fun pageRenderer(databaseService: DatabaseService) =
        DatabasePageRenderer(
            HtmlRenderer(
                isMinificationEnabled = false,
                cdnFolder = null,
                webTemplateRenderer = WebTemplateRenderer(),
            ),
            databaseService,
        )

    @Test
    fun `renderPlayerPage includes contributors and last edit date`() =
        runTest {
            val databaseService = mock<DatabaseService>()
            whenever(databaseService.fetchPlayerEdit("player-id", null)).thenReturn(
                DatabasePlayerProfileEdit(
                    playerId = "player-id",
                    canonicalName = "John Doe",
                    chineseName = null,
                    gender = null,
                    profileText = "some text",
                    sources = emptyList(),
                    editComment = null,
                    enabled = true,
                    versionTime = Instant.fromEpochMilliseconds(1704067200000),
                ),
            )
            whenever(databaseService.hasPlayerOpeningData("player-id")).thenReturn(false)
            whenever(databaseService.listAllEditorsUsername("player-id"))
                .thenReturn(listOf("Bob", "Alice", "Bob", "<script>"))

            val output = pageRenderer(databaseService).renderPlayerPage(
                databasePlayer = DatabasePlayer("player-id", "John Doe", null, null),
                requestedVersion = null,
            )

            assertTrue(output.contains("<b>Contributors:</b>"))
            assertTrue(output.contains("&lt;script&gt;"))
            assertTrue(output.contains("Alice"))
            assertTrue(output.contains("Bob"))
            assertTrue(output.contains("""href="/@/Alice""""))
            assertTrue(output.contains("""href="/@/Bob""""))
            assertTrue(output.contains("""href="/@/%3Cscript%3E""""))
            assertTrue(output.contains("<b>Last edit:</b> 1 Jan 2024"))
            assertTrue(output.contains("""<meta name="author""""))
            val descriptionIndex = output.indexOf("""<div id="player-profile-description">""")
            val profileMetaIndex = output.indexOf("""<div id="profile-meta-info">""")
            assertTrue(profileMetaIndex > descriptionIndex)
            assertFalse(output.contains("<script>"))
        }

    @Test
    fun `renderPlayerPage omits profile metadata when unavailable`() =
        runTest {
            val databaseService = mock<DatabaseService>()
            whenever(databaseService.fetchPlayerEdit("player-id", null)).thenReturn(
                DatabasePlayerProfileEdit(
                    playerId = "player-id",
                    canonicalName = "John Doe",
                    chineseName = null,
                    gender = null,
                    profileText = "some text",
                    sources = emptyList(),
                    editComment = null,
                    enabled = true,
                    versionTime = null,
                ),
            )
            whenever(databaseService.hasPlayerOpeningData("player-id")).thenReturn(false)
            whenever(databaseService.listAllEditorsUsername("player-id")).thenReturn(emptyList())

            val output = pageRenderer(databaseService).renderPlayerPage(
                databasePlayer = DatabasePlayer("player-id", "John Doe", null, null),
                requestedVersion = null,
            )

            assertFalse(output.contains("<b>Contributors:</b>"))
            assertFalse(output.contains("<b>Last edit:</b>"))
        }
}
