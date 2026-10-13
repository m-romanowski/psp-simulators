package dev.marcinromanowski.pspsim.buildlogic

object CoverageScope {

    val EXCLUDED_CLASS_FILES = listOf("dev/marcinromanowski/pspsim/SimulatorApplication.class")

    val EXCLUDED_SOURCE_GLOBS: List<String> = EXCLUDED_CLASS_FILES.map {
        "**/" + it.substringAfterLast('/').removeSuffix(".class") + ".java"
    }

    val EXCLUDED_CLASS_NAMES: List<String> = EXCLUDED_CLASS_FILES.map {
        it.removeSuffix(".class").replace('/', '.')
    }

    fun isExcluded(className: String): Boolean = EXCLUDED_CLASS_NAMES.contains(className)
}
