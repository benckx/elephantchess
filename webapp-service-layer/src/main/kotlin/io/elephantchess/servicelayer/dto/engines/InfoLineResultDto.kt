package io.elephantchess.servicelayer.dto.engines

import io.elephantchess.engines.protocol.model.InfoLineResult

data class InfoLineResultDto(
    val line: String?,
    val fen: String,
    val depth: Int?,
    val cp: Int?,
    val mate: Int?,
    val pv: List<String>,
    val bestMove: String?,
    val isCheckmate: Boolean,
) {

    companion object {

        fun mapToInfoLineResultDto(fen: String, rawLine: String): InfoLineResultDto {
            val infoLineResult = InfoLineResult.parseInfoLine(rawLine)
            return mapToInfoLineResultDto(fen, infoLineResult)
        }

        fun mapToInfoLineResultDto(fen: String, infoLineResult: InfoLineResult): InfoLineResultDto {
            return InfoLineResultDto(
                line = infoLineResult.line,
                fen = fen,
                depth = infoLineResult.depth,
                cp = infoLineResult.cp,
                mate = infoLineResult.mate,
                pv = infoLineResult.pv,
                bestMove = infoLineResult.pv.firstOrNull(),
                isCheckmate = infoLineResult.mate != null && infoLineResult.mate == 0
            )
        }
    }

}
