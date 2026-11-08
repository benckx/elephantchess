package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.UserType

data class ListContentSectionVoteFeedbackResponse(val aggregates: List<Aggregate>, val entries: List<Entry>) {
    data class Aggregate(
        val pageId: String,
        val sectionId: String,
        val upVotes: Int,
        val downVotes: Int,
        val totalVotes: Int,
    )

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
