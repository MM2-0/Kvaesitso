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
    namespace = "de.mm20.launcher2.widgets"
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)

    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.koin.android)

    implementation(project(":data:weather"))
    implementation(project(":data:calendar"))
    implementation(project(":services:music"))
    implementation(project(":core:ktx"))
    implementation(project(":core:base"))
    implementation(project(":core:preferences"))
    implementation(project(":data:database"))
    implementation(project(":core:crashreporter"))

}