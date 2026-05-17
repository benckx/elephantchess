package io.elephantchess.servicelayer.services

import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MailRendererTest {

    private val mailRenderer = MailRenderer(MailTemplateRender())

    @Test
    fun `render user flagged notification template`() = runTest {
        val html = mailRenderer.renderEmail(
            templateName = "user_flagged_notification",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("email_css", ""),
                SimpleValueTagResolver("game_id", "game-123"),
                SimpleValueTagResolver("user_id", "user-456"),
                SimpleValueTagResolver("username", "alice"),
                SimpleValueTagResolver("game_link", "https://elephantchess.test/g/game-123"),
            )
        )

        assertTrue(html.contains("user flagged in game: game-123"))
        assertTrue(html.contains("userId: user-456"))
        assertTrue(html.contains("username: alice"))
        assertTrue(html.contains("game link: https://elephantchess.test/g/game-123"))
    }
}
