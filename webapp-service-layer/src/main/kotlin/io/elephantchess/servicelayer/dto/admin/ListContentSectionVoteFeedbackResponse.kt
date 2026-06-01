package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.UserType

data class ListContentSectionVoteFeedbackResponse(
    val entries: List<Entry>,
) {
    data class Entry(
        val userId: String,
        val username: String,
        val userType: UserType,
        val pageId: String,
        val sectionId: String,
        val upVoted: Boolean,
        val feedback: String,
        val creationTime: Long,
        val updateTime: Long,
    )
}
