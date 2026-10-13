package dev.marcinromanowski.pspsim.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SonarRulesTest {

    private val everyRuleKey = SuppressedRule.entries.map(SuppressedRule::ruleKey)

    private val settingsThatHold = SonarRules.settingsStandInsRelyOn()

    private fun wordsIn(text: String): Int = Regex("[A-Za-z][A-Za-z']*").findAll(text).count()

    @Test
    fun `every rule switched off in the scanner names what answers it`() {
        assertEquals(
            mapOf(
                "java:S6878" to "AN_ARM_NAMES_THE_CASE_IT_HANDLES",
                "java:S6213" to "RECORD_IS_THIS_DOMAINS_OWN_NOUN",
                "java:S7467" to "A_CAUGHT_EXCEPTIONS_NAME_IS_THE_EXPLANATION",
                "java:S107" to "A_COMPOSITION_ROOT_IS_AS_WIDE_AS_ITS_COLLABORATORS",
                "java:S5838" to "AN_ASSERTION_NAMES_THE_VALUE_IT_CLAIMS_ABOUT",
                "java:S5853" to "A_FAILURE_NAMES_THE_ONE_CLAIM_THAT_BROKE",
                "java:S2699" to "A_SHARED_FLOOR_CARRIES_THE_ASSERTIONS",
                "java:S1186" to "AN_EMPTY_BODY_IS_THE_BEHAVIOUR",
                "java:S6353" to "A_WIRE_FORMAT_NAMES_ITS_OWN_DIGITS",
                "java:S4449" to "A_NARROWED_OVERRIDE_RULES_THE_NULL_OUT",
                "java:S108" to "AN_EXHAUSTIVE_SWITCH_FORCES_A_DECISION",
            ),
            SuppressedRule.entries.associate { it.ruleKey to it.answeredBy.name },
        )
    }

    @Test
    fun `each rule key is the scanner's spelling of the constant that carries it`() {
        assertEquals(
            SuppressedRule.entries.map { "java:" + it.name },
            SuppressedRule.entries.map(SuppressedRule::ruleKey),
        )
        assertEquals(11, SuppressedRule.entries.size)
    }

    @Test
    fun `every rule is scoped to the source root where it fires, and no wider`() {
        assertEquals(
            mapOf(
                "java:S6878" to "src/main/java/**/*.java",
                "java:S6213" to "**/*.java",
                "java:S7467" to "**/*.java",
                "java:S107" to "src/main/java/**/*.java",
                "java:S5838" to "src/test/java/**/*.java",
                "java:S5853" to "src/test/java/**/*.java",
                "java:S2699" to "src/test/java/**/*.java",
                "java:S1186" to "**/*.java",
                "java:S6353" to "src/main/java/**/*.java",
                "java:S4449" to "src/main/java/**/*.java",
                "java:S108" to "**/*.java",
            ),
            SuppressedRule.entries.associate { it.ruleKey to it.resourceKey },
        )
    }

    @Test
    fun `every rule carries a recovered re-enable condition, and none was composed`() {
        assertEquals(emptyList<String>(), SonarRules.aRuleWhoseConditionWasNeverRecovered())
        assertEquals(
            emptyMap<String, Int>(),
            SuppressedRule.entries
                .associate { it.ruleKey to wordsIn(it.whatWouldTurnItBackOn) }
                .filterValues { it < SonarRules.FEWEST_WORDS_THAT_STATE_A_CONDITION },
        )
        assertTrue(
            SuppressedRule.S6213.whatWouldTurnItBackOn.contains("`yield`, `sealed`, `permits`"),
            SuppressedRule.S6213.whatWouldTurnItBackOn,
        )
    }

    @Test
    fun `the two rules that stand on something name the gate and the settings its reason needs`() {
        assertEquals(
            mapOf(
                "java:S7467" to "verifyCaughtExceptionNames",
                "java:S4449" to "compileJava",
            ),
            SuppressedRule.entries
                .filter { it.enforcedInsteadBy != null }
                .associate { it.ruleKey to it.enforcedInsteadBy?.task },
        )
        assertEquals(
            mapOf(
                "NullAway" to "ERROR",
                "NullAway:AnnotatedPackages" to "dev.marcinromanowski.pspsim",
                "NullAway:JSpecifyMode" to "true",
            ),
            settingsThatHold,
        )
    }

    @Test
    fun `the table this build holds passes every arm of the gate`() {
        assertEquals(emptyList<String>(), SonarRules.aSuppressionNothingAnswers(everyRuleKey))
        assertEquals(emptyList<String>(), SonarRules.anAnswerThatSaysTooLittle())
        assertEquals(emptyList<String>(), SonarRules.aConditionThatSaysTooLittle())
        assertEquals(emptyList<String>(), SonarRules.anAnswerNoSuppressionNames())
        assertEquals(emptyList<String>(), SonarRules.anIdTwoRulesShare())
        assertEquals(
            emptyList<String>(),
            SonarRules.aGateThisBuildNoLongerRegisters(
                listOf("check", "compileJava", "verifyCaughtExceptionNames")
            ),
        )
        assertEquals(emptyList<String>(), SonarRules.aGateNoSuppressionStandsOn())
        assertEquals(
            emptyList<String>(),
            SonarRules.aSettingAStandInReliesOnThatIsGone(settingsThatHold),
        )
    }

    @Test
    fun `a rule switched off through the scanner's own property is read, and reported once`() {
        val script =
            """
            property("sonar.issue.ignore.multicriteria", "S1234,S6878")
            property("sonar.issue.ignore.multicriteria.S1234.ruleKey", "java:S1234")
            property("sonar.issue.ignore.multicriteria.S1234.resourceKey", "**/*.java")
            property("sonar.issue.ignore.multicriteria.S6878.ruleKey", "java:S6878")
            property("sonar.issue.ignore.multicriteria.S9999.ruleKey", "java:S1234")
            """
                .trimIndent()
        val configured = SonarRules.ruleKeysConfiguredIn(script)
        assertEquals(listOf("java:S1234", "java:S6878", "java:S1234"), configured)
        assertEquals(listOf("java:S1234"), SonarRules.aSuppressionNothingAnswers(configured))
        assertEquals(5, SonarRules.ignoreFiltersConfiguredIn(script))
        assertNull(SuppressedRule.of("java:S1234"))
        assertEquals(SuppressedRule.S6878, SuppressedRule.of("java:S6878"))
    }

    @Test
    fun `naming a rule in prose is not switching it off, so a gate may say what it stands in for`() {
        val gate =
            """
            appendLine("This gate stands in for `java:S7467`, which is suppressed because it")
            val A_RULE_KEY = Regex("java:S1234")
            """
                .trimIndent()
        assertEquals(emptyList<String>(), SonarRules.ruleKeysConfiguredIn(gate))
        assertEquals(emptyList<String>(), SonarRules.ruleKeysSuppressedInSource(gate))
        assertEquals(0, SonarRules.ignoreFiltersConfiguredIn(gate))
    }

    @Test
    fun `a rule switched off by an annotation in source is read, alone or beside another warning`() {
        val java =
            """
            @SuppressWarnings("java:S1234")
            void one() {}

            @SuppressWarnings({"unchecked", "java:S4449", "java:S9876"})
            void two() {}

            @SuppressWarnings("unchecked")
            void three() {}
            """
                .trimIndent()
        val annotated = SonarRules.ruleKeysSuppressedInSource(java)
        assertEquals(listOf("java:S1234", "java:S4449", "java:S9876"), annotated)
        assertEquals(
            listOf("java:S1234", "java:S9876"),
            SonarRules.aSuppressionNothingAnswers(annotated),
        )
    }

    @Test
    fun `a tree that contributed no file is reported, and a tree that contributed one is not`() {
        assertEquals(
            listOf("the Java sources the scanner reads", "the build scripts of this tree"),
            SonarRules.aTreeThatContributedNothing(
                mapOf(
                    "the build scripts of this tree" to 0,
                    "the build logic this gate lives in" to 24,
                    "the Java sources the scanner reads" to 0,
                )
            ),
        )
        assertEquals(
            emptyList<String>(),
            SonarRules.aTreeThatContributedNothing(mapOf("one tree" to 1)),
        )
    }

    @Test
    fun `the ignore filters belong to one file, and that file still configuring them is checked`() {
        val owner = SonarRules.THE_FILE_THAT_CONFIGURES_THE_SCANNER
        assertEquals(
            emptyList<String>(),
            SonarRules.aFileConfiguringTheScannerBeyondTheOneThatOwnsIt(
                mapOf(owner to 3, "build.gradle.kts" to 0)
            ),
        )
        assertFalse(SonarRules.theOwningFileConfiguresNoFilter(mapOf(owner to 3)))
        assertEquals(
            listOf("build.gradle.kts"),
            SonarRules.aFileConfiguringTheScannerBeyondTheOneThatOwnsIt(
                mapOf(owner to 3, "build.gradle.kts" to 1)
            ),
        )
        assertTrue(SonarRules.theOwningFileConfiguresNoFilter(mapOf(owner to 0, "other" to 2)))
        assertTrue(SonarRules.theOwningFileConfiguresNoFilter(emptyMap()))
    }

    @Test
    fun `an answer or a condition too short to act on is reported, and the word floor is the test`() {
        assertEquals(
            listOf("A_SILENT_ONE", "BECAUSE"),
            SonarRules.anAnswerThatSaysTooLittle(
                mapOf(
                    "A_SILENT_ONE" to "   ",
                    "BECAUSE" to "because",
                    "ONE_THAT_SPEAKS" to
                        SuppressionAnswer.A_SHARED_FLOOR_CARRIES_THE_ASSERTIONS.whatAnswersIt,
                )
            ),
        )
        assertEquals(
            listOf("java:S1234"),
            SonarRules.aConditionThatSaysTooLittle(
                mapOf(
                    "java:S1234" to "when it is fixed",
                    "java:S4321" to "",
                    "java:S6353" to SuppressedRule.S6353.whatWouldTurnItBackOn,
                )
            ),
        )
        assertEquals(
            listOf("java:S4321"),
            SonarRules.aRuleWhoseConditionWasNeverRecovered(
                mapOf("java:S1234" to "when it is fixed", "java:S4321" to "  ")
            ),
        )
    }

    @Test
    fun `a recorded answer no suppressed rule names is reported as answering nothing`() {
        assertEquals(
            listOf("ANSWERS_NOTHING"),
            SonarRules.anAnswerNoSuppressionNames(
                listOf("ANSWERS_A_RULE", "ANSWERS_NOTHING"),
                listOf("ANSWERS_A_RULE"),
            ),
        )
        assertEquals(
            emptyList<String>(),
            SonarRules.anAnswerNoSuppressionNames(
                listOf("ANSWERS_A_RULE"),
                listOf("ANSWERS_A_RULE", "ANSWERS_A_RULE"),
            ),
        )
    }

    @Test
    fun `a stand-in gate this build no longer registers is reported, once, and a registered one is not`() {
        assertEquals(
            listOf("verifyWhatWasRenamed"),
            SonarRules.aGateThisBuildNoLongerRegisters(
                listOf("check", "compileJava"),
                listOf("compileJava", "verifyWhatWasRenamed", "verifyWhatWasRenamed"),
            ),
        )
        assertEquals(
            emptyList<String>(),
            SonarRules.aGateThisBuildNoLongerRegisters(
                listOf("compileJava"),
                listOf("compileJava"),
            ),
        )
    }

    @Test
    fun `a setting the stand-in relies on that was downgraded, dropped or retargeted is reported`() {
        assertEquals(
            listOf("NullAway should be ERROR and is WARN"),
            SonarRules.aSettingAStandInReliesOnThatIsGone(
                settingsThatHold + mapOf("NullAway" to "WARN")
            ),
        )
        assertEquals(
            listOf("NullAway:JSpecifyMode should be true and is null"),
            SonarRules.aSettingAStandInReliesOnThatIsGone(
                settingsThatHold - "NullAway:JSpecifyMode"
            ),
        )
        assertEquals(
            listOf(
                "NullAway:AnnotatedPackages should be dev.marcinromanowski.pspsim and is com.elsewhere"
            ),
            SonarRules.aSettingAStandInReliesOnThatIsGone(
                settingsThatHold + mapOf("NullAway:AnnotatedPackages" to "com.elsewhere")
            ),
        )
        assertEquals(3, SonarRules.aSettingAStandInReliesOnThatIsGone(emptyMap()).size)
    }

    @Test
    fun `a gate recorded as standing in for a rule that no rule stands on is reported`() {
        assertEquals(
            listOf("verifyNobodyStandsOnThis"),
            SonarRules.aGateNoSuppressionStandsOn(
                listOf("compileJava", "verifyNobodyStandsOnThis"),
                listOf("compileJava"),
            ),
        )
    }

    @Test
    fun `two rules reducing to one multicriteria id are reported, because one would overwrite the other`() {
        assertEquals(
            listOf("S108"),
            SonarRules.anIdTwoRulesShare(listOf("java:S108", "kotlin:S108", "java:S107")),
        )
        assertEquals(
            emptyList<String>(),
            SonarRules.anIdTwoRulesShare(listOf("java:S108", "java:S107")),
        )
        assertEquals("S6878", SonarRules.multicriteriaIdOf("java:S6878"))
    }

    @Test
    fun `the decision record a failure prints carries every rule, its reason and its condition`() {
        val record = SonarRules.theDecisionRecord()
        assertEquals(13, record.size)
        assertEquals(
            everyRuleKey,
            record.take(11).map { it.substringBefore(" in ") },
        )
        record.take(11).forEachIndexed { at, entry ->
            val rule = SuppressedRule.entries[at]
            assertTrue(entry.contains(rule.answeredBy.whatAnswersIt), entry)
            assertTrue(entry.contains(rule.whatWouldTurnItBackOn), entry)
            assertTrue(entry.contains(rule.resourceKey), entry)
        }
        assertEquals(
            listOf(
                "EmptyCatch switched off in a `@SuppressWarnings` in source",
                "NullAway switched off in compileTestJava",
            ),
            record.drop(11).map { it.substringBefore("\n") },
        )
        record.drop(11).forEachIndexed { at, entry ->
            val check = SuppressedCheck.entries[at]
            assertTrue(entry.contains(check.answeredBy.whatAnswersIt), entry)
            assertTrue(entry.contains(check.whatWouldTurnItBackOn), entry)
        }
        assertTrue(
            record[2].contains("stands in: verifyCaughtExceptionNames"),
            record[2],
        )
        assertTrue(
            record[9].contains("stands in: compileJava with NullAway=ERROR"),
            record[9],
        )
        assertFalse(record[0].contains("stands in:"), record[0])
    }

    @Test
    fun `every form this sweep reads is read back against a specimen written in that form`() {
        assertEquals(
            listOf(
                "a `@SuppressWarnings` warning placed as a rule of the scanner",
                "a rule key written into a scanner ignore filter by hand",
                "a warning named in a `@SuppressWarnings`",
            ),
            SonarRules.theFormsThisSweepReads().keys.sorted(),
        )
        assertEquals(emptyList<String>(), SonarRules.aFormThisSweepCanNoLongerRead())
        assertEquals(
            listOf("a form that reads another key than the one planted in it"),
            SonarRules.aFormThisSweepCanNoLongerRead(
                mapOf(
                    "a form that reads another key than the one planted in it" to
                        ("""multicriteria.S4321.ruleKey", "java:S4321"""" to
                            SonarRules::ruleKeysConfiguredIn),
                    "a form still read" to
                        (SonarRules.THE_FORM_A_BUILD_SCRIPT_WRITES_A_RULE_KEY_IN to
                            SonarRules::ruleKeysConfiguredIn),
                )
            ),
        )
    }

    @Test
    fun `the specimen placed as a rule of the scanner is read through the placer, not past it`() {
        assertEquals(
            listOf(SonarRules.THE_RULE_KEY_NO_ENTRY_OF_THIS_TABLE_LISTS),
            SonarRules.ruleKeysSuppressedInSource(
                SonarRules.THE_FORM_A_JAVA_SOURCE_WRITES_A_SUPPRESSED_WARNING_IN
            ),
        )
        assertEquals(
            listOf(
                HowAWarningIsNamed.THE_COMPILERS_OWN_LINT,
                HowAWarningIsNamed.A_SCANNER_RULE,
            ),
            SonarRules.everyWarningSuppressedInSource(
                    SonarRules.THE_FORM_A_JAVA_SOURCE_WRITES_A_SUPPRESSED_WARNING_IN
                )
                .map(SonarRules::howAWarningIsNamed),
        )
    }

    @Test
    fun `no specimen is written in this source as the form it is a specimen of`() {
        val thisGatesOwnSource =
            java.io
                .File("src/main/kotlin/dev/marcinromanowski/pspsim/buildlogic/SonarRules.kt")
                .readText()
        assertEquals(emptyList<String>(), SonarRules.ruleKeysConfiguredIn(thisGatesOwnSource))
        assertEquals(0, SonarRules.ignoreFiltersConfiguredIn(thisGatesOwnSource))
        assertEquals(
            emptyList<String>(),
            SonarRules.ruleKeysSuppressedInSource(thisGatesOwnSource),
        )
        assertFalse(
            thisGatesOwnSource.contains(SonarRules.THE_FORM_A_BUILD_SCRIPT_WRITES_A_RULE_KEY_IN),
            SonarRules.THE_FORM_A_BUILD_SCRIPT_WRITES_A_RULE_KEY_IN,
        )
        assertFalse(
            thisGatesOwnSource.contains(
                SonarRules.THE_FORM_A_JAVA_SOURCE_WRITES_A_SUPPRESSED_WARNING_IN
            ),
            SonarRules.THE_FORM_A_JAVA_SOURCE_WRITES_A_SUPPRESSED_WARNING_IN,
        )
    }

    @Test
    fun `the key every specimen plants is listed by no entry of either table`() {
        assertEquals(emptyList<String>(), SonarRules.anEntryThatListsTheKeyNoEntryMay())
        assertNull(SuppressedRule.of(SonarRules.THE_RULE_KEY_NO_ENTRY_OF_THIS_TABLE_LISTS))
        assertEquals(
            listOf("java:S1234"),
            SonarRules.anEntryThatListsTheKeyNoEntryMay(
                listOf("java:S108", "java:S1234"),
                listOf("EmptyCatch"),
            ),
        )
    }

    @Test
    fun `a lowercase word javac publishes no category for is placed nowhere, and all is refused`() {
        assertEquals(38, SonarRules.THE_CATEGORIES_JAVACS_OWN_LINT_PUBLISHES.size)
        assertEquals(
            listOf(
                HowAWarningIsNamed.THE_COMPILERS_OWN_LINT,
                HowAWarningIsNamed.THE_COMPILERS_OWN_LINT,
                HowAWarningIsNamed.THE_COMPILERS_OWN_LINT,
                HowAWarningIsNamed.NOTHING_THIS_GATE_CAN_PLACE,
                HowAWarningIsNamed.NOTHING_THIS_GATE_CAN_PLACE,
                HowAWarningIsNamed.NOTHING_THIS_GATE_CAN_PLACE,
            ),
            listOf("unchecked", "deprecation", "this-escape", "all", "none", "whatever")
                .map(SonarRules::howAWarningIsNamed),
        )
        assertEquals(
            listOf("all", "none"),
            SonarRules.aWarningThatNamesEveryCategoryAtOnce(
                listOf("unchecked", "none", "all", "whatever", "all")
            ),
        )
        assertEquals(
            listOf("whatever"),
            SonarRules.aWarningThisGateCannotPlace(
                listOf("unchecked", "none", "all", "whatever", "EmptyCatch")
            ),
        )
        assertEquals(
            emptyList<String>(),
            SonarRules.aWarningThatNamesEveryCategoryAtOnce(listOf("unchecked", "EmptyCatch")),
        )
    }

    @Test
    fun `a warning is placed by the way it is spelled, and an unknown spelling is placed nowhere`() {
        val java =
            """
            @SuppressWarnings("unchecked")
            @SuppressWarnings({"EmptyCatch", "java:S4449", "empty_catch"})
            """
                .trimIndent()
        assertEquals(
            listOf("unchecked", "EmptyCatch", "java:S4449", "empty_catch"),
            SonarRules.everyWarningSuppressedInSource(java),
        )
        assertEquals(
            listOf(
                HowAWarningIsNamed.THE_COMPILERS_OWN_LINT,
                HowAWarningIsNamed.A_COMPILER_CHECK,
                HowAWarningIsNamed.A_SCANNER_RULE,
                HowAWarningIsNamed.NOTHING_THIS_GATE_CAN_PLACE,
            ),
            SonarRules.everyWarningSuppressedInSource(java).map(SonarRules::howAWarningIsNamed),
        )
        assertEquals(listOf("java:S4449"), SonarRules.ruleKeysSuppressedInSource(java))
        assertEquals(
            listOf("empty_catch"),
            SonarRules.aWarningThisGateCannotPlace(SonarRules.everyWarningSuppressedInSource(java)),
        )
        assertEquals(
            emptyList<String>(),
            SonarRules.aWarningThisGateCannotPlace(listOf("unchecked", "EmptyCatch", "java:S108")),
        )
    }

    @Test
    fun `a compiler check nothing answers is reported, and the one this build lists is not`() {
        assertEquals(
            mapOf(
                "EmptyCatch in a `@SuppressWarnings` in source" to
                    "A_BEST_EFFORT_CLOSE_HAS_NOTHING_TO_REPORT",
                "NullAway in compileTestJava" to "A_TEST_SENDS_THE_NULL_IT_IS_ABOUT",
            ),
            SuppressedCheck.entries.associate { it.whatThisTableCallsIt to it.answeredBy.name },
        )
        assertEquals(
            listOf("EmptyCatch"),
            SuppressedCheck.switchedOffByAnAnnotation().map(SuppressedCheck::check),
        )
        assertEquals(
            listOf("NullAway"),
            SuppressedCheck.switchedOffByTheCompiler().map(SuppressedCheck::check),
        )
        assertEquals(
            emptyList<String>(),
            SonarRules.aCheckNothingAnswers(listOf("unchecked", "EmptyCatch", "java:S108")),
        )
        assertEquals(
            listOf("ReturnValueIgnored", "UnusedVariable"),
            SonarRules.aCheckNothingAnswers(
                listOf("unchecked", "UnusedVariable", "EmptyCatch", "ReturnValueIgnored")
            ),
        )
        assertEquals(
            SuppressedCheck.EMPTY_CATCH_AT_A_BEST_EFFORT_CLOSE,
            SuppressedCheck.of("EmptyCatch"),
        )
        assertNull(SuppressedCheck.of("UnusedVariable"))
        assertNull(SuppressedCheck.of("NullAway"))
    }

    @Test
    fun `a check the compiler was told to switch off that nothing answers is reported`() {
        assertEquals(
            emptyList<String>(),
            SonarRules.aCheckTheCompilerWasToldToSwitchOffThatNothingAnswers(
                mapOf("compileJava/NullAway" to "ERROR", "compileTestJava/NullAway" to "OFF")
            ),
        )
        assertEquals(
            listOf("compileJava/EmptyCatch", "compileTestJava/ReturnValueIgnored"),
            SonarRules.aCheckTheCompilerWasToldToSwitchOffThatNothingAnswers(
                mapOf(
                    "compileJava/NullAway" to "ERROR",
                    "compileTestJava/NullAway" to "OFF",
                    "compileJava/EmptyCatch" to "OFF",
                    "compileTestJava/ReturnValueIgnored" to "OFF",
                    "compileJava/UnusedVariable" to "WARN",
                )
            ),
        )
    }

    @Test
    fun `a check listed as switched off that the compiler is running again is reported`() {
        assertEquals(
            emptyList<String>(),
            SonarRules.aCheckListedAsSwitchedOffTheCompilerStillRuns(
                mapOf("compileTestJava/NullAway" to "OFF")
            ),
        )
        assertEquals(
            listOf(
                "compileTestJava/NullAway is recorded as switched off and the compiler was given" +
                    " ERROR"
            ),
            SonarRules.aCheckListedAsSwitchedOffTheCompilerStillRuns(
                mapOf("compileTestJava/NullAway" to "ERROR")
            ),
        )
        assertEquals(
            listOf(
                "compileTestJava/NullAway is recorded as switched off and the compiler was given" +
                    " null"
            ),
            SonarRules.aCheckListedAsSwitchedOffTheCompilerStillRuns(emptyMap()),
        )
    }

    @Test
    fun `a listed check no source suppresses any more is reported as answering nothing`() {
        assertEquals(
            emptyList<String>(),
            SonarRules.aCheckNoSourceSwitchesOff(listOf("unchecked", "EmptyCatch")),
        )
        assertEquals(
            listOf("EmptyCatch"),
            SonarRules.aCheckNoSourceSwitchesOff(listOf("unchecked")),
        )
        assertEquals(
            emptyList<String>(),
            SonarRules.aCheckNothingAnswers(listOf("unchecked", "EmptyCatch")),
        )
    }

    @Test
    fun `the condition and the answer of a check are held to the same floors as a rule's`() {
        assertEquals(13, SonarRules.everyRecordedCondition().size)
        assertEquals(emptyList<String>(), SonarRules.aRuleWhoseConditionWasNeverRecovered())
        assertEquals(emptyList<String>(), SonarRules.aConditionThatSaysTooLittle())
        assertEquals(emptyList<String>(), SonarRules.anAnswerNoSuppressionNames())
        assertEquals(
            listOf("EmptyCatch"),
            SonarRules.aConditionThatSaysTooLittle(mapOf("EmptyCatch" to "when the ban goes")),
        )
        assertTrue(
            SonarRules.everyAnswerASuppressionNames()
                .contains("A_BEST_EFFORT_CLOSE_HAS_NOTHING_TO_REPORT")
        )
        assertTrue(
            SonarRules.everyAnswerASuppressionNames().contains("A_TEST_SENDS_THE_NULL_IT_IS_ABOUT")
        )
    }
}
