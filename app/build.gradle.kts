import java.text.SimpleDateFormat
import java.util.*

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    packagingOptions {
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/license.txt")
        resources.excludes.add("META-INF/NOTICE")
        resources.excludes.add("META-INF/NOTICE.txt")
        resources.excludes.add("META-INF/notice.txt")
        resources.excludes.add("META-INF/ASL2.0")
        resources.excludes.add("META-INF/LICENSE.md")
        resources.excludes.add("META-INF/NOTICE.md")
    }

    compileSdk = sdk.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "de.mm20.launcher2"
        minSdk = sdk.versions.minSdk.get().toInt()
        targetSdk = sdk.versions.targetSdk.get().toInt()
        versionCode = versionCodeDate()
        versionName = "1.8.0"
        signingConfig = signingConfigs.getByName("debug")
    }
    buildTypes {
        release {
            applicationIdSuffix = ".release"

            postprocessing {
                isRemoveUnusedCode = false
                isObfuscate = false
                isOptimizeCode = false
            }

            versionNameSuffix = "-" + buildTime()
        }
        debug {
            applicationIdSuffix = ".debug"
            // Jetpack Compose is unusably laggy in debug builds, it's ridiculous
            // This somehow seems to resolve that issue.
            isDebuggable = false
        }
    }
    configurations.all {
        //Fixes Error: Duplicate class: com.google.common.util.concurrent.ListenableFuture
        exclude(group = "com.google.guava", module = "listenablefuture")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    lint {
        abortOnError = false
    }
}

fun buildTime(): String {
    val df = SimpleDateFormat("yyyyMMdd")
    return df.format(Date())
}

fun versionCodeDate(): Int {
    val df = SimpleDateFormat("yyyyMMdd00")
    return df.format(Date()).toInt()
}


dependencies {
    implementation(libs.bundles.kotlin)

    //Android Jetpack
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.media2)
    implementation(libs.materialcomponents.core)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.androidx.work)

    implementation(libs.coil.core)
    implementation(libs.coil.svg)

    implementation(libs.bundles.materialdialogs)

    implementation(libs.draglinearlayout)
    implementation(libs.viewpropertyobjectanimator)

    implementation(libs.koin.android)

    implementation(project(":accounts"))
    implementation(project(":applications"))
    implementation(project(":appshortcuts"))
    implementation(project(":badges"))
    implementation(project(":base"))
    implementation(project(":calculator"))
    implementation(project(":calendar"))
    implementation(project(":contacts"))
    implementation(project(":crashreporter"))
    implementation(project(":currencies"))
    implementation(project(":favorites"))
    implementation(project(":files"))
    implementation(project(":g-services"))
    implementation(project(":hiddenitems"))
    implementation(project(":i18n"))
    implementation(project(":icons"))
    implementation(project(":ktx"))
    implementation(project(":ms-services"))
    implementation(project(":music"))
    implementation(project(":nextcloud"))
    implementation(project(":notifications"))
    implementation(project(":owncloud"))
    implementation(project(":permissions"))
    implementation(project(":preferences"))
    implementation(project(":search"))
    implementation(project(":transition"))
    implementation(project(":unitconverter"))
    implementation(project(":ui"))
    implementation(project(":weather"))
    implementation(project(":websites"))
    implementation(project(":widgets"))
    implementation(project(":wikipedia"))
    implementation(project(":database"))

    // Uncomment this if you want annoying notifications in your debug builds yelling at you how terrible your code is
    //debugImplementation(libs.leakcanary)
}