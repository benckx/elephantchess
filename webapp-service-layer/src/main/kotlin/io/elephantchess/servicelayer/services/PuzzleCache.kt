package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.PuzzleDaoService
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.model.PuzzleCategory
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRateStartImmediately
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.hours

class PuzzleCache(
    private val puzzleDaoService: PuzzleDaoService,
    private val puzzleResultDaoService: PuzzleResultDaoService,
    private val logger: KLogger,
    refresherScope: CoroutineScope
) {

    private var entries = listOf<PuzzleCacheEntry>()
    private val refreshJob = launchAtFixedRateStartImmediately(
        scope = refresherScope,
        period = 4.hours,
        action = {
            val attemptsMap = puzzleResultDaoService.countAllAttempts()
            val categoriesMap = puzzleDaoService.fetchAllCategories()

            entries =
                puzzleDaoService
                    .fetchAll()
                    .map { puzzleRecord ->
                        val attempts = attemptsMap[puzzleRecord.id] ?: 0
                        val categories = categoriesMap[puzzleRecord.id] ?: emptyList()
                        val enabled = puzzleRecord.enabled ?: true
                        PuzzleCacheEntry(puzzleRecord.id, puzzleRecord.rating, attempts, categories, enabled)
                    }

            logger.info { "loaded ${entries.size} puzzles from DB into cache" }
        }
    )

    fun cancel() {
        refreshJob.cancel()
    }

    fun size() = entries.size

    fun countAllAttempts() = entries.sumOf { entry -> entry.attempts }

    fun exists(puzzleId: String) = entries.any { entry -> entry.puzzleId == puzzleId }

    fun hasCategories(puzzleId: String, categories: List<PuzzleCategory>) =
        entries.find { entry -> entry.puzzleId == puzzleId }?.categories?.containsAll(categories) ?: false

    fun getById(puzzleId: String) = entries.find { entry -> entry.puzzleId == puzzleId }

    fun randomId(
        userRating: Int? = null,
        range: Int? = null,
        exclude: List<String> = emptyList(),
        categories: List<PuzzleCategory> = emptyList(),
    ): String {
        val enabledEntries = entries.filter { entry -> entry.enabled }
        var filtered = enabledEntries

        if (exclude.isNotEmpty()) {
            filtered = filtered.filter { entry -> !exclude.contains(entry.puzzleId) }
        }

        if (userRating != null && range != null) {
            filtered = filtered.filter { entry -> entry.rating in (userRating - range)..(userRating + range) }
        }

        if (categories.isNotEmpty()) {
            filtered = filtered.filter { entry -> entry.categories.containsAll(categories) }
        }

        logger.debug { "${filtered.size} puzzles for user with rating $userRating" }

        return if (filtered.isEmpty()) {
            enabledEntries.random().puzzleId
        } else {
            filtered.random().puzzleId
        }
    }

    fun countAll(): Int = entries.size

    fun countPuzzlePlayedAtLeast(numberOfTimes: Int): Int {
        return entries.count { entry -> entry.attempts >= numberOfTimes }
    }

    fun listCategories(puzzleId: String): List<PuzzleCategory> {
        return entries.find { entry -> entry.puzzleId == puzzleId }?.categories ?: emptyList()
    }

    companion object {

        data class PuzzleCacheEntry(
            val puzzleId: String,
            val rating: Int,
            val attempts: Int,
            val categories: List<PuzzleCategory>,
            val enabled: Boolean,
        )

    }

}
