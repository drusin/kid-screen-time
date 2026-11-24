plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Version: the Git tag is the only source (ticket 18). CI passes it as env RELEASE_TAG or -PreleaseTag (e.g. v1.2.3).
// Without a tag (local builds, normal CI) the version is 0.0.0-dev.
val releaseTag: String? = (providers.gradleProperty("releaseTag").orNull ?: providers.environmentVariable("RELEASE_TAG").orNull)
    ?.trim()?.takeIf { it.isNotEmpty() }
val tagVersion = releaseTag?.let {
    Regex("""v(\d{1,2})\.(\d{1,2})\.(\d{1,2})""").matchEntire(it)
        ?: throw GradleException("Release-Tag '$it' hat nicht die Form vX.Y.Z (je Teil 0-99)")
}?.destructured?.let { (x, y, z) -> Triple(x.toInt(), y.toInt(), z.toInt()) }

// Release signing: only from environment variables (CI secret of the tag workflow), never from the repo.
// Without them the release APK stays unsigned, so `./gradlew build` works locally and in normal CI.
val releaseKeystorePath: String? = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull?.takeIf { it.isNotBlank() }

// AGP 9 has built-in Kotlin support: no separate kotlin-android plugin.
android {
    namespace = "io.github.drusin.kidscreentime"
    // Current AndroidX (Compose 1.13, core 1.19) requires compileSdk 37.1. targetSdk stays 36.
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "io.github.drusin.kidscreentime"
        minSdk = 33
        targetSdk = 36
        // versionName = tag, versionCode = X*10000 + Y*100 + Z; local builds without a tag: 1 / 0.0.0-dev.
        versionCode = tagVersion?.let { (x, y, z) -> x * 10000 + y * 100 + z } ?: 1
        versionName = releaseTag?.removePrefix("v") ?: "0.0.0-dev"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    // German only.
    androidResources {
        localeFilters += "de"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // The app name "Bildschirmzeit Dev" comes from src/debug/res/values/strings.xml.
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        // Dependency freshness is not a quality gate; it would break CI whenever a new version appears.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests {
            // Robolectric needs merged resources and the manifest.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// Room exports its schema as JSON (committed in app/schemas): later tickets add entities and migrations against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":api"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
