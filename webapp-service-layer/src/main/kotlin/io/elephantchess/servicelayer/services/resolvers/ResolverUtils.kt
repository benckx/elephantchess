package io.elephantchess.servicelayer.services.resolvers

import kotlinx.html.a
import kotlinx.html.stream.createHTML

/** Builds an HTML anchor where both href and label are [url]. */
fun makeAnchor(url: String): String {
    return createHTML().a(href = url) { +url }
}
