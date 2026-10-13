plugins {
    java
}

val payerPageAddressChecker = "dev.marcinromanowski.pspsim.documentation.PayerPageAddressConvention"

val verifyNoPayerUrlOnAScreen =
    tasks.register<JavaExec>("verifyNoPayerUrlOnAScreen") {
        group = "verification"
        description =
            "Fails when a screen or template of this UI hardcodes a payer page address, when a " +
                "provider declares its page at a route this build answers nowhere, or when a " +
                "provider serves text/html and declares no page of its own."

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = payerPageAddressChecker
        javaLauncher = javaToolchains.launcherFor(java.toolchain)

        val compiledMainClasses = layout.buildDirectory.dir("classes/java/main")

        inputs.dir(compiledMainClasses)
        inputs.files(fileTree("src/main/resources/templates"))
        inputs.files(fileTree("src/main/java/dev/marcinromanowski/pspsim/ui"))

        val projectRoot = layout.projectDirectory.asFile.absolutePath

        doFirst {
            (this as JavaExec).setArgs(
                listOf(projectRoot, compiledMainClasses.get().asFile.absolutePath)
            )
        }
    }

tasks.named("check") { dependsOn(verifyNoPayerUrlOnAScreen) }
