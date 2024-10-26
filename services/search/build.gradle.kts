plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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
    namespace = "de.mm20.launcher2.search"
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)

    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.koin.android)

    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.coil.core)

    implementation(project(":data:calculator"))
    implementation(project(":data:unitconverter"))
    implementation(project(":data:customattrs"))
    implementation(project(":data:search-actions"))

    implementation(project(":core:base"))
    implementation(project(":core:preferences"))
    implementation(project(":core:profiles"))
    implementation(project(":core:crashreporter"))
    implementation(project(":core:ktx"))
}