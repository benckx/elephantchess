package io.elephantchess.utils

import io.elephantchess.utils.ResourceUtils.resourceAsLines
import java.time.LocalDate
import java.time.YearMonth

private val badWords by lazy {
    resourceAsLines("/bad-words.txt")
        .map { it.trim() }
        .filterNot { it.isBlank() }
        .filterNot { it.startsWith("#") }
}

private val chars by lazy { "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" }

/**
 * An item is more likely to be selected the more occurrences it has
 */
fun <T> selectByProbability(elements: List<T>, elementOccurrences: (T) -> Int): T {
    data class SpaceInterval<T>(val start: Int, val end: Int, val entry: T)

    if (elements.size > 1) {
        var i = 0
        val intervals = mutableListOf<SpaceInterval<T>>()
        elements.forEach { element ->
            val occurrences = elementOccurrences(element)
            intervals += SpaceInterval(i, i + occurrences, element)
            i += occurrences
        }
        if (i >= 1) {
            val randomIndex = (0 until i).random()
            intervals
                .find { it.start <= randomIndex && randomIndex < it.end }
                ?.let { return it.entry }
        }
    }

    return elements.first()
}

/**
 * "safe" in the sense that they don't include offensive words
 */
fun safeRandomAlphaNumericString(min: Int, max: Int): String {
    val length = (min until max).random()

    fun generate() = (1..length).map { chars.random() }.joinToString("")

    fun mayBeOffensive(generated: String): Boolean {
        return badWords.any { badWord -> generated.lowercase().contains(badWord.lowercase()) }
    }

    var result = generate()
    while (mayBeOffensive(result)) {
        result = generate()
    }

    return result
}


fun cropToFirstNWords(text: String, maxWords: Int): String {
    val words = text.split(Regex("\\s+"))
    if (words.size <= maxWords) {
        return text
    }

    // Take first maxWords words
    val croppedWords = words.take(maxWords)
    val croppedText = croppedWords.joinToString(" ")

    // Find the last period in the cropped text
    val lastPeriodIndex = croppedText.lastIndexOf('.')

    return if (lastPeriodIndex > 0) {
        // Cut at the last period found
        croppedText.substring(0, lastPeriodIndex + 1)
    } else {
        // No period found, return the cropped text as is
        croppedText
    }
}

fun stripHtml(text: String): String {
    return text
        .replace(Regex("<[^>]*>"), "")
        .replace("\"", "'")
}

/**
 * Returns a list of [LocalDate] between firstDay and lastDay inclusive
 */
fun rangeOfDays(firstDay: LocalDate, lastDay: LocalDate): List<LocalDate> {
    val result = mutableListOf<LocalDate>()
    var current = firstDay
    while (current.isBefore(lastDay) || current == lastDay) {
        result += current
        current = current.plusDays(1L)
    }
    return result.toList()
}

/**
 * Returns a list of [YearMonth] between firstMonth and lastMonth inclusive
 */
fun rangeOfYearMonths(firstMonth: YearMonth, lastMonth: YearMonth): List<YearMonth> {
    val result = mutableListOf<YearMonth>()
    var current = firstMonth
    while (current.isBefore(lastMonth) || current == lastMonth) {
        result += current
        current = current.plusMonths(1L)
    }
    return result.toList()
}

fun isChineseText(s: String): Boolean {
    return s.matches(Regex("^[\\u4E00-\\u9FFF]+$"))
}

fun formatWithChineseName(name: String, chineseName: String?): String {
    return if (chineseName.isNullOrBlank()) {
        name
    } else {
        "$name ($chineseName)"
    }
}

fun generateNameVariations(vararg names: String): List<String> {
    return names.flatMap { name -> generateNameVariations(name) }.distinct()
}

fun generateNameVariations(name: String): List<String> {
    fun splitCamelCase(name: String): String {
        return name.replace(Regex("([A-Z])"), " $1").trim()
    }

    fun <T> List<T>.permutations(): List<List<T>> {
        if (size <= 1) return listOf(this)

        val result = mutableListOf<List<T>>()
        for (i in indices) {
            val element = this[i]
            val remaining = this.toMutableList().apply { removeAt(i) }
            for (permutation in remaining.permutations()) {
                result.add(listOf(element) + permutation)
            }
        }
        return result
    }


    val toSplit = if (name.trim().contains(" ")) {
        name.trim()
    } else {
        splitCamelCase(name.trim())
    }
    val split = toSplit.split(" ").map { it.trim() }.filterNot { it.isBlank() }
    val variations = split.permutations()
        .flatMap { permutation ->
            listOf(
                permutation.joinToString(""),
                permutation.joinToString(" ")
            )
        }
        .map { it.lowercase() }

    return variations.distinct()
}
