plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dokka) apply true
}

dependencies {
    dokka(project(":plugins:sdk"))
    dokka(project(":core:shared"))
}