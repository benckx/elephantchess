package io.elephantchess.webapp.server

import io.elephantchess.model.Engine
import io.elephantchess.model.OpeningMode
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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class JacksonEnumConfigTest {

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
                        call.respond(receivedRequest!!)
                    }
                }
            }

            val response = client.post("/engine") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "color": "RED",
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
                    color = Color.RED,
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
            assertContains(body, "\"color\":\"RED\"")
        }
}
