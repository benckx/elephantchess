package io.elephantchess.servicelayer.dto.admin

data class UserSessionsResponse(val entries: List<Entry>) {

    data class Entry(
        val userId: String?,
        val username: String?,
        val os: String,
        val agentName: String,
        val agentClass:  String?,
        val countryCode: String?,
        val countryName: String?,
        val region: String?,
        val city: String?,
        val remoteAddress: String,
        val created: Long,
        val updated: Long,
    )

}
