package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.ContentSectionVoteDaoService
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.admin.ListContentSectionVoteFeedbackResponse
import io.elephantchess.servicelayer.services.UserCache

class AdminContentSectionVoteService(
    private val contentSectionVoteDaoService: ContentSectionVoteDaoService,
    private val userCache: UserCache,
) {

    suspend fun listLatestFeedback(): ListContentSectionVoteFeedbackResponse {
        val entries = contentSectionVoteDaoService
            .listLatestFeedback(250)
            .map { record ->
                ListContentSectionVoteFeedbackResponse.Entry(
                    userId = record.userId,
                    username = userCache.fetchUsernameOrDefault(record.userId),
                    userType = userCache.fetchUserType(record.userId) ?: UserType.GUEST,
                    pageId = record.pageId,
                    sectionId = record.sectionId,
                    upVoted = record.upVoted,
                    feedback = record.feedback ?: "",
                    updateTime = record.updateTime.toEpochMilliseconds(),
                )
            }
        return ListContentSectionVoteFeedbackResponse(entries)
    }
}
