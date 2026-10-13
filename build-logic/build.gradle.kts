plugins {
    `kotlin-dsl`
}

fun marker(plugin: Provider<PluginDependency>): Provider<String> = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

dependencies {
    implementation(marker(libs.plugins.spotless))
    implementation(marker(libs.plugins.errorprone))
    implementation(marker(libs.plugins.pitest))
    implementation(marker(libs.plugins.sonarqube))
    testImplementation(libs.pitest)
}

testing {
    suites {
        named<JvmTestSuite>("test") { useJUnitJupiter(libs.versions.junitForBuildLogic) }
    }
}

tasks.test {
    description =
        "Runs the unit tests of the logic this build's gates decide on, which the root " +
            "`check` depends on so a gate checking the wrong thing cannot pass as a working one."
}
