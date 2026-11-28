package io.elephantchess.db.model

data class ContentSectionVoteCountsRecord(
    val pageId: String,
    val sectionId: String,
    val upVotes: Int,
    val downVotes: Int,
)
