plugins {
    java
}

val trailWithholdingChecker = "dev.marcinromanowski.pspsim.documentation.TrailWithholdingConvention"

val verifyEveryProviderWithholdsWhatItAnswers =
    tasks.register<JavaExec>("verifyEveryProviderWithholdsWhatItAnswers") {
        group = "verification"
        description =
            "Fails when a provider module declares no fields the audit trail keeps redacted of " +
                "its answers or its deliveries, when a class of it answers or delivers without " +
                "reading that declaration, or when it publishes an address-shaped name it " +
                "neither withholds nor registers."

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = trailWithholdingChecker
        javaLauncher = javaToolchains.launcherFor(java.toolchain)

        val compiledMainClasses = layout.buildDirectory.dir("classes/java/main")

        inputs.dir(compiledMainClasses)

        doFirst {
            (this as JavaExec).setArgs(listOf(compiledMainClasses.get().asFile.absolutePath))
        }
    }

tasks.named("check") { dependsOn(verifyEveryProviderWithholdsWhatItAnswers) }
