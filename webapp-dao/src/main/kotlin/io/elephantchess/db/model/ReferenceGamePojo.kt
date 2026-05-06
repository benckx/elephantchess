package io.elephantchess.db.model

import io.elephantchess.db.dao.codegen.tables.pojos.ReferenceGame
import io.elephantchess.db.dao.codegen.tables.pojos.ReferenceGameHalfMove

data class ReferenceGamePojo(
    val game: ReferenceGame,
    val moves: List<ReferenceGameHalfMove>,
)
