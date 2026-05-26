package io.elephantchess.servicelayer.dto

data class FaqVoteRequest(
    val sectionId: String,
    val upVoted: Boolean,
    val feedback: String? = null,
)
