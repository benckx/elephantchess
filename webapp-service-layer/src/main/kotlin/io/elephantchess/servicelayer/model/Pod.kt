package io.elephantchess.servicelayer.model

import kotlin.math.absoluteValue

data class Pod(
    val index: Int,
    val total: Int,
) {

    fun mustProcess(id: String): Boolean {
        return id.hashCode().absoluteValue % total == index
    }

    override fun toString(): String {
        return "Pod{index=$index, total=$total}"
    }

}
