package io.elephantchess.servicelayer.dto.database

import io.elephantchess.servicelayer.dto.gamedata.GameMetadataDto

data class ReferenceGameSearchResult(val entries: List<GameMetadataDto>)
