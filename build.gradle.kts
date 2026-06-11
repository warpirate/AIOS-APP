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

// ktlint-gradle attempted (plugin 12.1.2, ktlint 1.5.0): the worker fails to parse several
// otherwise-valid files (AgentLoop.kt, build.gradle.kts, AgentRuntimeTest.kt) and blocks `check`.
// The autofix ("ktlintFormat") cleanups it produced are kept in this tree as a mechanical
// style baseline. Revisit once ktlint releases a parser that handles Kotlin 2.2 source cleanly,
// or switch to spotless-with-ktlint and re-evaluate.
