package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.tables.daos.SettingPreferenceEventDao
import io.elephantchess.db.dao.codegen.tables.pojos.SettingPreferenceEvent
import io.elephantchess.db.utils.insertReactive
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

class SettingPreferenceEventDaoService(private val dslContext: DSLContext) {

    suspend fun save(record: SettingPreferenceEvent) {
        dslContext.transactionCoroutine { cfg ->
            SettingPreferenceEventDao(cfg).insertReactive(record)
        }
    }

}
