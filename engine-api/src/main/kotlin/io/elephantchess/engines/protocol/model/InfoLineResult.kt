package io.elephantchess.engines.protocol.model

data class InfoLineResult(
    val depth: Int?,
    val time: Long?,
    val mate: Int? = null,
    val cp: Int? = null,
    val pv: List<String> = listOf(),
    val line: String,
) {

    companion object {

        fun parseInfoLine(line: String): InfoLineResult {
            var depth: Int? = null
            var time: Long? = null
            var mate: Int? = null
            var cp: Int? = null
            var pv = listOf<String>()
            val words = line.split(" ")
            words.forEachIndexed { i, word ->
                when (word) {
                    "depth" -> depth = words[i + 1].toInt()
                    "time" -> time = words[i + 1].toLong()
                    "mate" -> mate = words[i + 1].toInt()
                    "cp" -> cp = words[i + 1].toInt()
                    "pv" -> pv = words.subList(i + 1, words.size)
                }
            }

            return InfoLineResult(
                depth = depth,
                time = time,
                mate = mate,
                cp = cp,
                pv = pv,
                line = line
            )
        }

    }

}
