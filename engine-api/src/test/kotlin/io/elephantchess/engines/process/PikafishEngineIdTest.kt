package io.elephantchess.engines.process

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PikafishEngineIdTest {

    @Test
    fun `releases before 2026 use the modern executable name`() {
        assertEquals(
            "pikafish/2025-12-31/pikafish-modern",
            PikafishEngineId.pathOfExecutable("2025-12-31", "Linux", "x86_64"),
        )
    }

    @Test
    fun `releases from 2026 use the sse41 popcnt executable name`() {
        assertEquals(
            "pikafish/2026-01-02/pikafish-sse41-popcnt",
            PikafishEngineId.pathOfExecutable("2026-01-02", "Linux", "x86_64"),
        )
        assertEquals(
            "pikafish/2027-01-01/Pikafish-Linux-x86-64-universal",
            PikafishEngineId.pathOfExecutable("2027-01-01", "Linux", "x86_64"),
        )
    }

    @Test
    fun `Apple Silicon uses the macOS executable`() {
        assertEquals(
            "pikafish/2026-01-02/pikafish-apple-silicon",
            PikafishEngineId.pathOfExecutable("2026-01-02", "Mac OS X", "aarch64"),
        )
        assertEquals(
            "pikafish/2026-01-02/pikafish-apple-silicon",
            PikafishEngineId.pathOfExecutable("2026-01-02", "Mac OS X", "arm64"),
        )
    }

    @Test
    fun `September 2026 release uses universal executables`() {
        assertEquals(
            "pikafish/2026-09-06/Pikafish-Linux-x86-64-universal",
            PikafishEngineId.pathOfExecutable("2026-09-06", "Linux", "x86_64"),
        )
        assertEquals(
            "pikafish/2026-09-06/Pikafish-MacOS-universal",
            PikafishEngineId.pathOfExecutable("2026-09-06", "Mac OS X", "aarch64"),
        )
        assertEquals(
            "pikafish/2026-09-06/Pikafish-MacOS-universal",
            PikafishEngineId.pathOfExecutable("2026-09-06", "Mac OS X", "x86_64"),
        )
    }

    @Test
    fun `only universal releases support Intel macOS`() {
        assertFalse(PikafishEngineId.supportsMacOs("2026-01-02"))
        assertTrue(PikafishEngineId.supportsMacOs("2026-09-06"))
    }

    @Test
    fun `version must use yyyy-MM-dd format`() {
        assertFailsWith<IllegalArgumentException> {
            PikafishEngineId.pathOfExecutable("2026", "Linux", "x86_64")
        }
        assertFailsWith<IllegalArgumentException> {
            PikafishEngineId.pathOfExecutable("2026-1-2", "Linux", "x86_64")
        }
        assertFailsWith<IllegalArgumentException> {
            PikafishEngineId.pathOfExecutable(null, "Linux", "x86_64")
        }
    }
}
