import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    checkstyle
    id("net.ltgt.errorprone")
}

val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        allDisabledChecksAsWarnings = false
        error("NullAway")
        option("NullAway:AnnotatedPackages", "dev.marcinromanowski.pspsim")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone { disable("NullAway") }
}

checkstyle {
    toolVersion = versions.findVersion("checkstyle").get().requiredVersion
    configFile = rootProject.file("checkstyle/checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required = true
        html.required = true
    }
}
