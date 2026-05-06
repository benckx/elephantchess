package io.elephantchess.servicelayer.dto.admin

data class ListLastUserAnalysisResponse(val entries : List<Entry>) {

    data class Entry(
        val analysisId: String,
        val currentVersion: Int,
        val name: String,
        val created: Long,
        val lastUpdated: Long,
        val userId: String,
        val username: String,
    )

}
