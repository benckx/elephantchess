package io.elephantchess.servicelayer.dto.admin

data class PlayerDuplicatesResponse(val entries: List<Entry>) {
    data class Entry(
        val playerId: String,
        val playerCanonicalName: String,
        val isDuplicateOf: String,
        val canonicalPlayerCanonicalName: String
    )
}
