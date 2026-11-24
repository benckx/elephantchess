package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.OpeningRepositoryCacheDaoService
import io.elephantchess.db.services.OpeningRepositoryReferencePlayerCacheDaoService
import io.elephantchess.servicelayer.dto.analysis.OpeningReferencePlayerNextMovesRequest
import io.elephantchess.xiangqi.Color
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class OpeningServiceTest {

    @ParameterizedTest
    @NullSource
    @EnumSource(Color::class)
    fun `opening repertoire passes the typed color filter to the repository`(color: Color?) =
        runTest {
            val populationRepository = mock<OpeningRepositoryCacheDaoService>()
            val playerRepository = mock<OpeningRepositoryReferencePlayerCacheDaoService>()
            val service = OpeningService(populationRepository, playerRepository)
            val moves = listOf("h2e2")
            whenever(populationRepository.fetchNextMovesData(moves)).thenReturn(emptyList())
            whenever(playerRepository.fetchNextMovesData("player", color, moves)).thenReturn(emptyList())

            val response = service.fetchReferencePlayerNextMovesData(
                OpeningReferencePlayerNextMovesRequest(moves, "player", color),
            )

            verify(playerRepository).fetchNextMovesData("player", color, moves)
            assertEquals(moves, response.moves)
            assertEquals(emptyList(), response.entries)
        }
}
