package io.elephantchess.servicelayer.services.ws

import io.elephantchess.db.utils.generateId
import io.github.oshai.kotlinlogging.KotlinLogging

abstract class WebSocketSession<U>(val sessionId: String = generateId()) {

    protected val logger = KotlinLogging.logger(this.javaClass.name + "[$sessionId]")

    private var closed = false

    val isClosed
        get() = closed

    fun markAsClosed() {
        closed = true
    }

    abstract fun update(update: U)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebSocketSession<*>) return false
        if (sessionId != other.sessionId) return false
        return true
    }

    override fun hashCode(): Int {
        return sessionId.hashCode()
    }

}
