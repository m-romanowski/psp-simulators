import dev.marcinromanowski.pspsim.buildlogic.ControlApi

plugins {
    java
}

val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")

val openApiChecker = "dev.marcinromanowski.pspsim.documentation.OpenApiConvention"

fun openApiArguments(): List<String> =
    listOf(
        layout.projectDirectory.asFile.absolutePath,
        layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath,
    ) +
        ControlApi.openApiControlAnswers.entries
            .sortedBy { it.key }
            .map { "route=" + it.key + "=" + it.value.first + "|" + it.value.second } +
        ControlApi.openApiRefusalRecords.map { "refusal=" + it } +
        listOf(
            "swaggerUi=" + versions.findVersion("swaggerUi").get().requiredVersion,
            "product=" + version,
        )

fun openApiInvocation(mode: String): List<String> =
    openApiArguments().let { it.take(2) + listOf(mode) + it.drop(2) }

val writeOpenApi =
    tasks.register<JavaExec>("writeOpenApi") {
        group = "documentation"
        description =
            "Writes the browsable API view under src/main/resources/static/openapi from this " +
                "build's compiled classes: routes from the mapping annotations, request schemas " +
                "from the records Spring binds bodies to, response schemas from the records " +
                "`openApiControlAnswers` names."

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = openApiChecker
        javaLauncher = javaToolchains.launcherFor(java.toolchain)
        args(openApiInvocation("write"))
    }

val verifyOpenApi =
    tasks.register<JavaExec>("verifyOpenApi") {
        group = "verification"
        description =
            "Fails when the committed browsable API view is not what this build generates: a " +
                "component added, removed or retyped on a published record, a control or provider " +
                "route added or removed, or a published example anywhere in the view."

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = openApiChecker
        javaLauncher = javaToolchains.launcherFor(java.toolchain)

        val compiledMainClasses = layout.buildDirectory.dir("classes/java/main")
        inputs.dir(compiledMainClasses)
        inputs.files(fileTree("src/main/resources/static/openapi"))
        inputs.property("openApiControlAnswers", ControlApi.openApiControlAnswers.toString())
        inputs.property("openApiRefusalRecords", ControlApi.openApiRefusalRecords.toString())
        inputs.property("swaggerUi", versions.findVersion("swaggerUi").get().requiredVersion)

        args(openApiInvocation("verify"))
    }

tasks.named("check") { dependsOn(verifyOpenApi) }
