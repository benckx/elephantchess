package io.elephantchess.servicelayer.dto.database

data class ReferenceGameSearchRequest(
    val year: Int? = null,
    val dateStart: String? = null,
    val dateEnd: String? = null,
    val playerName: String?,
    val playerIds: List<String> = listOf(),
    val eventName: String?,
    val eventIds: List<String> = listOf(),
)
