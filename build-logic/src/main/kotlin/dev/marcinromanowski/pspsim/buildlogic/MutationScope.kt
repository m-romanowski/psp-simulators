package dev.marcinromanowski.pspsim.buildlogic

object MutationScope {

    val mutationTargetPackages =
        listOf(
            "dev.marcinromanowski.pspsim.control",
            "dev.marcinromanowski.pspsim.kernel",
            "dev.marcinromanowski.pspsim.platform",
            "dev.marcinromanowski.pspsim.provider",
            "dev.marcinromanowski.pspsim.ui",
        )

    const val JDBC_ADAPTER_GLOB = "dev.marcinromanowski.pspsim.**.Jdbc*"

    const val CONTROLLER_GLOB = "dev.marcinromanowski.pspsim.**.*Controller"

    const val CONFIGURATION_GLOB = "dev.marcinromanowski.pspsim.**.*Configuration"

    const val PROVIDER_MODULE_GLOB = "dev.marcinromanowski.pspsim.provider.*.*Module"

    val mutationAdapterExclusions = setOf(JDBC_ADAPTER_GLOB, CONTROLLER_GLOB)

    val mutationWiringExclusions = setOf(CONFIGURATION_GLOB, PROVIDER_MODULE_GLOB)

    val mutationExclusionBudgets =
        mapOf(
            JDBC_ADAPTER_GLOB to (238 to 9),
            CONTROLLER_GLOB to (248 to 13),
            CONFIGURATION_GLOB to (3 to 2),
            PROVIDER_MODULE_GLOB to (0 to 0),
        )

    fun matches(glob: String, className: String): Boolean =
        Regex(glob.split("*").joinToString(".*") { Regex.escape(it) }).matches(className)

    fun inTargetPackage(className: String): Boolean = mutationTargetPackages.any {
        className.startsWith("$it.")
    }

    fun scorable(compiledMainClasses: List<String>): List<String> =
        compiledMainClasses.filterNot(CoverageScope::isExcluded)

    fun unaccountedBy(targetGlobs: Set<String>, compiledMainClasses: List<String>): List<String> =
        scorable(compiledMainClasses).filterNot { className ->
            targetGlobs.any { matches(it, className) }
        }
}
