package io.elephantchess.servicelayer.services

import io.elephantchess.servicelayer.dto.analysis.SaveAnalysisRequest
import io.elephantchess.servicelayer.exceptions.BadRequestException
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnalysisServiceTest {

    private val analysisService =
        AnalysisService(
            analysisDaoService = mock(),
            userDaoService = mock(),
            gameDataService = mock(),
            engineService = mock(),
            logger = mock(),
        )

    @Test
    fun `saveOrUpdateAnalysis should reject empty analysis`() = runTest {
        val exception = assertFailsWith<BadRequestException> {
            analysisService.saveOrUpdateAnalysis(
                userId = "user-1",
                request = SaveAnalysisRequest(
                    analysisId = null,
                    name = "empty analysis",
                    nodes = emptyList(),
                    gameId = null,
                    engineCache = emptyList(),
                    startFen = null,
                    selectedNodeId = null,
                )
            )
        }

        assertEquals("Analysis must contain at least one move", exception.message)
    }
}
