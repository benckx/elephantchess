package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.TemplateRenderer
import io.elephantchess.servicelayer.dto.database.DatabasePlayer
import io.elephantchess.servicelayer.dto.database.DatabasePlayerProfileEdit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabasePageRendererTest {

    private fun pageRenderer(): DatabasePageRenderer {
        val templateRenderer = object : TemplateRenderer(baseTagResolvers = emptyList(), disabledTemplates = emptyList()) {}
        val htmlRenderer = HtmlRenderer(isMinificationEnabled = false, cdnFolder = null, webTemplateRenderer = templateRenderer)
        return DatabasePageRenderer(htmlRenderer)
    }

    @Test
    fun `renderPlayerPage includes contributors and last edit date`() = runTest {
        val output = pageRenderer().renderPlayerPage(
            databasePlayer = DatabasePlayer("player-id", "John Doe", null, null),
            requestedVersion = null,
            edit = DatabasePlayerProfileEdit(
                playerId = "player-id",
                canonicalName = "John Doe",
                chineseName = null,
                gender = null,
                profileText = "some text",
                sources = emptyList(),
                editComment = null,
                enabled = true,
                versionTime = 1704067200000
            ),
            fetchEditorsUsername = { listOf("Bob", "Alice", "Bob", "<script>") }
        )

        assertTrue(output.contains("<b>Contributors:</b> &lt;script&gt;, Alice, Bob"))
        assertTrue(output.contains("<b>Last edit:</b> 1 Jan 2024"))
    }

    @Test
    fun `renderPlayerPage omits profile metadata when unavailable`() = runTest {
        val output = pageRenderer().renderPlayerPage(
            databasePlayer = DatabasePlayer("player-id", "John Doe", null, null),
            requestedVersion = null,
            edit = DatabasePlayerProfileEdit(
                playerId = "player-id",
                canonicalName = "John Doe",
                chineseName = null,
                gender = null,
                profileText = "some text",
                sources = emptyList(),
                editComment = null,
                enabled = true,
                versionTime = null
            ),
            fetchEditorsUsername = { emptyList() }
        )

        assertFalse(output.contains("<b>Contributors:</b>"))
        assertFalse(output.contains("<b>Last edit:</b>"))
    }
}
