package io.elephantchess.servicelayer.dto.botgame

import io.elephantchess.model.Engine
import io.elephantchess.model.OpeningMode
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreateBotGameRequestTest {

    @Test
    fun `manchu variant requires engine-only opening mode`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CreateBotGameRequest(
                color = RED,
                depth = 4,
                engine = Engine.FAIRYSTOCKFISH,
                startFen = null,
                openingMode = OpeningMode.BY_FREQUENCY,
                variant = Variant.MANCHU
            )
        }

        assertEquals("Manchu variant requires engine-only opening mode", exception.message)
    }
}
