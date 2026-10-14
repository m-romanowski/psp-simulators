import dev.marcinromanowski.pspsim.buildlogic.VerifyCaughtExceptionNames
import dev.marcinromanowski.pspsim.buildlogic.VerifyEveryFormSendsWhatItCarries
import dev.marcinromanowski.pspsim.buildlogic.VerifyMigrationNumbers
import dev.marcinromanowski.pspsim.buildlogic.VerifyNoBrowserValidation
import dev.marcinromanowski.pspsim.buildlogic.VerifyNoComments
import dev.marcinromanowski.pspsim.buildlogic.VerifyNoMergeConflicts
import dev.marcinromanowski.pspsim.buildlogic.VerifyTaskDescriptions

plugins {
    java
}

val commentsAreBanned = true

val pathsGitIgnoresOrTracksAsBinary =
    listOf(
        ".git",
        ".git/**",
        ".claude/**",
        ".tmp/**",
        ".idea/**",
        ".vscode/**",
        ".settings/**",
        "**/.gradle/**",
        "**/node_modules/**",
        "**/build/**",
        "**/bin/**",
        "**/out/**",
        "**/*.class",
        "**/*.jar",
        "**/*.log",
        "**/.DS_Store",
    )

val theGitignoreGradlesOwnDefaultExcludesDropFromAFileTree = file(".gitignore")

val conflictFreeSources =
    files(
        fileTree(layout.projectDirectory) { exclude(pathsGitIgnoresOrTracksAsBinary) },
        theGitignoreGradlesOwnDefaultExcludesDropFromAFileTree,
    )

val commentFreeSources =
    files(
        fileTree(layout.projectDirectory) {
            include(
                "**/*.java",
                "**/*.kt",
                "**/*.kts",
                "**/*.js",
                "**/*.mjs",
                "**/templates/**/*.html",
            )
            exclude(pathsGitIgnoresOrTracksAsBinary)
        },
        fileTree("src/main/resources/db/migration") { include("**/*.sql") },
    )

val everyTemplateOfThisUi =
    fileTree(layout.projectDirectory.dir("src/main/resources/templates")) {
        include("**/*.html")
        exclude(pathsGitIgnoresOrTracksAsBinary)
    }

val everyJavaSourceInThisTree =
    fileTree(layout.projectDirectory) {
        include("**/*.java")
        exclude(pathsGitIgnoresOrTracksAsBinary)
    }

val flywayLocation = layout.projectDirectory.dir("src/main/resources/db/migration")

val verifyMigrationNumbers =
    tasks.register<VerifyMigrationNumbers>("verifyMigrationNumbers") {
        group = "verification"
        description =
            "Fails when two migrations claim one version, reading a version the way Flyway " +
                "reads it rather than the way the filename spells it. The scan is every file " +
                "under the location `spring.flyway.locations` names, subdirectories included."

        migrations.from(fileTree(flywayLocation))
        migrationDirectory = flywayLocation
        outputs.upToDateWhen { false }
    }

val verifyNoMergeConflicts =
    tasks.register<VerifyNoMergeConflicts>("verifyNoMergeConflicts") {
        group = "verification"
        description =
            "Fails when a file in this working tree carries a git conflict marker, which no " +
                "other check in this build looks at. The scan is the whole tree less what git " +
                "ignores and the binaries it tracks."

        scanned.from(conflictFreeSources)
        outputs.upToDateWhen { false }
    }

val verifyCaughtExceptionNames =
    tasks.register<VerifyCaughtExceptionNames>("verifyCaughtExceptionNames") {
        group = "verification"
        description =
            "Fails on a catch clause binding its exception to a name that carries no " +
                "information. Under the comment ban that name is the only statement of what went " +
                "wrong a reader gets, so a clause either explains the failure or binds `_`."

        sources.from(everyJavaSourceInThisTree)
        projectRoot = layout.projectDirectory
        outputs.upToDateWhen { false }
    }

val everyCoreSourceAValueATesterTypedTravelsThrough =
    fileTree(layout.projectDirectory.dir("src/main/java/dev/marcinromanowski/pspsim/kernel")) {
        include("**/*.java")
        exclude(pathsGitIgnoresOrTracksAsBinary)
    }

val everyScreenSourceThatOffersWhatAProviderPublishes =
    fileTree(layout.projectDirectory.dir("src/main/java/dev/marcinromanowski/pspsim/ui")) {
        include("**/*.java")
        exclude(pathsGitIgnoresOrTracksAsBinary)
    }

val verifyNoBrowserValidation =
    tasks.register<VerifyNoBrowserValidation>("verifyNoBrowserValidation") {
        group = "verification"
        description =
            "Fails on a form control of this UI, or a core or screen source, that judges what a " +
                "tester typed before the provider sees it. The provider is this simulator's only " +
                "validator and its refusals are written for a tester to read."

        templates.from(everyTemplateOfThisUi)
        coreSources.from(everyCoreSourceAValueATesterTypedTravelsThrough)
        screenSources.from(everyScreenSourceThatOffersWhatAProviderPublishes)
        projectRoot = layout.projectDirectory
        outputs.upToDateWhen { false }
    }

val verifyEveryFormSendsWhatItCarries =
    tasks.register<VerifyEveryFormSendsWhatItCarries>("verifyEveryFormSendsWhatItCarries") {
        group = "verification"
        description =
            "Fails on a form of this UI submitted by a browser as a query that names a value " +
                "only in the address it submits to, which the browser replaces with the fields " +
                "of the form, so a tester clicking reaches the route without that value."

        templates.from(everyTemplateOfThisUi)
        projectRoot = layout.projectDirectory
        outputs.upToDateWhen { false }
    }

val verifyNoComments =
    tasks.register<VerifyNoComments>("verifyNoComments") {
        group = "verification"
        description =
            "Fails on any comment in Java, Kotlin, Gradle Kotlin, JavaScript, SQL or template sources, " +
                "anywhere in this working tree, the three sibling builds included. A claim about " +
                "behaviour belongs in a test and prose about this system in doc/."

        val banned = commentsAreBanned
        sources.from(commentFreeSources)
        projectRoot = layout.projectDirectory
        onlyIf { banned }
    }

val everyBuildScriptInThisTree =
    files(
        fileTree(layout.projectDirectory) {
            include("**/*.gradle.kts")
            exclude(pathsGitIgnoresOrTracksAsBinary)
        }
    )

val verifyTaskDescriptions =
    tasks.register<VerifyTaskDescriptions>("verifyTaskDescriptions") {
        group = "verification"
        description =
            "Fails when a task description of any build script in this tree says more or " +
                "less than the one line `./gradlew tasks` prints beside it, or when this scan " +
                "could read only part of one."

        buildScripts.from(everyBuildScriptInThisTree)
        projectRoot = layout.projectDirectory
    }

tasks.named("check") {
    dependsOn(
        verifyNoComments,
        verifyNoBrowserValidation,
        verifyEveryFormSendsWhatItCarries,
        verifyCaughtExceptionNames,
        verifyNoMergeConflicts,
        verifyMigrationNumbers,
        verifyTaskDescriptions,
    )
}
