package io.elephantchess.utils

object SequenceUtils {

    fun <T> allCombinationsOfMax(pieces: Collection<T>, maxSize: Int): Set<Set<T>> {
        return allCombinations(pieces.toSet()).filter { it.size <= maxSize }.toSet()
    }

    fun <T> allCombinations(elements: Set<T>): Set<Set<T>> {
        when (elements.size) {
            0 -> return setOf()
            1 -> return elements.map { setOf(it) }.toSet()
            2 -> return elements.flatMap { e1 -> elements.map { e2 -> setOf(e1, e2) } }.toSet()
            else -> {
                val first = elements.first()
                val rest = elements - first
                val combinations = allCombinations(rest)
                return combinations + combinations.map { it + first }
            }
        }
    }

    /**
     * Elements must be sorted but not necessarily consecutive (i.e. the list may have been filtered)
     */
    fun <T> longestConsecutiveSequence(elements: List<T>, areConsecutive: (T, T) -> Boolean): List<T> {
        val seq = mutableListOf<T>()
        var maxSeq = mutableListOf<T>()
        for (element in elements) {
            if (seq.isEmpty()) {
                seq.add(element)
            } else {
                if (areConsecutive(seq.last(), element)) {
                    seq.add(element)
                } else {
                    if (seq.size > maxSeq.size) {
                        maxSeq = seq
                    }
                    seq.clear()
                    seq.add(element)
                }
            }
        }

        return maxSeq.toList()
    }

}
