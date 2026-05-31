package io.elephantchess.servicelayer.services

import io.elephantchess.model.OpeningMode
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.xiangqi.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerVsBotGameServiceValidationTest {

    @Test
    fun `manchu variant requires engine-only opening mode in service`() {
        val exception = assertFailsWith<BadRequestException> {
            validateManchuOpeningMode(Variant.MANCHU, OpeningMode.BY_FREQUENCY)
        }

        assertEquals("Manchu variant requires engine-only opening mode", exception.message)
    }
}
