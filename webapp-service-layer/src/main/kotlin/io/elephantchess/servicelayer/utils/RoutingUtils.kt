package io.elephantchess.servicelayer.utils

fun extractAddress(remoteAddress: String, headers: Map<String, List<String>>): String? {
    val xForwardedFor = headers["x-forwarded-for"]?.firstOrNull()
    return xForwardedFor ?: remoteAddress
}
