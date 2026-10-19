package io.elephantchess.servicelayer.utils

fun extractAddress(remoteAddress: String, headers: Map<String, List<String>>): String? {
    // HTTP header names are case-insensitive: proxies/ingresses may send "X-Forwarded-For",
    // "x-forwarded-for", etc. Match them regardless of casing to avoid silently falling back
    // to the (internal) socket peer address.
    fun header(name: String): String? =
        headers.entries
            .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    return header("x-forwarded-for")
        ?: header("x-real-ip")
        ?: remoteAddress
}
