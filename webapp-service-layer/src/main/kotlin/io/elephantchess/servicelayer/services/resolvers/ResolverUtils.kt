package io.elephantchess.servicelayer.services.resolvers

fun makeAnchor(url: String): String {
    return "<a href=\"$url\">$url</a>"
}
