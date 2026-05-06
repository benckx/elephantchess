package io.elephantchess.servicelayer.model

data class InvalidToken(private val exception: Exception) : TokenVerificationResult {

    override fun toString(): String {
        val attributes = mutableListOf<Pair<String, String>>()
        attributes += "exception" to exception.toString()
        return "InvalidToken{${attributes.joinToString(", ") { (k, v) -> "$k=$v" }}}"
    }

}
