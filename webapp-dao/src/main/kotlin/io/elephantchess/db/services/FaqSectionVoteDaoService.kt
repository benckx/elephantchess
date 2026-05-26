package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.FAQ_SECTION_VOTE
import io.elephantchess.db.utils.awaitExecute
import org.jooq.DSLContext
import kotlin.time.Clock

class FaqSectionVoteDaoService(private val dslContext: DSLContext) {

    suspend fun persistVote(userId: String, faqSectionId: String, upVoted: Boolean, feedback: String?) {
        val now = Clock.System.now()
        dslContext
            .insertInto(FAQ_SECTION_VOTE)
            .set(FAQ_SECTION_VOTE.USER_ID, userId)
            .set(FAQ_SECTION_VOTE.FAQ_SECTION_ID, faqSectionId)
            .set(FAQ_SECTION_VOTE.UP_VOTED, upVoted)
            .set(FAQ_SECTION_VOTE.FEEDBACK, feedback)
            .set(FAQ_SECTION_VOTE.UPDATE_TIME, now)
            .onConflict(FAQ_SECTION_VOTE.FAQ_SECTION_ID, FAQ_SECTION_VOTE.USER_ID)
            .doUpdate()
            .set(FAQ_SECTION_VOTE.UP_VOTED, upVoted)
            .set(FAQ_SECTION_VOTE.FEEDBACK, feedback)
            .set(FAQ_SECTION_VOTE.UPDATE_TIME, now)
            .awaitExecute()
    }

}
