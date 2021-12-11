plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = sdk.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = sdk.versions.minSdk.get().toInt()
        targetSdk = sdk.versions.targetSdk.get().toInt()

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

    buildFeatures {
        // Enables Jetpack Compose for this module
        compose = true
        viewBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidx.compose.get()
    }

    lint {
        isAbortOnError = false
    }
}

dependencies {
    implementation(libs.bundles.kotlin)

    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.livedata)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundationlayout)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.uitooling)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.materialicons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animationgraphics)

    implementation(libs.androidx.navigation.compose)

    // Legacy dependencies
    implementation(libs.androidx.transition)
    implementation(libs.materialcomponents)
    implementation(libs.viewpropertyobjectanimator)
    implementation(libs.glide)
    implementation(libs.draglinearlayout)
    implementation(libs.lottie.core)
    implementation(libs.bundles.groupie)
    implementation(libs.glidetransformations)

    implementation(libs.accompanist.insets)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.pager)
    implementation(libs.accompanist.flowlayout)
    implementation(libs.accompanist.navigationanimation)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activitycompose)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)

    implementation(libs.androidx.lifecycle.viewmodelcompose)

    implementation(libs.bundles.materialdialogs)

    implementation(libs.jsoup)

    implementation(libs.koin.android)
    implementation(libs.koin.androidxcompose)

    implementation(project(":base"))
    implementation(project(":i18n"))
    implementation(project(":compat"))
    implementation(project(":ktx"))
    implementation(project(":icons"))
    implementation(project(":music"))
    implementation(project(":weather"))
    implementation(project(":calendar"))
    implementation(project(":search"))
    implementation(project(":preferences"))
    implementation(project(":applications"))
    implementation(project(":calculator"))
    implementation(project(":files"))
    implementation(project(":widgets"))
    implementation(project(":favorites"))
    implementation(project(":wikipedia"))
    implementation(project(":badges"))
    implementation(project(":crashreporter"))
    implementation(project(":notifications"))
    implementation(project(":transition"))
    implementation(project(":contacts"))
    implementation(project(":permissions"))
    implementation(project(":websites"))
    implementation(project(":unitconverter"))
    implementation(project(":nextcloud"))
    implementation(project(":g-services"))
    implementation(project(":ms-services"))
    implementation(project(":owncloud"))

}