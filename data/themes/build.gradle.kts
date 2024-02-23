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

    kotlinOptions {
        jvmTarget = "1.8"
    }
    namespace = "de.mm20.launcher2.themes"
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.koin.android)

    implementation(project(":core:base"))
    implementation(project(":data:database"))
    implementation(project(":core:crashreporter"))
    implementation(project(":core:preferences"))
    implementation(project(":libs:material-color-utilities"))

}
