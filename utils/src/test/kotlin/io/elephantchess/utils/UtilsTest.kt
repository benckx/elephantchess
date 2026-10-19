package io.elephantchess.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilsTest {

    @Test
    fun mayBeOffensiveShouldMatchBadWords() {
        assertTrue(mayBeOffensive("xxassxx"))
        assertTrue(mayBeOffensive("assxx"))
        assertTrue(mayBeOffensive("xxaSs"))
    }

    @Test
    fun mayBeOffensiveShouldMatchSensitiveWords() {
        assertTrue(mayBeOffensive("xxallahxx"))
        assertTrue(mayBeOffensive("lesbianxx"))
    }

    @Test
    fun mayBeOffensiveShouldIgnoreSafeWords() {
        assertFalse(mayBeOffensive("xxelephantxx"))
    }

}
