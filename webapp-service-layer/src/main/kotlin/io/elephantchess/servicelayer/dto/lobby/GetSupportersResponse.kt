package io.elephantchess.servicelayer.dto.lobby

import io.elephantchess.servicelayer.dto.kofi.LatestSupporter

data class GetSupportersResponse(
    val entries: List<LatestSupporter>
)

