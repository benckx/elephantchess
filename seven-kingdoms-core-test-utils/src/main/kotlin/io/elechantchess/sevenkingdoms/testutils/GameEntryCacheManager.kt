package io.elechantchess.sevenkingdoms.testutils

import tools.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS
import tools.jackson.databind.SerializationFeature.INDENT_OUTPUT
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

object GameEntryCacheManager {

    private val mapper = JsonMapper
        .builder()
        .addModule(KotlinModule.Builder().build())
        .configure(ACCEPT_CASE_INSENSITIVE_ENUMS, true)
        .configure(INDENT_OUTPUT, false)
        .build()

    private val entries by lazy {
        mapper.readValue(
            object {}.javaClass.getResource("/games.json")?.readText(),
            GameEntriesDto::class.java,
        )?.entries ?: emptyList()
    }

    fun gameEntriesToJson(value: List<GameEntryDto>): String =
        gameEntriesToJson(GameEntriesDto(value))

    fun gameEntriesToJson(value: GameEntriesDto): String =
        mapper.writeValueAsString(value)

    fun getAllGames(): List<GameEntryDto> =
        entries

    fun randomGame(): GameEntryDto =
        entries.random()
}
