plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/**
 * Reads a value from git, or null if git is unavailable or the command fails.
 *
 * `providers.exec` rather than a raw process, so the result participates in
 * Gradle's configuration cache instead of being re-run on every configuration.
 * Anything that goes wrong -- no git on PATH, a source archive with no `.git`,
 * a shallow clone -- yields null and the caller falls back.
 */
fun gitOrNull(vararg args: String): String? = runCatching {
    providers.exec { commandLine(*args) }
        .standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

/**
 * Commit count as the version code, so successive builds are upgrades.
 *
 * It was hardcoded to 1, which meant a second APK was not seen as an upgrade
 * at all. Commit count is monotonic, needs no tags, and needs nothing
 * remembered between builds.
 *
 * **CI must check out with full history** — a shallow clone counts 1 commit and
 * would silently produce version 1 forever. See `.github/workflows/build.yml`.
 */
val gitVersionCode = gitOrNull("git", "rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

/** `git describe`: the tag if there is one, else a short SHA, plus `-dirty`. */
val gitVersionName = gitOrNull("git", "describe", "--tags", "--always", "--dirty") ?: "0.1"

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
        versionCode = gitVersionCode
        versionName = gitVersionName
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
}
