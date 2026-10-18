package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class ExclusionBudgetTest {

    @TempDir lateinit var classes: File

    private fun compile(vararg names: String) {
        names.forEach {
            val file = File(classes, it.replace('.', '/') + ".class")
            file.parentFile.mkdirs()
            file.writeText("")
        }
    }

    @Test
    fun `each family is one argument of glob, totals and the classes it names`() {
        compile(
            "dev.marcinromanowski.pspsim.kernel.JdbcThing",
            "dev.marcinromanowski.pspsim.ui.AScreen",
        )
        val arguments =
            ExclusionBudget.argumentsFor(
                classes,
                setOf("dev.marcinromanowski.pspsim.**.Jdbc*"),
                mapOf("dev.marcinromanowski.pspsim.**.Jdbc*" to (239 to 9)),
            )
        assertEquals(classes.absolutePath, arguments[0])
        assertEquals(
            "dev.marcinromanowski.pspsim.**.Jdbc*|239|9|dev.marcinromanowski.pspsim.kernel.JdbcThing",
            arguments[1],
        )
        assertEquals(2, arguments.size)
    }

    @Test
    fun `a package-info is no class of a family`() {
        compile(
            "dev.marcinromanowski.pspsim.kernel.JdbcThing",
            "dev.marcinromanowski.pspsim.kernel.package-info",
        )
        val arguments =
            ExclusionBudget.argumentsFor(
                classes,
                setOf("dev.marcinromanowski.pspsim.**.*"),
                mapOf("dev.marcinromanowski.pspsim.**.*" to (0 to 0)),
            )
        assertEquals(
            "dev.marcinromanowski.pspsim.**.*|0|0|dev.marcinromanowski.pspsim.kernel.JdbcThing",
            arguments[1],
        )
    }

    @Test
    fun `a class the glob matches outside the target packages is no class of a family`() {
        compile(
            "dev.marcinromanowski.pspsim.kernel.JdbcThing",
            "dev.marcinromanowski.pspsim.platform.JdbcThing",
            "dev.marcinromanowski.pspsim.nowhere.JdbcThing",
        )
        assertTrue(
            MutationScope.matches(
                MutationScope.JDBC_ADAPTER_GLOB,
                "dev.marcinromanowski.pspsim.nowhere.JdbcThing",
            )
        )
        assertFalse(MutationScope.inTargetPackage("dev.marcinromanowski.pspsim.nowhere.JdbcThing"))

        val arguments =
            ExclusionBudget.argumentsFor(
                classes,
                setOf(MutationScope.JDBC_ADAPTER_GLOB),
                mapOf(MutationScope.JDBC_ADAPTER_GLOB to (239 to 9)),
            )

        assertEquals(
            "dev.marcinromanowski.pspsim.**.Jdbc*|239|9|dev.marcinromanowski.pspsim.kernel.JdbcThing,dev.marcinromanowski.pspsim.platform.JdbcThing",
            arguments[1],
        )
    }

    @Test
    fun `an exclusion no ratchet bounds is refused`() {
        val refusal =
            assertThrows<GradleException> {
                ExclusionBudget.argumentsFor(
                    classes,
                    setOf("dev.marcinromanowski.pspsim.**.Jdbc*"),
                    emptyMap(),
                )
            }
        assertTrue(refusal.message!!.contains("declares no branch ratchet"), refusal.message)
    }

    @Test
    fun `a ratchet bounding an exclusion nothing names any more is refused`() {
        val refusal =
            assertThrows<GradleException> {
                ExclusionBudget.argumentsFor(
                    classes,
                    emptySet(),
                    mapOf("dev.marcinromanowski.pspsim.**.Gone" to (1 to 1)),
                )
            }
        assertTrue(refusal.message!!.contains("guarding nothing"), refusal.message)
    }

    @Test
    fun `the globs this build excludes are each bounded by the ratchet beside them`() {
        compile("dev.marcinromanowski.pspsim.ui.AController")
        val excluded =
            MutationScope.mutationAdapterExclusions + MutationScope.mutationWiringExclusions
        val arguments =
            ExclusionBudget.argumentsFor(classes, excluded, MutationScope.mutationExclusionBudgets)
        assertEquals(
            listOf(
                "dev.marcinromanowski.pspsim.**.*Configuration|3|2|",
                "dev.marcinromanowski.pspsim.**.*Controller|248|13|dev.marcinromanowski.pspsim.ui.AController",
                "dev.marcinromanowski.pspsim.**.Jdbc*|231|9|",
                "dev.marcinromanowski.pspsim.provider.*.*Module|0|0|",
            ),
            arguments.drop(1),
        )
    }
}
