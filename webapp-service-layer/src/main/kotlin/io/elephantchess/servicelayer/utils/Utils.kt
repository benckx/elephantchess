package io.elephantchess.servicelayer.utils

import io.elephantchess.engines.process.FairyStockfishEngineId
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.model.Engine

fun modelToProcess(engine: Engine) =
    when (engine) {
        Engine.PIKAFISH -> PikafishEngineId
        Engine.FAIRYSTOCKFISH -> FairyStockfishEngineId
    }

fun obfuscateEmail(email: String): String {
    val parts = email.split("@")
    val localPart = parts[0]
    val domainPart = parts[1]

    val obfuscatedLocalPart = when (localPart.length) {
        in 0..3 -> localPart
        in 4..6 -> localPart.take(2) + "..."
        else -> localPart.take(3) + "..." + localPart.substring(localPart.length - 2)
    }

    return "$obfuscatedLocalPart@$domainPart"
}
