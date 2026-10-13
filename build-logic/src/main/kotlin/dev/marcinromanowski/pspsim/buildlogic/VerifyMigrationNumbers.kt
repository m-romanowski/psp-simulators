package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import java.math.BigInteger
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class VerifyMigrationNumbers : DefaultTask() {

    @get:InputFiles abstract val migrations: ConfigurableFileCollection

    @get:Internal abstract val migrationDirectory: DirectoryProperty

    private val versionOrder =
        Comparator<List<BigInteger>> { left, right ->
            val longest = maxOf(left.size, right.size)
            var verdict = 0
            for (position in 0 until longest) {
                if (verdict == 0) {
                    verdict =
                        left
                            .getOrElse(position) { BigInteger.ZERO }
                            .compareTo(right.getOrElse(position) { BigInteger.ZERO })
                }
            }
            verdict
        }

    @TaskAction
    fun verify() {
        val directory: File = migrationDirectory.get().asFile
        if (!directory.isDirectory) {
            throw GradleException(
                "verifyMigrationNumbers: " +
                    directory.path +
                    " is not a directory that exists, so there was nothing to read and this " +
                    "run proved nothing. `spring.flyway.locations` points Flyway at this " +
                    "location; a gate that agrees with an absent directory is the shape of the " +
                    "conflict scan that named a path nobody had ever created and so scanned " +
                    "neither of the two files it was meant to cover."
            )
        }

        val scanned = migrations.files.sortedBy { it.name }
        if (scanned.isEmpty()) {
            throw GradleException(
                "verifyMigrationNumbers: " +
                    directory.path +
                    " holds no files, so this run compared no versions. A gate that is green " +
                    "over nothing is indistinguishable from a gate that is green over a " +
                    "migration history in which every file claims a version of its own."
            )
        }

        val read = scanned.map { migration ->
            migration.toRelativeString(directory) to FlywayMigrationNames.read(migration.name)
        }

        val unapplied =
            read
                .mapNotNull { (path, verdict) ->
                    if (verdict is FlywayMigrationName.NotAppliedByFlyway) {
                        path + ": " + verdict.reason
                    } else {
                        null
                    }
                }
                .sorted()
        if (unapplied.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "verifyMigrationNumbers: " +
                            unapplied.size +
                            " file(s) under " +
                            directory.path +
                            " are not migrations Flyway will apply:"
                    )
                    unapplied.forEach { appendLine("  - " + it) }
                    append(
                        "Flyway scans this location and its subdirectories, recognises no " +
                            "migration in the name, logs one line at info that nobody is " +
                            "watching and carries on, so the file sits among the migrations " +
                            "looking applied and never runs. Either name it the way Flyway " +
                            "names a migration, or keep it somewhere Flyway is not pointed at."
                    )
                }
            )
        }

        val byVersion = mutableMapOf<List<BigInteger>, MutableList<String>>()
        read.forEach { (path, verdict) ->
            if (verdict is FlywayMigrationName.Versioned) {
                byVersion
                    .getOrPut(verdict.components) { mutableListOf() }
                    .add(path + " declaring " + verdict.rawVersion)
            }
        }

        val claimedTwice = byVersion.filterValues { it.size > 1 }
        if (claimedTwice.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "verifyMigrationNumbers: " +
                            claimedTwice.size +
                            " Flyway version(s) are claimed by more than one of the " +
                            scanned.size +
                            " migration(s) under " +
                            directory.path +
                            ":"
                    )
                    claimedTwice.keys.sortedWith(versionOrder).forEach { components ->
                        appendLine(
                            "  - version " +
                                FlywayMigrationNames.asOneVersion(components) +
                                ": " +
                                claimedTwice.getValue(components).sorted().joinToString(", ")
                        )
                    }
                    append(
                        "Flyway compares versions as numbers and not as the text in the " +
                            "filename: it reads `_` as `.`, reads each component as an integer " +
                            "so a leading zero counts for nothing, and drops trailing zero " +
                            "components, which makes V1, V01, V1.0 and V1_0 one single " +
                            "version. Two branches off one commit each take the next free " +
                            "number, the filenames differ so git merges them in silence, and " +
                            "Flyway is the first thing to object: it refuses the pair when a " +
                            "container starts, so the collision surfaces as every integration " +
                            "test failing at once, five minutes in, looking like a broken " +
                            "application context. Renumber the later one against main as it " +
                            "stands now, and check whether a test names the version."
                    )
                }
            )
        }

        logger.lifecycle(
            "verifyMigrationNumbers: " +
                scanned.size +
                " file(s) under " +
                directory.path +
                " are all migrations Flyway applies, " +
                byVersion.size +
                " of them versioned, and no two of those claim one version."
        )
    }
}
