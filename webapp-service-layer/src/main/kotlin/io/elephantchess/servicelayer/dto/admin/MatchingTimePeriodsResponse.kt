package io.elephantchess.servicelayer.dto.admin

/**
 * Available months (format "YYYY-MM") for which matching-time stats can be requested, plus the
 * ordered list of time control category group names (from [io.elephantchess.model.TimeControlCategoryGroup])
 * used to lay out the per-category-group breakdown tables. The admin page iterates over [periods] and
 * loads one month at a time.
 */
data class MatchingTimePeriodsResponse(val periods: List<String>, val categoryGroups: List<String>)
