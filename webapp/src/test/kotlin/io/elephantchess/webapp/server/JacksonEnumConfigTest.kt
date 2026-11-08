package io.elephantchess.webapp.server

import io.elephantchess.model.Engine
import io.elephantchess.model.OpeningMode
import io.elephantchess.servicelayer.dto.analysis.OpeningReferencePlayerNextMovesRequest
import io.elephantchess.servicelayer.dto.botgame.CreateBotGameRequest
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson3.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class JacksonEnumConfigTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["WHITE", "BLACK", "white", "black"])
    fun `API parses opening repertoire color as an enum`(color: String?) =
        testApplication {
            var receivedRequest: OpeningReferencePlayerNextMovesRequest? = null

            application {
                install(ContentNegotiation) {
                    jackson { configureEnums() }
                }
                routing {
                    post("/openings") {
                        receivedRequest = call.receive<OpeningReferencePlayerNextMovesRequest>()
                        call.respond(receivedRequest)
                    }
                }
            }

            val response = client.post("/openings") {
                contentType(ContentType.Application.Json)
                val colorJson = color?.let { "\"$it\"" } ?: "null"
                setBody("""{"moves":[],"playerId":"player","color":$colorJson}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(color?.let { Color.valueOf(it.uppercase()) }, receivedRequest?.color)
            assertEquals("player", receivedRequest?.playerId)
            assertEquals(emptyList(), receivedRequest?.moves)
            val colorJson = color?.let { "\"${it.uppercase()}\"" } ?: "null"
            assertContains(response.bodyAsText(), "\"color\":$colorJson")
        }

    @Test
    fun `API rejects legacy RED opening repertoire color`() =
        testApplication {
            application {
                install(ContentNegotiation) {
                    jackson { configureEnums() }
                }
                routing {
                    post("/openings") {
                        call.receive<OpeningReferencePlayerNextMovesRequest>()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response = client.post("/openings") {
                contentType(ContentType.Application.Json)
                setBody("""{"moves":[],"playerId":"player","color":"RED"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `API parses CreateBotGameRequest sent by frontend and writes enums by name`() =
        testApplication {
            var receivedRequest: CreateBotGameRequest? = null

            application {
                install(ContentNegotiation) {
                    jackson {
                        configureEnums()
                    }
                }
                routing {
                    post("/engine") {
                        receivedRequest = call.receive<CreateBotGameRequest>()
                        call.respond(receivedRequest)
                    }
                }
            }

            val response = client.post("/engine") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "color": "WHITE",
                      "depth": 6,
                      "engine": "FAIRYSTOCKFISH",
                      "startFen": null,
                      "openingMode": "BY_FREQUENCY",
                      "variant": "XIANGQI"
                    }
                    """.trimIndent(),
                )
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                CreateBotGameRequest(
                    color = Color.WHITE,
                    depth = 6,
                    engine = Engine.FAIRYSTOCKFISH,
                    startFen = null,
                    openingMode = OpeningMode.BY_FREQUENCY,
                    variant = Variant.XIANGQI,
                ),
                receivedRequest,
            )
            // enums must be written using their names (not toString), as expected by the frontend
            val body = response.bodyAsText()
            assertContains(body, "\"engine\":\"FAIRYSTOCKFISH\"")
            assertContains(body, "\"color\":\"WHITE\"")
        }
}
