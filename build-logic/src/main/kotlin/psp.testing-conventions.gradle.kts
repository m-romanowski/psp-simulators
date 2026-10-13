import dev.marcinromanowski.pspsim.buildlogic.CoverageScope

plugins {
    java
    jacoco
}

val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")

val withoutDocker = providers.gradleProperty("withoutDocker").isPresent

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        if (withoutDocker) {
            excludeTags("docker")
        }
    }
    testLogging { events("passed", "skipped", "failed") }
}

jacoco { toolVersion = versions.findVersion("jacoco").get().requiredVersion }

val coverageExclusions = CoverageScope.EXCLUDED_CLASS_FILES

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } })
    )
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } })
    )
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

val buildLogicTests = gradle.includedBuild("build-logic").task(":test")

tasks.check {
    dependsOn(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification, buildLogicTests)
}
