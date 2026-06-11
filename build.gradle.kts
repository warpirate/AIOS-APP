// Root build file. Plugin versions live here; modules apply them without a version.
// Toolchain pins span four files — keep all four in sync when bumping:
//   - this file (AGP + Kotlin plugin versions)
//   - app/build.gradle.kts (compileSdk / minSdk / targetSdk / ndkVersion)
//   - gradle/wrapper/gradle-wrapper.properties (Gradle distribution)
//   - .tool-versions (developer + CI toolchain pins — Java / Gradle / Kotlin)
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
