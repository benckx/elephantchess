package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.CONTENT_SECTION_VOTE
import io.elephantchess.db.dao.codegen.tables.pojos.ContentSectionVote
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitMappedRecords
import org.jooq.DSLContext
import kotlin.time.Clock

class ContentSectionVoteDaoService(private val dslContext: DSLContext) {

    suspend fun persistVote(userId: String, pageId: String, sectionId: String, upVoted: Boolean, feedback: String?) {
        val now = Clock.System.now()
        dslContext
            .insertInto(CONTENT_SECTION_VOTE)
            .set(CONTENT_SECTION_VOTE.USER_ID, userId)
            .set(CONTENT_SECTION_VOTE.PAGE_ID, pageId)
            .set(CONTENT_SECTION_VOTE.SECTION_ID, sectionId)
            .set(CONTENT_SECTION_VOTE.UP_VOTED, upVoted)
            .set(CONTENT_SECTION_VOTE.FEEDBACK, feedback)
            .set(CONTENT_SECTION_VOTE.CREATION_TIME, now)
            .set(CONTENT_SECTION_VOTE.UPDATE_TIME, now)
            .onConflict(CONTENT_SECTION_VOTE.PAGE_ID, CONTENT_SECTION_VOTE.SECTION_ID, CONTENT_SECTION_VOTE.USER_ID)
            .doUpdate()
            .set(CONTENT_SECTION_VOTE.UP_VOTED, upVoted)
            .set(CONTENT_SECTION_VOTE.FEEDBACK, feedback)
            .set(CONTENT_SECTION_VOTE.UPDATE_TIME, now)
            .awaitExecute()
    }

    suspend fun listVotesByUserAndPage(userId: String, pageId: String): List<ContentSectionVote> {
        return dslContext
            .selectFrom(CONTENT_SECTION_VOTE)
            .where(CONTENT_SECTION_VOTE.USER_ID.eq(userId))
            .and(CONTENT_SECTION_VOTE.PAGE_ID.eq(pageId))
            .awaitMappedRecords()
    }

    suspend fun listLatestFeedback(limit: Int): List<ContentSectionVote> {
        return dslContext
            .selectFrom(CONTENT_SECTION_VOTE)
            .orderBy(CONTENT_SECTION_VOTE.UPDATE_TIME.desc())
            .limit(limit)
            .awaitMappedRecords<ContentSectionVote>()
    }

}
