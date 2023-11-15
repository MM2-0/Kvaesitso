@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.mm20.launcher2.openstreetmaps"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {

    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)

    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.okhttp)
    implementation(libs.bundles.retrofit)

    implementation(libs.koin.android)

    implementation(libs.androidx.appcompat)

    implementation(project(":core:preferences"))
    implementation(project(":core:base"))
    implementation(project(":core:ktx"))
    implementation(project(":core:permissions"))
    implementation(project(":core:crashreporter"))
    implementation(project(":core:devicepose"))
}