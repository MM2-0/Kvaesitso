plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    compileSdk = sdk.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = sdk.versions.minSdk.get().toInt()
        targetSdk = sdk.versions.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
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
    namespace = "de.mm20.launcher2.database"
}

dependencies {

    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    api(libs.androidx.roomruntime)
    kapt(libs.androidx.roomcompiler)
    implementation(libs.androidx.room)
    implementation(libs.koin.android)

    implementation(project(":i18n"))
    implementation(project(":ktx"))

}