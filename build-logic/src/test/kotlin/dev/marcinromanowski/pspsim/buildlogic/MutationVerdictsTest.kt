package dev.marcinromanowski.pspsim.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MutationVerdictsTest {

    private fun reportOf(vararg mutants: Pair<String, String>): List<Mutant> =
        MutationReport.mutantsIn(
            mutants.joinToString("\n", "<mutations>\n", "\n</mutations>") { (status, index) ->
                """
                <mutation detected='true' status='$status' numberOfTestsRun='0'>
                <mutatedClass>dev.marcinromanowski.pspsim.provider.autra.AutraJsonReader</mutatedClass>
                <mutatedMethod>array</mutatedMethod>
                <lineNumber>85</lineNumber>
                <mutator>org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator</mutator>
                <index>$index</index>
                </mutation>
                """
                    .trimIndent()
            }
        )

    private val everyStatus =
        reportOf(
            "KILLED" to "1",
            "SURVIVED" to "2",
            "TIMED_OUT" to "3",
            "NON_VIABLE" to "4",
            "MEMORY_ERROR" to "5",
            "NOT_STARTED" to "6",
            "STARTED" to "7",
            "RUN_ERROR" to "8",
            "NO_COVERAGE" to "9",
            "EQUIVALENT" to "10",
        )

    private fun indexesOf(mutants: List<Mutant>): List<String> = mutants.map(Mutant::index)

    private fun outcomeOf(fullRun: String, reRun: String): String {
        val full = reportOf(fullRun to "1")
        val narrowed = reportOf(reRun to "1")
        return when {
            MutationVerdicts.unaccountedForByTheRerun(full, narrowed).isNotEmpty() -> "UNREACHED"
            MutationVerdicts.thoseNoRerunCanExcuse(full, narrowed).isNotEmpty() -> "FAILS"
            MutationVerdicts.thoseAFirstHiccupExplains(full, narrowed).isNotEmpty() ->
                "NAMED AS A FIRST HICCUP"
            MutationVerdicts.thoseAStandingLimitExplains(full, narrowed).isNotEmpty() ->
                "NAMED AS A STANDING LIMIT"
            else -> "CLEARED"
        }
    }

    @Test
    fun `a report in which every status PIT declares appears leaves none without a verdict`() {
        assertEquals(10, everyStatus.size)
        assertEquals(emptyList<String>(), MutationVerdicts.statusesWithNoVerdictHere(everyStatus))
    }

    @Test
    fun `a status the table does not name is reported, once, and does not stop the known ones`() {
        val withAnUnknownOne =
            everyStatus + reportOf("HEAP_EXHAUSTED" to "11", "HEAP_EXHAUSTED" to "12")
        assertEquals(
            listOf("HEAP_EXHAUSTED"),
            MutationVerdicts.statusesWithNoVerdictHere(withAnUnknownOne),
        )
        assertEquals(
            listOf("3", "5", "8"),
            indexesOf(MutationVerdicts.awaitingANarrowedRerun(withAnUnknownOne)),
        )
    }

    @Test
    fun `a status carrying a digit or the wrong case reaches the guard instead of vanishing`() {
        val oddlyNamed = reportOf("TIMED_OUT2" to "1", "timed_out" to "2")
        assertEquals(2, oddlyNamed.size)
        assertEquals(
            listOf("TIMED_OUT2", "timed_out"),
            MutationVerdicts.statusesWithNoVerdictHere(oddlyNamed),
        )
        assertEquals(
            emptyList<String>(),
            indexesOf(MutationVerdicts.awaitingANarrowedRerun(oddlyNamed)),
        )
    }

    @Test
    fun `each verdict gathers the statuses the table gave it and nothing else`() {
        assertEquals(
            listOf("2"),
            indexesOf(MutationVerdicts.with(everyStatus, StatusVerdict.NAMED_AS_A_SURVIVOR)),
        )
        assertEquals(
            listOf("4", "10"),
            indexesOf(
                MutationVerdicts.with(everyStatus, StatusVerdict.NAMED_AS_PITS_OWN_JUDGEMENT)
            ),
        )
        assertEquals(
            listOf("6", "7"),
            indexesOf(
                MutationVerdicts.with(everyStatus, StatusVerdict.NAMED_AS_A_RUN_THAT_DID_NOT_FINISH)
            ),
        )
        assertEquals(
            listOf("9"),
            indexesOf(MutationVerdicts.with(everyStatus, StatusVerdict.FAILS_THE_BUILD)),
        )
        assertEquals(
            listOf("1"),
            indexesOf(MutationVerdicts.with(everyStatus, StatusVerdict.KILLED_BY_A_TEST)),
        )
    }

    @Test
    fun `the mutants awaiting a narrowed re-run are the timed out, the memory error and the run error`() {
        assertEquals(
            listOf("TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR"),
            MutationVerdicts.awaitingANarrowedRerun(everyStatus).map(Mutant::status),
        )
    }

    @Test
    fun `only a run error the full run already stamped and the re-run stamped again fails`() {
        assertEquals(
            mapOf(
                "RUN_ERROR then RUN_ERROR" to "FAILS",
                "TIMED_OUT then RUN_ERROR" to "NAMED AS A FIRST HICCUP",
                "MEMORY_ERROR then RUN_ERROR" to "NAMED AS A FIRST HICCUP",
                "TIMED_OUT then TIMED_OUT" to "NAMED AS A STANDING LIMIT",
                "TIMED_OUT then MEMORY_ERROR" to "NAMED AS A STANDING LIMIT",
                "MEMORY_ERROR then MEMORY_ERROR" to "NAMED AS A STANDING LIMIT",
                "MEMORY_ERROR then TIMED_OUT" to "NAMED AS A STANDING LIMIT",
                "RUN_ERROR then TIMED_OUT" to "NAMED AS A STANDING LIMIT",
                "RUN_ERROR then MEMORY_ERROR" to "NAMED AS A STANDING LIMIT",
                "TIMED_OUT then KILLED" to "CLEARED",
                "MEMORY_ERROR then KILLED" to "CLEARED",
                "MEMORY_ERROR then SURVIVED" to "CLEARED",
                "RUN_ERROR then KILLED" to "CLEARED",
            ),
            listOf(
                    "RUN_ERROR" to "RUN_ERROR",
                    "TIMED_OUT" to "RUN_ERROR",
                    "MEMORY_ERROR" to "RUN_ERROR",
                    "TIMED_OUT" to "TIMED_OUT",
                    "TIMED_OUT" to "MEMORY_ERROR",
                    "MEMORY_ERROR" to "MEMORY_ERROR",
                    "MEMORY_ERROR" to "TIMED_OUT",
                    "RUN_ERROR" to "TIMED_OUT",
                    "RUN_ERROR" to "MEMORY_ERROR",
                    "TIMED_OUT" to "KILLED",
                    "MEMORY_ERROR" to "KILLED",
                    "MEMORY_ERROR" to "SURVIVED",
                    "RUN_ERROR" to "KILLED",
                )
                .associate { (fullRun, reRun) ->
                    "$fullRun then $reRun" to outcomeOf(fullRun, reRun)
                },
        )
    }

    @Test
    fun `a memory error the narrowed re-run kills has reached a verdict and is not held`() {
        val narrowed = reportOf("KILLED" to "5", "KILLED" to "3", "KILLED" to "8")
        assertEquals(
            emptyList<String>(),
            indexesOf(MutationVerdicts.stillAwaitingAVerdict(everyStatus, narrowed)),
        )
    }

    @Test
    fun `a memory error the narrowed re-run stamps again is held, with the narrowed run's status`() {
        val narrowed =
            reportOf("MEMORY_ERROR" to "5", "KILLED" to "3", "KILLED" to "8", "SURVIVED" to "2")
        val held = MutationVerdicts.stillAwaitingAVerdict(everyStatus, narrowed)
        assertEquals(listOf("5"), indexesOf(held))
        assertEquals(
            listOf("5"),
            indexesOf(MutationVerdicts.thoseAStandingLimitExplains(everyStatus, narrowed)),
        )
        assertEquals(
            emptyList<String>(),
            indexesOf(MutationVerdicts.thoseNoRerunCanExcuse(everyStatus, narrowed)),
        )
    }

    @Test
    fun `a timed out that comes back a memory error has still reached no verdict`() {
        val narrowed = reportOf("MEMORY_ERROR" to "3", "KILLED" to "5", "KILLED" to "8")
        val held = MutationVerdicts.stillAwaitingAVerdict(everyStatus, narrowed)
        assertEquals(listOf("MEMORY_ERROR"), held.map(Mutant::status))
        assertEquals(
            listOf("3"),
            indexesOf(MutationVerdicts.thoseAStandingLimitExplains(everyStatus, narrowed)),
        )
    }

    @Test
    fun `the three outcomes of one re-run are told apart in the same report`() {
        val narrowed = reportOf("RUN_ERROR" to "8", "RUN_ERROR" to "3", "MEMORY_ERROR" to "5")
        assertEquals(
            listOf("8"),
            indexesOf(MutationVerdicts.thoseNoRerunCanExcuse(everyStatus, narrowed)),
        )
        assertEquals(
            listOf("3"),
            indexesOf(MutationVerdicts.thoseAFirstHiccupExplains(everyStatus, narrowed)),
        )
        assertEquals(
            listOf("5"),
            indexesOf(MutationVerdicts.thoseAStandingLimitExplains(everyStatus, narrowed)),
        )
        assertEquals(
            listOf("TIMED_OUT"),
            MutationVerdicts.statusesOf(
                MutationVerdicts.asTheFullRunSawThem(
                    everyStatus,
                    MutationVerdicts.thoseAFirstHiccupExplains(everyStatus, narrowed),
                )
            ),
        )
    }

    @Test
    fun `a mutant the narrowed report says nothing about is unaccounted for rather than cleared`() {
        val narrowed = reportOf("KILLED" to "3", "KILLED" to "5")
        assertEquals(
            listOf("8"),
            indexesOf(MutationVerdicts.unaccountedForByTheRerun(everyStatus, narrowed)),
        )
        assertEquals(
            emptyList<String>(),
            indexesOf(MutationVerdicts.stillAwaitingAVerdict(everyStatus, narrowed)),
        )
        assertEquals(
            listOf("3", "5", "8"),
            indexesOf(MutationVerdicts.unaccountedForByTheRerun(everyStatus, emptyList())),
        )
        assertEquals(
            emptyList<String>(),
            indexesOf(
                MutationVerdicts.unaccountedForByTheRerun(
                    everyStatus,
                    reportOf("KILLED" to "3", "KILLED" to "5", "KILLED" to "8"),
                )
            ),
        )
    }

    @Test
    fun `a re-run that leaves one not started has not accounted for it any more than omitting it`() {
        listOf("NOT_STARTED", "STARTED").forEach { leftUnfinished ->
            val narrowed = reportOf(leftUnfinished to "5", "KILLED" to "3", "KILLED" to "8")
            assertEquals(
                listOf("5"),
                indexesOf(MutationVerdicts.unaccountedForByTheRerun(everyStatus, narrowed)),
                leftUnfinished,
            )
            assertEquals(
                emptyList<String>(),
                indexesOf(MutationVerdicts.stillAwaitingAVerdict(everyStatus, narrowed)),
                leftUnfinished,
            )
        }
        val judged = reportOf("NO_COVERAGE" to "5", "KILLED" to "3", "SURVIVED" to "8")
        assertEquals(
            emptyList<String>(),
            indexesOf(MutationVerdicts.unaccountedForByTheRerun(everyStatus, judged)),
        )
    }

    @Test
    fun `a mutant with no coverage or a survivor is never waiting on a re-run to account for it`() {
        assertEquals(
            emptyList<String>(),
            indexesOf(
                MutationVerdicts.unaccountedForByTheRerun(
                    reportOf("SURVIVED" to "2", "NO_COVERAGE" to "9", "KILLED" to "1"),
                    emptyList(),
                )
            ),
        )
        assertTrue(MutationVerdicts.stillAwaitingAVerdict(emptyList(), everyStatus).isEmpty())
    }

    @Test
    fun `the statuses of a set are named once each, in a reader's order`() {
        assertEquals(
            listOf("MEMORY_ERROR", "RUN_ERROR", "TIMED_OUT"),
            MutationVerdicts.statusesOf(MutationVerdicts.awaitingANarrowedRerun(everyStatus)),
        )
        assertEquals(
            listOf("MEMORY_ERROR"),
            MutationVerdicts.statusesOf(reportOf("MEMORY_ERROR" to "1", "MEMORY_ERROR" to "2")),
        )
    }
}
