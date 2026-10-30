package io.elephantchess.engines.process

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
            "pikafish/2027-01-01/pikafish-sse41-popcnt",
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
