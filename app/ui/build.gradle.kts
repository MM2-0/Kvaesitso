import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.plugin.compose)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        
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

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
            optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
            optIn.add("androidx.compose.ui.text.ExperimentalTextApi")
            optIn.add("androidx.compose.ui.unit.ExperimentalUnitApi")
            optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
            optIn.add("androidx.compose.material.ExperimentalMaterialApi")
            optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
            optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            optIn.add("androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi")
            optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
            optIn.add("com.google.accompanist.pager.ExperimentalPagerApi")
            optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    lint {
        abortOnError = false
    }
    namespace = "de.mm20.launcher2.ui"
}


dependencies {
    implementation(libs.bundles.kotlin)

    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundationlayout)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.uitooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animationgraphics)
    implementation(libs.androidx.constraintlayout.compose)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material3adaptive.navigation3)

    implementation(libs.jsoup)
    implementation(libs.markdown)

    // Legacy dependencies
    implementation(libs.androidx.transition)

    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.pager)
    implementation(libs.accompanist.pagerindicators)
    implementation(libs.accompanist.flowlayout)

    implementation(libs.haze)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activitycompose)
    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.emojipicker)

    implementation(libs.androidx.lifecycle.viewmodelcompose)
    implementation(libs.androidx.lifecycle.runtimecompose)

    implementation(libs.jsoup)

    implementation(libs.koin.android)
    implementation(libs.koin.androidxcompose)

    implementation(libs.coil.core)
    implementation(libs.coil.compose)

    implementation(libs.smartspacer) {
        exclude(group = "com.github.skydoves", module = "balloon")
    }

    implementation(project(":libs:material-color-utilities"))

    implementation(project(":core:base"))
    implementation(project(":core:i18n"))
    implementation(project(":core:compat"))
    implementation(project(":core:ktx"))
    implementation(project(":core:profiles"))
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
    implementation(project(":data:searchable"))
    implementation(project(":data:themes"))
    implementation(project(":data:wikipedia"))
    implementation(project(":services:badges"))
    implementation(project(":core:crashreporter"))
    implementation(project(":data:notifications"))
    implementation(project(":data:contacts"))
    implementation(project(":data:locations"))
    implementation(project(":core:permissions"))
    implementation(project(":data:websites"))
    implementation(project(":data:unitconverter"))
    implementation(project(":libs:nextcloud"))
    implementation(project(":libs:owncloud"))
    implementation(project(":services:accounts"))
    implementation(project(":services:plugins"))
    implementation(project(":services:backup"))
    implementation(project(":data:search-actions"))
    implementation(project(":services:global-actions"))
    implementation(project(":services:widgets"))
    implementation(project(":services:favorites"))
    implementation(project(":core:devicepose"))
}