plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    `java-test-fixtures`
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.drusin.kidscreentime.server.MainKt")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":api"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback.classic)

    // TestServer: shared with the app module's sync scenario tests (see AGENTS.md). No kotlin-test here, so that
    // the app (JUnit 4) and the server (JUnit 5) can each bring their own.
    testFixturesApi(testFixtures(project(":domain")))
    testFixturesApi(libs.ktor.server.test.host)
    testFixturesApi(libs.ktor.client.content.negotiation)
    testFixturesApi(libs.ktor.serialization.kotlinx.json)
    testFixturesImplementation(project(":api"))
    testFixturesImplementation(project(":domain"))
    testFixturesApi(project(":server"))

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("kid-screen-time-server")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

// `./gradlew build` must produce the fat JAR as well.
tasks.assemble {
    dependsOn(tasks.shadowJar)
}
