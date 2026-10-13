import dev.marcinromanowski.pspsim.buildlogic.ExclusionBudget
import dev.marcinromanowski.pspsim.buildlogic.MutationScope
import dev.marcinromanowski.pspsim.buildlogic.PitInvocation
import dev.marcinromanowski.pspsim.buildlogic.VerifyMutationScope

plugins {
    java
    id("info.solidsoft.pitest")
}

val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")

pitest {
    pitestVersion = versions.findVersion("pitest").get().requiredVersion
    junit5PluginVersion = versions.findVersion("pitestJunit5").get().requiredVersion
    targetClasses = MutationScope.mutationTargetPackages.map { "$it.*" }.toSet()
    targetTests = setOf("dev.marcinromanowski.pspsim.*")
    excludedTestClasses = setOf("dev.marcinromanowski.pspsim.*IntegrationTest")
    excludedClasses =
        MutationScope.mutationAdapterExclusions + MutationScope.mutationWiringExclusions
    mutationThreshold = 90
    failWhenNoMutations = false
    timestampedReports = false
    outputFormats = setOf("XML", "HTML")
    threads = 2
    useClasspathFile = true
}

val configuredMutationTargets = pitest.targetClasses.get()

val configuredMutationExclusions = pitest.excludedClasses.getOrElse(emptySet())

val recordedPitInvocation = layout.buildDirectory.file("tmp/pitest-invocation.txt")

val theFormattingTasksThisGateWillNotDeleteAReportUnderneath =
    listOf("spotlessJava", "spotlessKotlinGradle", "spotlessMisc")

tasks.named<JavaExec>("pitest") {
    val reportDirectory = layout.buildDirectory.dir("reports/pitest")
    val invocation = recordedPitInvocation
    outputs.file(invocation)
    mustRunAfter(theFormattingTasksThisGateWillNotDeleteAReportUnderneath)
    doFirst { reportDirectory.get().asFile.deleteRecursively() }
    doLast(
        Action<Task> {
            val pit = this as JavaExec
            val target = invocation.get().asFile
            target.parentFile.mkdirs()
            target.writeText(
                PitInvocation.recordOf(
                    executable = pit.javaLauncher.get().executablePath.asFile.absolutePath,
                    mainClass = pit.mainClass.get(),
                    jvmArguments = pit.jvmArgs.orEmpty(),
                    classpath = pit.classpath.files.map { it.absolutePath },
                    arguments = pit.args.orEmpty(),
                )
            )
        }
    )
}

val verifyMutationScope =
    tasks.register<VerifyMutationScope>("verifyMutationScope") {
        group = "verification"
        description =
            "Fails when the threshold is not measuring the domain packages, when a mutant PIT " +
                "never evaluated is credited as detected and a re-run over its class alone still " +
                "reaches no verdict, or when PIT returns a status `MutationStatus` holds no " +
                "verdict for."

        dependsOn(tasks.named("pitest"))

        mutationsReport = layout.buildDirectory.file("reports/pitest/mutations.xml")
        retryReportDirectory = layout.buildDirectory.dir("reports/pitest-retry")
        pitInvocation = recordedPitInvocation
        compiledMainClasses = layout.buildDirectory.dir("classes/java/main")
        targetPackages = MutationScope.mutationTargetPackages
        targetGlobs = configuredMutationTargets
        excludedGlobs = configuredMutationExclusions

        outputs.upToDateWhen { false }
    }

val exclusionBudgetChecker = "dev.marcinromanowski.pspsim.architecture.ExclusionBranchBudget"

val verifyExclusionBudget =
    tasks.register<JavaExec>("verifyExclusionBudget") {
        group = "verification"
        description =
            "Fails when the classes `pitest.excludedClasses` hides from mutation testing carry " +
                "more conditional jumps than that family carried when its ratchet was measured, " +
                "or when one of their methods is denser than the densest that family held."

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = exclusionBudgetChecker
        javaLauncher = javaToolchains.launcherFor(java.toolchain)

        val compiledMainClasses = layout.buildDirectory.dir("classes/java/main")
        val excludedGlobs = configuredMutationExclusions
        val budgets = MutationScope.mutationExclusionBudgets

        inputs.dir(compiledMainClasses)
        inputs.property("budgets", budgets.toString())

        doFirst {
            (this as JavaExec).setArgs(
                ExclusionBudget.argumentsFor(
                    compiledMainClasses.get().asFile,
                    excludedGlobs,
                    budgets,
                )
            )
        }
    }

tasks.named("check") { dependsOn(verifyMutationScope, verifyExclusionBudget) }
