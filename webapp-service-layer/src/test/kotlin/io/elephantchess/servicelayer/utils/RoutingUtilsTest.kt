package io.elephantchess.servicelayer.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingUtilsTest {

    private val remoteAddress = "10.0.0.5"

    @Test
    fun `extractAddress reads lowercase x-forwarded-for`() {
        val headers = mapOf("x-forwarded-for" to listOf("203.0.113.7"))
        assertEquals("203.0.113.7", extractAddress(remoteAddress, headers))
    }

    @Test
    fun `extractAddress reads x-forwarded-for regardless of casing`() {
        val headers = mapOf("X-Forwarded-For" to listOf("203.0.113.7"))
        assertEquals("203.0.113.7", extractAddress(remoteAddress, headers))
    }

    @Test
    fun `extractAddress falls back to x-real-ip when forwarded-for is absent`() {
        val headers = mapOf("X-Real-IP" to listOf("203.0.113.9"))
        assertEquals("203.0.113.9", extractAddress(remoteAddress, headers))
    }

    @Test
    fun `extractAddress falls back to remoteAddress when no forwarding headers present`() {
        assertEquals(remoteAddress, extractAddress(remoteAddress, emptyMap()))
    }

    @Test
    fun `extractAddress ignores blank forwarded-for value`() {
        val headers = mapOf("x-forwarded-for" to listOf("   "))
        assertEquals(remoteAddress, extractAddress(remoteAddress, headers))
    }

    @Test
    fun `extractAddress keeps full comma-separated forwarded-for chain`() {
        // cleanAddress downstream is responsible for picking the first valid public IP
        val headers = mapOf("x-forwarded-for" to listOf("203.0.113.7, 10.0.0.1"))
        assertEquals("203.0.113.7, 10.0.0.1", extractAddress(remoteAddress, headers))
    }
}
