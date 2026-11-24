// Plugins are declared here (apply false) so every module uses the same versions from gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.shadow) apply false
}

// Central test configuration for ALL modules, so later tickets need no extra setup.
subprojects {
    tasks.withType<Test>().configureEach {
        // Robolectric downloads its android-all jars from Maven Central. repo1.maven.org is blocked in
        // the sandbox; repo.maven.apache.org serves the same artifacts.
        systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        // The architecture test needs the repo root to find the sources of all modules.
        systemProperty("kidscreentime.repoRoot", rootProject.projectDir.absolutePath)
    }
}
