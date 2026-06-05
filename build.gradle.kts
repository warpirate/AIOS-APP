// Root build file. Plugin versions live here; modules apply them without a version.
// Pinning exact SDK/AGP/Kotlin versions is an M0 task — adjust if your toolchain differs.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
