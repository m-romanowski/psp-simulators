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

dependencies {
    testImplementation(platform("org.junit:junit-bom:${libs.versions.junit.get()}"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.playwright) {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.opentest4j")
    }
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val installTheBrowserThisSuiteDrives =
    tasks.register<JavaExec>("installTheBrowserThisSuiteDrives") {
        group = "verification"
        description =
            "Downloads only the headless Chromium shell this suite launches. Playwright's own " +
                "first-run install fetches Chromium, its headless shell, Firefox and WebKit, " +
                "four browsers for a suite that drives one."

        classpath = sourceSets["test"].runtimeClasspath
        mainClass = "com.microsoft.playwright.CLI"
        javaLauncher = javaToolchains.launcherFor(java.toolchain)
        val theWholeBrowser = providers.gradleProperty("theWholeBrowser").isPresent
        val theSystemLibrariesToo = providers.gradleProperty("withSystemDeps").isPresent
        args(
            listOfNotNull(
                "install",
                "chromium",
                if (theWholeBrowser) null else "--only-shell",
                if (theSystemLibrariesToo) "--with-deps" else null,
            )
        )
        outputs.upToDateWhen { false }
    }

val theInvocationPropertiesThisSuiteForwards =
    listOf("simulator.baseUrl", "simulator.jar", "browser.headed", "browser.slowMo")

tasks.named<Test>("test") {
    dependsOn(installTheBrowserThisSuiteDrives)
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
    useJUnitPlatform()
    forkEvery = 0
    maxParallelForks = 1
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
    }
    theInvocationPropertiesThisSuiteForwards.forEach { property ->
        providers.systemProperty(property).orNull?.let { systemProperty(property, it) }
    }
    outputs.upToDateWhen { false }
}
