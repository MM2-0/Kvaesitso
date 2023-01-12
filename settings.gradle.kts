enableFeaturePreview("VERSION_CATALOGS")


dependencyResolutionManagement {
    versionCatalogs {
        create("sdk") {
            version("minSdk", "26")
            version("compileSdk", "33")
            version("targetSdk", "33")
        }
        create("libs") {
            version("kotlin", "1.7.21")
            version("kotlinx.coroutines", "1.6.4")
            library("kotlin.stdlib", "org.jetbrains.kotlin", "kotlin-stdlib")
                .versionRef("kotlin")
            library("kotlin.gradle", "org.jetbrains.kotlin", "kotlin-gradle-plugin")
                .versionRef("kotlin")
            library("kotlinx.coroutines.core", "org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                .versionRef("kotlinx.coroutines")
            library("kotlinx.coroutines.android", "org.jetbrains.kotlinx", "kotlinx-coroutines-android")
                .versionRef("kotlinx.coroutines")
            library("kotlinx.collections.immutable", "org.jetbrains.kotlinx", "kotlinx-collections-immutable")
                .version("0.3.5")
            bundle(
                "kotlin",
                listOf(
                    "kotlin.stdlib",
                    "kotlinx.coroutines.core",
                    "kotlinx.coroutines.android",
                    "kotlinx.collections.immutable"
                )
            )

            version("androidx.compose.compiler", "1.4.0-alpha02")
            library("androidx.compose.runtime", "androidx.compose.runtime", "runtime")
                .version("1.4.0-alpha04")
            library("androidx.compose.livedata", "androidx.compose.runtime", "runtime-livedata")
                .version("1.4.0-alpha04")
            library("androidx.compose.foundation", "androidx.compose.foundation", "foundation")
                .version("1.4.0-alpha04")
            library("androidx.compose.foundationlayout", "androidx.compose.foundation", "foundation-layout")
                .version("1.4.0-alpha04")
            library("androidx.compose.ui", "androidx.compose.ui", "ui")
                .version("1.4.0-alpha04")
            library("androidx.compose.uitooling", "androidx.compose.ui", "ui-tooling")
                .version("1.4.0-alpha04")
            library("androidx.compose.material", "androidx.compose.material", "material")
                .version("1.4.0-alpha04")
            library("androidx.compose.materialicons", "androidx.compose.material", "material-icons-extended")
                .version("1.4.0-alpha04")
            library("androidx.compose.animation", "androidx.compose.animation", "animation")
                .version("1.4.0-alpha04")
            library("androidx.compose.animationgraphics", "androidx.compose.animation", "animation-graphics")
                .version("1.4.0-alpha04")
            library("androidx.compose.material3", "androidx.compose.material3", "material3")
                .version("1.1.0-alpha04")

            version("androidx.lifecycle", "2.6.0-alpha04")
            library("androidx.lifecycle.viewmodel", "androidx.lifecycle", "lifecycle-viewmodel-ktx")
                .versionRef("androidx.lifecycle")
            library("androidx.lifecycle.livedata", "androidx.lifecycle", "lifecycle-livedata-ktx")
                .versionRef("androidx.lifecycle")
            library("androidx.lifecycle.common", "androidx.lifecycle", "lifecycle-common-java8")
                .versionRef("androidx.lifecycle")
            library("androidx.lifecycle.runtime", "androidx.lifecycle", "lifecycle-runtime-ktx")
                .versionRef("androidx.lifecycle")
            library("androidx.lifecycle.viewmodelcompose", "androidx.lifecycle", "lifecycle-viewmodel-compose")
                .versionRef("androidx.lifecycle")
            bundle(
                "androidx.lifecycle",
                listOf(
                    "androidx.lifecycle.viewmodel",
                    "androidx.lifecycle.livedata",
                    "androidx.lifecycle.common",
                    "androidx.lifecycle.runtime"
                )
            )

            version("accompanist", "0.28.0")
            library("accompanist.insets", "com.google.accompanist", "accompanist-insets")
                .versionRef("accompanist")
            library("accompanist.systemuicontroller", "com.google.accompanist", "accompanist-systemuicontroller")
                .versionRef("accompanist")
            library("accompanist.pager", "com.google.accompanist", "accompanist-pager")
                .versionRef("accompanist")
            library("accompanist.pagerindicators", "com.google.accompanist", "accompanist-pager-indicators")
                .versionRef("accompanist")
            library("accompanist.flowlayout", "com.google.accompanist", "accompanist-flowlayout")
                .versionRef("accompanist")
            library("accompanist.navigationanimation", "com.google.accompanist", "accompanist-navigation-animation")
                .versionRef("accompanist")

            library("androidx.core", "androidx.core", "core-ktx")
                .version("1.9.0")

            version("androidx.appcompat", "1.6.0-rc01")
            library("androidx.appcompat", "androidx.appcompat", "appcompat")
                .versionRef("androidx.appcompat")

            version("androidx.activity", "1.6.0-rc02")
            library("androidx.activity", "androidx.activity", "activity-ktx")
                .versionRef("androidx.activity")
            library("androidx.activitycompose", "androidx.activity", "activity-compose")
                .versionRef("androidx.activity")

            library("androidx.work", "androidx.work", "work-runtime-ktx")
                .version("2.8.0-alpha04")

            library("androidx.browser", "androidx.browser", "browser")
                .version("1.4.0")

            library("androidx.palette", "androidx.palette", "palette")
                .version("1.0.0")

            library("androidx.media2", "androidx.media2", "media2-session")
                .version("1.2.1")

            library("androidx.constraintlayout", "androidx.constraintlayout", "constraintlayout")
                .version("2.1.3")

            library("androidx.cardview", "androidx.cardview", "cardview")
                .version("1.0.0")

            library("androidx.recyclerview", "androidx.recyclerview", "recyclerview")
                .version("1.3.0-alpha01")

            library("androidx.transition", "androidx.transition", "transition")
                .version("1.4.1")

            library("androidx.exifinterface", "androidx.exifinterface", "exifinterface")
                .version("1.3.3")

            library("androidx.securitycrypto", "androidx.security", "security-crypto")
                .version("1.1.0-alpha03")

            library("androidx.datastore", "androidx.datastore", "datastore")
                .version("1.0.0")

            version("androidx.room", "2.5.0-alpha03")
            library("androidx.roomruntime", "androidx.room", "room-runtime")
                .versionRef("androidx.room")
            library("androidx.roomcompiler", "androidx.room", "room-compiler")
                .versionRef("androidx.room")
            library("androidx.room", "androidx.room", "room-ktx")
                .versionRef("androidx.room")

            version("androidx.appsearch", "1.1.0-alpha02")
            library("androidx.appsearch", "androidx.appsearch", "appsearch")
                .versionRef("androidx.appsearch")
            library("androidx.appsearchcompiler", "androidx.appsearch", "appsearch-compiler")
                .versionRef("androidx.appsearch")
            library("androidx.appsearchstorage", "androidx.appsearch", "appsearch-platform-storage")
                .versionRef("androidx.appsearch")
            bundle(
                "androidx.appsearch", listOf(
                    "androidx.appsearch",
                    "androidx.appsearchstorage"
                )
            )

            library("androidx.navigation.compose", "androidx.navigation", "navigation-compose")
                .version("2.6.0-alpha01")

            library("materialcomponents.core", "com.google.android.material", "material")
                .version("1.8.0-alpha01")

            library("okhttp", "com.squareup.okhttp3", "okhttp")
                .version("4.10.0")

            library("retrofit.core", "com.squareup.retrofit2", "retrofit")
                .version("2.9.0")
            library("retrofit.gson", "com.squareup.retrofit2", "converter-gson")
                .version("2.9.0")
            bundle(
                "retrofit",
                listOf(
                    "retrofit.core",
                    "retrofit.gson"
                )
            )

            version("coil", "2.2.1")
            library("coil.core", "io.coil-kt", "coil")
                .versionRef("coil")
            library("coil.svg", "io.coil-kt", "coil-svg")
                .versionRef("coil")
            library("coil.compose", "io.coil-kt", "coil-compose")
                .versionRef("coil")

            library("composecolorpicker", "com.godaddy.android.colorpicker", "compose-color-picker")
                .version("0.5.0")

            library("leakcanary", "com.squareup.leakcanary", "leakcanary-android")
                .version("2.9.1")

            library("suncalc", "org.shredzone.commons", "commons-suncalc")
                .version("3.5")

            library("jsoup", "org.jsoup", "jsoup")
                .version("1.15.3")

            library("commons.text", "org.apache.commons", "commons-text")
                .version("1.9")

            // 4.4.2 is the last GPL compatible version, don't update to 5.x
            library("mathparser", "org.mariuszgromada.math", "MathParser.org-mXparser")
                .version("4.4.2")

            library("google.auth", "com.google.auth", "google-auth-library-oauth2-http")
                .version("1.11.0")
            library("google.apiclient", "com.google.api-client", "google-api-client-android")
                .version("2.0.0")
            library("google.drive", "com.google.apis", "google-api-services-drive")
                .version("v3-rev197-1.25.0")
            library("google.oauth2", "com.google.apis", "google-api-services-oauth2")
                .version("v2-rev157-1.25.0")

            library("gson", "com.google.code.gson", "gson")
                .version("2.9.1")

            library("guava", "com.google.guava", "guava")
                .version("31.1-android")

            library("microsoft.graph", "com.microsoft.graph", "microsoft-graph")
                .version("5.42.0")
            library("microsoft.identity", "com.microsoft.identity.client", "msal")
                .version("4.1.0")

            version("protobuf", "3.14.0")
            library("protobuf.protoc", "com.google.protobuf", "protoc")
                .versionRef("protobuf")
            library("protobuf.javalite", "com.google.protobuf", "protobuf-javalite")
                .versionRef("protobuf")

            version("koin", "3.2.0")
            library("koin.android", "io.insert-koin", "koin-android")
                .versionRef("koin")
            library("koin.androidxcompose", "io.insert-koin", "koin-androidx-compose")
                .versionRef("koin")

            library("tinypinyin", "com.github.promeg", "tinypinyin")
                .version("2.0.2")

            library("lottie", "com.airbnb.android", "lottie-compose")
                .version("5.2.0")

            version("junit", "4.13")
            library("junit", "junit", "junit").versionRef("junit")
            bundle("tests", listOf("junit"))
        }
    }
}

include(":app:app")
include(":app:ui")

include(":core:base")
include(":core:crashreporter")
include(":core:compat")
include(":core:preferences")
include(":core:ktx")
include(":core:i18n")
include(":core:database")
include(":core:permissions")

include(":data:appshortcuts")
include(":data:customattrs")
include(":data:applications")
include(":data:calendar")
include(":data:calculator")
include(":data:contacts")
include(":data:currencies")
include(":data:files")
include(":data:unitconverter")
include(":data:websites")
include(":data:wikipedia")
include(":data:widgets")
include(":data:weather")
include(":data:notifications")
include(":data:search-actions")
include(":data:favorites")

include(":services:accounts")
include(":services:tags")
include(":services:search")
include(":services:badges")
include(":services:icons")
include(":services:backup")
include(":services:music")

include(":libs:material-color-utilities")
include(":libs:nextcloud")
include(":libs:owncloud")
include(":libs:webdav")
include(":libs:g-services")
include(":libs:ms-services")
