plugins {
    java
}

val payerPagePolicyChecker = "dev.marcinromanowski.pspsim.documentation.PayerPagePolicyConvention"

val verifyEveryPayerPageDeclaresWhatItMayLoad =
    tasks.register<JavaExec>("verifyEveryPayerPageDeclaresWhatItMayLoad") {
        group = "verification"
        description =
            "Fails when a class rendering an HTML document a browser loads declares no " +
                "Content-Security-Policy, when its policy drops the floor or names a directive " +
                "registered against no page, or when the document loads a subresource it blocks."

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = payerPagePolicyChecker
        javaLauncher = javaToolchains.launcherFor(java.toolchain)

        val compiledMainClasses = layout.buildDirectory.dir("classes/java/main")

        inputs.dir(compiledMainClasses)

        doFirst {
            (this as JavaExec).setArgs(listOf(compiledMainClasses.get().asFile.absolutePath))
        }
    }

tasks.named("check") { dependsOn(verifyEveryPayerPageDeclaresWhatItMayLoad) }
