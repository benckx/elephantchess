package io.elephantchess.servicelayer.dto.engines

import io.elephantchess.model.Engine

data class EngineRequest(val fen: String, val engine: Engine, val depth: Int) {

    fun validatedDepth(): Int {
        return if (depth in 1..22) {
            depth
        } else {
            6
        }
    }

}
