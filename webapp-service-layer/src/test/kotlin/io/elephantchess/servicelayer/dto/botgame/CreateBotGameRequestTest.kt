package io.elephantchess.servicelayer.dto.botgame

import io.elephantchess.model.Engine
import io.elephantchess.model.OpeningMode
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.Variant
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateBotGameRequestTest {

    @Test
    fun `create request allows manchu with non-engine-only opening mode`() {
        val request = CreateBotGameRequest(
            color = RED,
            depth = 4,
            engine = Engine.FAIRYSTOCKFISH,
            startFen = null,
            openingMode = OpeningMode.BY_FREQUENCY,
            variant = Variant.MANCHU
        )

        assertEquals(Variant.MANCHU, request.variant)
        assertEquals(OpeningMode.BY_FREQUENCY, request.openingMode)
    }
}
