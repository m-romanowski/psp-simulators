package dev.marcinromanowski.pspsim.buildlogic

import java.io.File

class PitInvocation(private val recorded: List<String>) {

    fun valuesOf(kind: String): List<String> =
        recorded.filter { it.substringBefore('\t') == kind }.map { it.substringAfter('\t') }

    fun narrowedTo(targetClasses: List<String>, reportDirectory: File): List<String> {
        val arguments =
            valuesOf(ARGUMENT).filterNot { argument ->
                NARROWING_OWNS.any { argument.startsWith(it) }
            } +
                listOf(
                    "--targetClasses=" + targetClasses.joinToString(","),
                    "--reportDir=" + reportDirectory.absolutePath,
                    "--outputFormats=XML",
                )
        return listOf(valuesOf(EXECUTABLE).single()) +
            valuesOf(JVM_ARGUMENT) +
            listOf("-cp", valuesOf(CLASSPATH).joinToString(File.pathSeparator)) +
            listOf(valuesOf(MAIN_CLASS).single()) +
            arguments
    }

    companion object {

        const val EXECUTABLE = "EXEC"

        const val MAIN_CLASS = "MAIN"

        const val JVM_ARGUMENT = "JVM"

        const val CLASSPATH = "CP"

        const val ARGUMENT = "ARG"

        private val NARROWING_OWNS =
            listOf(
                "--targetClasses=",
                "--reportDir=",
                "--mutationThreshold=",
                "--maxSurviving=",
                "--outputFormats=",
            )

        fun readFrom(invocation: File): PitInvocation =
            PitInvocation(invocation.readLines().filter { it.contains('\t') })

        fun recordOf(
            executable: String,
            mainClass: String,
            jvmArguments: List<String>,
            classpath: List<String>,
            arguments: List<String>,
        ): String = buildString {
            appendLine("$EXECUTABLE\t$executable")
            appendLine("$MAIN_CLASS\t$mainClass")
            jvmArguments.forEach { appendLine("$JVM_ARGUMENT\t$it") }
            classpath.forEach { appendLine("$CLASSPATH\t$it") }
            arguments.forEach { appendLine("$ARGUMENT\t$it") }
        }
    }
}
