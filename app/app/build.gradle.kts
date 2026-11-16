import java.util.Properties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

val releaseVersion = Properties().apply {
    rootProject.file("version.properties").reader().use { load(it) }
}

// see services/downloads/build.gradle.kts: the yt-dlp runtime is only built in with -Ptelos.media=true
val mediaRuntime = (findProperty("telos.media") as String?)?.toBoolean() == true

// R8 renames classes and members in release and nightly builds. -Ptelos.keepNames=true keeps the original
// names (stable-names.pro), e.g. to compare APK sizes or to debug a keep rule.
val keepNames = providers.gradleProperty("telos.keepNames")
    .map { it.toBooleanStrict() }.getOrElse(false)

// One APK per CPU type: -Ptelos.abi=arm64-v8a or -Ptelos.abi=armeabi-v7a puts only the native libraries of that
// CPU into the APK. Without it both are included (local and debug builds). Keep in sync with services/comms.
val telosAbis = ((findProperty("telos.abi") as String?)?.takeIf { it.isNotBlank() }?.let { listOf(it) }
    ?: listOf("arm64-v8a"))

android {
    androidResources {
        generateLocaleConfig = true
        // Only the languages Telos is translated into (core/i18n). Library translations for other
        // languages would be dead weight in resources.arsc, which is stored uncompressed.
        localeFilters += listOf(
            "en", "ar", "az", "be", "bg", "bn", "ca", "cs", "da", "de", "el", "eo", "es", "et", "eu",
            "fa", "fi", "fr", "ga", "he", "iw", "hi", "hr", "hu", "ia", "in", "id", "it", "ja", "ko",
            "lt", "lv", "ms", "mt", "nb", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "th", "tr",
            "uk", "vi", "zh",
            "en-rGB", "en-rUS", "nb-rNO", "pt-rBR", "zh-rCN", "zh-rTW",
        )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/DEPENDENCIES.txt")
        resources.excludes.add("META-INF/INDEX.LIST")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/license.txt")
        resources.excludes.add("META-INF/LICENSE.md")
        resources.excludes.add("META-INF/NOTICE")
        resources.excludes.add("META-INF/NOTICE.txt")
        resources.excludes.add("META-INF/notice.txt")
        resources.excludes.add("META-INF/NOTICE.md")
        resources.excludes.add("META-INF/ASL2.0")
        resources.excludes.add("META-INF/versions/**")
        resources.excludes.add("META-INF/*.kotlin_module")
        resources.excludes.add("META-INF/*.version")
        resources.excludes.add("DebugProbesKt.bin")
        resources.excludes.add("kotlin-tooling-metadata.json")
        resources.excludes.add("META-INF/AL2.0")
        resources.excludes.add("META-INF/LGPL2.1")
        // Picnic (legacy post-quantum signatures) lookup tables: nothing in Telos uses Picnic
        resources.excludes.add("org/bouncycastle/pqc/legacy/picnic/**")
        // Quartz/Amethyst pull protobuf-java while Telos uses protobuf-javalite.
        resources.excludes.add("google/protobuf/**")
        // Extract .so files so the Shizuku starter can be exec'd from nativeLibraryDir
        // (libshizuku.so) and so youtubedl-android can run Python/FFmpeg from disk.
        jniLibs.useLegacyPackaging = true
        
        // Keep the selected ABI's native libraries; size filtering must not empty a 32-bit APK.
        listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            .filterNot { it in telosAbis }
            .forEach { jniLibs.excludes.add("lib/$it/**") }
    }

    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    ndkVersion = "27.0.12077973"
    defaultConfig {
        applicationId = "com.dimitris.telos"
        minSdk = libs.versions.minSdk.get().toInt()
        missingDimensionStrategy("distribution", "fdroid")
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = System.getenv("VERSION_CODE_OVERRIDE")?.toIntOrNull() ?: releaseVersion.getProperty("versionCode").toInt()
        // Stable Android, desktop and GitHub tags share version.properties.
        versionName = System.getenv("VERSION_NAME_OVERRIDE")?.takeIf { it.isNotBlank() } ?: releaseVersion.getProperty("versionName")
        signingConfig = signingConfigs.getByName("debug")

        // Only the CPU types we ship: without this the x86 and x86_64 builds of the FFmpeg decoders
        // (and other libraries) end up in the APK although nothing runs them.
        ndk { abiFilters += telosAbis }
    }

    signingConfigs {
        create("gh-actions") {
            storeFile = file("${System.getenv("RUNNER_TEMP")}/keystore/keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            applicationIdSuffix = ".release"
            // Στο GitHub Actions (υπάρχει KEYSTORE_PASSWORD) υπογράφει με το Keystore, τοπικά με το debug κλειδί
            signingConfig = if (System.getenv("KEYSTORE_PASSWORD") != null)
                signingConfigs.getByName("gh-actions")
            else
                signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keepNames) proguardFile("stable-names.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        create("nightly") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))}-nightly"
            signingConfig = signingConfigs.findByName("gh-actions")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keepNames) proguardFile("stable-names.pro")
        }

        flavorDimensions += "variant"
        productFlavors {
            create("default") {
                dimension = "variant"
            }
            create("fdroid") {
                dimension = "variant"
                versionNameSuffix = "-fdroid"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    lint {
        abortOnError = false
    }
    namespace = "de.mm20.launcher2"
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures {
        buildConfig = true
        // === TELOS_PENDING_REVIEW_START: sandbox_cloning_and_bridge ===
        aidl = true
        // === TELOS_PENDING_REVIEW_END: sandbox_cloning_and_bridge ===
    }
}


dependencies {
    implementation("com.telos.amethyst:amethyst:1.21.0")
    implementation(libs.bundles.kotlin)

    // Android Jetpack & Optimization
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.coil.core)
    implementation(libs.coil.svg)

    implementation(libs.koin.android)

    implementation(project(":services:accounts"))
    implementation(project(":data:applications"))
    implementation(project(":data:appshortcuts"))
    implementation(project(":services:backup"))
    implementation(project(":services:badges"))
    implementation(project(":core:base"))
    implementation(project(":data:calculator"))
    implementation(project(":data:calendar"))
    implementation(project(":data:contacts"))
    implementation(project(":core:crashreporter"))
    implementation(project(":data:currencies"))
    implementation(project(":data:customattrs"))
    implementation(project(":data:searchable"))
    implementation(project(":data:plugins"))
    implementation(project(":data:themes"))
    implementation(project(":data:i18n"))
    implementation(project(":data:files"))
    implementation(project(":core:i18n"))
    implementation(project(":services:icons"))
    implementation(project(":core:ktx"))
    implementation(project(":services:music"))
    implementation(project(":libs:nextcloud"))
    implementation(project(":data:notifications"))
    implementation(project(":libs:owncloud"))
    implementation(project(":core:permissions"))
    implementation(project(":core:profiles"))
    implementation(project(":core:preferences"))
    implementation(project(":services:search"))
    implementation(project(":services:tags"))
    implementation(project(":data:unitconverter"))
    implementation(project(":app:ui"))
    implementation(project(":data:weather"))
    implementation(project(":data:websites"))
    implementation(project(":data:webappshortcuts"))
    implementation(project(":data:widgets"))
    implementation(project(":data:wikipedia"))
    implementation(project(":data:database"))
    implementation(project(":data:search-actions"))
    implementation(project(":services:global-actions"))
    implementation(project(":services:widgets"))
    implementation(project(":services:favorites"))
    implementation(project(":data:locations"))
    implementation(project(":services:plugins"))
    implementation(project(":core:devicepose"))
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
    implementation(project(":data:comms"))

    // === TELOS_PENDING_REVIEW_START: smart_freeze_dependencies ===
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    // === TELOS_PENDING_REVIEW_END: smart_freeze_dependencies ===

    // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
    implementation(libs.dhizuku.api)
    // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===

    // Uncomment this if you want annoying notifications in your debug builds
    //debugImplementation(libs.leakcanary)
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

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
