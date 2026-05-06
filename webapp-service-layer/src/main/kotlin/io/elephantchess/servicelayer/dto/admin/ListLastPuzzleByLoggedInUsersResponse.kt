package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.PuzzleOutcome
import io.elephantchess.model.UserType

data class ListLastPuzzleByLoggedInUsersResponse(val entries: List<Entry>) {

    data class Entry(
        val puzzleId: String,
        val outcome: PuzzleOutcome,
        val userId: String,
        val userType: UserType?,
        val username: String?,
        val date: Long,
        val upVoted: Boolean?,
    )

}
