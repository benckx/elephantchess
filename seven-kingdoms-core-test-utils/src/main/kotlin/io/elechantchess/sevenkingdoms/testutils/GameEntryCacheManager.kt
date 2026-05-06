package io.elechantchess.sevenkingdoms.testutils

import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

object GameEntryCacheManager {

    private val mapper = JsonMapper
        .builder()
        .addModule(KotlinModule.Builder().build())
        .configure(INDENT_OUTPUT, false)
        .build()

    private val entries by lazy {
        mapper.readValue(
            object {}.javaClass.getResource("/games.json")?.readText(),
            GameEntriesDto::class.java
        )?.entries ?: emptyList()
    }

    fun gameEntriesToJson(value: List<GameEntryDto>): String = gameEntriesToJson(GameEntriesDto(value))
    fun gameEntriesToJson(value: GameEntriesDto): String = mapper.writeValueAsString(value)

    fun getAllGames(): List<GameEntryDto> = entries
    fun randomGame(): GameEntryDto = entries.random()

}
