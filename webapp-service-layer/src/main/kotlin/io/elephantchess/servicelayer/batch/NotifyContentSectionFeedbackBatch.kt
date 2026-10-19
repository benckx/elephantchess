package io.elephantchess.servicelayer.batch

import io.elephantchess.db.services.ContentSectionVoteDaoService
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.services.ContentFeedbackEntry
import io.elephantchess.servicelayer.services.MailService
import io.elephantchess.utils.di.KoinSingleton
import io.github.oshai.kotlinlogging.KLogger

/**
 * Sends a single admin digest email summarizing content section votes that have not been notified yet.
 *
 * Previously an email was sent synchronously on every vote submission. Instead, votes are flagged as
 * not notified when persisted, and this batch collects the un-notified ones, emails a single summary
 * and marks them all as notified afterwards.
 */
@KoinSingleton
class NotifyContentSectionFeedbackBatch(
    override val logger: KLogger,
    private val contentSectionVoteDaoService: ContentSectionVoteDaoService,
    private val mailService: MailService,
) : SinglePodBatch {

    override suspend fun run() {
        val votes = contentSectionVoteDaoService.listUnnotifiedVotes()

        if (votes.isEmpty()) {
            return
        }

        mailService.sendContentSectionVoteNotificationDigest(
            votes.map { vote ->
                ContentFeedbackEntry(
                    userId = vote.userId,
                    pageId = vote.pageId,
                    sectionId = vote.sectionId,
                    upVoted = vote.upVoted,
                    feedback = vote.feedback,
                )
            },
        )

        votes.forEach { vote ->
            contentSectionVoteDaoService.markAsNotified(
                pageId = vote.pageId,
                sectionId = vote.sectionId,
                userId = vote.userId,
            )
        }

        logger.info { "notified ${votes.size} content section vote(s)" }
    }
}
