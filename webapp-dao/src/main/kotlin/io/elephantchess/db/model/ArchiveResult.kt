package io.elephantchess.db.model

data class ArchiveResult(
    val archivedGuests: Int,
    val deletedPageViews: Int,
    val deletedSearchQueries: Int,
    val deletedSessions: Int,
)
