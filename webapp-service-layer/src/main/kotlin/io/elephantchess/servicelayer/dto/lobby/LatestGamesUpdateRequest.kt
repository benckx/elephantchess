package io.elephantchess.servicelayer.dto.lobby

import io.elephantchess.model.GameId

data class LatestGamesUpdateRequest(val gameIds: List<GameId>)
