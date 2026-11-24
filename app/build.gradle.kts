plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

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
        // Placeholder for local builds. The release pipeline (ticket 18) derives both from the Git tag.
        versionCode = 1
        versionName = "0.0.0-dev"
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
    // Sync scenario tests run the real server in the test host (see AGENTS.md).
    testImplementation(testFixtures(project(":server")))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
