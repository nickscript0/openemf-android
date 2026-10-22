// Top-level build file for OpenEMF Android app
plugins {
    alias(libs.plugins.android.application) apply false
    // Align the compiler used by AGP built-in Kotlin and the Compose compiler.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
