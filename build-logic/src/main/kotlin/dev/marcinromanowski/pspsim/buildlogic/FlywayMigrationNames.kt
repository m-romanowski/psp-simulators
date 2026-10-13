package dev.marcinromanowski.pspsim.buildlogic

import java.math.BigInteger

sealed interface FlywayMigrationName {

    class Versioned(val rawVersion: String, val components: List<BigInteger>) : FlywayMigrationName

    class Repeatable : FlywayMigrationName

    class NotAppliedByFlyway(val reason: String) : FlywayMigrationName
}

object FlywayMigrationNames {

    private const val VERSIONED_PREFIX = "V"

    private const val REPEATABLE_PREFIX = "R"

    private const val SEPARATOR = "__"

    private const val SUFFIX = ".sql"

    private val componentBoundary = Regex("\\.(?=\\d)")

    fun read(fileName: String): FlywayMigrationName {
        if (!fileName.endsWith(SUFFIX, ignoreCase = true)) {
            return FlywayMigrationName.NotAppliedByFlyway(
                "its name does not end in " +
                    SUFFIX +
                    ", so Flyway never offers it to a migration resolver"
            )
        }
        val body = fileName.dropLast(SUFFIX.length)
        val prefix = body.take(1)
        if (prefix != VERSIONED_PREFIX && prefix != REPEATABLE_PREFIX) {
            return FlywayMigrationName.NotAppliedByFlyway(
                "it opens with neither " +
                    VERSIONED_PREFIX +
                    " nor " +
                    REPEATABLE_PREFIX +
                    ", the two prefixes this build leaves at their defaults"
            )
        }
        val afterPrefix = body.drop(prefix.length)
        val separatorAt = afterPrefix.indexOf(SEPARATOR)
        if (separatorAt < 0) {
            return FlywayMigrationName.NotAppliedByFlyway(
                "it carries no " + SEPARATOR + " between its version and its description"
            )
        }
        val rawVersion = afterPrefix.take(separatorAt)
        if (prefix == REPEATABLE_PREFIX) {
            if (rawVersion.isEmpty()) {
                return FlywayMigrationName.Repeatable()
            }
            return FlywayMigrationName.NotAppliedByFlyway(
                "a repeatable migration carries no version, and this one puts " +
                    rawVersion +
                    " before the separator"
            )
        }
        if (rawVersion.isEmpty()) {
            return FlywayMigrationName.NotAppliedByFlyway(
                "it names no version between " + VERSIONED_PREFIX + " and " + SEPARATOR
            )
        }
        val components =
            componentsFlywayComparesBy(rawVersion)
                ?: return FlywayMigrationName.NotAppliedByFlyway(
                    "Flyway reads a version as numbers separated by . or _, and " +
                        rawVersion +
                        " is not one"
                )
        return FlywayMigrationName.Versioned(rawVersion, components)
    }

    fun componentsFlywayComparesBy(rawVersion: String): List<BigInteger>? {
        val components =
            componentBoundary.split(rawVersion.replace('_', '.')).map { component ->
                component.toBigIntegerOrNull() ?: return null
            }
        val lastThatCounts = components.indexOfLast { it.signum() != 0 }
        return components.subList(0, maxOf(lastThatCounts + 1, 1))
    }

    fun asOneVersion(components: List<BigInteger>): String = components.joinToString(".")
}
