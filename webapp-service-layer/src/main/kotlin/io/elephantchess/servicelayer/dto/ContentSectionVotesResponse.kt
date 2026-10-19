package io.elephantchess.servicelayer.dto

data class ContentSectionVotesResponse(
    val entries: List<Entry>,
) {
    data class Entry(
        val sectionId: String,
        val upVoted: Boolean,
        val feedback: String?,
    )
}
