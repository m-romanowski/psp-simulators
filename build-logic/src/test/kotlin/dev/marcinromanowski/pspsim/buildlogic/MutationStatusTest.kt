package dev.marcinromanowski.pspsim.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.pitest.mutationtest.DetectionStatus
import org.pitest.util.ExitCode

class MutationStatusTest {

    @Test
    fun `the table is every status PIT declares, in PIT's order, with PIT's own detected flag`() {
        assertEquals(
            DetectionStatus.values().map { it.name to it.isDetected },
            MutationStatus.entries.map { it.name to it.creditedToTheScore },
        )
    }

    @Test
    fun `every status carries the verdict this gate reaches on it`() {
        assertEquals(
            mapOf(
                "KILLED" to StatusVerdict.KILLED_BY_A_TEST,
                "SURVIVED" to StatusVerdict.NAMED_AS_A_SURVIVOR,
                "TIMED_OUT" to StatusVerdict.RE_RUN_THEN_NAMED,
                "NON_VIABLE" to StatusVerdict.NAMED_AS_PITS_OWN_JUDGEMENT,
                "MEMORY_ERROR" to StatusVerdict.RE_RUN_THEN_NAMED,
                "NOT_STARTED" to StatusVerdict.NAMED_AS_A_RUN_THAT_DID_NOT_FINISH,
                "STARTED" to StatusVerdict.NAMED_AS_A_RUN_THAT_DID_NOT_FINISH,
                "RUN_ERROR" to StatusVerdict.RE_RUN_THEN_FAILED,
                "NO_COVERAGE" to StatusVerdict.FAILS_THE_BUILD,
                "EQUIVALENT" to StatusVerdict.NAMED_AS_PITS_OWN_JUDGEMENT,
            ),
            MutationStatus.entries.associate { it.name to it.verdict },
        )
    }

    @Test
    fun `only an unfinished run fails to account for the mutant it was stamped on`() {
        assertEquals(
            listOf("NOT_STARTED", "STARTED"),
            MutationStatus.entries.filterNot { it.accountsForAMutant }.map { it.name },
        )
    }

    @Test
    fun `a status PIT does not declare today resolves to no verdict rather than to a default`() {
        assertNull(MutationStatus.of("HEAP_EXHAUSTED"))
        assertNull(MutationStatus.of("memory_error"))
        assertNull(MutationStatus.of(""))
        assertEquals(MutationStatus.MEMORY_ERROR, MutationStatus.of("MEMORY_ERROR"))
    }

    @Test
    fun `the statuses a minion's exit code can stamp are exactly the ones re-run narrowed`() {
        assertEquals(
            listOf("TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR"),
            MutationStatus.stampedByAMinionExitCode(),
        )
        assertEquals(
            MutationStatus.stampedByAMinionExitCode().toSet(),
            ExitCode.values()
                .filterNot { it.isOk }
                .map { DetectionStatus.getForErrorExitCode(it).name }
                .toSet(),
        )
    }

    @Test
    fun `an out-of-memory exit is the status PIT credits and no test judged`() {
        val outOfMemory = DetectionStatus.getForErrorExitCode(ExitCode.OUT_OF_MEMORY)
        assertEquals(MutationStatus.MEMORY_ERROR.name, outOfMemory.name)
        assertEquals(true, outOfMemory.isDetected)
        assertEquals(true, MutationStatus.MEMORY_ERROR.creditedToTheScore)
        assertEquals(true, MutationStatus.MEMORY_ERROR.awaitsANarrowedRerun)
    }
}
