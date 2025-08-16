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
    namespace = "de.mm20.launcher2.badges"
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.materialicons)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.koin.android)

    implementation(project(":core:ktx"))
    implementation(project(":data:applications"))
    implementation(project(":data:appshortcuts"))
    implementation(project(":data:notifications"))
    implementation(project(":core:preferences"))
    implementation(project(":core:profiles"))
    implementation(project(":core:base"))
    implementation(project(":data:files"))
    implementation(project(":data:searchable"))
}