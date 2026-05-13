package io.elephantchess.servicelayer.dto.admin

data class RegisterPlayerDuplicateRequest(
    val playerId: String,
    val isNewDuplicateOf: String
)

data class DeletePlayerDuplicateRequest(
    val playerId: String
)
