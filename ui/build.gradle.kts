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
        signingConfig = signingConfigs.getByName("debug")
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
        freeCompilerArgs = listOf(
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.text.ExperimentalTextApi",
            "-opt-in=androidx.compose.ui.unit.ExperimentalUnitApi",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=com.google.accompanist.pager.ExperimentalPagerApi",
        )
    }

    buildFeatures {
        // Enables Jetpack Compose for this module
        compose = true
        viewBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
    }

    lint {
        abortOnError = false
    }
    namespace = "de.mm20.launcher2.ui"
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

    implementation(libs.composecolorpicker)

    implementation(libs.jsoup)

    // Legacy dependencies
    implementation(libs.androidx.transition)

    implementation(libs.accompanist.insets)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.pager)
    implementation(libs.accompanist.pagerindicators)
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

    implementation(libs.coil.core)
    implementation(libs.coil.compose)

    implementation(libs.lottie)

    implementation(project(":material-color-utilities"))

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
    implementation(project(":appshortcuts"))
    implementation(project(":calculator"))
    implementation(project(":files"))
    implementation(project(":widgets"))
    implementation(project(":favorites"))
    implementation(project(":wikipedia"))
    implementation(project(":badges"))
    implementation(project(":crashreporter"))
    implementation(project(":notifications"))
    implementation(project(":contacts"))
    implementation(project(":permissions"))
    implementation(project(":websites"))
    implementation(project(":unitconverter"))
    implementation(project(":nextcloud"))
    implementation(project(":g-services"))
    implementation(project(":ms-services"))
    implementation(project(":owncloud"))
    implementation(project(":accounts"))
    implementation(project(":backup"))
}