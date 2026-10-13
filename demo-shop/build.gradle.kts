import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    alias(libs.plugins.springBoot)
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
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation(libs.stripe.java)
    implementation("com.google.code.gson:gson")
    runtimeOnly(libs.htmx)

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.playwright) {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.opentest4j")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val theWalkThroughARealBrowser = "walks-the-real-simulator"

tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags(theWalkThroughARealBrowser) }
    testLogging { events("passed", "failed") }
}

val installTheBrowserTheWalkDrives =
    tasks.register<JavaExec>("installTheBrowserTheWalkDrives") {
        group = "verification"
        description = "Downloads only the headless Chromium shell the walk launches."
        classpath = sourceSets["test"].runtimeClasspath
        mainClass = "com.microsoft.playwright.CLI"
        javaLauncher = javaToolchains.launcherFor(java.toolchain)
        args("install", "chromium", "--only-shell")
        outputs.upToDateWhen { false }
    }

val walkTheShop =
    tasks.register<Test>("walkTheShop") {
        group = "verification"
        description =
            "Plays both stories in a real browser against a real simulator, booted from this " +
                "repository's own boot jar unless -Dsimulator.baseUrl names a running one."
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        dependsOn(installTheBrowserTheWalkDrives)
        environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
        useJUnitPlatform { includeTags(theWalkThroughARealBrowser) }
        testLogging {
            showStandardStreams = true
            events("passed", "failed")
        }
        listOf("simulator.baseUrl", "simulator.jar", "browser.headed").forEach { property ->
            providers.systemProperty(property).orNull?.let { systemProperty(property, it) }
        }
        outputs.upToDateWhen { false }
    }

tasks.named("check") { dependsOn(walkTheShop) }
