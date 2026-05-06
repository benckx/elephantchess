package io.elephantchess.config

data class DbConfig(
    val dbName : String,
    val url: String,
    val user: String,
    val password: String,
)
