import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    // The full-screen browser / napplet windows draw the shared Compose browser chrome (commonsUI).
    alias(libs.plugins.jetbrainsComposeCompiler)
}

android {
    namespace = "com.vitorpamplona.amethyst.napplethost"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        // :amethyst defines a `benchmark` build type (release + profileable) for macrobenchmarks.
        // Declare a matching variant here so the app can resolve this library for benchmark builds.
        create("benchmark") {
            initWith(getByName("release"))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    // The sandbox runtime depends ONLY on the protocol/contract (commons), the shell/shim
    // Compose resources it serves (commonsUI's NappletWebContract) and Nostr resolution
    // (quartz) — never on :amethyst. This makes it impossible for the `:napplet` process code to
    // reach for Amethyst.instance / LocalCache / Account (which don't exist in that process).
    implementation(project(":commons"))
    implementation(project(":commonsUI"))
    implementation(libs.quartz)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.jetbrains.compose.ui)
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.jetbrains.compose.runtime)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.components.resources)
    implementation(libs.androidx.webkit)
    implementation(libs.okhttp)

    // Tree-level JSON for the bridge/broker envelopes (no @Serializable codegen, so no plugin needed).
    implementation(libs.kotlinx.serialization.json)

    // Provider side of the cross-process UI embedding: hosts the browser WebView in this keyless
    // `:napplet` process and ships its rendered surface to the main app via SurfaceControlViewHost.
    implementation(libs.androidx.privacysandbox.ui.core)
    implementation(libs.androidx.privacysandbox.ui.provider)
}
