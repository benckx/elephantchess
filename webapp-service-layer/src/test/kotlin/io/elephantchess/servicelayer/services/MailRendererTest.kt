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
                SimpleValueTagResolver("username", "alice"),
                SimpleValueTagResolver("game_link", "https://elephantchess.test/g/game-123"),
                SimpleValueTagResolver("update_mail_settings", ""),
            )
        )

        assertTrue(html.contains("Hi alice"))
        assertTrue(html.contains("You lost your game on time (flagged): game-123."))
        assertTrue(html.contains("Review the game at https://elephantchess.test/g/game-123"))
    }

    @Test
    fun `render opponent flagged while offline template`() = runTest {
        val html = mailRenderer.renderEmail(
            templateName = "opponent_flagged_while_offline",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("email_css", ""),
                SimpleValueTagResolver("opponent", "alice"),
                SimpleValueTagResolver("game_link", "https://elephantchess.test/g/game-123"),
                SimpleValueTagResolver("update_mail_settings", ""),
            )
        )

        assertTrue(html.contains("alice flagged on time while you were offline."))
        assertTrue(html.contains("Review the game at https://elephantchess.test/g/game-123"))
    }
}
