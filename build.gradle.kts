import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    alias(libs.plugins.springBoot)
    id("psp.compile-conventions")
    id("psp.formatting-conventions")
    id("psp.testing-conventions")
    id("psp.mutation-gate")
    id("psp.source-conventions")
    id("psp.api-documentation")
    id("psp.clickable-capabilities")
    id("psp.payer-page-addresses")
    id("psp.payer-page-policies")
    id("psp.related-object-addresses")
    id("psp.trail-withholding")
    id("psp.sonar-conventions")
}

group = "dev.marcinromanowski"

springBoot {
    buildInfo()
}

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
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation(libs.jspecify)
    implementation("org.postgresql:postgresql")

    runtimeOnly(libs.htmx)
    runtimeOnly(libs.swagger.ui)
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.archunit.junit5)

    pitest(libs.pitest.junit5)
}
