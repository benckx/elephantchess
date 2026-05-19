package io.elephantchess.servicelayer.services.resolvers

import kotlinx.html.a
import kotlinx.html.stream.createHTML

fun makeAnchor(url: String): String {
    return createHTML().a(href = url) { +url }
}
