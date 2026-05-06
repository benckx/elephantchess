package io.elephantchess.servicelayer.services

import io.elephantchess.servicelayer.model.Pod
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import kotlin.test.Test
import kotlin.test.assertEquals

class ShardingTest {

    private val logger = KotlinLogging.logger {}

    @Test
    fun shardingTest01() {
        val nbrOfPods = 4
        val map = (0 until nbrOfPods).toList().associate { Pod(it, nbrOfPods) to 0 }.toMutableMap()

        map.forEach { (pod, count) ->
            logger.info { "$pod -> $count" }
        }

        val n = 10_000
        val shard = n / nbrOfPods.toDouble()
        val margin = shard * .05

        logger.debug { "total: $n" }
        logger.debug { "shard: $shard" }
        logger.debug { "margin: $margin" }

        repeat(n) {
            val id = randomAlphanumeric(12)
            val pods = map.keys.filter { pod -> pod.mustProcess(id) }
            assertEquals(1, pods.size)

            val pod = pods.first()
            map[pod] = map[pod]!! + 1
        }

        map.forEach { (pod, count) ->
            logger.info { "$pod -> $count" }
        }

        // check total
        val sum = map.values.toList().sum()
        assertEquals(n, sum)

        // check distribution
        map.forEach { (_, count) ->
            assertEquals(shard, count.toDouble(), margin)
        }
    }

}
