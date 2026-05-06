package io.elephantchess.servicelayer.dto.kofi

data class LatestSupporter(
    val userId: String?,
    val username: String,
    val timestamp: Long,
    val amount: Double,
    val currency: String,
    val recurring: Boolean
)
