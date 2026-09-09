plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wk2.climate.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Deliberately inside com.android.*, to survive com.syu.ms's sleep sweep.
        //
        // On sleep the vendor MCU service walks every running process and calls
        // the hidden ActivityManager.forceStopPackage() on each, unless the name
        // matches a regex compiled from an asset inside its own APK. That regex
        // is 85 alternatives joined with `|`, tested with find() rather than
        // matches(), so it is an unanchored substring search -- and one of its
        // entries is the wildcard `com\.android\..`. `com.android.wk2climate`
        // therefore matches on `com.android.w` and is skipped.
        //
        // Verified by rebuilding the pattern from the extracted asset and
        // testing this exact string against it. `com.syu.air` is on that list
        // (commented "air-conditioning control"), which is why the factory bar
        // survives every sweep and our old `com.wk2.climate` did not.
        //
        // Being force-stopped also prunes our accessibility entry at the
        // framework level, and a stopped package has no process left to re-add
        // it -- so this is not a nicety, it is the difference between the bar
        // coming back after ignition-off and needing adb every time.
        //
        // See docs/captures/2026-09-09-syu-ms-force-stop.md. The Kotlin
        // packages stay com.wk2.climate.*; only the applicationId moves, since
        // the sweep matches on process name.
        applicationId = "com.android.wk2climate"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
}
