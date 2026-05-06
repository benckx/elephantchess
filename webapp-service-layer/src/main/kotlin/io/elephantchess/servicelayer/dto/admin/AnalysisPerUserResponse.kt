package io.elephantchess.servicelayer.dto.admin

data class AnalysisPerUserResponse(val entries: List<Entry>) {

    data class Entry(
        val userId: String,
        val username: String,
        val count: Int,
        val lastUpdated: Long? = null,
    )

}
