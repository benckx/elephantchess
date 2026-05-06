package io.elephantchess.webapp.ops

import com.fasterxml.jackson.databind.json.JsonMapper
import io.elephantchess.servicelayer.utils.ops.koin
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import java.io.IOException
import java.nio.channels.ClosedChannelException

val wsOpsLogger by lazy { logger {} }
val wsJsonMapper by koin<JsonMapper>("wsJsonMapper")

fun DefaultWebSocketServerSession.sendWs(o: Any): ChannelResult<Unit> {
    return outgoing.trySend(Frame.Text(wsJsonMapper.writeValueAsString(o)))
}

suspend fun DefaultWebSocketServerSession.handleWebSocketSession(
    startSession: suspend () -> String,
    closeSession: suspend (String) -> Unit,
) {
    val sessionId = startSession()
    try {
        @OptIn(DelicateCoroutinesApi::class)
        while (!incoming.isClosedForReceive) delay(1_000)
    } catch (e: Exception) {
        if (isWebSocketDisconnectException(e)) {
            wsOpsLogger.debug { "WebSocket disconnected normally: ${e.message}" }
        } else {
            wsOpsLogger.error(e) { "WebSocket error in session $sessionId" }
        }
    } finally {
        closeSession(sessionId)
    }
}

suspend inline fun <reified T : Any> DefaultWebSocketServerSession.handleBidirectionalWebSocketSession(
    startSession: suspend () -> String,
    receive: suspend (T) -> Unit,
    closeSession: suspend (String) -> Unit,
) {
    val sessionId = startSession()
    try {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                val text = frame.readText()
                receive(wsJsonMapper.readValue(text, T::class.java))
            } else if (frame is Frame.Close) {
                break
            }
        }
    } catch (e: Exception) {
        if (isWebSocketDisconnectException(e)) {
            wsOpsLogger.debug { "WebSocket disconnected normally: ${e.message}" }
        } else {
            wsOpsLogger.error(e) { "WebSocket error in session $sessionId" }
        }
    } finally {
        closeSession(sessionId)
    }
}

/**
 * Checks if an exception is caused by a normal WebSocket disconnection.
 * These include ping timeouts, closed channels, and other network-related closures.
 */
fun isWebSocketDisconnectException(t: Throwable): Boolean {
    var current: Throwable? = t
    while (current != null) {
        if (current is ClosedChannelException ||
            current is ClosedReceiveChannelException ||
            current is IOException && current.message?.contains("Ping timeout") == true ||
            current is IOException && current.message?.contains("Broken pipe") == true ||
            current is IOException && current.message?.contains("Connection reset") == true
        ) {
            return true
        }
        current = current.cause
    }
    return false
}
