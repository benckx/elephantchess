package io.elephantchess.servicelayer.model

/**
 * @param embeddedImages List of embedded images to attach using CID references.
 *                       Key is the Content-ID (used in HTML as src="cid:key"),
 *                       Value is the resource path (e.g., "/newsletters/2026-01/image.png")
 */
data class Email(
    val to: String,
    val cc: String? = null,
    val bcc: String? = null,
    val subject: String,
    val body: String,
    val embeddedImages: Map<String, String> = emptyMap(),
) {

    override fun toString(): String {
        val abridgedBody = if (body.length <= 200) body else body.take(200) + "..."
        return "Email(to=$to, cc=$cc, bcc=$bcc, subject=$subject, body=$abridgedBody, embeddedImages=${embeddedImages.keys})"
    }

}
