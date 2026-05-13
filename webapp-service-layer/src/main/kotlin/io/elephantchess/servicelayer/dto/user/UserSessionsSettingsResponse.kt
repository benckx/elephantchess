package io.elephantchess.servicelayer.dto.user

data class UserSessionsSettingsResponse(
    val entries: List<Entry>,
    val total: Int,
) {
    data class Entry(
        val id: Int,
        val os: String,
        val agentName: String,
        val countryName: String?,
        val region: String?,
        val city: String?,
        val remoteAddress: String,
        val created: Long,
        val updated: Long,
    )
}
