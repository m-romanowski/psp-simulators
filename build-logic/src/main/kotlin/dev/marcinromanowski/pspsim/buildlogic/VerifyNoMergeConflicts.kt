package dev.marcinromanowski.pspsim.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

abstract class VerifyNoMergeConflicts : DefaultTask() {

    @get:InputFiles abstract val scanned: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val declared = scanned.files.sortedBy { it.path }
        val absent = declared.filterNot { it.isFile }
        if (absent.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "verifyNoMergeConflicts: " +
                            absent.size +
                            " declared path(s) are not files that exist:"
                    )
                    absent.forEach { appendLine("  - " + it.path) }
                    append(
                        "A path that resolves to nothing used to be skipped in silence, and " +
                            "`application.yaml` was skipped that way for as long as it was " +
                            "declared: the gate scanned neither it nor the real file under " +
                            "src/main/resources. Name a path that exists, or stop naming it."
                    )
                }
            )
        }
        if (declared.isEmpty()) {
            throw GradleException(
                "verifyNoMergeConflicts: the scan resolved to no files at all, so this run " +
                    "proved nothing about this tree. A gate that is green over nothing is " +
                    "indistinguishable from a gate that is green over a clean tree."
            )
        }

        val marked = mutableListOf<String>()
        declared.forEach { source ->
            source.readLines().forEachIndexed { index, line ->
                if (line.startsWith("<<<<<<< ") || line.startsWith(">>>>>>> ")) {
                    marked.add(source.path + " line " + (index + 1) + ": " + line.take(40))
                }
            }
        }
        if (marked.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "verifyNoMergeConflicts: " +
                            marked.size +
                            " git conflict marker(s) are still in the tree:"
                    )
                    marked.forEach { appendLine("  - " + it) }
                    append(
                        "A half-resolved merge reads as finished work to every other check " +
                            "in this build. Resolve it and gate the result, because the run " +
                            "that passed described a tree nobody meant to ship."
                    )
                }
            )
        }

        logger.lifecycle(
            "verifyNoMergeConflicts: " +
                declared.size +
                " files in this tree carry no git conflict marker."
        )
    }
}
