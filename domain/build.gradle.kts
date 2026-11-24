plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(21)
}

// Pure Kotlin: no Android, no server dependencies.
dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.konsist)
}

tasks.test {
    useJUnitPlatform()
    // The architecture test reads the production sources of ALL modules, so they are inputs of this task.
    // Without this, Gradle would skip the test as up-to-date after a change in another module.
    inputs.files(fileTree(rootDir) { include("*/src/main/**") })
        .withPropertyName("productionSourcesOfAllModules")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
