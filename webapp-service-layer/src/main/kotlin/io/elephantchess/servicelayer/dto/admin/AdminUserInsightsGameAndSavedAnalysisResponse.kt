package io.elephantchess.servicelayer.dto.admin

data class AdminUserInsightsGameAndSavedAnalysisResponse(
    val puzzle: AdminUserInsightsDetailsResponse.ActivitySummary,
    val pvp: AdminUserInsightsDetailsResponse.ActivitySummary,
    val pvpOver3: AdminUserInsightsDetailsResponse.ActivitySummary,
    val pvb: AdminUserInsightsDetailsResponse.ActivitySummary,
    val pvbOver3: AdminUserInsightsDetailsResponse.ActivitySummary,
    val savedAnalysis: List<AdminUserInsightsDetailsResponse.SavedAnalysisEntry>,
)
