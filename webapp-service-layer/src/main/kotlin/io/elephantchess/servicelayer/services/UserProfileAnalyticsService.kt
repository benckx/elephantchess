package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.PuzzleDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.model.PuzzleOutcome
import io.elephantchess.servicelayer.dto.puzzles.PuzzleStatsNumbersResponse
import io.elephantchess.servicelayer.dto.puzzles.PuzzleStatsRatingResponse
import io.elephantchess.servicelayer.dto.puzzles.PuzzleStatsSummaryResponse
import io.elephantchess.servicelayer.dto.user.TimeCategoryStatsDto
import io.elephantchess.servicelayer.dto.user.TimeCategoryStatsResponse
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.services.UserService.Companion.PUZZLE_START_RATING
import java.time.LocalDate

class UserProfileAnalyticsService(
    private val userDaoService: UserDaoService,
    private val puzzleDaoService: PuzzleDaoService
) {

    suspend fun fetchGameRatings(userId: String): TimeCategoryStatsResponse {
        val userRecord = userDaoService.fetchAllRatings(userId)
            ?: throw NotFoundException("User $userId could not be found")

        return TimeCategoryStatsResponse(
            TimeCategoryStatsDto(
                bullet = userRecord.gameRatingBullet,
                blitz = userRecord.gameRatingBlitz,
                rapid = userRecord.gameRatingRapid,
                classical = userRecord.gameRatingClassical,
                severalDays = userRecord.gameRatingSeveralDays,
                correspondence = userRecord.gameRatingCorrespondence
            )
        )
    }

    suspend fun fetchPuzzleStatsSummary(userId: String): PuzzleStatsSummaryResponse {
        val rating = puzzleDaoService.fetchRatingForUser(userId) ?: PUZZLE_START_RATING
        val maxRating = puzzleDaoService.fetchMaxRatingForUser(userId) ?: rating
        val totalPlayed = puzzleDaoService.countPlayedForUser(userId) ?: 0

        return PuzzleStatsSummaryResponse(
            rating = rating,
            maxRating = maxRating,
            totalPlayed = totalPlayed
        )
    }

    /**
     * For the line chart on user's profile
     */
    suspend fun fetchPuzzleStatsRating(userId: String): PuzzleStatsRatingResponse {
        val limit = LocalDate.now()!!.minusDays(MAX_DAYS_LINE_CHART.toLong())
        val lastPerDay = puzzleDaoService.fetchPuzzleStatsLastRating(userId)
        val maxPerDay = puzzleDaoService.fetchPuzzleStatsMaxRating(userId)
        val allDates = (lastPerDay.map { it.date }) + (maxPerDay.map { it.date }).distinct().sorted()
        val entries = mutableListOf<PuzzleStatsRatingResponse.Entry>()
        if (allDates.isNotEmpty()) {
            val minDate = allDates.min()
            val maxDate = LocalDate.now()!!
            var currentDate = minDate
            var currentLastValue = lastPerDay.find { it.date == currentDate }!!.value
            var currentMaxValue = maxPerDay.find { it.date == currentDate }!!.value
            while (currentDate <= maxDate) {
                if (currentDate >= limit) {
                    lastPerDay.find { it.date == currentDate }?.let { currentLastValue = it.value }
                    maxPerDay.find { it.date == currentDate }?.let { currentMaxValue = it.value }
                    entries += PuzzleStatsRatingResponse.Entry(
                        currentDate.toString(),
                        currentLastValue,
                        currentMaxValue
                    )
                }
                currentDate = currentDate.plusDays(1L)
            }
        }

        return PuzzleStatsRatingResponse(entries)
    }

    /**
     * For the vertical bar chart on user's profile
     */
    suspend fun fetchPuzzleStatsNumberPerOutcome(userId: String): PuzzleStatsNumbersResponse {
        // TODO: add outcome to the groupBy query (i.e. do these 3 queries in 1)
        // TODO: filter by date in SQL
        val solved = puzzleDaoService.fetchPuzzleStatsNumberPerOutcome(userId, PuzzleOutcome.SOLVED)
        val skipped = puzzleDaoService.fetchPuzzleStatsNumberPerOutcome(userId, PuzzleOutcome.SKIPPED)
        val failed = puzzleDaoService.fetchPuzzleStatsNumberPerOutcome(userId, PuzzleOutcome.FAILED)
        val statsEntries = (skipped.map { it.date } + solved.map { it.date } + failed.map { it.date })
        val allDates =
            statsEntries
                .distinct()
                .sorted()
                .takeLast(MAX_BAR_CHART)

        val entries = mutableListOf<PuzzleStatsNumbersResponse.Entry>()
        if (allDates.isNotEmpty()) {
            val minDate = allDates.min()
            val maxDate = LocalDate.now()!!
            var currentDate = minDate
            while (currentDate <= maxDate) {
                var solvedForDay = 0
                var skippedForDay = 0
                var failedForDay = 0
                solved.find { it.date == currentDate }?.let { solvedForDay = it.value }
                skipped.find { it.date == currentDate }?.let { skippedForDay = it.value }
                failed.find { it.date == currentDate }?.let { failedForDay = it.value }
                entries +=
                    PuzzleStatsNumbersResponse.Entry(
                        date = currentDate.toString(),
                        solved = solvedForDay,
                        skipped = skippedForDay,
                        failed = failedForDay
                    )

                currentDate = currentDate.plusDays(1L)
            }
        }

        return PuzzleStatsNumbersResponse(entries.filterNot { entry -> entry.isOnlyZeros() })
    }

    private companion object {

        const val MAX_BAR_CHART = 10
        const val MAX_DAYS_LINE_CHART = 180

    }

}
