package io.elephantchess.webapp.routing.api

import io.elephantchess.servicelayer.dto.ContactFormRequest
import io.elephantchess.servicelayer.dto.user.*
import io.elephantchess.servicelayer.model.GuestToken
import io.elephantchess.servicelayer.services.GlobalAnalyticsService
import io.elephantchess.servicelayer.services.UserProfileAnalyticsService
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.*
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

private val userService by koin<UserService>()
private val userProfileAnalyticsService by koin<UserProfileAnalyticsService>()
private val globalAnalyticsService by koin<GlobalAnalyticsService>()

fun Route.userRoutes() {
    loginAndSignUpRoutes()
    pingRoute()
    isOnlineIndicatorsRoutes()
    publicProfileRoutes()
    globalStatsRoutes()
    userSettingsRoutes()
    passwordRecoveryRoutes()
    contactFormRoutes()
}

private fun Route.loginAndSignUpRoutes() {
    route("/api") {
        post("/login") {
            val userLoginRequest = call.receive<UserLoginRequest>()
            call.respond(userService.login(userLoginRequest))
        }
        post("/validate-signup") {
            handleValidatedResponse<SignUpRequest, Unit> { request ->
                userService.validateSignUp(request)
            }
        }
        post("/signup") {
            requireIdentificationWithBody<SignUpRequest> { verifiedToken, request ->
                val guestUserId =
                    if (request.transferGuestData) {
                        (verifiedToken as? GuestToken)?.userId
                    } else {
                        null
                    }

                handleValidatedResponse<SignUpRequest, SignUpResponse> { request ->
                    userService.signUp(request, guestUserId)
                }
            }
        }
        get("/obtain-guest-user-token") {
            call.respond(userService.obtainGuestUserToken())
        }
        get("/token-expiration-date") {
            requireIdentification { verifiedToken ->
                TokenExpirationDateResponse(verifiedToken.expiresAtInstant()?.toEpochMilliseconds() ?: 0)
            }
        }
    }
}

private fun Route.pingRoute() {
    route("/api") {
        post("/ping-session") {
            requireIdentificationWithBody<PingSessionRequest> { verifiedToken, request ->
                val headers = call.request.headers.toMap()
                val remoteAddress = call.request.origin.remoteAddress
                userService.pingUserSession(verifiedToken, request, remoteAddress, headers)
            }
        }
    }
}

private fun Route.isOnlineIndicatorsRoutes() {
    route("/api/user/info") {
        get("/is-online") {
            // FIXME: still used?
            requireUserId { userService.isOnline(it) }
        }
        get("/are-online") {
            val userIds = call.request.queryParameters.getAll("userId") ?: emptyList()
            call.respond(userService.areOnline(userIds))
        }
    }
}

private fun Route.publicProfileRoutes() {
    route("/api/user/info") {
        get("/game-ratings") {
            requireUserId { userProfileAnalyticsService.fetchGameRatings(it) }
        }
        route("/puzzles") {
            route("/stats") {
                get("/rating/{userId}") {
                    requireUserId { userProfileAnalyticsService.fetchPuzzleStatsRating(it) }
                }
                get("/numbers/{userId}") {
                    requireUserId { userProfileAnalyticsService.fetchPuzzleStatsNumberPerOutcome(it) }
                }
                get("/summary/{userId}") {
                    requireUserId { userProfileAnalyticsService.fetchPuzzleStatsSummary(it) }
                }
            }
        }
    }
}

private fun Route.globalStatsRoutes() {
    route("/api/user/info") {
        get("/global") {
            call.respond(globalAnalyticsService.fetchGlobalUserStats())
        }
        get("/puzzles/stats/leaderboard") {
            call.respond(globalAnalyticsService.fetchPuzzleLeaderboard(20))
        }
        get("/puzzles/stats/leaderboard-last-12-months") {
            call.respond(globalAnalyticsService.fetchPuzzleLeaderboardLast12Months(20))
        }
    }
}

private fun Route.userSettingsRoutes() {
    route("/api/user/settings") {
        get("/profile") {
            requireAuthentication { verifiedToken ->
                userService.fetchProfileSettings(verifiedToken.userId)
            }
        }
        post("/profile") {
            requireAuthenticationWithBody<ProfileSettingsDto> { verifiedToken, request ->
                userService.updateProfileSettings(verifiedToken.userId, request)
            }
        }
        get("/notifications") {
            requireAuthentication { verifiedToken ->
                userService.fetchNotificationsSettings(verifiedToken.userId)
            }
        }
        post("/notifications") {
            requireAuthenticationWithBody<NotificationsSettingsDto> { verifiedToken, request ->
                userService.updateNotificationsSettings(verifiedToken.userId, request)
            }
        }
        get("/email-address") {
            requireAuthentication { verifiedToken ->
                userService.fetchEmailAddressSettings(verifiedToken.userId)
            }
        }
        get("/sessions") {
            requireAuthentication { verifiedToken ->
                val limit =
                    call.request.queryParameters["limit"]
                        ?.toIntOrNull()
                        ?.coerceAtLeast(1)
                        ?: 10
                val offset =
                    call.request.queryParameters["offset"]
                        ?.toIntOrNull()
                        ?.coerceAtLeast(0)
                        ?: 0

                userService.fetchUserSessions(verifiedToken.userId, limit, offset)
            }
        }
        post("/sessions/delete") {
            requireAuthenticationWithBody<DeleteUserSessionsRequest> { verifiedToken, request ->
                userService.deleteUserSessions(verifiedToken.userId, request)
            }
        }
        post("/sessions/delete-all") {
            requireAuthentication { verifiedToken ->
                userService.deleteAllUserSessions(verifiedToken.userId)
            }
        }
    }
}

private fun Route.passwordRecoveryRoutes() {
    route("/api/user/password/recovery") {
        post("/attempt") {
            val request = call.receive<AttemptPasswordRecoveryRequest>()
            call.respond(Created, userService.attemptPasswordRecovery(request))
        }
        post("/finalize") {
            handleValidatedResponse<FinalizePasswordRecoveryRequest, Unit> { request ->
                userService.finalizePasswordRecovery(request)
            }
        }
    }
}

private fun Route.contactFormRoutes() {
    route("/api/contact/form") {
        post("/submit") {
            requireIdentificationWithBody<ContactFormRequest> { verifiedToken, request ->
                userService.submitContact(request, verifiedToken.userId())
            }
        }
    }
}
