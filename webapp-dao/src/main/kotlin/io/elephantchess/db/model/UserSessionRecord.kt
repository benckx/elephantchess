package io.elephantchess.db.model

import kotlin.time.Instant

data class UserSessionRecord(
    val userId: String?,
    val remoteAddress: String,
    val userAgent: String,
    val operatingSystemName: String,
    val agentName: String,
    val agentClass: String? = null,
    val created: Instant? = null,
    val lastUpdated: Instant? = null,
    val countryName: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
)
