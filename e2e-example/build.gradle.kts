plugins {
    java
}

group = "com.example"

version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val cucumber = "8.0.2"

val junitPlatform = "6.1.3"

dependencies {
    testImplementation("io.cucumber:cucumber-java:$cucumber")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumber")
    testImplementation("io.cucumber:cucumber-picocontainer:$cucumber")
    testImplementation("org.junit.platform:junit-platform-suite:$junitPlatform")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    testImplementation("com.stripe:stripe-java:33.4.2")
    testCompileOnly("com.google.code.gson:gson:2.10.1")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatform")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val theInvocationPropertiesThisSuiteForwards = listOf("simulator.baseUrl", "simulator.jar")

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.execution.parallel.enabled", "false")
    systemProperty("cucumber.publish.quiet", "true")
    forkEvery = 0
    maxParallelForks = 1
    testLogging {
        showStandardStreams = true
        events("failed")
    }
    theInvocationPropertiesThisSuiteForwards.forEach { property ->
        providers.systemProperty(property).orNull?.let { systemProperty(property, it) }
    }
    outputs.upToDateWhen { false }
}
