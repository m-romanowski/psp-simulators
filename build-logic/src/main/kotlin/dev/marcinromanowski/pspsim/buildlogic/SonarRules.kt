package dev.marcinromanowski.pspsim.buildlogic

enum class WhereItFires(val resourceKey: String) {
    MAIN("src/main/java/**/*.java"),
    TESTS("src/test/java/**/*.java"),
    BOTH("**/*.java"),
}

enum class ThisBuildsOwnGate(val task: String, val theSettingsItReliesOn: Map<String, String>) {
    CAUGHT_EXCEPTION_NAMES("verifyCaughtExceptionNames", emptyMap()),
    NULLAWAY_OVER_ANNOTATED_MAIN(
        "compileJava",
        mapOf(
            "NullAway" to "ERROR",
            "NullAway:AnnotatedPackages" to "dev.marcinromanowski.pspsim",
            "NullAway:JSpecifyMode" to "true",
        ),
    ),
}

enum class SuppressionAnswer(val whatAnswersIt: String) {
    AN_ARM_NAMES_THE_CASE_IT_HANDLES(
        "A switch arm over a sealed verdict binds a variable that names the case and calls an " +
            "accessor that names the payload; a record pattern keeps the second name and deletes " +
            "the first, and under the comment ban that variable is the only word for the case."
    ),
    RECORD_IS_THIS_DOMAINS_OWN_NOUN(
        "`record` is the audit trail's row and the verb that writes one across this build's " +
            "provider operations, restricted only in type-declaration position, so renaming it " +
            "would rename the domain to suit the grammar."
    ),
    A_CAUGHT_EXCEPTIONS_NAME_IS_THE_EXPLANATION(
        "Under the comment ban the name a catch clause binds is the only statement of what went " +
            "wrong a reader gets, and the unnamed variable erases it; the rule fires on an " +
            "unread parameter whatever it is called, so it cannot tell `connectTimedOut` from `e`."
    ),
    A_COMPOSITION_ROOT_IS_AS_WIDE_AS_ITS_COLLABORATORS(
        "A provider constructor takes one collaborator per parameter and an audit writer one " +
            "component of the row per parameter, so a parameter object would rename the " +
            "dependency graph without shortening it."
    ),
    AN_ASSERTION_NAMES_THE_VALUE_IT_CLAIMS_ABOUT(
        "The dedicated AssertJ form moves the subject of the assertion from the value a test " +
            "makes a claim about to the container it came out of, so the failure stops naming " +
            "the field the test is about."
    ),
    A_FAILURE_NAMES_THE_ONE_CLAIM_THAT_BROKE(
        "Independent claims about one page or one row are written one per statement, so a " +
            "failure names the one claim that broke and a claim is added without re-punctuating " +
            "its neighbours; a chain reports the first failure and says nothing about the rest."
    ),
    A_SHARED_FLOOR_CARRIES_THE_ASSERTIONS(
        "The test bodies call `TheSingleIdentifierFloor`, a floor several providers are held to, " +
            "which carries the assertions, and the rule cannot follow an assertion into a " +
            "helper class."
    ),
    AN_EMPTY_BODY_IS_THE_BEHAVIOUR(
        "The empty methods are no-op test doubles and the null-object arm of a sealed " +
            "hierarchy, whose emptiness is what they are for; of the rule's three remedies a " +
            "comment is what `verifyNoComments` fails the build on, an exception would break " +
            "every double called precisely because it does nothing, and completing the body is " +
            "the opposite of the arm's meaning."
    ),
    A_WIRE_FORMAT_NAMES_ITS_OWN_DIGITS(
        "`\\d` widens to every Unicode decimal digit once `UNICODE_CHARACTER_CLASS` is set and " +
            "`[0-9]` does not, and every one of these patterns is a validator that accepts the " +
            "digits a vendor's protocol names and nothing else."
    ),
    A_NARROWED_OVERRIDE_RULES_THE_NULL_OUT(
        "The rule asks for the JSR-305 annotation this project does not use, resolves the call " +
            "against the sealed interface's wider nullable return rather than the refusing " +
            "record's narrowed override, and does not read jspecify's own annotation."
    ),
    AN_EXHAUSTIVE_SWITCH_FORCES_A_DECISION(
        "An empty arm per variant is what forces a decision when a variant is added, and " +
            "deleting the arm moves that decision to a default that absorbs the new case " +
            "silently; the rule's remedy is a comment, which `verifyNoComments` fails the build on."
    ),
    A_BEST_EFFORT_CLOSE_HAS_NOTHING_TO_REPORT(
        "Closing a fixture socket that is already gone is not a test result, and the test " +
            "merchants close one a finished test has no further use for, so a failure there has " +
            "nowhere to be reported and nothing to change; the check's own remedy is that " +
            "sentence written inside the block, which is what `verifyNoComments` fails the " +
            "build on."
    ),
    A_TEST_SENDS_THE_NULL_IT_IS_ABOUT(
        "Test code exercises invalid input on purpose, nulls among it, so a null a test passes " +
            "to an annotated method is the case that test is about rather than a defect, and " +
            "NullAway reads those annotations whichever source set the call is written in. On " +
            "the main sources it stays an error, which is what `java:S4449` is suppressed " +
            "against."
    ),
}

enum class SuppressedRule(
    val ruleKey: String,
    val firesIn: WhereItFires,
    val answeredBy: SuppressionAnswer,
    val enforcedInsteadBy: ThisBuildsOwnGate?,
    val whatWouldTurnItBackOn: String,
) {
    S6878(
        "java:S6878",
        WhereItFires.MAIN,
        SuppressionAnswer.AN_ARM_NAMES_THE_CASE_IT_HANDLES,
        null,
        "The comment ban is lifted, or a record pattern can bind a component without renaming " +
            "the case that matched it.",
    ),
    S6213(
        "java:S6213",
        WhereItFires.BOTH,
        SuppressionAnswer.RECORD_IS_THIS_DOMAINS_OWN_NOUN,
        null,
        "The mechanism can filter on the identifier rather than only on the rule and the path. " +
            "The cost of this entry is exactly that it cannot: with the rule off the scanner no " +
            "longer reports `yield`, `sealed`, `permits` or `var` used as identifiers either, " +
            "and a search for those is the check this entry is traded against.",
    ),
    S7467(
        "java:S7467",
        WhereItFires.BOTH,
        SuppressionAnswer.A_CAUGHT_EXCEPTIONS_NAME_IS_THE_EXPLANATION,
        ThisBuildsOwnGate.CAUGHT_EXCEPTION_NAMES,
        "The rule can tell a parameter that is unread from a parameter that says nothing, which " +
            "is the distinction `verifyCaughtExceptionNames` draws and the rule cannot.",
    ),
    S107(
        "java:S107",
        WhereItFires.MAIN,
        SuppressionAnswer.A_COMPOSITION_ROOT_IS_AS_WIDE_AS_ITS_COLLABORATORS,
        null,
        "The parameter counts start growing for a reason other than a new collaborator or a new " +
            "audit component, which a reviewer reads in the diff and this rule cannot tell apart.",
    ),
    S5838(
        "java:S5838",
        WhereItFires.TESTS,
        SuppressionAnswer.AN_ASSERTION_NAMES_THE_VALUE_IT_CLAIMS_ABOUT,
        null,
        "A rewrite of these tests adopts the dedicated forms, at which point the rule is free.",
    ),
    S5853(
        "java:S5853",
        WhereItFires.TESTS,
        SuppressionAnswer.A_FAILURE_NAMES_THE_ONE_CLAIM_THAT_BROKE,
        null,
        "A chained failure reports every unmet claim in the chain, which is the only thing the " +
            "separate statements are buying.",
    ),
    S2699(
        "java:S2699",
        WhereItFires.TESTS,
        SuppressionAnswer.A_SHARED_FLOOR_CARRIES_THE_ASSERTIONS,
        null,
        "The rule learns a project's assertion helpers, which for this one is a single class name.",
    ),
    S1186(
        "java:S1186",
        WhereItFires.BOTH,
        SuppressionAnswer.AN_EMPTY_BODY_IS_THE_BEHAVIOUR,
        null,
        "This codebase grows an empty method that is a stub rather than a decision, which a " +
            "reviewer reads in the diff and this rule cannot tell apart.",
    ),
    S6353(
        "java:S6353",
        WhereItFires.MAIN,
        SuppressionAnswer.A_WIRE_FORMAT_NAMES_ITS_OWN_DIGITS,
        null,
        "These patterns stop describing somebody else's wire format, because then the widening " +
            "the rule invites is no longer a change of meaning.",
    ),
    S4449(
        "java:S4449",
        WhereItFires.MAIN,
        SuppressionAnswer.A_NARROWED_OVERRIDE_RULES_THE_NULL_OUT,
        ThisBuildsOwnGate.NULLAWAY_OVER_ANNOTATED_MAIN,
        "The rule reads jspecify's own `Nullable` and resolves an override's narrowed return, at " +
            "which point it reports zero here and starts being useful.",
    ),
    S108(
        "java:S108",
        WhereItFires.BOTH,
        SuppressionAnswer.AN_EXHAUSTIVE_SWITCH_FORCES_A_DECISION,
        null,
        "An exhaustive switch stops being how this codebase forces a decision on a new variant.",
    );

    val resourceKey: String
        get() = firesIn.resourceKey

    companion object {

        fun of(ruleKey: String): SuppressedRule? = entries.firstOrNull { it.ruleKey == ruleKey }
    }
}

enum class HowAWarningIsNamed {
    A_SCANNER_RULE,
    A_COMPILER_CHECK,
    THE_COMPILERS_OWN_LINT,
    NOTHING_THIS_GATE_CAN_PLACE,
}

const val AN_ANNOTATION_IN_SOURCE = "a `@SuppressWarnings` in source"

enum class SuppressedCheck(
    val check: String,
    val whereItIsSwitchedOff: String,
    val answeredBy: SuppressionAnswer,
    val whatWouldTurnItBackOn: String,
) {
    EMPTY_CATCH_AT_A_BEST_EFFORT_CLOSE(
        "EmptyCatch",
        AN_ANNOTATION_IN_SOURCE,
        SuppressionAnswer.A_BEST_EFFORT_CLOSE_HAS_NOTHING_TO_REPORT,
        "The comment ban is lifted, at which point that sentence goes back inside the block and " +
            "the check is satisfied by its own remedy rather than by an annotation.",
    ),
    NULLAWAY_OVER_THE_TEST_SOURCES(
        "NullAway",
        "compileTestJava",
        SuppressionAnswer.A_TEST_SENDS_THE_NULL_IT_IS_ABOUT,
        "No test passes a null to an annotated method any more, at which point the check reports " +
            "nothing over these sources and can stay on for them too.",
    );

    val whatThisTableCallsIt: String
        get() = "$check in $whereItIsSwitchedOff"

    companion object {

        fun switchedOffByAnAnnotation(): List<SuppressedCheck> = entries.filter {
            it.whereItIsSwitchedOff == AN_ANNOTATION_IN_SOURCE
        }

        fun switchedOffByTheCompiler(): List<SuppressedCheck> = entries.filterNot {
            it.whereItIsSwitchedOff == AN_ANNOTATION_IN_SOURCE
        }

        fun of(check: String): SuppressedCheck? =
            switchedOffByAnAnnotation().firstOrNull { it.check == check }
    }
}

object SonarRules {

    const val THE_FILE_THAT_CONFIGURES_THE_SCANNER =
        "build-logic/src/main/kotlin/psp.sonar-conventions.gradle.kts"

    const val FEWEST_WORDS_THAT_STATE_AN_ANSWER = 20

    const val FEWEST_WORDS_THAT_STATE_A_CONDITION = 12

    const val SWITCHED_OFF = "OFF"

    private val WORD = Regex("[A-Za-z][A-Za-z']*")

    private val AN_IGNORE_FILTER = Regex("""property\(\s*"sonar\.issue\.ignore""")

    private val A_CONFIGURED_RULE_KEY = Regex("""ruleKey"\s*,\s*"(java:S\d+)"""")

    private val A_SUPPRESS_WARNINGS = Regex("""@SuppressWarnings\s*\(([^)]*)\)""")

    private val A_WARNING_IT_NAMES = Regex(""""([^"]+)"""")

    private val HOW_THE_SCANNER_SPELLS_A_RULE = Regex("""java:S\d+""")

    private val HOW_THE_COMPILER_SPELLS_A_CHECK = Regex("""[A-Z][A-Za-z0-9]*""")

    val THE_CATEGORIES_JAVACS_OWN_LINT_PUBLISHES: Set<String> =
        setOf(
            "auxiliaryclass",
            "cast",
            "classfile",
            "dangling-doc-comments",
            "dep-ann",
            "deprecation",
            "divzero",
            "empty",
            "exports",
            "fallthrough",
            "finally",
            "identity",
            "incubating",
            "lossy-conversions",
            "missing-explicit-ctor",
            "module",
            "opens",
            "options",
            "output-file-clash",
            "overloads",
            "overrides",
            "path",
            "preview",
            "processing",
            "rawtypes",
            "removal",
            "requires-automatic",
            "requires-transitive-automatic",
            "restricted",
            "serial",
            "static",
            "strictfp",
            "synchronization",
            "text-blocks",
            "this-escape",
            "try",
            "unchecked",
            "varargs",
        )

    val THE_KEYS_XLINT_TAKES_THAT_NAME_NO_CATEGORY: Set<String> = setOf("all", "none")

    const val THE_RULE_KEY_NO_ENTRY_OF_THIS_TABLE_LISTS = "java:S1234"

    private const val THE_QUOTE_SPLIT_OFF_SO_THE_SPECIMEN_IS_NOT_A_FILTER_THIS_SWEEP_FINDS = "\""

    val THE_FORM_A_BUILD_SCRIPT_WRITES_A_RULE_KEY_IN: String =
        "property(" +
            THE_QUOTE_SPLIT_OFF_SO_THE_SPECIMEN_IS_NOT_A_FILTER_THIS_SWEEP_FINDS +
            "sonar.issue.ignore.multicriteria.S1234.ruleKey" +
            THE_QUOTE_SPLIT_OFF_SO_THE_SPECIMEN_IS_NOT_A_FILTER_THIS_SWEEP_FINDS +
            ", " +
            THE_QUOTE_SPLIT_OFF_SO_THE_SPECIMEN_IS_NOT_A_FILTER_THIS_SWEEP_FINDS +
            THE_RULE_KEY_NO_ENTRY_OF_THIS_TABLE_LISTS +
            THE_QUOTE_SPLIT_OFF_SO_THE_SPECIMEN_IS_NOT_A_FILTER_THIS_SWEEP_FINDS +
            ")"

    val THE_FORM_A_JAVA_SOURCE_WRITES_A_SUPPRESSED_WARNING_IN: String =
        """@SuppressWarnings({"unchecked", "$THE_RULE_KEY_NO_ENTRY_OF_THIS_TABLE_LISTS"})"""

    fun multicriteriaIdOf(rule: String): String = rule.substringAfter(':')

    fun ignoreFiltersConfiguredIn(source: String): Int = AN_IGNORE_FILTER.findAll(source).count()

    fun ruleKeysConfiguredIn(source: String): List<String> =
        A_CONFIGURED_RULE_KEY.findAll(source).map { it.groupValues[1] }.toList()

    fun everyWarningSuppressedInSource(source: String): List<String> =
        A_SUPPRESS_WARNINGS.findAll(source)
            .flatMap { A_WARNING_IT_NAMES.findAll(it.groupValues[1]) }
            .map { it.groupValues[1] }
            .toList()

    fun howAWarningIsNamed(warning: String): HowAWarningIsNamed =
        when {
            HOW_THE_SCANNER_SPELLS_A_RULE.matches(warning) -> HowAWarningIsNamed.A_SCANNER_RULE
            HOW_THE_COMPILER_SPELLS_A_CHECK.matches(warning) -> HowAWarningIsNamed.A_COMPILER_CHECK
            THE_CATEGORIES_JAVACS_OWN_LINT_PUBLISHES.contains(warning) ->
                HowAWarningIsNamed.THE_COMPILERS_OWN_LINT
            else -> HowAWarningIsNamed.NOTHING_THIS_GATE_CAN_PLACE
        }

    fun ruleKeysSuppressedInSource(source: String): List<String> =
        everyWarningSuppressedInSource(source).filter {
            howAWarningIsNamed(it) == HowAWarningIsNamed.A_SCANNER_RULE
        }

    fun theFormsThisSweepReads(): Map<String, Pair<String, (String) -> List<String>>> =
        mapOf(
            "a rule key written into a scanner ignore filter by hand" to
                (THE_FORM_A_BUILD_SCRIPT_WRITES_A_RULE_KEY_IN to ::ruleKeysConfiguredIn),
            "a warning named in a `@SuppressWarnings`" to
                (THE_FORM_A_JAVA_SOURCE_WRITES_A_SUPPRESSED_WARNING_IN to
                    ::everyWarningSuppressedInSource),
            "a `@SuppressWarnings` warning placed as a rule of the scanner" to
                (THE_FORM_A_JAVA_SOURCE_WRITES_A_SUPPRESSED_WARNING_IN to
                    ::ruleKeysSuppressedInSource),
        )

    fun aFormThisSweepCanNoLongerRead(
        forms: Map<String, Pair<String, (String) -> List<String>>> = theFormsThisSweepReads(),
        plantedIn: String = THE_RULE_KEY_NO_ENTRY_OF_THIS_TABLE_LISTS,
    ): List<String> =
        forms.filterNot { (_, form) -> form.second(form.first).contains(plantedIn) }.keys.sorted()

    fun aCheckNothingAnswers(
        warnings: List<String>,
        answered: List<String> =
            SuppressedCheck.switchedOffByAnAnnotation().map(SuppressedCheck::check),
    ): List<String> =
        warnings
            .filter { howAWarningIsNamed(it) == HowAWarningIsNamed.A_COMPILER_CHECK }
            .filterNot(answered::contains)
            .distinct()
            .sorted()

    fun aWarningThatNamesEveryCategoryAtOnce(
        warnings: List<String>,
        naming: Set<String> = THE_KEYS_XLINT_TAKES_THAT_NAME_NO_CATEGORY,
    ): List<String> = warnings.filter(naming::contains).distinct().sorted()

    fun aWarningThisGateCannotPlace(
        warnings: List<String>,
        naming: Set<String> = THE_KEYS_XLINT_TAKES_THAT_NAME_NO_CATEGORY,
    ): List<String> =
        warnings
            .filter { howAWarningIsNamed(it) == HowAWarningIsNamed.NOTHING_THIS_GATE_CAN_PLACE }
            .filterNot(naming::contains)
            .distinct()
            .sorted()

    fun aCheckNoSourceSwitchesOff(
        warnings: List<String>,
        listed: List<String> =
            SuppressedCheck.switchedOffByAnAnnotation().map(SuppressedCheck::check),
    ): List<String> = listed.filterNot(warnings::contains).sorted()

    fun aCheckTheCompilerWasToldToSwitchOffThatNothingAnswers(
        severities: Map<String, String>,
        listed: List<String> =
            SuppressedCheck.switchedOffByTheCompiler().map {
                "${it.whereItIsSwitchedOff}/${it.check}"
            },
        off: String = SWITCHED_OFF,
    ): List<String> =
        severities.filterValues { it == off }.keys.filterNot(listed::contains).sorted()

    fun aCheckListedAsSwitchedOffTheCompilerStillRuns(
        severities: Map<String, String>,
        listed: List<String> =
            SuppressedCheck.switchedOffByTheCompiler().map {
                "${it.whereItIsSwitchedOff}/${it.check}"
            },
        off: String = SWITCHED_OFF,
    ): List<String> =
        listed
            .filterNot { severities[it] == off }
            .map { "$it is recorded as switched off and the compiler was given ${severities[it]}" }
            .sorted()

    fun anEntryThatListsTheKeyNoEntryMay(
        ruleKeys: List<String> = SuppressedRule.entries.map(SuppressedRule::ruleKey),
        checks: List<String> = SuppressedCheck.entries.map(SuppressedCheck::check),
        theSpecimens: String = THE_RULE_KEY_NO_ENTRY_OF_THIS_TABLE_LISTS,
    ): List<String> = (ruleKeys + checks).filter { it == theSpecimens }

    fun aSuppressionNothingAnswers(
        ruleKeys: List<String>,
        answered: List<String> = SuppressedRule.entries.map(SuppressedRule::ruleKey),
    ): List<String> = ruleKeys.filterNot(answered::contains).distinct().sorted()

    fun aTreeThatContributedNothing(filesByTree: Map<String, Int>): List<String> =
        filesByTree.filterValues { it == 0 }.keys.sorted()

    fun aFileConfiguringTheScannerBeyondTheOneThatOwnsIt(
        filtersByFile: Map<String, Int>,
        theOwner: String = THE_FILE_THAT_CONFIGURES_THE_SCANNER,
    ): List<String> =
        filtersByFile.filterValues { it > 0 }.keys.filterNot { it == theOwner }.sorted()

    fun theOwningFileConfiguresNoFilter(
        filtersByFile: Map<String, Int>,
        theOwner: String = THE_FILE_THAT_CONFIGURES_THE_SCANNER,
    ): Boolean = filtersByFile.getOrDefault(theOwner, 0) == 0

    fun anAnswerThatSaysTooLittle(
        answers: Map<String, String> =
            SuppressionAnswer.entries.associate { it.name to it.whatAnswersIt }
    ): List<String> =
        answers.filterValues { wordsIn(it) < FEWEST_WORDS_THAT_STATE_AN_ANSWER }.keys.sorted()

    fun aConditionThatSaysTooLittle(
        conditions: Map<String, String> = everyRecordedCondition()
    ): List<String> =
        conditions
            .filterValues { it.isNotBlank() && wordsIn(it) < FEWEST_WORDS_THAT_STATE_A_CONDITION }
            .keys
            .sorted()

    fun aRuleWhoseConditionWasNeverRecovered(
        conditions: Map<String, String> = everyRecordedCondition()
    ): List<String> = conditions.filterValues(String::isBlank).keys.sorted()

    fun everyRecordedCondition(): Map<String, String> =
        SuppressedRule.entries.associate { it.ruleKey to it.whatWouldTurnItBackOn } +
            SuppressedCheck.entries.associate {
                it.whatThisTableCallsIt to it.whatWouldTurnItBackOn
            }

    fun anAnswerNoSuppressionNames(
        answers: List<String> = SuppressionAnswer.entries.map(SuppressionAnswer::name),
        named: List<String> = everyAnswerASuppressionNames(),
    ): List<String> = answers.filterNot(named::contains)

    fun everyAnswerASuppressionNames(): List<String> =
        SuppressedRule.entries.map { it.answeredBy.name } +
            SuppressedCheck.entries.map { it.answeredBy.name }

    fun aGateThisBuildNoLongerRegisters(
        registered: List<String>,
        standIns: List<String> = standInTasks(),
    ): List<String> = standIns.distinct().filterNot(registered::contains)

    fun aSettingAStandInReliesOnThatIsGone(
        observed: Map<String, String>,
        relied: Map<String, String> = settingsStandInsRelyOn(),
    ): List<String> =
        relied
            .filterNot { (setting, value) -> observed[setting] == value }
            .map { (setting, value) -> "$setting should be $value and is ${observed[setting]}" }
            .sorted()

    fun aGateNoSuppressionStandsOn(
        gates: List<String> = ThisBuildsOwnGate.entries.map(ThisBuildsOwnGate::task),
        standIns: List<String> = standInTasks(),
    ): List<String> = gates.filterNot(standIns::contains)

    fun anIdTwoRulesShare(
        ruleKeys: List<String> = SuppressedRule.entries.map(SuppressedRule::ruleKey)
    ): List<String> =
        ruleKeys.groupBy(::multicriteriaIdOf).filterValues { it.size > 1 }.keys.sorted()

    fun settingsStandInsRelyOn(): Map<String, String> =
        SuppressedRule.entries
            .mapNotNull { it.enforcedInsteadBy }
            .distinct()
            .fold(emptyMap()) { all, gate -> all + gate.theSettingsItReliesOn }

    fun theDecisionRecord(): List<String> =
        SuppressedRule.entries.map {
            listOf(
                    "${it.ruleKey} in ${it.resourceKey}",
                    "answered by ${it.answeredBy.name}: ${it.answeredBy.whatAnswersIt}",
                    it.enforcedInsteadBy?.let { gate ->
                        "stands in: ${gate.task}${settingsOf(gate)}"
                    },
                    it.whatWouldTurnItBackOn
                        .ifBlank { null }
                        ?.let { condition ->
                            "turn it back on when: $condition"
                        },
                )
                .filterNotNull()
                .joinToString("\n      ")
        } +
            SuppressedCheck.entries.map {
                listOf(
                        "${it.check} switched off in ${it.whereItIsSwitchedOff}",
                        "answered by ${it.answeredBy.name}: ${it.answeredBy.whatAnswersIt}",
                        it.whatWouldTurnItBackOn
                            .ifBlank { null }
                            ?.let { condition -> "turn it back on when: $condition" },
                    )
                    .filterNotNull()
                    .joinToString("\n      ")
            }

    private fun settingsOf(gate: ThisBuildsOwnGate): String =
        if (gate.theSettingsItReliesOn.isEmpty()) ""
        else
            gate.theSettingsItReliesOn.entries.joinToString(", ", " with ") {
                "${it.key}=${it.value}"
            }

    private fun wordsIn(text: String): Int = WORD.findAll(text).count()

    private fun standInTasks(): List<String> =
        SuppressedRule.entries.mapNotNull { it.enforcedInsteadBy?.task }
}
