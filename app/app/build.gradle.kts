import android.annotation.SuppressLint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/license.txt")
        resources.excludes.add("META-INF/NOTICE")
        resources.excludes.add("META-INF/NOTICE.txt")
        resources.excludes.add("META-INF/notice.txt")
        resources.excludes.add("META-INF/ASL2.0")
        resources.excludes.add("META-INF/LICENSE.md")
        resources.excludes.add("META-INF/NOTICE.md")
    }

    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "de.mm20.launcher2"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        @SuppressLint("HighAppVersionCode")
        versionCode = System.getenv("VERSION_CODE_OVERRIDE")?.toIntOrNull() ?: 2025123000
        versionName = "1.39.2"
        signingConfig = signingConfigs.getByName("debug")
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

            postprocessing {
                isRemoveUnusedCode = true
                isObfuscate = false
                isOptimizeCode = true
            }
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

            postprocessing {
                isRemoveUnusedCode = true
                isObfuscate = false
                isOptimizeCode = true
            }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    lint {
        abortOnError = false
    }
    namespace = "de.mm20.launcher2"
}


dependencies {
    implementation(libs.bundles.kotlin)

    //Android Jetpack
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)

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

    // Uncomment this if you want annoying notifications in your debug builds
    //debugImplementation(libs.leakcanary)
}
