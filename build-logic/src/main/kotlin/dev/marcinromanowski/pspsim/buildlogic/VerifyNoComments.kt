package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class VerifyNoComments : DefaultTask() {

    @get:InputFiles abstract val sources: ConfigurableFileCollection

    @get:Internal abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val root: File = projectRoot.get().asFile

        val offences =
            sources.files.sorted().flatMap { source ->
                val text = source.readText()
                CommentScanner.commentsIn(text, CommentDialect.of(source.name)).map { (offset, kind)
                    ->
                    "${source.toRelativeString(root)}:${CommentScanner.lineOf(text, offset)}: $kind"
                }
            }

        if (offences.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "Comments are banned in this project for the duration of " +
                            "development. ${offences.size} found:"
                    )
                    offences.forEach { appendLine("  - $it") }
                    appendLine(
                        "A comment is the one claim about this system that no test can " +
                            "falsify. Five have already asserted something the code did not " +
                            "do, and one let a hold the simulator reported as cancelled be " +
                            "captured anyway. Put the fact in a test, or in doc/; or name " +
                            "the code so the sentence is not needed."
                    )
                    appendLine(
                        "String literals, char literals and text blocks are not scanned, so " +
                            "a URL and a Hebrew wire string are never reported here, and in a " +
                            "script neither are template literals nor a regular expression " +
                            "standing where an expression opens. A " +
                            "template is scanned for the four characters that open a markup " +
                            "comment, wherever they stand."
                    )
                    appendLine(
                        "To lift the ban, set `commentsAreBanned` to false in " +
                            "build-logic/src/main/kotlin/psp.source-conventions.gradle.kts."
                    )
                }
            )
        }

        logger.lifecycle(
            "verifyNoComments: ${sources.files.size} sources carry no comment of any kind."
        )
    }
}
