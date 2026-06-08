package io.elephantchess.servicelayer.dto.puzzles

import io.elephantchess.servicelayer.dto.gamedata.GameMetadataDto

data class PuzzlesOriginalGameMetadataResponse(
    val entries: List<Entry>,
) {

    data class Entry(
        val puzzleId: String,
        val gameMetadata: GameMetadataDto,
        val puzzleRating: Int?,
    )

}
