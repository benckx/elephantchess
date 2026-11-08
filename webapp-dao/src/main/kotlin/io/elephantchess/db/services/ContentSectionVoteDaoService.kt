package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.CONTENT_SECTION_VOTE
import io.elephantchess.db.dao.codegen.tables.pojos.ContentSectionVote
import io.elephantchess.db.model.ContentSectionVoteCountsRecord
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.utils.di.KoinSingleton
import org.jooq.DSLContext
import org.jooq.impl.DSL
import kotlin.time.Clock

@KoinSingleton
class ContentSectionVoteDaoService(private val dslContext: DSLContext) {

    suspend fun listVoteCountsByPageAndSection(): List<ContentSectionVoteCountsRecord> {
        val upVotes = DSL.count().filterWhere(CONTENT_SECTION_VOTE.UP_VOTED.isTrue).`as`("up_votes")
        val downVotes = DSL.count().filterWhere(CONTENT_SECTION_VOTE.UP_VOTED.isFalse).`as`("down_votes")

        return dslContext
            .select(
                CONTENT_SECTION_VOTE.PAGE_ID,
                CONTENT_SECTION_VOTE.SECTION_ID,
                upVotes,
                downVotes,
            )
            .from(CONTENT_SECTION_VOTE)
            .groupBy(CONTENT_SECTION_VOTE.PAGE_ID, CONTENT_SECTION_VOTE.SECTION_ID)
            .orderBy(CONTENT_SECTION_VOTE.PAGE_ID, CONTENT_SECTION_VOTE.SECTION_ID)
            .awaitRecords()
            .map { record ->
                ContentSectionVoteCountsRecord(
                    pageId = requireNotNull(record.value1()),
                    sectionId = requireNotNull(record.value2()),
                    upVotes = record.value3(),
                    downVotes = record.value4(),
                )
            }
    }

    suspend fun persistVote(userId: String, pageId: String, sectionId: String, upVoted: Boolean, feedback: String?) {
        val now = Clock.System.now()
        dslContext
            .insertInto(CONTENT_SECTION_VOTE)
            .set(CONTENT_SECTION_VOTE.USER_ID, userId)
            .set(CONTENT_SECTION_VOTE.PAGE_ID, pageId)
            .set(CONTENT_SECTION_VOTE.SECTION_ID, sectionId)
            .set(CONTENT_SECTION_VOTE.UP_VOTED, upVoted)
            .set(CONTENT_SECTION_VOTE.FEEDBACK, feedback)
            .set(CONTENT_SECTION_VOTE.NOTIFIED, false)
            .set(CONTENT_SECTION_VOTE.CREATION_TIME, now)
            .set(CONTENT_SECTION_VOTE.UPDATE_TIME, now)
            .onConflict(
                CONTENT_SECTION_VOTE.PAGE_ID,
                CONTENT_SECTION_VOTE.SECTION_ID,
                CONTENT_SECTION_VOTE.USER_ID,
            )
            .doUpdate()
            .set(CONTENT_SECTION_VOTE.UP_VOTED, upVoted)
            .set(CONTENT_SECTION_VOTE.FEEDBACK, feedback)
            .set(CONTENT_SECTION_VOTE.NOTIFIED, false)
            .set(CONTENT_SECTION_VOTE.UPDATE_TIME, now)
            .awaitExecute()
    }

    suspend fun listUnnotifiedVotes(): List<ContentSectionVote> =
        dslContext
            .selectFrom(CONTENT_SECTION_VOTE)
            .where(CONTENT_SECTION_VOTE.NOTIFIED.isFalse)
            .orderBy(CONTENT_SECTION_VOTE.UPDATE_TIME.asc())
            .awaitMappedRecords()

    suspend fun markAsNotified(pageId: String, sectionId: String, userId: String) {
        dslContext
            .update(CONTENT_SECTION_VOTE)
            .set(CONTENT_SECTION_VOTE.NOTIFIED, true)
            .where(CONTENT_SECTION_VOTE.PAGE_ID.eq(pageId))
            .and(CONTENT_SECTION_VOTE.SECTION_ID.eq(sectionId))
            .and(CONTENT_SECTION_VOTE.USER_ID.eq(userId))
            .awaitExecute()
    }

    suspend fun listVotesByUserAndPage(userId: String, pageId: String): List<ContentSectionVote> =
        dslContext
            .selectFrom(CONTENT_SECTION_VOTE)
            .where(CONTENT_SECTION_VOTE.USER_ID.eq(userId))
            .and(CONTENT_SECTION_VOTE.PAGE_ID.eq(pageId))
            .awaitMappedRecords()

    suspend fun listLatestFeedback(limit: Int): List<ContentSectionVote> =
        dslContext
            .selectFrom(CONTENT_SECTION_VOTE)
            .orderBy(CONTENT_SECTION_VOTE.UPDATE_TIME.desc())
            .limit(limit)
            .awaitMappedRecords<ContentSectionVote>()
}
