package dev.marcinromanowski.pspsim.buildlogic

import java.io.File

object CompiledClasses {

    fun namesUnder(root: File): List<String> =
        if (!root.isDirectory) {
            emptyList()
        } else {
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map {
                    it.toRelativeString(root)
                        .removeSuffix(".class")
                        .replace('/', '.')
                        .replace('\\', '.')
                }
                .filterNot { it.endsWith("package-info") }
                .toList()
                .sorted()
        }
}
