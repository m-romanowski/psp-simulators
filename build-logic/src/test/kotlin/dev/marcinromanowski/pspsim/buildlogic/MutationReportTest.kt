package dev.marcinromanowski.pspsim.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MutationReportTest {

    private val report =
        """
        <mutations>
        <mutation detected='true' status='KILLED' numberOfTestsRun='3'>
        <sourceFile>Thing.java</sourceFile>
        <mutatedClass>dev.marcinromanowski.pspsim.kernel.Thing</mutatedClass>
        <mutatedMethod>decide</mutatedMethod>
        <lineNumber>41</lineNumber>
        <mutator>org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator</mutator>
        <index>7</index>
        </mutation>
        <mutation detected='false' status='RUN_ERROR' numberOfTestsRun='0'>
        <mutatedClass>dev.marcinromanowski.pspsim.kernel.Other</mutatedClass>
        <mutatedMethod>of</mutatedMethod>
        <lineNumber>9</lineNumber>
        <mutator>org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator</mutator>
        <index>2</index>
        </mutation>
        <mutation detected='false' status='NO_COVERAGE' numberOfTestsRun='0'>
        <mutatedClass>dev.marcinromanowski.pspsim.ui.Screen</mutatedClass>
        <mutatedMethod>head&lt;T&gt;</mutatedMethod>
        <lineNumber>12</lineNumber>
        <mutator>org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator</mutator>
        <index>1</index>
        </mutation>
        </mutations>
        """
            .trimIndent()

    @Test
    fun `every mutated class is named once per mutant`() {
        assertEquals(
            listOf(
                "dev.marcinromanowski.pspsim.kernel.Thing",
                "dev.marcinromanowski.pspsim.kernel.Other",
                "dev.marcinromanowski.pspsim.ui.Screen",
            ),
            MutationReport.mutatedClasses(report),
        )
    }

    @Test
    fun `a mutant carries its status, class, method, line, mutator and index`() {
        val killed = MutationReport.mutantsIn(report).first()
        assertEquals("KILLED", killed.status)
        assertEquals("dev.marcinromanowski.pspsim.kernel.Thing", killed.className)
        assertEquals("decide", killed.method)
        assertEquals("41", killed.line)
        assertEquals("NegateConditionalsMutator", killed.mutator)
        assertEquals("7", killed.index)
    }

    @Test
    fun `a mutant is spelled out as the class, method, line and mutator a reader compares`() {
        assertEquals(
            "dev.marcinromanowski.pspsim.kernel.Thing.decide(), line 41: NegateConditionalsMutator",
            MutationReport.mutantsIn(report).first().spelledOut(),
        )
    }

    @Test
    fun `a mutant PIT reached no verdict on is spelled out with the status it was stamped`() {
        assertEquals(
            "RUN_ERROR at dev.marcinromanowski.pspsim.kernel.Other.of(), line 9: VoidMethodCallMutator",
            MutationReport.mutantsIn(report)
                .single { it.status == "RUN_ERROR" }
                .spelledOutWithItsStatus(),
        )
    }

    @Test
    fun `an escaped method name is read back as the characters it stood for`() {
        assertEquals("head<T>", MutationReport.mutantsIn(report)[2].method)
    }

    @Test
    fun `identity is everything but the status, so one run's mutant is found in another's`() {
        val erred = MutationReport.mutantsIn(report).single { it.status == "RUN_ERROR" }
        assertEquals(
            listOf(
                "dev.marcinromanowski.pspsim.kernel.Other",
                "of",
                "9",
                "VoidMethodCallMutator",
                "2",
            ),
            erred.identity,
        )
    }

    @Test
    fun `a report that is not there is no mutants rather than a failure`() {
        assertEquals(emptyList<Mutant>(), MutationReport.mutantsIn(""))
        assertEquals(emptyList<String>(), MutationReport.mutatedClasses(""))
    }
}
