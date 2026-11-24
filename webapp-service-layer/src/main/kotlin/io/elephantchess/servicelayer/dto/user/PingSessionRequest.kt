package io.elephantchess.servicelayer.dto.user

data class PingSessionRequest(val currentPage: String? = null, val fromAndroidApp: Boolean = false)
