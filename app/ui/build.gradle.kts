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
        create("nightly") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
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
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
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
    implementation(libs.androidx.lifecycle.runtimecompose)

    implementation(libs.jsoup)

    implementation(libs.koin.android)
    implementation(libs.koin.androidxcompose)

    implementation(libs.coil.core)
    implementation(libs.coil.compose)

    implementation(libs.lottie)

    implementation(project(":libs:material-color-utilities"))

    implementation(project(":core:base"))
    implementation(project(":core:i18n"))
    implementation(project(":core:compat"))
    implementation(project(":core:ktx"))
    implementation(project(":services:icons"))
    implementation(project(":services:music"))
    implementation(project(":services:tags"))
    implementation(project(":data:weather"))
    implementation(project(":data:calendar"))
    implementation(project(":services:search"))
    implementation(project(":core:preferences"))
    implementation(project(":data:applications"))
    implementation(project(":data:appshortcuts"))
    implementation(project(":data:calculator"))
    implementation(project(":data:files"))
    implementation(project(":data:widgets"))
    implementation(project(":data:favorites"))
    implementation(project(":data:wikipedia"))
    implementation(project(":services:badges"))
    implementation(project(":core:crashreporter"))
    implementation(project(":data:notifications"))
    implementation(project(":data:contacts"))
    implementation(project(":core:permissions"))
    implementation(project(":data:websites"))
    implementation(project(":data:unitconverter"))
    implementation(project(":libs:nextcloud"))
    implementation(project(":libs:g-services"))
    implementation(project(":libs:ms-services"))
    implementation(project(":libs:owncloud"))
    implementation(project(":services:accounts"))
    implementation(project(":services:backup"))
    implementation(project(":data:search-actions"))
    implementation(project(":services:global-actions"))
    implementation(project(":services:widgets"))
}