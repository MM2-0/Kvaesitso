import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.plugin.serialization)
}

// the CPU types of this build, see -Ptelos.abi in app/app/build.gradle.kts
val telosAbis = ((findProperty("telos.abi") as String?)?.takeIf { it.isNotBlank() }?.let { listOf(it) }
    ?: listOf("arm64-v8a"))

android {
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    ndkVersion = "27.0.12077973"
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // The native SIP engine is only built when the baresip static libraries are present
        // (downloaded by CI from the "sip-libs" release, or built locally with sip-native/Makefile).
        if (rootProject.file("sip-native/distribution/baresip").exists()) {
            ndk { abiFilters += telosAbis }
            // The static libraries and the AAudio calls need API 28; older devices fall back to no SIP
            externalNativeBuild { cmake { arguments += "-DANDROID_PLATFORM=android-28" } }
        }
    }

    if (rootProject.file("sip-native/distribution/baresip").exists()) {
        externalNativeBuild {
            cmake { path = file("src/main/cpp/CMakeLists.txt") }
        }
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            optIn.add("androidx.media3.common.util.UnstableApi")
        }
    }
    namespace = "de.mm20.launcher2.comms"
}

dependencies {
    implementation("com.sun.mail:android-mail:1.6.8")
    implementation("com.sun.mail:android-activation:1.6.8")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    // Torrent streaming (MIT): Java API and the native libraries for 64 and 32 bit ARM
    implementation(libs.libtorrent4j)
    // only the native library of the CPU type that is built (see -Ptelos.abi in app/app/build.gradle.kts)
    if ("arm64-v8a" in telosAbis) implementation(libs.libtorrent4j.arm64)
    if ("armeabi-v7a" in telosAbis) implementation(libs.libtorrent4j.arm)
    if ("x86_64" in telosAbis) implementation(libs.libtorrent4j.x8664)
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.koin.android)
    implementation(libs.androidx.work)
    testImplementation(libs.commons.net)
    testImplementation(libs.bundles.tests)

    implementation(project(":core:ktx"))
    implementation(project(":core:i18n"))
    implementation(project(":core:base"))
    implementation(project(":core:crashreporter"))
    implementation(project(":core:preferences"))
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work)
    implementation(libs.shizuku.api)
    implementation(libs.taglib)
    
    // === TELOS_PENDING_REVIEW_START: sms_and_radio_engine ===
    testImplementation(libs.bundles.tests)
    testImplementation("org.json:json:20260814")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.hls)
    implementation(libs.androidx.media3.dash)
    implementation(libs.nextlib) // official NextLib FFmpeg decoders (Maven Central, GPL-3.0)
    // === TELOS_PENDING_REVIEW_END: sms_and_radio_engine ===
}
