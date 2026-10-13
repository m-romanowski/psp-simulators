plugins {
    java
}

val relatedObjectAddressChecker =
    "dev.marcinromanowski.pspsim.documentation.RelatedObjectAddressConvention"

val verifyNoPayerUrlAmongRelatedObjects =
    tasks.register<JavaExec>("verifyNoPayerUrlAmongRelatedObjects") {
        group = "verification"
        description =
            "Fails when a provider module builds the state of a provider object out of the " +
                "address of a payer page this simulator serves. Also fails when a provider " +
                "module builds no such state, leaving this gate nothing of it to read."

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = relatedObjectAddressChecker
        javaLauncher = javaToolchains.launcherFor(java.toolchain)

        val compiledMainClasses = layout.buildDirectory.dir("classes/java/main")

        inputs.dir(compiledMainClasses)

        doFirst {
            (this as JavaExec).setArgs(listOf(compiledMainClasses.get().asFile.absolutePath))
        }
    }

tasks.named("check") { dependsOn(verifyNoPayerUrlAmongRelatedObjects) }
