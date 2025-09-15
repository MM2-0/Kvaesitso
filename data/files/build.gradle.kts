import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    namespace = "de.mm20.launcher2.files"
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.datastore)

    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.koin.android)
    implementation(libs.coil.core)

    implementation(project(":core:base"))
    implementation(project(":core:ktx"))
    implementation(project(":libs:nextcloud"))
    implementation(project(":libs:owncloud"))
    implementation(project(":core:i18n"))
    implementation(project(":core:permissions"))
    implementation(project(":core:crashreporter"))
    implementation(project(":core:preferences"))
}