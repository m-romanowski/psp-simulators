package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class VerifyMutationScope : DefaultTask() {

    @get:Internal abstract val mutationsReport: RegularFileProperty

    @get:Internal abstract val retryReportDirectory: DirectoryProperty

    @get:Internal abstract val pitInvocation: RegularFileProperty

    @get:Internal abstract val compiledMainClasses: DirectoryProperty

    @get:Internal abstract val targetPackages: ListProperty<String>

    @get:Internal abstract val targetGlobs: SetProperty<String>

    @get:Internal abstract val excludedGlobs: SetProperty<String>

    @get:Inject protected abstract val execOperations: ExecOperations

    @TaskAction
    fun verify() {
        val domainPackages = targetPackages.get()
        val inScope = targetGlobs.get()
        val notInScope = excludedGlobs.get()

        fun inDomainPackage(className: String): Boolean = domainPackages.any {
            className.startsWith("$it.")
        }

        val report = mutationsReport.get().asFile
        val reportText = if (report.isFile) report.readText() else ""
        val mutatedClasses = MutationReport.mutatedClasses(reportText)

        val mutants = MutationReport.mutantsIn(reportText)
        val statusesByClass = mutants.groupBy({ it.className }, { it.status })

        val statusBreakdown =
            mutants
                .groupingBy { it.status }
                .eachCount()
                .entries
                .sortedBy { it.key }
                .joinToString(", ") { "${it.key} ${it.value}" }
        if (mutants.isNotEmpty()) {
            logger.lifecycle(
                "verifyMutationScope: ${mutants.size} mutants by status: $statusBreakdown."
            )
        }

        val unjudgeable = MutationVerdicts.statusesWithNoVerdictHere(mutants)
        if (unjudgeable.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "PIT returned mutants in ${unjudgeable.size} status(es) this gate holds " +
                            "no verdict for, so nothing here decided whether they are a hole in " +
                            "the suite, a judgement PIT made about the mutation, or a machine " +
                            "that gave up, and PIT may be crediting every one of them to the " +
                            "90% threshold:"
                    )
                    unjudgeable.forEach {
                        val many = mutants.count { mutant -> mutant.status == it }
                        appendLine("  - $it ($many mutants)")
                    }
                    appendLine(
                        "The statuses with a verdict here are " +
                            "${MutationStatus.entries.map(MutationStatus::name)}, which is every " +
                            "constant of org.pitest.mutationtest.DetectionStatus the pinned PIT " +
                            "declares, so this can only be reached by a PIT that declares a new " +
                            "one. Give it a verdict in MutationStatus, where a reader can see it " +
                            "beside the other ten."
                    )
                }
            )
        }

        val outlived =
            MutationVerdicts.with(mutants, StatusVerdict.NAMED_AS_A_SURVIVOR)
                .map(Mutant::spelledOut)
                .distinct()
                .sorted()
        if (outlived.isNotEmpty()) {
            logger.lifecycle(
                "verifyMutationScope: " +
                    (if (outlived.size == 1) "one mutant SURVIVED"
                    else "these ${outlived.size} mutants SURVIVED") +
                    ". The threshold " +
                    "is a regression guard, so a survivor is not a failure by itself, but a " +
                    "reader comparing one run against another needs the set and not the " +
                    "count: a survivor that swaps places with a killed mutant leaves the " +
                    "score untouched and means a test stopped proving what it claimed:"
            )
            outlived.forEach { logger.lifecycle("  - $it") }
        }

        val pitDecidedItself =
            MutationVerdicts.with(mutants, StatusVerdict.NAMED_AS_PITS_OWN_JUDGEMENT)
        if (pitDecidedItself.isNotEmpty()) {
            logger.lifecycle(
                "verifyMutationScope: PIT decided these ${pitDecidedItself.size} mutants " +
                    "without running a test over them: a NON_VIABLE mutant is bytecode the JVM " +
                    "refused, and an EQUIVALENT one is a mutation PIT ruled cannot change " +
                    "behaviour. It counts both as detected and folds them into the score. Each " +
                    "is a property of the mutation rather than a hole in the suite, so they are " +
                    "named here rather than failed. Read them, because a line that starts " +
                    "producing them is a line the threshold has stopped measuring:"
            )
            pitDecidedItself.map(Mutant::spelledOutWithItsStatus).distinct().sorted().forEach {
                logger.lifecycle("  - $it")
            }
        }

        val leftUnfinished =
            MutationVerdicts.with(mutants, StatusVerdict.NAMED_AS_A_RUN_THAT_DID_NOT_FINISH)
        if (leftUnfinished.isNotEmpty()) {
            logger.lifecycle(
                "verifyMutationScope: PIT left these ${leftUnfinished.size} mutants NOT_STARTED " +
                    "or STARTED, which is a run that ended before anything judged them. PIT " +
                    "counts neither as detected, so the 90% threshold already charges for them " +
                    "and the score is what fails if there are enough of them. They are named " +
                    "here because their number is the size of the run that was lost:"
            )
            leftUnfinished.map(Mutant::spelledOutWithItsStatus).distinct().sorted().forEach {
                logger.lifecycle("  - $it")
            }
        }

        val foreignClasses = mutatedClasses.filterNot(::inDomainPackage).distinct().sorted()
        if (foreignClasses.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "PIT mutated classes outside the domain packages " +
                            "$domainPackages, so the 90% threshold is scoring the wrong code:"
                    )
                    foreignClasses.forEach { appendLine("  - $it") }
                    appendLine(
                        "Check `pitest.targetClasses` in " +
                            "build-logic/src/main/kotlin/psp.mutation-gate.gradle.kts."
                    )
                }
            )
        }

        val classesRoot = compiledMainClasses.get().asFile
        val compiledMainClassNames = CompiledClasses.namesUnder(classesRoot)

        val measured =
            MutationScope.scorable(compiledMainClassNames).filter { className ->
                inScope.any { MutationScope.matches(it, className) } &&
                    notInScope.none { MutationScope.matches(it, className) }
            }
        val unaccounted = MutationScope.unaccountedBy(inScope, compiledMainClassNames)
        if (unaccounted.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "No `pitest.targetClasses` glob of $inScope reaches these compiled " +
                            "main classes, so the 90% threshold does not measure them, and " +
                            "`pitest.excludedClasses` $notInScope cannot account for them " +
                            "either: an exclusion only subtracts from what PIT already " +
                            "targets, and its ratchet only counts classes inside a targeted " +
                            "package:"
                    )
                    unaccounted.forEach { appendLine("  - $it") }
                    appendLine(
                        "Name the package in `pitest.targetClasses` and cover the class with " +
                            "a test PIT can run, or name the class in " +
                            "`pitest.excludedClasses` with its ratchet in " +
                            "`mutationExclusionBudgets`. The only main classes this check does " +
                            "not ask about are ${CoverageScope.EXCLUDED_CLASS_NAMES}, which " +
                            "coverage excludes."
                    )
                }
            )
        }

        val neverExecuted =
            statusesByClass
                .filterValues { statuses -> statuses.all { it == MutationStatus.NO_COVERAGE.name } }
                .keys
                .sorted()
        if (neverExecuted.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "No test executes these classes, yet they sit inside the 90% " +
                            "mutation threshold, so the score is measuring code nobody drives:"
                    )
                    neverExecuted.forEach {
                        appendLine(
                            "  - $it (${statusesByClass.getValue(it).size} mutants, " +
                                "all NO_COVERAGE)"
                        )
                    }
                    appendLine(
                        "Either cover them with a test PIT can run, or name them in " +
                            "`pitest.excludedClasses` so the exclusion is written down."
                    )
                }
            )
        }

        val uncovered =
            MutationVerdicts.with(mutants, StatusVerdict.FAILS_THE_BUILD)
                .map(Mutant::spelledOut)
                .distinct()
                .sorted()
        if (uncovered.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "No test executes this code, yet it sits inside the 90% mutation " +
                            "threshold, so the score counts it as a denominator and nothing " +
                            "else:"
                    )
                    uncovered.forEach { appendLine("  - $it") }
                    appendLine(
                        "Each is either a case the suite is missing or a path no test can " +
                            "reach. Cover it with a test PIT can run, or delete the code."
                    )
                }
            )
        }

        val awaitingAVerdict = MutationVerdicts.awaitingANarrowedRerun(mutants)
        if (awaitingAVerdict.isNotEmpty()) {
            reachAVerdictOn(awaitingAVerdict)
        }

        if (measured.isNotEmpty() && !report.isFile) {
            throw GradleException(
                "PIT wrote no ${report.name}, so nothing proves the 90% threshold ran " +
                    "over the ${measured.size} domain classes in scope."
            )
        }

        when {
            mutatedClasses.isNotEmpty() ->
                logger.lifecycle(
                    "verifyMutationScope: ${mutatedClasses.size} mutants from " +
                        "${mutatedClasses.distinct().size} of ${measured.size} domain classes " +
                        "in scope; all of them in $domainPackages, and every one of them is " +
                        "executed by at least one test."
                )

            measured.isNotEmpty() ->
                logger.lifecycle(
                    "verifyMutationScope: ${measured.size} domain classes are in PIT's scope " +
                        "and none produced a mutant. PIT does not mutate interfaces, enums or " +
                        "plain records; the 90% threshold stays armed for the first class that " +
                        "carries behaviour."
                )

            else ->
                logger.lifecycle(
                    "verifyMutationScope: no compiled domain classes yet. " +
                        "The 90% threshold stays armed for the first domain class."
                )
        }
    }

    private fun reachAVerdictOn(awaitingAVerdict: List<Mutant>) {
        val erroringClasses = awaitingAVerdict.map { it.className }.distinct().sorted()
        val stamped = MutationVerdicts.statusesOf(awaitingAVerdict)
        logger.lifecycle(
            "verifyMutationScope: PIT reached no verdict on ${awaitingAVerdict.size} mutant(s) " +
                "in ${erroringClasses.size} class(es), stamped $stamped. A minion that exits " +
                "leaves PIT stamping every mutation of its batch that had not reported a result " +
                "from the exit code alone, ${MutationStatus.stampedByAMinionExitCode()}, so the " +
                "status says which limit the machine hit and the mutant named may not be the one " +
                "that hit it. Re-running PIT over those classes alone to tell the machine from " +
                "the mutant."
        )
        awaitingAVerdict.map(Mutant::spelledOutWithItsStatus).distinct().sorted().forEach {
            logger.lifecycle("  - $it")
        }

        val invocation = pitInvocation.get().asFile
        if (!invocation.isFile) {
            throw GradleException(
                whatItSays(
                    "PIT reached no verdict on these mutants, and the recorded PIT invocation " +
                        "${invocation.absolutePath} is missing, so they could not be re-run:",
                    awaitingAVerdict,
                    THE_MECHANISM_DID_NOT_RUN,
                )
            )
        }

        val pit = PitInvocation.readFrom(invocation)
        val retryReports = retryReportDirectory.get().asFile
        retryReports.deleteRecursively()
        retryReports.mkdirs()
        execOperations.exec {
            commandLine(pit.narrowedTo(erroringClasses, retryReports))
            isIgnoreExitValue = true
        }

        val retried = File(retryReports, "mutations.xml")
        if (!retried.isFile) {
            throw GradleException(
                whatItSays(
                    "PIT reached no verdict on these mutants, and the re-run narrowed to " +
                        "$erroringClasses wrote no ${retried.name}:",
                    awaitingAVerdict,
                    THE_MECHANISM_DID_NOT_RUN,
                )
            )
        }

        val narrowed = MutationReport.mutantsIn(retried.readText())

        val unreached = MutationVerdicts.unaccountedForByTheRerun(awaitingAVerdict, narrowed)
        if (unreached.isNotEmpty()) {
            throw GradleException(
                whatItSays(
                    "PIT reached no verdict on these mutants, and the re-run narrowed to " +
                        "$erroringClasses wrote a ${retried.name} of ${narrowed.size} mutants " +
                        "that reaches no verdict on them:",
                    unreached,
                    THE_RERUN_NEVER_REACHED_THEM,
                )
            )
        }

        val refused = MutationVerdicts.thoseNoRerunCanExcuse(awaitingAVerdict, narrowed)
        if (refused.isNotEmpty()) {
            throw GradleException(
                whatItSays(
                    "PIT stamped RUN_ERROR on these mutants twice: once in the full run, and " +
                        "again in a run narrowed to $erroringClasses, where nothing else was " +
                        "competing with them. A harness hiccup clears on the second attempt and " +
                        "this did not:",
                    refused,
                    A_RERUN_DID_NOT_CLEAR_IT,
                )
            )
        }

        val aFirstHiccup = MutationVerdicts.thoseAFirstHiccupExplains(awaitingAVerdict, narrowed)
        if (aFirstHiccup.isNotEmpty()) {
            val whatTheFullRunSaid =
                MutationVerdicts.statusesOf(
                    MutationVerdicts.asTheFullRunSawThem(awaitingAVerdict, aFirstHiccup)
                )
            logger.lifecycle(
                "verifyMutationScope: the re-run narrowed to $erroringClasses stamped RUN_ERROR " +
                    "on these ${aFirstHiccup.size} mutant(s), where the full run had stamped " +
                    "$whatTheFullRunSaid. That is a first minion failure on them and not a " +
                    "second, so it is this project's documented hiccup sampled once more by a " +
                    "re-run that now happens on nearly every gate run, and it is named rather " +
                    "than failed: the build fails only where a RUN_ERROR of the full run came " +
                    "back a RUN_ERROR. Read them anyway, because two runs reaching no verdict " +
                    "on one mutant by two different routes is also how a mutant nothing can " +
                    "judge looks:"
            )
            aFirstHiccup.map(Mutant::spelledOutWithItsStatus).distinct().sorted().forEach {
                logger.lifecycle("  - $it")
            }
        }

        val held = MutationVerdicts.thoseAStandingLimitExplains(awaitingAVerdict, narrowed)
        if (held.isNotEmpty()) {
            logger.lifecycle(
                "verifyMutationScope: the re-run narrowed to $erroringClasses reached no " +
                    "verdict on these ${held.size} mutant(s) either, with nothing else " +
                    "competing for the machine, and it stamped " +
                    "${MutationVerdicts.statusesOf(held)} on them again. A mutant that runs " +
                    "alone and still exhausts the clock or the heap is arguably detected by the " +
                    "limit that stopped it, which is how PIT counts it, so these are named " +
                    "rather than failed. Read them, because a timeout is also how an unending " +
                    "body looks, and an out-of-memory is also how an unbounded allocation looks:"
            )
            held.map(Mutant::spelledOutWithItsStatus).distinct().sorted().forEach {
                logger.lifecycle("  - $it")
            }
        }

        if (aFirstHiccup.isEmpty() && held.isEmpty()) {
            logger.lifecycle(
                "verifyMutationScope: the re-run narrowed to $erroringClasses reached a " +
                    "verdict on every one of them, so the $stamped of the full run was the " +
                    "harness and not the mutant. The score stands."
            )
        }
    }

    private fun whatItSays(headline: String, listed: List<Mutant>, counsel: String): String =
        buildString {
            appendLine(headline)
            listed.map(Mutant::spelledOutWithItsStatus).distinct().sorted().forEach {
                appendLine("  - $it")
            }
            appendLine(
                "Each of these is a mutant no test reached a verdict on: the minion that would " +
                    "have judged it exited, and PIT stamped the status of that exit code over " +
                    "every mutation of the batch that had not reported one. PIT credits all of " +
                    "${MutationStatus.stampedByAMinionExitCode()} to the 90% threshold as " +
                    "detected, by an accident of the harness rather than by an assertion."
            )
            appendLine(counsel)
            appendLine(
                "Do not answer it by naming the class in `pitest.excludedClasses`. That would " +
                    "write down a hole that is not there and drop a well-tested class from the " +
                    "score for good, which is what the `*Controller` exclusion already cost " +
                    "this project. Look at PIT's log for this run, and at what else was " +
                    "competing for this machine."
            )
        }

    private companion object {

        const val THE_MECHANISM_DID_NOT_RUN =
            "Nothing distinguishes the machine from the mutant until this re-run works, and " +
                "this gate does not bless a score it has not checked, so the build fails here " +
                "rather than printing a sentence and going green. Look at whether the `pitest` " +
                "task recorded its invocation, and at whether anything removed the retry report " +
                "directory under it."

        const val THE_RERUN_NEVER_REACHED_THEM =
            "A mutant PIT generates is a mutant PIT reports, in whatever status, so a report " +
                "that leaves these out, or leaves them NOT_STARTED or STARTED, is a re-run that " +
                "never reached them: either the narrowing targeted something other than their " +
                "classes, or the re-run died before it judged them. The one mechanism that " +
                "tells the machine from the mutant did not run, so the build fails rather than " +
                "reporting that the score stands."

        const val A_RERUN_DID_NOT_CLEAR_IT =
            "Take the class named above seriously. Every other time a RUN_ERROR has landed in " +
                "this project the mutant was KILLED on the runs that did not error, in nine " +
                "different classes across seven observed failures, and a re-run cleared it. " +
                "This one did not clear, so read it as a mutant nothing can reach a verdict on " +
                "rather than as PIT losing a minion."
    }
}
