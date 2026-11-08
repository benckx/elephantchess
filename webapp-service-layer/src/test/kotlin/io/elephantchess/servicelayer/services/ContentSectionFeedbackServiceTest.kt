package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.CONTENT_SECTION_VOTE
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.ContentSectionVoteRequest
import io.elephantchess.servicelayer.exceptions.NotAcceptableException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.admin.AdminFeedService
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ContentSectionFeedbackServiceTest : ServiceTest() {

    private val contentSectionFeedbackService by inject<ContentSectionFeedbackService>()
    private val adminFeedService by inject<AdminFeedService>()
    private val dslContext by inject<DSLContext>()

    @AfterTest
    fun afterEach() =
        runTest {
            listOf(CONTENT_SECTION_VOTE, USER)
                .forEach { table ->
                    dslContext
                        .deleteFrom(table)
                        .awaitExecute()
                }
        }

    @Test
    fun `submitContentSectionVote should upsert vote and feedback by page and section id`() =
        runTest {
            val userId = signUpTestUser().second
            val actor = UserId(UserType.AUTHENTICATED, userId)

            contentSectionFeedbackService.submitContentSectionVote(ContentSectionVoteRequest("faq", "why-sign-up", true), actor)
            contentSectionFeedbackService.submitContentSectionVote(
                ContentSectionVoteRequest("faq", "why-sign-up", false, "I could not find this quickly."),
                actor,
            )

            val upVoted = dslContext.fetchValueAsync(
                CONTENT_SECTION_VOTE.UP_VOTED,
                CONTENT_SECTION_VOTE.USER_ID.eq(userId)
                    .and(CONTENT_SECTION_VOTE.PAGE_ID.eq("faq"))
                    .and(CONTENT_SECTION_VOTE.SECTION_ID.eq("why-sign-up")),
            )
            assertEquals(false, upVoted)

            val feedback = dslContext.fetchValueAsync(
                CONTENT_SECTION_VOTE.FEEDBACK,
                CONTENT_SECTION_VOTE.USER_ID.eq(userId)
                    .and(CONTENT_SECTION_VOTE.PAGE_ID.eq("faq"))
                    .and(CONTENT_SECTION_VOTE.SECTION_ID.eq("why-sign-up")),
            )
            assertEquals("I could not find this quickly.", feedback)
        }

    @Test
    fun `submitContentSectionVote should keep creation time and bump update time on upsert`() =
        runTest {
            val userId = signUpTestUser().second
            val actor = UserId(UserType.AUTHENTICATED, userId)

            val condition = CONTENT_SECTION_VOTE.USER_ID.eq(userId)
                .and(CONTENT_SECTION_VOTE.PAGE_ID.eq("faq"))
                .and(CONTENT_SECTION_VOTE.SECTION_ID.eq("why-sign-up"))

            contentSectionFeedbackService.submitContentSectionVote(ContentSectionVoteRequest("faq", "why-sign-up", true), actor)
            val creationTime = dslContext.fetchValueAsync(CONTENT_SECTION_VOTE.CREATION_TIME, condition)
            val firstUpdateTime = dslContext.fetchValueAsync(CONTENT_SECTION_VOTE.UPDATE_TIME, condition)

            contentSectionFeedbackService.submitContentSectionVote(ContentSectionVoteRequest("faq", "why-sign-up", false), actor)
            val creationTimeAfter = dslContext.fetchValueAsync(CONTENT_SECTION_VOTE.CREATION_TIME, condition)
            val secondUpdateTime = dslContext.fetchValueAsync(CONTENT_SECTION_VOTE.UPDATE_TIME, condition)

            assertEquals(creationTime, creationTimeAfter)
            assertEquals(true, secondUpdateTime!! >= firstUpdateTime!!)
        }

    @Test
    fun `submitContentSectionVote should reject invalid page or section ids`() =
        runTest {
            val userId = signUpTestUser().second
            val actor = UserId(UserType.AUTHENTICATED, userId)

            assertFailsWith<NotAcceptableException> {
                contentSectionFeedbackService.submitContentSectionVote(ContentSectionVoteRequest("faq", "invalid section id", true), actor)
            }
            assertFailsWith<NotAcceptableException> {
                contentSectionFeedbackService.submitContentSectionVote(
                    ContentSectionVoteRequest("invalid/page", "why-sign-up", true),
                    actor,
                )
            }
            assertFailsWith<NotAcceptableException> {
                contentSectionFeedbackService.submitContentSectionVote(ContentSectionVoteRequest("about", "why-sign-up", true), actor)
            }
        }

    @Test
    fun `fetchContentSectionVotes should list votes and feedback for user and page`() =
        runTest {
            val userId = signUpTestUser().second
            val actor = UserId(UserType.AUTHENTICATED, userId)

            contentSectionFeedbackService.submitContentSectionVote(
                ContentSectionVoteRequest("faq", "why-sign-up", true, "This answer is very useful."),
                actor,
            )
            contentSectionFeedbackService.submitContentSectionVote(
                ContentSectionVoteRequest("roadmap", "pre-move", false),
                actor,
            )

            val faqVotes = contentSectionFeedbackService.fetchContentSectionVotes("faq", actor)
            assertEquals(1, faqVotes.entries.size)
            assertEquals("why-sign-up", faqVotes.entries[0].sectionId)
            assertEquals(true, faqVotes.entries[0].upVoted)
            assertEquals("This answer is very useful.", faqVotes.entries[0].feedback)

            val roadmapVotes = contentSectionFeedbackService.fetchContentSectionVotes("roadmap", actor)
            assertEquals(1, roadmapVotes.entries.size)
            assertEquals("pre-move", roadmapVotes.entries[0].sectionId)
            assertEquals(false, roadmapVotes.entries[0].upVoted)
            assertNull(roadmapVotes.entries[0].feedback)

            assertFailsWith<NotAcceptableException> {
                contentSectionFeedbackService.fetchContentSectionVotes("about", actor)
            }
        }

    @Test
    fun `admin feedback should include current vote totals per section`() =
        runTest {
            val users = (1..3).map { signUpTestUser().second }

            contentSectionFeedbackService.submitContentSectionVote(
                ContentSectionVoteRequest("faq", "why-sign-up", true),
                UserId(UserType.AUTHENTICATED, users[0]),
            )
            contentSectionFeedbackService.submitContentSectionVote(
                ContentSectionVoteRequest("faq", "why-sign-up", true),
                UserId(UserType.AUTHENTICATED, users[1]),
            )
            contentSectionFeedbackService.submitContentSectionVote(
                ContentSectionVoteRequest("faq", "why-sign-up", false),
                UserId(UserType.AUTHENTICATED, users[2]),
            )
            contentSectionFeedbackService.submitContentSectionVote(
                ContentSectionVoteRequest("roadmap", "pre-move", true),
                UserId(UserType.AUTHENTICATED, users[0]),
            )
            contentSectionFeedbackService.submitContentSectionVote(
                ContentSectionVoteRequest("roadmap", "pre-move", false),
                UserId(UserType.AUTHENTICATED, users[1]),
            )

            val response = adminFeedService.listLatestFeedback()
            val aggregate = response.aggregates.single { it.pageId == "faq" && it.sectionId == "why-sign-up" }

            assertEquals(2, aggregate.upVotes)
            assertEquals(1, aggregate.downVotes)
            assertEquals(3, aggregate.totalVotes)
            assertEquals(3, response.aggregates.first().totalVotes)
            assertEquals("faq", response.aggregates.first().pageId)
            assertEquals(2, response.aggregates.last().totalVotes)
        }
}
