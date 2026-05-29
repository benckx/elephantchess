package io.elephantchess.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilsTest {

    @Test
    fun mayBeOffensiveShouldMatchBadWords() {
        assertTrue(mayBeOffensive("xxassxx"))
    }

    @Test
    fun mayBeOffensiveShouldMatchSensitiveWords() {
        assertTrue(mayBeOffensive("xxallahxx"))
        assertFalse(mayBeOffensive("xxelephantxx"))
    }

}
