package io.elephantchess.servicelayer.dto.database

data class DatabasePlayer(
    val id: String,
    val canonicalName: String,
    val chineseName: String?,
    val gender: String?,
) {

    val urlName: String
        get() = canonicalName.replace(" ", "_")

}
