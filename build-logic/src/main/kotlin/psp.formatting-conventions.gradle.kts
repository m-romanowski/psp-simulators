import com.diffplug.spotless.LineEnding

plugins {
    id("com.diffplug.spotless")
}

val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")

val theDirectoriesGitIgnoresOrThisBuildGenerates =
    arrayOf(
        ".git",
        ".claude",
        ".tmp",
        ".idea",
        "**/.gradle",
        "**/node_modules",
        "**/build",
        "**/bin",
        "**/out",
    )

val pathsGitIgnoresOrThisBuildGenerates =
    theDirectoriesGitIgnoresOrThisBuildGenerates +
        theDirectoriesGitIgnoresOrThisBuildGenerates.map { "$it/**" }

val theLineEndingEveryFileThisBuildFormatsIsStoredWithNamedHereRatherThanReadFromGitAttributes =
    LineEnding.UNIX

spotless {
    lineEndings =
        theLineEndingEveryFileThisBuildFormatsIsStoredWithNamedHereRatherThanReadFromGitAttributes

    java {
        target("**/*.java")
        targetExclude(*pathsGitIgnoresOrThisBuildGenerates)
        googleJavaFormat(versions.findVersion("googleJavaFormat").get().requiredVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts", "**/*.kt")
        targetExclude(*pathsGitIgnoresOrThisBuildGenerates)
        ktfmt(versions.findVersion("ktfmt").get().requiredVersion).kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("**/*.md", "**/*.yaml", "**/*.yml", "**/*.sql", "**/*.toml")
        targetExclude(*pathsGitIgnoresOrThisBuildGenerates)
        trimTrailingWhitespace()
        endWithNewline()
    }
}
