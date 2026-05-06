package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.KUBERNETES_POD
import io.elephantchess.db.dao.codegen.tables.daos.KubernetesPodDao
import io.elephantchess.db.dao.codegen.tables.pojos.KubernetesPod
import io.elephantchess.db.utils.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock
import kotlin.time.Duration

class PodDaoService(private val dslContext: DSLContext) {

    suspend fun insertOrUpdate(podName: String) {
        dslContext.transactionCoroutine { cfg ->
            val dao = KubernetesPodDao(cfg)
            val now = Clock.System.now()
            val exists = DSL
                .using(cfg)
                .selectFrom(KUBERNETES_POD)
                .where(KUBERNETES_POD.POD_NAME.eq(podName))
                .awaitMappedRecords<KubernetesPod>()
                .isNotEmpty()

            if (!exists) {
                // insert
                val record = KubernetesPod()
                record.podName = podName
                record.entryCreation = now
                record.entryUpdate = now
                dao.insertReactive(record)
            } else {
                // update
                DSL
                    .using(cfg)
                    .update(KUBERNETES_POD)
                    .set(KUBERNETES_POD.ENTRY_UPDATE.fixed(), now)
                    .where(KUBERNETES_POD.POD_NAME.fixed().eq(podName))
                    .awaitExecute()
            }
        }
    }

    suspend fun listAllPodNamesWithin(duration: Duration): List<String> {
        return dslContext
            .selectDistinct(KUBERNETES_POD.POD_NAME)
            .from(KUBERNETES_POD)
            .where(KUBERNETES_POD.ENTRY_UPDATE.isWithin(duration))
            .orderBy(KUBERNETES_POD.POD_NAME)
            .awaitRecords()
            .map { record -> record.value1()!! }
            .toList()
    }

    suspend fun listLastPodNames(limit: Int): List<String> {
        return dslContext
            .select(KUBERNETES_POD.POD_NAME, KUBERNETES_POD.ENTRY_UPDATE)
            .from(KUBERNETES_POD)
            .orderBy(KUBERNETES_POD.ENTRY_UPDATE.desc())
            .limit(limit)
            .awaitRecords()
            .map { record2 -> record2.value1()!! }
            .toList()
    }

}
