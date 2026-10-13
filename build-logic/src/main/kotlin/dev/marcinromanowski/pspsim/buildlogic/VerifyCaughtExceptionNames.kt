package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class VerifyCaughtExceptionNames : DefaultTask() {

    @get:InputFiles abstract val sources: ConfigurableFileCollection

    @get:Internal abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val uninformative = CaughtExceptionNames.namesThatExplainNoFailure
        val root: File = projectRoot.get().asFile
        val clause = Regex("""catch\s*\(\s*(?:final\s+)?[\w.]+(?:\s*\|\s*[\w.]+)*\s+(\w+)\s*\)""")
        val offences = mutableListOf<String>()
        var clauses = 0
        sources.files
            .filter { it.isFile }
            .sortedBy { it.path }
            .forEach { source ->
                val text = source.readText()
                clause.findAll(text).forEach { found ->
                    clauses++
                    val bound = found.groupValues[1]
                    if (uninformative.contains(bound.lowercase())) {
                        val line = text.take(found.range.first).count { it == '\n' } + 1
                        offences.add(
                            source.toRelativeString(root) +
                                " line " +
                                line +
                                ": " +
                                found.value.trim()
                        )
                    }
                }
            }
        if (offences.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "verifyCaughtExceptionNames: " +
                            offences.size +
                            " catch clause(s) bind a name that explains no failure:"
                    )
                    offences.forEach { appendLine("  - $it") }
                    appendLine(
                        "Name the parameter for what went wrong, the way `notBase64`, " +
                            "`connectTimedOut` and `unreadable` already do, or bind `_` where " +
                            "nothing is caught but the control flow. `_` is enforced by the " +
                            "compiler; a name is the sentence a comment is not allowed to be."
                    )
                    appendLine(
                        "This gate stands in for `${SuppressedRule.S7467.ruleKey}`: " +
                            SuppressedRule.S7467.answeredBy.whatAnswersIt
                    )
                    append(
                        "To widen or narrow what counts as uninformative, edit " +
                            "`namesThatExplainNoFailure` in " +
                            "build-logic/src/main/kotlin/dev/marcinromanowski/pspsim/buildlogic/CaughtExceptionNames.kt."
                    )
                }
            )
        }

        logger.lifecycle(
            "verifyCaughtExceptionNames: $clauses catch clauses, every bound name explains " +
                "its failure."
        )
    }
}
