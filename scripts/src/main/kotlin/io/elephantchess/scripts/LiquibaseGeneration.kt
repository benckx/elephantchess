package io.elephantchess.scripts

import java.io.File

/**
 * Remove the <sql></sql> tags from the liquibase-changelog.xml file,
 * in order to make a Liquibase file fit for DB code generation.
 * Also excludes entire changesets that are blacklisted.
 */
fun main() {
    val path = "webapp-dao-migration/src/main/resources"
    val original = File("$path/liquibase-changelog.xml")
    val destination = File("$path/liquibase-changelog-generation.xml")

    // Blacklist of changeset IDs to exclude from generation
    val blacklistedChangesets = setOf("0070", "0080", "0085", "0087")

    var isInsideSqlTag = false
    var isInsideBlacklistedChangeset = false
    val output = mutableListOf<String>()

    original.readLines().forEach { line ->
        // Check if we're entering a blacklisted changeset
        if (line.contains("<changeSet")) {
            val isBlacklisted = blacklistedChangesets.any { id ->
                line.contains("id=\"$id\"")
            }
            if (isBlacklisted) {
                isInsideBlacklistedChangeset = true
                return@forEach
            }
        }

        // Check if we're exiting a blacklisted changeset
        if (isInsideBlacklistedChangeset && line.contains("</changeSet>")) {
            isInsideBlacklistedChangeset = false
            return@forEach
        }

        // Skip lines inside blacklisted changesets
        if (isInsideBlacklistedChangeset) {
            return@forEach
        }

        // Handle SQL tags
        if (line.contains("<sql>")) {
            isInsideSqlTag = true
        } else if (line.contains("</sql>")) {
            isInsideSqlTag = false
        } else if (!isInsideSqlTag) {
            output.add(line)
        }
    }

    destination.delete()
    destination.writeText(output.joinToString("\n") + "\n")
}
