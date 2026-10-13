package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PitInvocationTest {

    private val recordedArguments =
        listOf(
            "--reportDir=/build/reports/pitest",
            "--targetClasses=dev.marcinromanowski.pspsim.control.*,dev.marcinromanowski.pspsim.kernel.*",
            "--targetTests=dev.marcinromanowski.pspsim.*",
            "--threads=2",
            "--excludedClasses=dev.marcinromanowski.pspsim.**.Jdbc*",
            "--excludedTestClasses=dev.marcinromanowski.pspsim.*IntegrationTest",
            "--verbosity=NO_SPINNER",
            "--outputFormats=XML,HTML",
            "--failWhenNoMutations=false",
            "--sourceDirs=/src/main/java",
            "--timestampedReports=false",
            "--mutableCodePaths=/build/classes/java/main",
            "--mutationThreshold=90",
            "--includeLaunchClasspath=false",
            "--classPathFile=/build/tmp/pitClasspath",
        )

    private val record =
        PitInvocation.recordOf(
            executable = "/jdk/bin/java",
            mainClass = "org.pitest.mutationtest.commandline.MutationCoverageReport",
            jvmArguments = emptyList(),
            classpath = listOf("/one.jar", "/two.jar"),
            arguments = recordedArguments,
        )

    @Test
    fun `the record is one tab-separated key and value a line, EXEC and MAIN once`() {
        val lines = record.trimEnd().lines()
        assertEquals("EXEC\t/jdk/bin/java", lines[0])
        assertEquals(
            "MAIN\torg.pitest.mutationtest.commandline.MutationCoverageReport",
            lines[1],
        )
        assertEquals(listOf("CP\t/one.jar", "CP\t/two.jar"), lines.subList(2, 4))
        assertEquals(recordedArguments.map { "ARG\t$it" }, lines.drop(4))
        assertEquals(1, lines.count { it.startsWith("EXEC\t") })
        assertEquals(1, lines.count { it.startsWith("MAIN\t") })
        assertEquals(0, lines.count { it.startsWith("JVM\t") })
    }

    @Test
    fun `a jvm argument is recorded between the main class and the classpath`() {
        val withJvm =
            PitInvocation.recordOf(
                executable = "/jdk/bin/java",
                mainClass = "Main",
                jvmArguments = listOf("-Xmx2g"),
                classpath = listOf("/one.jar"),
                arguments = listOf("--threads=2"),
            )
        assertEquals(
            listOf(
                "EXEC\t/jdk/bin/java",
                "MAIN\tMain",
                "JVM\t-Xmx2g",
                "CP\t/one.jar",
                "ARG\t--threads=2",
            ),
            withJvm.trimEnd().lines(),
        )
    }

    @Test
    fun `the record read back answers each key with the values it carried`() {
        val read = PitInvocation(record.trimEnd().lines())
        assertEquals(listOf("/jdk/bin/java"), read.valuesOf(PitInvocation.EXECUTABLE))
        assertEquals(listOf("/one.jar", "/two.jar"), read.valuesOf(PitInvocation.CLASSPATH))
        assertEquals(recordedArguments, read.valuesOf(PitInvocation.ARGUMENT))
    }

    @Test
    fun `the narrowed re-run keeps every argument the narrowing does not own`() {
        val narrowed =
            PitInvocation(record.trimEnd().lines())
                .narrowedTo(
                    listOf("dev.marcinromanowski.pspsim.kernel.Thing"),
                    File("/build/reports/pitest-retry"),
                )

        assertEquals("/jdk/bin/java", narrowed[0])
        assertEquals("-cp", narrowed[1])
        assertEquals("/one.jar" + File.pathSeparator + "/two.jar", narrowed[2])
        assertEquals("org.pitest.mutationtest.commandline.MutationCoverageReport", narrowed[3])

        val arguments = narrowed.drop(4)
        assertEquals(
            listOf(
                "--targetTests=dev.marcinromanowski.pspsim.*",
                "--threads=2",
                "--excludedClasses=dev.marcinromanowski.pspsim.**.Jdbc*",
                "--excludedTestClasses=dev.marcinromanowski.pspsim.*IntegrationTest",
                "--verbosity=NO_SPINNER",
                "--failWhenNoMutations=false",
                "--sourceDirs=/src/main/java",
                "--timestampedReports=false",
                "--mutableCodePaths=/build/classes/java/main",
                "--includeLaunchClasspath=false",
                "--classPathFile=/build/tmp/pitClasspath",
                "--targetClasses=dev.marcinromanowski.pspsim.kernel.Thing",
                "--reportDir=" + File("/build/reports/pitest-retry").absolutePath,
                "--outputFormats=XML",
            ),
            arguments,
        )
    }

    @Test
    fun `the narrowed re-run carries the jvm arguments of the recorded run`() {
        val record =
            PitInvocation.recordOf(
                executable = "/jdk/bin/java",
                mainClass = "Main",
                jvmArguments = listOf("-Xmx2g", "-Dfile.encoding=UTF-8"),
                classpath = listOf("/one.jar"),
                arguments = listOf("--threads=2"),
            )
        val narrowed =
            PitInvocation(record.trimEnd().lines()).narrowedTo(listOf("A"), File("/retry"))
        assertEquals(
            listOf("/jdk/bin/java", "-Xmx2g", "-Dfile.encoding=UTF-8", "-cp"),
            narrowed.take(4),
        )
    }
}
