package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.ContentSectionVoteDaoService
import io.elephantchess.servicelayer.dto.ContentSectionVoteRequest
import io.elephantchess.servicelayer.dto.ContentSectionVotesResponse
import io.elephantchess.servicelayer.exceptions.NotAcceptableException
import io.elephantchess.servicelayer.model.UserId

class ContentSectionFeedbackService(
    private val contentSectionVoteDaoService: ContentSectionVoteDaoService,
    private val mailService: MailService,
) {

    suspend fun submitContentSectionVote(request: ContentSectionVoteRequest, userId: UserId) {
        if (!isContentPageIdValid(request.pageId)) {
            throw NotAcceptableException("invalid page id")
        }
        if (!isContentSectionIdValid(request.sectionId)) {
            throw NotAcceptableException("invalid section id (lowercase dash-separated, max 80)")
        }
        val feedback = request.feedback?.trim()?.ifBlank { null }
        if (feedback != null && feedback.length > 1_000) throw NotAcceptableException("feedback too long")

        contentSectionVoteDaoService.persistVote(
            userId = userId.id,
            pageId = request.pageId,
            sectionId = request.sectionId,
            upVoted = request.upVoted,
            feedback = feedback
        )

        mailService.sendContentSectionVoteNotification(
            userId = userId,
            pageId = request.pageId,
            sectionId = request.sectionId,
            upVoted = request.upVoted,
            feedback = feedback,
        )
    }

    suspend fun fetchContentSectionVotes(pageId: String, userId: UserId): ContentSectionVotesResponse {
        if (!isContentPageIdValid(pageId)) {
            throw NotAcceptableException("invalid page id")
        }

        val entries = contentSectionVoteDaoService
            .listVotesByUserAndPage(userId.id, pageId)
            .map { record ->
                ContentSectionVotesResponse.Entry(
                    sectionId = record.sectionId,
                    upVoted = record.upVoted,
                    feedback = record.feedback
                )
            }

        return ContentSectionVotesResponse(entries)
    }

    companion object {

        private val DASH_SEPARATED_ID_REGEX = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
        private val ALLOWED_CONTENT_PAGE_IDS = setOf("faq", "roadmap")

        private fun isContentPageIdValid(pageId: String): Boolean =
            pageId in ALLOWED_CONTENT_PAGE_IDS

        private fun isContentSectionIdValid(sectionId: String): Boolean =
            sectionId.length <= 80 && DASH_SEPARATED_ID_REGEX.matches(sectionId)

    }

}
