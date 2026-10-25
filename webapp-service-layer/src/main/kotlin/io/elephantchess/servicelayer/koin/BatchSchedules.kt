package io.elephantchess.servicelayer.koin

import io.elephantchess.servicelayer.batch.ArchiveOldGuestsBatch
import io.elephantchess.servicelayer.batch.ArchiveOldPageViewsBatch
import io.elephantchess.servicelayer.batch.AutoCancelCreatedGamesFromOfflineUsersBatch
import io.elephantchess.servicelayer.batch.AutoResignIdleBotGamesBatch
import io.elephantchess.servicelayer.batch.BackgroundGameAnalysisBatch
import io.elephantchess.servicelayer.batch.CheckEmailListVerifyCreditBatch
import io.elephantchess.servicelayer.batch.DetectSockPuppetGamesBatch
import io.elephantchess.servicelayer.batch.FetchDailyUsersMetricsBatch
import io.elephantchess.servicelayer.batch.FetchMinutesUsersMetricsBatch
import io.elephantchess.servicelayer.batch.FetchUserSessionGeographicDataBatch
import io.elephantchess.servicelayer.batch.FlagGamesBatch
import io.elephantchess.servicelayer.batch.NotifyContentSectionFeedbackBatch
import io.elephantchess.servicelayer.batch.PreAnalysisCleanUpBatch
import io.elephantchess.servicelayer.batch.SendOutNewslettersBatch
import io.elephantchess.servicelayer.batch.VerifyEmailsBatch
import io.elephantchess.servicelayer.batch.definitions.ShardedBatchSchedule
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatchSchedule
import org.koin.dsl.module
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal fun batchSchedulesModule() =
    module {
        single {
            listOf(
                ShardedBatchSchedule(
                    get<PreAnalysisCleanUpBatch>(),
                    period = 6.hours,
                ),
                ShardedBatchSchedule(
                    get<FetchUserSessionGeographicDataBatch>(),
                    period = 15.minutes,
                ),
                SinglePodBatchSchedule(
                    get<DetectSockPuppetGamesBatch>(),
                    period = 30.minutes,
                    delay = 90.seconds,
                    podNumber = 0,
                ),
                ShardedBatchSchedule(
                    get<FlagGamesBatch>(),
                    period = 5.seconds,
                    delay = 10.seconds,
                ),
                ShardedBatchSchedule(
                    get<AutoCancelCreatedGamesFromOfflineUsersBatch>(),
                    period = 15.minutes,
                    delay = 2.minutes,
                ),
                ShardedBatchSchedule(
                    get<AutoResignIdleBotGamesBatch>(),
                    period = 15.minutes,
                    delay = 4.minutes,
                ),
                ShardedBatchSchedule(
                    get<BackgroundGameAnalysisBatch>(),
                    period = 5.minutes,
                    delay = 1.minutes,
                ),
                SinglePodBatchSchedule(
                    get<FetchMinutesUsersMetricsBatch>(),
                    period = 5.minutes,
                    delay = 5.seconds,
                    podNumber = 0,
                ),
                SinglePodBatchSchedule(
                    get<FetchDailyUsersMetricsBatch>(),
                    period = 6.hours,
                    delay = 15.minutes,
                    podNumber = 0,
                ),
                SinglePodBatchSchedule(
                    get<ArchiveOldGuestsBatch>(),
                    period = 6.hours,
                    delay = 30.minutes,
                    podNumber = 1,
                ),
                SinglePodBatchSchedule(
                    get<ArchiveOldPageViewsBatch>(),
                    period = 6.hours,
                    delay = 45.minutes,
                    podNumber = 0,
                ),
                SinglePodBatchSchedule(
                    get<SendOutNewslettersBatch>(),
                    period = 5.minutes,
                    delay = 3.minutes,
                    podNumber = 0,
                ),
                SinglePodBatchSchedule(
                    get<NotifyContentSectionFeedbackBatch>(),
                    period = 4.hours,
                    delay = 1.hours,
                    podNumber = 1,
                ),
                SinglePodBatchSchedule(
                    get<CheckEmailListVerifyCreditBatch>(),
                    period = 48.hours,
                    delay = 30.seconds,
                    podNumber = 1,
                ),
                SinglePodBatchSchedule(
                    get<VerifyEmailsBatch>(),
                    period = 48.hours,
                    delay = 12.hours,
                    podNumber = 1,
                ),
            )
        }
    }
