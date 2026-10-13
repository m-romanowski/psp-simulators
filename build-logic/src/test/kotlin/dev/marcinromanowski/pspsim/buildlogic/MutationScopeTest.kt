package dev.marcinromanowski.pspsim.buildlogic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MutationScopeTest {

    @Test
    fun `a star in a glob stands for any run of characters, dots included`() {
        assertTrue(
            MutationScope.matches(
                "dev.marcinromanowski.pspsim.**.Jdbc*",
                "dev.marcinromanowski.pspsim.kernel.a.JdbcThing",
            )
        )
        assertTrue(
            MutationScope.matches(
                "dev.marcinromanowski.pspsim.**.*Controller",
                "dev.marcinromanowski.pspsim.ui.UiController",
            )
        )
        assertFalse(
            MutationScope.matches(
                "dev.marcinromanowski.pspsim.**.*Controller",
                "dev.marcinromanowski.pspsim.ui.UiScreen",
            )
        )
    }

    @Test
    fun `a glob matches the whole class name and not a prefix of it`() {
        assertFalse(
            MutationScope.matches(
                "dev.marcinromanowski.pspsim.provider.*.*Module",
                "com.other.GrowModule",
            )
        )
        assertTrue(
            MutationScope.matches(
                "dev.marcinromanowski.pspsim.provider.*.*Module",
                "dev.marcinromanowski.pspsim.provider.grow.GrowModule",
            )
        )
    }

    @Test
    fun `every glob the exclusions name carries a ratchet, and no ratchet is spare`() {
        val excluded =
            MutationScope.mutationAdapterExclusions + MutationScope.mutationWiringExclusions
        assertTrue(MutationScope.mutationExclusionBudgets.keys == excluded)
    }

    @Test
    fun `a class of a target package is in the target packages and a foreign one is not`() {
        assertTrue(MutationScope.inTargetPackage("dev.marcinromanowski.pspsim.kernel.Thing"))
        assertTrue(MutationScope.inTargetPackage("dev.marcinromanowski.pspsim.platform.Thing"))
        assertFalse(MutationScope.inTargetPackage("dev.marcinromanowski.pspsim.Thing"))
        assertFalse(MutationScope.inTargetPackage("com.other.kernel.Thing"))
    }

    @Test
    fun `a class in a package no target glob names is unaccounted for`() {
        val unaccounted =
            MutationScope.unaccountedBy(
                setOf("dev.marcinromanowski.pspsim.kernel.*"),
                listOf(
                    "dev.marcinromanowski.pspsim.kernel.Thing",
                    "dev.marcinromanowski.pspsim.platform.Thing",
                ),
            )

        assertTrue(
            unaccounted == listOf("dev.marcinromanowski.pspsim.platform.Thing"),
            unaccounted.toString(),
        )
    }

    @Test
    fun `an exclusion glob does not account for a class no target glob names`() {
        val unaccounted =
            MutationScope.unaccountedBy(
                setOf("dev.marcinromanowski.pspsim.kernel.*"),
                listOf("dev.marcinromanowski.pspsim.platform.SmokeConfiguration"),
            )

        assertTrue(
            MutationScope.matches(MutationScope.CONFIGURATION_GLOB, unaccounted.single()),
            unaccounted.toString(),
        )
    }

    @Test
    fun `the class coverage excludes is the one class the scope does not ask about`() {
        val compiled =
            listOf(
                "dev.marcinromanowski.pspsim.Other",
                "dev.marcinromanowski.pspsim.SimulatorApplication",
            )

        assertTrue(CoverageScope.isExcluded("dev.marcinromanowski.pspsim.SimulatorApplication"))
        assertFalse(CoverageScope.isExcluded("dev.marcinromanowski.pspsim.Other"))
        val unaccounted = MutationScope.unaccountedBy(emptySet(), compiled)
        assertTrue(
            unaccounted == listOf("dev.marcinromanowski.pspsim.Other"),
            unaccounted.toString(),
        )
    }
}
