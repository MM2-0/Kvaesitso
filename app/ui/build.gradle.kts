import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.plugin.compose)
}

android {
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    packaging {
        // The standalone library test APK includes both existing JavaMail jars. Preserve
        // their duplicate notices together rather than dropping license text to package it.
        resources.merges.add("META-INF/LICENSE.md")
        resources.merges.add("META-INF/NOTICE.md")
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/INDEX.LIST")
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        signingConfig = signingConfigs.getByName("debug")
    }

    // Same flavors as :app:app. The "default" flavor adds the RAR reader (prebuilt native library, see RarBackend),
    // the "fdroid" flavor does not ship prebuilt binaries and gets a stub with the same API.
    flavorDimensions += "variant"
    productFlavors {
        create("default") { dimension = "variant" }
        create("fdroid") { dimension = "variant" }
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("nightly") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
            optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
            optIn.add("androidx.compose.ui.text.ExperimentalTextApi")
            optIn.add("androidx.compose.ui.unit.ExperimentalUnitApi")
            optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
            optIn.add("androidx.compose.material.ExperimentalMaterialApi")
            optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
            optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            optIn.add("androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi")
            optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
            optIn.add("com.google.accompanist.pager.ExperimentalPagerApi")
            optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
        // === TELOS_PENDING_REVIEW_START: sandbox_cloning_and_bridge ===
        aidl = true
        // === TELOS_PENDING_REVIEW_END: sandbox_cloning_and_bridge ===
    }

    lint {
        abortOnError = false
    }
    namespace = "de.mm20.launcher2.ui"
}


dependencies {
    implementation(project(":libs:appops-next"))
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // CameraX
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.camera:camera-video:1.6.2")
    implementation("androidx.camera:camera-extensions:1.6.2")
    implementation("androidx.health.connect:connect-client:1.1.0")

    implementation(libs.bundles.kotlin)

    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundationlayout)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.uitooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animationgraphics)
    implementation(libs.androidx.constraintlayout.compose)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material3adaptive.navigation3)

    implementation(libs.markdown)

    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.pager)
    implementation(libs.accompanist.pagerindicators)
    implementation(libs.accompanist.flowlayout)


    implementation(libs.androidx.core)
    implementation(libs.androidx.activitycompose)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.emojipicker)

    implementation(libs.androidx.lifecycle.viewmodelcompose)
    implementation(libs.androidx.lifecycle.runtimecompose)

    implementation(libs.koin.android)
    implementation(libs.koin.androidxcompose)

    implementation(libs.coil.core)
    // Telos Files: network and cloud storages, archives
    implementation(libs.okhttp)
    implementation(libs.sshj)
    implementation(libs.smbj)
    implementation(libs.commons.net)
    implementation(libs.commons.compress)
    // optional dependency of commons-compress, needed for .xz, .tar.xz and the LZMA/LZMA2 of 7z archives
    implementation(libs.tukaani.xz)
    // password-protected zip files (ZipCrypto and AES): reading, and creating AES-256 zips
    implementation(libs.zip4j)
    // RAR and RAR5 (read only, no decryption): libarchive with prebuilt native code, not for F-Droid
    "defaultImplementation"(libs.libarchive.android)
    implementation(libs.bouncycastle)
    // Telos PDF tools (adapted from PaperKnife+): PDF editing
    implementation(libs.pdfbox.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.zxing.core)

    implementation(libs.smartspacer) {
        exclude(group = "com.github.skydoves", module = "balloon")
    }

    implementation(project(":libs:material-color-utilities"))

    implementation(project(":core:base"))
    implementation(project(":ui-kmp"))
    implementation(libs.shizuku.api)
    implementation(libs.hiddenapibypass)
    implementation(project(":core:i18n"))
    implementation(project(":core:compat"))
    implementation(project(":core:ktx"))
    implementation(project(":core:profiles"))
    implementation(project(":services:icons"))
    implementation(project(":services:music"))
    implementation(project(":services:tags"))
    implementation(project(":data:weather"))
    implementation(project(":data:calendar"))
    implementation(project(":services:search"))
    implementation(project(":core:preferences"))
    implementation(project(":data:applications"))
    implementation(project(":data:appshortcuts"))
    implementation(project(":data:calculator"))
    implementation(project(":data:files"))
    implementation(project(":data:widgets"))
    implementation(project(":data:searchable"))
    implementation(project(":data:themes"))
    implementation(project(":data:wikipedia"))
    implementation(project(":services:badges"))
    implementation(project(":core:crashreporter"))
    implementation(project(":data:notifications"))
    implementation(project(":data:contacts"))
    implementation(project(":data:locations"))
    implementation(project(":core:permissions"))
    implementation(project(":data:websites"))
    implementation(project(":data:webappshortcuts"))
    implementation(project(":data:unitconverter"))
    implementation(project(":libs:nextcloud"))
    implementation(project(":libs:owncloud"))
    implementation(project(":services:accounts"))
    implementation(project(":services:plugins"))
    implementation(project(":services:backup"))
    implementation(project(":data:search-actions"))
    implementation(project(":services:global-actions"))
    implementation(project(":services:widgets"))
    implementation(project(":services:favorites"))
    implementation(project(":services:feed"))
    implementation(project(":services:freeze"))
    implementation(project(":services:wallpapers"))
    implementation(project(":services:context-profiles"))
    implementation(project(":services:desktop-mode"))
    implementation(project(":services:applock"))
    implementation(project(":services:app-management"))
    implementation(project(":services:store"))
    implementation(project(":data:store"))
    implementation(project(":services:comms"))
    implementation(project(":services:downloads"))
    implementation(project(":services:network"))
    implementation(libs.androidx.work)

    implementation(libs.androidx.biometric)
    implementation(project(":core:devicepose"))

    // === TELOS_PENDING_REVIEW_START: radio_mini_player ===
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.hls)
    implementation(libs.androidx.media3.dash)
    implementation(libs.androidx.media3.rtsp)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.exifinterface)
    implementation(libs.guava.android)
    // === TELOS_PENDING_REVIEW_END: radio_mini_player ===

    androidTestImplementation(libs.androidx.compose.uitestjunit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(project(":data:database"))
    debugImplementation(libs.androidx.compose.uitestmanifest)
    // Compact offline Unicode romanization; works on minSdk 26 without ICU API 29.
    implementation("com.anyascii:anyascii:0.3.3")
    testImplementation(libs.bundles.tests)
    testImplementation("org.json:json:20260814")
    
    implementation(project(":services:keepasstl"))
    implementation(libs.kotpass)
}
// Bouncy Castle comes in with several versions (sshj, other libraries); the parts must match or the classes are duplicated
configurations.all {
    // pdfbox-android brings the jdk15to18 builds of Bouncy Castle, sshj the jdk18on builds: same classes twice
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
    exclude(group = "org.bouncycastle", module = "bcutil-jdk15to18")
    resolutionStrategy.eachDependency {
        if (requested.group == "org.bouncycastle") useVersion(libs.versions.bouncycastle.get())
    }
}

