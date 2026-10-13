import dev.marcinromanowski.pspsim.buildlogic.CoverageScope
import dev.marcinromanowski.pspsim.buildlogic.SonarRules
import dev.marcinromanowski.pspsim.buildlogic.SuppressedRule
import dev.marcinromanowski.pspsim.buildlogic.VerifySonarSuppressions
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    jacoco
    id("org.sonarqube")
}

val sonarHostUrl = providers.environmentVariable("SONAR_HOST_URL").orElse("http://localhost:9000")

val jacocoXmlReport =
    tasks.jacocoTestReport.flatMap { it.reports.xml.outputLocation }.map { it.asFile.absolutePath }

sonar {
    properties {
        property("sonar.projectKey", "psp-simulators")
        property("sonar.projectName", "PSP Simulators")
        property("sonar.projectVersion", project.version.toString())
        property("sonar.host.url", sonarHostUrl.get())
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
        property("sonar.java.source", java.toolchain.languageVersion.get().toString())
        property("sonar.coverage.jacoco.xmlReportPaths", jacocoXmlReport.get())
        property("sonar.coverage.exclusions", CoverageScope.EXCLUDED_SOURCE_GLOBS.joinToString(","))
        property(
            "sonar.issue.ignore.multicriteria",
            SuppressedRule.entries.joinToString(",") { SonarRules.multicriteriaIdOf(it.ruleKey) },
        )
        SuppressedRule.entries.forEach {
            val id = SonarRules.multicriteriaIdOf(it.ruleKey)
            property("sonar.issue.ignore.multicriteria.$id.ruleKey", it.ruleKey)
            property("sonar.issue.ignore.multicriteria.$id.resourceKey", it.resourceKey)
        }
    }
}

tasks.named("sonar") { dependsOn(tasks.jacocoTestReport) }

val pathsGitIgnoresOrThisBuildGenerates =
    listOf(
        ".git/**",
        ".claude/**",
        ".idea/**",
        "**/.gradle/**",
        "**/build/**",
        "**/bin/**",
        "**/out/**",
    )

val everyBuildScriptInThisTree =
    files(
        fileTree(layout.projectDirectory) {
            include("**/*.gradle.kts")
            exclude(pathsGitIgnoresOrThisBuildGenerates)
        }
    )

val everySourceOfThisBuildsOwnLogic =
    files(
        fileTree(layout.projectDirectory.dir("build-logic/src/main/kotlin")) { include("**/*.kt") }
    )

val everyJavaSourceTheScannerReads =
    files(
        fileTree(layout.projectDirectory) {
            include("**/*.java")
            exclude(pathsGitIgnoresOrThisBuildGenerates)
        }
    )

val verifySonarSuppressions =
    tasks.register<VerifySonarSuppressions>("verifySonarSuppressions") {
        group = "verification"
        description =
            "Fails when a rule switched off in the scanner or a check switched off in the " +
                "compiler carries no recorded answer and no condition that would turn it back " +
                "on, or when this sweep stops reading a form it is for."

        buildScripts.from(everyBuildScriptInThisTree)
        buildLogicSources.from(everySourceOfThisBuildsOwnLogic)
        javaSources.from(everyJavaSourceTheScannerReads)
        projectRoot = layout.projectDirectory
        outputs.upToDateWhen { false }
    }

afterEvaluate {
    val registered = tasks.names.toList()
    val errorproneOptions = tasks.named<JavaCompile>("compileJava").get().options.errorprone
    val severities =
        errorproneOptions.checks.getOrElse(emptyMap()).mapValues { it.value.toString() }
    val switches = errorproneOptions.checkOptions.getOrElse(emptyMap())
    val severityPerCompilation = mutableMapOf<String, String>()
    tasks.withType<JavaCompile>().forEach { compiling ->
        compiling.options.errorprone.checks.getOrElse(emptyMap()).forEach { (check, severity) ->
            severityPerCompilation["${compiling.name}/$check"] = severity.toString()
        }
    }
    verifySonarSuppressions.configure {
        tasksThisBuildRegisters = registered
        settingsTheCompilerWasGiven = severities + switches
        severityPerCheckAndCompilation = severityPerCompilation
    }
}

tasks.named("check") { dependsOn(verifySonarSuppressions) }
