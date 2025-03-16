package io.elephantchess.engines.protocol

class FirstMatchLineListener(private val linePredicate: (String) -> Boolean) : LineListener {

    var matchingLine: String? = null

    fun hasMatched(): Boolean = matchingLine != null

    override fun receivedLine(line: String) {
        if (matchingLine == null && linePredicate(line)) {
            matchingLine = line
        }
    }

}
