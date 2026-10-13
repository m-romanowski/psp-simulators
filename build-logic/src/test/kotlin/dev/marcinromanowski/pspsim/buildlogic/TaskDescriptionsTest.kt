package dev.marcinromanowski.pspsim.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskDescriptionsTest {

    private fun onlyOne(source: String): ADescription {
        val found = TaskDescriptions.inSource(source)
        assertEquals(1, found.size, "expected exactly one description in: $source")
        return found.single()
    }

    @Test
    fun `a description split across concatenated literals is read as one sentence`() {
        val registered =
            """
            tasks.register<VerifyNothing>("verifyNothing") {
                description =
                    "Fails when a thing " +
                        "is not another thing."
            }
            """

        val described = onlyOne(registered)

        assertEquals("Fails when a thing is not another thing.", described.text)
        assertEquals("verifyNothing", described.task)
        assertEquals(8, described.words)
        assertEquals(1, described.sentences)
    }

    @Test
    fun `the task a description belongs to is the one registered above it, not the file's first`() {
        val two =
            """
            tasks.register<A>("writeFirst") { description = "Writes the first thing." }
            tasks.register<B>("verifySecond") { description = "Fails on the second thing." }
            """

        assertEquals(
            listOf(
                "writeFirst" to "Writes the first thing.",
                "verifySecond" to "Fails on the second thing.",
            ),
            TaskDescriptions.inSource(two).map { it.task to it.text },
        )
    }

    @Test
    fun `a description reached by tasks named rather than register is still attributed`() {
        val named =
            """
            tasks.named("check") {
                description = "Runs every gate of this build."
            }
            """

        assertEquals("check", onlyOne(named).task)
    }

    @Test
    fun `an unterminated literal is read as no description rather than as the rest of the file`() {
        val broken =
            """
            tasks.register<A>("verifyBroken") {
                description = "Fails when
                classpath = somethingElse
            }
            """

        assertEquals(emptyList<ADescription>(), TaskDescriptions.inSource(broken))
    }

    @Test
    fun `concatenation stops at the first thing that is not a literal`() {
        val followed =
            """
            tasks.register<A>("verifyStops") {
                description = "Fails when a thing is wrong."
                classpath = sourceSets["test"].runtimeClasspath
            }
            """

        assertEquals("Fails when a thing is wrong.", onlyOne(followed).text)
    }

    @Test
    fun `a path spells its segments and a backtick is not part of a word`() {
        val described =
            onlyOne(
                """
                tasks.register<A>("verifyPaths") {
                    description = "Reads src/main/resources and `openApiControlAnswers`."
                }
                """
            )

        assertEquals(6, described.words)
        assertEquals(2, ADescription("t", 1, "`one` two").words)
        assertEquals(1, ADescription("t", 1, "provider's").words)
    }

    @Test
    fun `a listing entry is too long on either count, and the bounds are the ones published`() {
        assertEquals(45, TaskDescriptions.MOST_WORDS_A_LISTING_ENTRY_READS)
        assertEquals(2, TaskDescriptions.MOST_SENTENCES_A_LISTING_ENTRY_READS)

        val fortySix = "Fails " + (1..45).joinToString(" ") { "word" } + "."
        val three = "Fails on one thing. It is a thing. That is why."

        assertEquals(46, ADescription("t", 1, fortySix).words)
        assertTrue(TaskDescriptions.tooLongToRead(ADescription("t", 1, fortySix)))
        assertEquals(3, ADescription("t", 1, three).sentences)
        assertTrue(TaskDescriptions.tooLongToRead(ADescription("t", 1, three)))

        val fortyFiveInTwo = "Fails on a thing. " + (1..41).joinToString(" ") { "word" } + "."
        assertEquals(45, ADescription("t", 1, fortyFiveInTwo).words)
        assertEquals(2, ADescription("t", 1, fortyFiveInTwo).sentences)
        assertFalse(TaskDescriptions.tooLongToRead(ADescription("t", 1, fortyFiveInTwo)))
    }

    @Test
    fun `a description nothing registered above is named as unattributable rather than guessed`() {
        val loose =
            """
            someOtherThing {
                description = "Describes nothing this scan can name."
            }
            """

        assertEquals(TaskDescriptions.UNNAMED, onlyOne(loose).task)
    }

    @Test
    fun `the line reported is the line the assignment stands on`() {
        val third =
            """
            tasks.register<A>("verifyLine") {

                description = "Fails on a thing."
            }
            """
                .trimIndent()

        assertEquals(3, onlyOne(third).line)
    }

    @Test
    fun `a literal followed by an identifier is counted as a description not read whole`() {
        val extracted =
            """
            tasks.register<A>("verifyPartly") {
                description = "Fails when a thing " + THE_REST_OF_THE_SENTENCE
            }
            """

        assertEquals("Fails when a thing ", onlyOne(extracted).text)
        assertEquals(1, TaskDescriptions.howManyDescriptionsAreSet(extracted))
        assertEquals(
            0,
            TaskDescriptions.howManyWereReadWhole(extracted),
            "a prefix of a description is not the description, so this must not count as read",
        )
    }

    @Test
    fun `the three other ways of setting a description are counted even though none parses`() {
        val otherWays =
            """
            tasks.register<A>("one") { setDescription("Fails on a thing a reader can see.") }
            tasks.register<B>("two") { description.set("Fails on another thing entirely.") }
            tasks.register<C>("three") { description = THE_CONSTANT }
            """

        assertEquals(
            emptyList<ADescription>(),
            TaskDescriptions.inSource(otherWays),
            "none of the three is a literal this scan can measure",
        )
        assertEquals(3, TaskDescriptions.howManyDescriptionsAreSet(otherWays))
        assertEquals(0, TaskDescriptions.howManyWereReadWhole(otherWays))
    }

    @Test
    fun `an empty description is read whole and says too little`() {
        val empty =
            """
            tasks.register<A>("verifyNothingSaid") { description = "" }
            """

        assertEquals(1, TaskDescriptions.howManyDescriptionsAreSet(empty))
        assertEquals(1, TaskDescriptions.howManyWereReadWhole(empty))
        assertTrue(TaskDescriptions.saysTooLittle(ADescription("verifyNothingSaid", 1, "")))
        assertTrue(TaskDescriptions.saysTooLittle(ADescription("t", 1, "Fails on a thing.")))
        assertFalse(
            TaskDescriptions.saysTooLittle(
                ADescription("t", 1, "Fails when a thing is another thing.")
            )
        )
    }

    @Test
    fun `a description on a dsl accessor is attributed to the task the accessor names`() {
        val accessed =
            """
            tasks.test {
                description = "Runs the unit tests of this build's own logic."
            }
            """

        assertEquals("test", onlyOne(accessed).task)
    }

    @Test
    fun `the words tasks register and named are not read as task names`() {
        val registered =
            """
            tasks.register<A>("verifyReal") {
                description = "Fails when a real thing is wrong somewhere."
            }
            """

        assertEquals("verifyReal", onlyOne(registered).task)
    }

    @Test
    fun `an allowance widens the task it names and leaves every other task on the ceiling`() {
        val sixty = "Fails " + (1..60).joinToString(" ") { "word" } + "."
        val allowed = ADescription("verifyAllowed", 1, sixty)
        val notAllowed = ADescription("verifyOther", 1, sixty)
        val registry = mapOf("verifyAllowed" to ALongerEntry(70, "it names five providers"))

        assertEquals(70, TaskDescriptions.theMostWordsAllowed("verifyAllowed", registry))
        assertEquals(
            TaskDescriptions.MOST_WORDS_A_LISTING_ENTRY_READS,
            TaskDescriptions.theMostWordsAllowed("verifyOther", registry),
        )
        assertFalse(TaskDescriptions.tooLongToRead(allowed, registry))
        assertTrue(TaskDescriptions.tooLongToRead(notAllowed, registry))
    }

    @Test
    fun `an allowance no description needs is an offence, and one being used is not`() {
        val registry = mapOf("verifyAllowed" to ALongerEntry(70, "it names five providers"))
        val sixty =
            ADescription("verifyAllowed", 1, "Fails " + (1..60).joinToString(" ") { "word" } + ".")
        val short = ADescription("verifyAllowed", 1, "Fails when a thing is another thing.")

        assertEquals(
            emptyList<String>(),
            TaskDescriptions.anAllowanceNoDescriptionNeeds(listOf(sixty), registry),
        )
        assertEquals(
            listOf("verifyAllowed"),
            TaskDescriptions.anAllowanceNoDescriptionNeeds(listOf(short), registry),
        )
    }

    @Test
    fun `an allowance that names no reason is an offence whatever its number`() {
        assertEquals(
            listOf("verifySilent"),
            TaskDescriptions.anAllowanceThatNamesNoReason(
                mapOf(
                    "verifyExplained" to ALongerEntry(70, "it names five providers"),
                    "verifySilent" to ALongerEntry(70, "   "),
                )
            ),
        )
        assertEquals(
            emptyList<String>(),
            TaskDescriptions.anAllowanceThatNamesNoReason(
                TaskDescriptions.THE_TASKS_A_LONGER_ENTRY_IS_ALLOWED
            ),
        )
    }
}
