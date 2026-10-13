package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class VerifySonarSuppressions : DefaultTask() {

    @get:InputFiles abstract val buildScripts: ConfigurableFileCollection

    @get:InputFiles abstract val buildLogicSources: ConfigurableFileCollection

    @get:InputFiles abstract val javaSources: ConfigurableFileCollection

    @get:Input abstract val tasksThisBuildRegisters: ListProperty<String>

    @get:Input abstract val settingsTheCompilerWasGiven: MapProperty<String, String>

    @get:Input abstract val severityPerCheckAndCompilation: MapProperty<String, String>

    @get:Internal abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val root: File = projectRoot.get().asFile
        val scripts = buildScripts.files.sorted()
        val buildLogic = buildLogicSources.files.sorted()
        val java = javaSources.files.sorted()

        val filtersByFile = mutableMapOf<String, Int>()
        val configured = mutableListOf<Pair<String, String>>()
        for (source in scripts + buildLogic) {
            val where = source.toRelativeString(root)
            val text = source.readText()
            filtersByFile[where] = SonarRules.ignoreFiltersConfiguredIn(text)
            configured += SonarRules.ruleKeysConfiguredIn(text).map { where to it }
        }
        val suppressed = mutableListOf<Pair<String, String>>()
        for (source in java) {
            val where = source.toRelativeString(root)
            suppressed +=
                SonarRules.everyWarningSuppressedInSource(source.readText()).map { where to it }
        }
        val byHowItIsNamed = suppressed.groupBy { SonarRules.howAWarningIsNamed(it.second) }
        val annotated = byHowItIsNamed[HowAWarningIsNamed.A_SCANNER_RULE].orEmpty()

        val switchedOff = configured + annotated
        val unanswerable = SonarRules.aSuppressionNothingAnswers(switchedOff.map { it.second })
        val unanswered = switchedOff.filter { unanswerable.contains(it.second) }
        val empty =
            SonarRules.aTreeThatContributedNothing(
                mapOf(
                    "the build scripts of this tree" to scripts.size,
                    "the build logic this gate lives in" to buildLogic.size,
                    "the Java sources the scanner reads" to java.size,
                )
            )
        val elsewhere = SonarRules.aFileConfiguringTheScannerBeyondTheOneThatOwnsIt(filtersByFile)
        val ownerSilent = SonarRules.theOwningFileConfiguresNoFilter(filtersByFile)
        val tooLittle = SonarRules.anAnswerThatSaysTooLittle()
        val thinConditions = SonarRules.aConditionThatSaysTooLittle()
        val spare = SonarRules.anAnswerNoSuppressionNames()
        val gone = SonarRules.aGateThisBuildNoLongerRegisters(tasksThisBuildRegisters.get())
        val downgraded =
            SonarRules.aSettingAStandInReliesOnThatIsGone(settingsTheCompilerWasGiven.get())
        val unusedGates = SonarRules.aGateNoSuppressionStandsOn()
        val collided = SonarRules.anIdTwoRulesShare()
        val unrecovered = SonarRules.aRuleWhoseConditionWasNeverRecovered()
        val warnings = suppressed.map { it.second }
        val unanswerableChecks = SonarRules.aCheckNothingAnswers(warnings)
        val unansweredChecks = suppressed.filter { unanswerableChecks.contains(it.second) }
        val unknownSpellings = SonarRules.aWarningThisGateCannotPlace(warnings)
        val unplaceable = suppressed.filter { unknownSpellings.contains(it.second) }
        val everyCategoryAtOnce = SonarRules.aWarningThatNamesEveryCategoryAtOnce(warnings)
        val switchingEverythingOff = suppressed.filter { everyCategoryAtOnce.contains(it.second) }
        val staleChecks = SonarRules.aCheckNoSourceSwitchesOff(warnings)
        val unreadable = SonarRules.aFormThisSweepCanNoLongerRead()
        val severities = severityPerCheckAndCompilation.get()
        val switchedOffUnanswered =
            SonarRules.aCheckTheCompilerWasToldToSwitchOffThatNothingAnswers(severities)
        val switchedBackOn = SonarRules.aCheckListedAsSwitchedOffTheCompilerStillRuns(severities)
        val claimed = SonarRules.anEntryThatListsTheKeyNoEntryMay()

        if (
            unanswered.isNotEmpty() ||
                empty.isNotEmpty() ||
                elsewhere.isNotEmpty() ||
                ownerSilent ||
                tooLittle.isNotEmpty() ||
                thinConditions.isNotEmpty() ||
                spare.isNotEmpty() ||
                gone.isNotEmpty() ||
                downgraded.isNotEmpty() ||
                unusedGates.isNotEmpty() ||
                collided.isNotEmpty() ||
                unansweredChecks.isNotEmpty() ||
                unplaceable.isNotEmpty() ||
                switchingEverythingOff.isNotEmpty() ||
                staleChecks.isNotEmpty() ||
                unreadable.isNotEmpty() ||
                switchedOffUnanswered.isNotEmpty() ||
                switchedBackOn.isNotEmpty() ||
                claimed.isNotEmpty()
        ) {
            throw GradleException(
                buildString {
                    appendLine(THE_RULE)
                    unanswered.forEach { (where, rule) ->
                        appendLine(
                            "  - $where switches $rule off and `SuppressedRule` does not list " +
                                "it, so nothing here says what answers it"
                        )
                    }
                    empty.forEach {
                        appendLine(
                            "  - $it contributed no file to this scan, so the half of the sweep " +
                                "it names measured nothing"
                        )
                    }
                    elsewhere.forEach {
                        appendLine(
                            "  - $it configures the scanner's ignore filters, which only " +
                                "${SonarRules.THE_FILE_THAT_CONFIGURES_THE_SCANNER} may do, so a " +
                                "suppression can be written where this table does not reach"
                        )
                    }
                    if (ownerSilent) {
                        appendLine(
                            "  - ${SonarRules.THE_FILE_THAT_CONFIGURES_THE_SCANNER} configures " +
                                "no ignore filter, so either the mechanism moved or this scan " +
                                "can no longer read it, and either way it is checking nothing"
                        )
                    }
                    tooLittle.forEach {
                        appendLine(
                            "  - $it says fewer than " +
                                "${SonarRules.FEWEST_WORDS_THAT_STATE_AN_ANSWER} words, which is " +
                                "too few to be a reason a reader can act on"
                        )
                    }
                    thinConditions.forEach {
                        appendLine(
                            "  - $it records a re-enable condition of fewer than " +
                                "${SonarRules.FEWEST_WORDS_THAT_STATE_A_CONDITION} words, which " +
                                "does not say what would change a reader's mind"
                        )
                    }
                    spare.forEach {
                        appendLine(
                            "  - $it is recorded as an answer to a suppressed rule and no " +
                                "suppressed rule names it"
                        )
                    }
                    gone.forEach {
                        appendLine(
                            "  - a suppression stands on the task `$it`, which this build no " +
                                "longer registers, so what that rule was standing in for is " +
                                "enforced by nothing"
                        )
                    }
                    downgraded.forEach {
                        appendLine(
                            "  - a suppression stands on a compiler setting that no longer " +
                                "holds: $it"
                        )
                    }
                    unusedGates.forEach {
                        appendLine(
                            "  - `$it` is recorded as a gate that stands in for a rule and no " +
                                "suppressed rule stands on it"
                        )
                    }
                    collided.forEach {
                        appendLine(
                            "  - two rules reduce to the multicriteria id `$it`, so one entry " +
                                "would overwrite the other and a rule would be scanned after all"
                        )
                    }
                    unansweredChecks.forEach { (where, check) ->
                        appendLine(
                            "  - $where switches the compiler check `$check` off and " +
                                "`SuppressedCheck` does not list it, so nothing here says what " +
                                "answers it"
                        )
                    }
                    switchingEverythingOff.forEach { (where, warning) ->
                        appendLine(
                            "  - $where suppresses `$warning`, which names no category and is " +
                                "taken for all of them at once, so it switches off every check " +
                                "this table answers for as well as javac's own lint, and no " +
                                "entry here can stand for it; name the one thing being " +
                                "suppressed instead"
                        )
                    }
                    unplaceable.forEach { (where, warning) ->
                        appendLine(
                            "  - $where suppresses `$warning`, which is spelled neither as a " +
                                "rule of the scanner, nor as a check of the compiler, nor as " +
                                "one of the ${SonarRules.THE_CATEGORIES_JAVACS_OWN_LINT_PUBLISHES.size} " +
                                "categories `javac --help-lint` publishes, so this gate cannot " +
                                "say which table should answer it"
                        )
                    }
                    staleChecks.forEach {
                        appendLine(
                            "  - `SuppressedCheck` lists `$it` and no source suppresses it any " +
                                "more, so the reason recorded for it answers nothing"
                        )
                    }
                    switchedOffUnanswered.forEach {
                        appendLine(
                            "  - the compiler was told to switch `$it` off and `SuppressedCheck` " +
                                "does not list it, so a check is off for a whole source set and " +
                                "nothing here says what answers it"
                        )
                    }
                    switchedBackOn.forEach {
                        appendLine(
                            "  - a recorded reason stands on a check being off and it is not: $it"
                        )
                    }
                    claimed.forEach {
                        appendLine(
                            "  - `$it` is both a specimen this sweep plants in its own patterns " +
                                "and an entry of a table here, so a reader of a finding cannot " +
                                "tell a planted key from a real suppression"
                        )
                    }
                    unreadable.forEach {
                        appendLine(
                            "  - this sweep read $it against a specimen written in that very " +
                                "form and found nothing in it, so every zero it reports is a " +
                                "pattern that stopped matching rather than a repository with " +
                                "nothing to find"
                        )
                    }
                    appendLine(WHAT_IS_ENFORCED)
                    appendLine("The rules switched off here, and what each one is traded against:")
                    SonarRules.theDecisionRecord().forEach { appendLine("  - $it") }
                    if (unrecovered.isNotEmpty()) {
                        appendLine(
                            "No re-enable condition was recovered for $unrecovered; nothing here " +
                                "composes one."
                        )
                    }
                }
            )
        }

        logger.lifecycle(
            "verifySonarSuppressions: ${SuppressedRule.entries.size} rule(s) switched off in the " +
                "scanner and ${SuppressedCheck.entries.size} check(s) switched off in the " +
                "compiler, each naming one of ${SuppressionAnswer.entries.size} recorded " +
                "answers and ${SonarRules.everyRecordedCondition().size - unrecovered.size} of " +
                "them a recovered re-enable condition; swept ${scripts.size} build scripts, " +
                "${buildLogic.size} build logic sources and ${java.size} Java sources, finding " +
                "${filtersByFile.values.sum()} ignore filter(s), all in " +
                "${SonarRules.THE_FILE_THAT_CONFIGURES_THE_SCANNER}, ${configured.size} rule " +
                "key(s) written into one by hand, and ${suppressed.size} warning(s) suppressed " +
                "by an annotation: ${annotated.size} spelled as a scanner rule, " +
                "${byHowItIsNamed[HowAWarningIsNamed.A_COMPILER_CHECK].orEmpty().size} as a " +
                "compiler check and " +
                "${byHowItIsNamed[HowAWarningIsNamed.THE_COMPILERS_OWN_LINT].orEmpty().size} " +
                "as a category of javac's own lint. Each of the " +
                "${SonarRules.theFormsThisSweepReads().size} forms this sweep reads was read " +
                "back against a specimen written in it, so a zero above is a sweep that found " +
                "nothing and not one that read nothing. Read " +
                "${severities.size} check severity(ies) the compiler was given across this " +
                "project's compilations, " +
                "${severities.values.count { it == SonarRules.SWITCHED_OFF }} of them off and " +
                "each one listed; ${SonarRules.settingsStandInsRelyOn().size} compiler " +
                "setting(s) a stand-in relies on still hold."
        )
    }

    companion object {
        val THE_RULE =
            "A rule this build switches off in the scanner, and a check it switches off in the " +
                "compiler — at one site by annotation or for a whole source set by a compiler " +
                "setting — is answered either by a convention this codebase holds on purpose or " +
                "by a gate of this build that enforces what it stood in for, and the entry that " +
                "switches it off carries that answer and what would turn it back on. These do " +
                "not:"

        val WHAT_IS_ENFORCED =
            "What this gate enforces: every rule switched off through the scanner's ignore " +
                "filters is listed in `SuppressedRule` and every check switched off by a " +
                "`@SuppressWarnings` in source is listed in `SuppressedCheck`, each with an " +
                "answer of at least ${SonarRules.FEWEST_WORDS_THAT_STATE_AN_ANSWER} words and a " +
                "condition that would turn it back on; a listed check is still suppressed " +
                "somewhere; the filters are configured in one file and that file still " +
                "configures them; each tree this sweep names contributes files; each form it " +
                "reads is read back against a specimen written in that form, so a zero is a " +
                "sweep that found nothing rather than a pattern that stopped matching; and a " +
                "gate a suppression stands on is still registered with the settings its reason " +
                "relies on. It does not read the scanner's findings, and it cannot see a rule " +
                "key assembled from parts. A check the compiler was told to switch off for a " +
                "whole source set is listed too, and a reason recorded against a check being " +
                "off fails once the compiler is given anything else. A category of javac's own " +
                "lint is counted and named as one and answers to no table here, because " +
                "`-Xlint:all -Werror` has the compiler raise it on the code at that site and " +
                "suppressing it there switches no rule off for this build; the categories are " +
                "the ones `javac --help-lint` publishes, and `all` and `none` are refused " +
                "because they name no category and `all` would switch off every check listed " +
                "here as well."
    }
}
