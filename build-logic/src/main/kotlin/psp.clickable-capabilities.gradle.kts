import dev.marcinromanowski.pspsim.buildlogic.ClickableCapabilities

plugins {
    java
}

val clickableCapabilityChecker =
    "dev.marcinromanowski.pspsim.documentation.ClickableCapabilityConvention"

val verifyClickableCapabilities =
    tasks.register<JavaExec>("verifyClickableCapabilities") {
        group = "verification"
        description =
            "Fails when a control API capability is reachable by no click: a route bound to no " +
                "screen, a binding naming a route, screen or kernel method this build lacks, or " +
                "an API-only facet a screen reaches."

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = clickableCapabilityChecker
        javaLauncher = javaToolchains.launcherFor(java.toolchain)

        val compiledMainClasses = layout.buildDirectory.dir("classes/java/main")
        val bindings = ClickableCapabilities.screensThatOfferAControlCapability
        val facets = ClickableCapabilities.facetsOnlyTheApiOffers

        inputs.dir(compiledMainClasses)
        inputs.files(fileTree("src/main/resources/templates"))
        inputs.property("screensThatOfferAControlCapability", bindings.toString())
        inputs.property("facetsOnlyTheApiOffers", facets.toString())

        val projectRoot = layout.projectDirectory.asFile.absolutePath

        doFirst {
            (this as JavaExec).setArgs(
                listOf(projectRoot, compiledMainClasses.get().asFile.absolutePath) +
                    bindings.map {
                        "clickable=" + it.first + "|" + it.second + "|" + it.third
                    } +
                    facets.map { "apiOnly=" + it.joinToString("|") }
            )
        }
    }

tasks.named("check") { dependsOn(verifyClickableCapabilities) }
