package io.elephantchess.servicelayer.dto

data class ContentSectionVoteRequest(
    val pageId: String,
    val sectionId: String,
    val upVoted: Boolean,
    val feedback: String? = null,
)
