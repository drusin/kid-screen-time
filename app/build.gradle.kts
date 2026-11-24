plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
