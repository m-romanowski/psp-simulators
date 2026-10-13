package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.gradle.api.GradleException

object ExclusionBudget {

    fun argumentsFor(
        classesRoot: File,
        excludedGlobs: Set<String>,
        budgets: Map<String, Pair<Int, Int>>,
    ): List<String> {
        val undeclared = excludedGlobs.filterNot(budgets::containsKey).sorted()
        if (undeclared.isNotEmpty()) {
            throw GradleException(
                "`pitest.excludedClasses` names $undeclared, and " +
                    "`mutationExclusionBudgets` declares no branch ratchet for them. An " +
                    "exclusion nothing bounds is the defect this task exists to stop, so " +
                    "measure the family and write its number down before excluding it."
            )
        }
        val stale = budgets.keys.filterNot(excludedGlobs::contains).sorted()
        if (stale.isNotEmpty()) {
            throw GradleException(
                "`mutationExclusionBudgets` bounds $stale, which `pitest.excludedClasses` no " +
                    "longer names, so that number is guarding nothing and the two have drifted."
            )
        }

        val compiled =
            CompiledClasses.namesUnder(classesRoot).filter(MutationScope::inTargetPackage)
        val families =
            excludedGlobs.sorted().map { glob ->
                val budget = budgets.getValue(glob)
                val named = compiled.filter { MutationScope.matches(glob, it) }
                listOf(glob, budget.first, budget.second, named.joinToString(",")).joinToString("|")
            }
        return listOf(classesRoot.absolutePath) + families
    }
}
