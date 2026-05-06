package io.elephantchess.servicelayer.utils

import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration

abstract class NullableCache<K : Any, V : Any>(
    expireAfterWrite: Duration,
    open val wontResolvePlaceHolder: V,
) {

    private val delegate =
        Cache
            .Builder<K, V>()
            .expireAfterWrite(expireAfterWrite)
            .build()

    abstract suspend fun loader(key: K): V?

    suspend fun get(key: K): V? {
        val fromCache = delegate.get(key) {
            when (val value = loader(key)) {
                null -> wontResolvePlaceHolder
                else -> value
            }
        }

        return if (fromCache == wontResolvePlaceHolder) {
            null
        } else {
            fromCache
        }
    }

}
