package io.elephantchess.servicelayer.dto.database

/**
 * Names are canonical and URL encoded
 */
data class ListPlayersResponse(
    val entries: List<Entry>
) {

    data class Entry(
        val slug: String,
        val displayName: String,
    )

}
