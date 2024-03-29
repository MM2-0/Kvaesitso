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
    namespace = "de.mm20.launcher2.weather"
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work)
    implementation(libs.okhttp)
    implementation(libs.bundles.retrofit)
    implementation(libs.suncalc)
    implementation(libs.koin.android)

    implementation(project(":data:database"))
    implementation(project(":core:base"))
    implementation(project(":core:ktx"))
    implementation(project(":core:crashreporter"))
    implementation(project(":core:preferences"))
    implementation(project(":core:permissions"))
    implementation(project(":core:i18n"))
    implementation(project(":core:devicepose"))

}