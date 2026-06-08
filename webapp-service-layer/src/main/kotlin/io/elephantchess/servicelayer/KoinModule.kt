package io.elephantchess.servicelayer

import io.elephantchess.config.AppConfig
import io.elephantchess.config.ArgConfig
import io.elephantchess.config.DbConfig
import io.elephantchess.config.loadAppConfig
import io.elephantchess.db.services.*
import io.elephantchess.db.utils.getDslContext
import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.FairyStockfishEngineId
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.engines.protocol.commands.LocalProcessLocator
import io.elephantchess.servicelayer.batch.*
import io.elephantchess.servicelayer.batch.definitions.BatchSchedule
import io.elephantchess.servicelayer.batch.definitions.BatchesScheduler
import io.elephantchess.servicelayer.clients.ApiLayerClient
import io.elephantchess.servicelayer.clients.DigitalOceanSpacesClient
import io.elephantchess.servicelayer.clients.DiscordClient
import io.elephantchess.servicelayer.clients.EmailListVerifyClient
import io.elephantchess.servicelayer.services.*
import io.elephantchess.servicelayer.services.admin.*
import io.elephantchess.servicelayer.services.resolvers.ContactLinkTagResolver
import io.elephantchess.servicelayer.services.resolvers.MailFragmentResolver
import io.elephantchess.servicelayer.services.resolvers.UpdateMailSettingsTagResolver
import io.elephantchess.servicelayer.utils.DockerizedProcessLocator
import io.elephantchess.servicelayer.utils.ShutdownHandler
import io.elephantchess.servicelayer.utils.ops.singleAuto
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jooq.DSLContext
import org.koin.dsl.module
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val kLogger = KotlinLogging.logger {}

fun serviceLayerModule(
    argConfig: ArgConfig,
    eagerAllowed: Boolean = false,
    dslBuilder: (DbConfig) -> DSLContext = { getDslContext(it) },
    enginesPool: EnginePool? = null,
) = module {
    val appConfig = loadAppConfig(argConfig)

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { appConfig }
    single { appConfig.dbConfig }
    single { dslBuilder(get()) }
    single { enginesPool ?: buildDefaultEnginePool(get()) }
    includes(technicalModule(eagerAllowed))
    includes(daoModule())
    includes(applicativeModule(eagerAllowed))
}

private fun daoModule() = module {
    singleAuto<AnalysisDaoService>()
    singleAuto<ChatMessageDaoService>()
    singleAuto<ContentSectionVoteDaoService>()
    singleAuto<DatabaseAdminDaoService>()
    singleAuto<DiscordGameNotificationDaoService>()
    singleAuto<EmailVerificationDaoService>()
    singleAuto<EngineCacheDaoService>()
    singleAuto<GameChatTypingStatusDaoService>()
    singleAuto<KofiEventDaoService>()
    singleAuto<MoveAnalysisDaoService>()
    singleAuto<NewsletterDaoService>()
    singleAuto<OpeningRepositoryCacheDaoService>()
    singleAuto<PasswordRecoveryAttemptsDaoService>()
    singleAuto<PageViewEventDaoService>()
    singleAuto<PlayerVsBotGameDaoService>()
    singleAuto<PlayerVsPlayerGameDaoService>()
    singleAuto<PodDaoService>()
    singleAuto<PuzzleDaoService>()
    singleAuto<PuzzleResultDaoService>()
    singleAuto<ReferenceEventDaoService>()
    singleAuto<ReferenceGameDaoService>()
    singleAuto<ReferencePlayerDaoService>()
    singleAuto<SettingPreferenceEventDaoService>()
    singleAuto<SevenKingdomsGameDaoService>()
    singleAuto<ThrownExceptionDaoService>()
    singleAuto<UpcomingEventDaoService>()
    singleAuto<UserDaoService>()
    singleAuto<UserSessionDaoService>()
    singleAuto<UserStatsDaoService>()
    singleAuto<UtmMediumClickDaoService>()
}

private fun batchModule() = module {
    singleAuto<PreAnalysisCleanUpBatch>()
    singleAuto<FetchUserSessionGeographicDataBatch>()
    singleAuto<FlagGamesBatch>()
    singleAuto<AutoCancelCreatedGamesFromOfflineUsersBatch>()
    singleAuto<AutoResignIdleBotGamesBatch>()
    singleAuto<FetchMinutesUsersMetricsBatch>()
    singleAuto<FetchDailyUsersMetricsBatch>()
    singleAuto<SendOutNewslettersBatch>()
    singleAuto<CheckEmailListVerifyCreditBatch>()
    singleAuto<VerifyEmailsBatch>()
    singleAuto<BackgroundGameAnalysisBatch>()
    single {
        listOf(
            BatchSchedule(get<PreAnalysisCleanUpBatch>(), period = 6.hours),
            BatchSchedule(get<FetchUserSessionGeographicDataBatch>(), period = 15.minutes),
            BatchSchedule(get<FlagGamesBatch>(), period = 5.seconds, delay = 10.seconds),
            BatchSchedule(get<AutoCancelCreatedGamesFromOfflineUsersBatch>(), period = 15.minutes, delay = 2.minutes),
            BatchSchedule(get<AutoResignIdleBotGamesBatch>(), period = 15.minutes, delay = 4.minutes),
            BatchSchedule(get<FetchMinutesUsersMetricsBatch>(), period = 5.minutes, delay = 5.seconds),
            BatchSchedule(get<FetchDailyUsersMetricsBatch>(), period = 6.hours, delay = 15.minutes),
            BatchSchedule(get<SendOutNewslettersBatch>(), period = 5.minutes, delay = 3.minutes),
            BatchSchedule(get<CheckEmailListVerifyCreditBatch>(), period = 48.hours, delay = 30.seconds),
            BatchSchedule(get<VerifyEmailsBatch>(), period = 48.hours, delay = 12.hours),
            BatchSchedule(get<BackgroundGameAnalysisBatch>(), period = 10.minutes, delay = 1.minutes),
        )
    }
}

private fun technicalModule(eagerAllowed: Boolean) = module {
    // clients
    singleAuto<ApiLayerClient>()
    singleAuto<DiscordClient>()
    singleAuto<EmailListVerifyClient>()
    singleAuto<DigitalOceanSpacesClient>()

    // others
    singleAuto<BatchesScheduler>(eager = eagerAllowed)
    singleAuto<PageViewEventService>()
    singleAuto<SettingPreferenceEventService>()
    singleAuto<PodService>(eager = eagerAllowed)
    singleAuto<TokenManager>(eager = eagerAllowed)
    singleAuto<DiscordService>()
    singleAuto<ExceptionService>()
    includes(batchModule())

    // emails
    single {
        val appConfig = get<AppConfig>()
        MailTemplateRender(
            baseTagResolvers = listOf(
                UpdateMailSettingsTagResolver(appConfig),
                ContactLinkTagResolver(appConfig),
                MailFragmentResolver("email_css"),
            )
        )
    }
    singleAuto<MailRenderer>()
    singleAuto<MailService>()
    singleAuto<KofiService>()

    // shutdown handler
    singleAuto<ShutdownHandler>(eager = eagerAllowed)
}

private fun applicativeModule(eagerAllowed: Boolean) = module {
    // caching
    singleAuto<UserCache>()

    // play games
    singleAuto<PlayerVsPlayerGameService>()
    singleAuto<PlayerVsBotGameService>()
    singleAuto<SevenKingdomsGameService>()

    // puzzles
    singleAuto<PuzzleCache>(eager = eagerAllowed)
    singleAuto<PuzzleService>(eager = eagerAllowed)

    // game data
    singleAuto<GameDataService>()
    singleAuto<DatabaseService>()

    // analysis and engine
    singleAuto<AnalysisService>()
    singleAuto<OpeningService>()
    singleAuto<EngineService>()
    singleAuto<EngineCacheService>()

    // users
    singleAuto<ContentSectionFeedbackService>()
    singleAuto<UserService>(eager = eagerAllowed)
    singleAuto<UserProfileAnalyticsService>()
    singleAuto<GlobalAnalyticsService>(eager = eagerAllowed)
    singleAuto<UserSessionService>()

    // lobby
    singleAuto<LobbyService>()

    // admin
    singleAuto<AdminOverviewService>()
    singleAuto<AdminFeedService>()
    singleAuto<AdminAnalyticsService>()
    singleAuto<AdminUserSessionService>()
    singleAuto<AdminAnalysisService>()
    singleAuto<AdminChatService>()
    singleAuto<AdminDatabaseSearchService>()
    singleAuto<AdminPasswordRecoveryService>()
    singleAuto<AdminPostgresService>()
    singleAuto<AdminDatabaseService>()
    singleAuto<AdminExceptionService>()
    singleAuto<AdminNewsletterService>()
    singleAuto<AdminUpcomingEventsService>()
}

private fun buildDefaultEnginePool(appConfig: AppConfig): EnginePool {
    val osName = System.getProperty("os.name")
    kLogger.info { "OS name: $osName" }
    val isMacOs = osName.startsWith("Mac OS")
    if (isMacOs) kLogger.warn { "engines are disabled on Mac OS" }
    val processLocator = if (appConfig.isDockerized) DockerizedProcessLocator else LocalProcessLocator
    val configMap = if (!isMacOs && appConfig.isEnginePoolEnabled) {
        mapOf(
            PikafishEngineId to EngineConfig(
                version = appConfig.pikafishVersion,
                poolSize = 1,
                numberOfThreads = appConfig.enginesThreads
            ),
            FairyStockfishEngineId to EngineConfig(
                version = appConfig.fairyStockfishVersion,
                poolSize = 1,
                numberOfThreads = appConfig.enginesThreads
            )
        )
    } else {
        mapOf()
    }

    return EnginePool(
        configMap = configMap,
        executor = Executors.newVirtualThreadPerTaskExecutor(),
        engineProcessLocator = processLocator,
    )
}
