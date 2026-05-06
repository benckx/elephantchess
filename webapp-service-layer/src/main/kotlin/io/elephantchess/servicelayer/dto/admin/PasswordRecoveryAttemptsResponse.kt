package io.elephantchess.servicelayer.dto.admin

data class PasswordRecoveryAttemptsResponse(val entries: List<Entry>) {

    data class Entry(
        val creationTime: Long,
        val email:String,
        val userId:String?,
        val username:String?,
        val recoveryTime: Long?,
        val userCreation: Long?,
    )

}
