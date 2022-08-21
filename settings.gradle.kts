enableFeaturePreview("VERSION_CATALOGS")

include(":currencies")
include(":nextcloud")
include(":owncloud")
include(":webdav")
include(":unitconverter")
include(":websites")
include(":wikipedia")
include(":contacts")
include(":g-services")
include(":files")
include(":calculator")
include(":badges")
include(":applications")
include(":ms-services")
include(":favorites")
include(":calendar")
include(":icons")
include(":search")
include(":crashreporter")
include(":music")
include(":preferences")
include(":base")
include(":transition")
include(":widgets")
include(":weather")
include(":i18n")
include(":database")
include(":ktx")
include(":app")
include(":permissions")
include(":ui")
include(":compat")

dependencyResolutionManagement {
    versionCatalogs {
        create("sdk") {
            version("minSdk", "26")
            version("compileSdk", "33")
            version("targetSdk", "33")
        }
        create("libs") {
            version("kotlin", "1.7.10")
            version("kotlinx.coroutines", "1.6.4")
            alias("kotlin.stdlib")
                .to("org.jetbrains.kotlin", "kotlin-stdlib")
                .versionRef("kotlin")
            alias("kotlin.gradle")
                .to("org.jetbrains.kotlin", "kotlin-gradle-plugin")
                .versionRef("kotlin")
            alias("kotlinx.coroutines.core")
                .to("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
                .versionRef("kotlinx.coroutines")
            alias("kotlinx.coroutines.android")
                .to("org.jetbrains.kotlinx", "kotlinx-coroutines-android")
                .versionRef("kotlinx.coroutines")
            alias("kotlinx.collections.immutable")
                .to("org.jetbrains.kotlinx", "kotlinx-collections-immutable")
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

            version("androidx.compose.compiler", "1.3.0-rc01")
            alias("androidx.compose.runtime")
                .to("androidx.compose.runtime", "runtime")
                .version("1.3.0-alpha03")
            alias("androidx.compose.livedata")
                .to("androidx.compose.runtime", "runtime-livedata")
                .version("1.3.0-alpha03")
            alias("androidx.compose.foundation")
                .to("androidx.compose.foundation", "foundation")
                .version("1.3.0-alpha03")
            alias("androidx.compose.foundationlayout")
                .to("androidx.compose.foundation", "foundation-layout")
                .version("1.3.0-alpha03")
            alias("androidx.compose.ui")
                .to("androidx.compose.ui", "ui")
                .version("1.3.0-alpha03")
            alias("androidx.compose.uitooling")
                .to("androidx.compose.ui", "ui-tooling")
                .version("1.3.0-alpha03")
            alias("androidx.compose.material")
                .to("androidx.compose.material", "material")
                .version("1.3.0-alpha03")
            alias("androidx.compose.materialicons")
                .to("androidx.compose.material", "material-icons-extended")
                .version("1.3.0-alpha03")
            alias("androidx.compose.animation")
                .to("androidx.compose.animation", "animation")
                .version("1.3.0-alpha03")
            alias("androidx.compose.animationgraphics")
                .to("androidx.compose.animation", "animation-graphics")
                .version("1.3.0-alpha03")
            alias("androidx.compose.material3")
                .to("androidx.compose.material3", "material3")
                .version("1.0.0-alpha16")

            version("androidx.lifecycle", "2.4.1")
            alias("androidx.lifecycle.viewmodel")
                .to("androidx.lifecycle", "lifecycle-viewmodel-ktx")
                .versionRef("androidx.lifecycle")
            alias("androidx.lifecycle.livedata")
                .to("androidx.lifecycle", "lifecycle-livedata-ktx")
                .versionRef("androidx.lifecycle")
            alias("androidx.lifecycle.common")
                .to("androidx.lifecycle", "lifecycle-common-java8")
                .versionRef("androidx.lifecycle")
            alias("androidx.lifecycle.runtime")
                .to("androidx.lifecycle", "lifecycle-runtime-ktx")
                .versionRef("androidx.lifecycle")
            alias("androidx.lifecycle.viewmodelcompose")
                .to("androidx.lifecycle", "lifecycle-viewmodel-compose")
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

            version("accompanist", "0.24.11-rc")
            alias("accompanist.insets")
                .to("com.google.accompanist", "accompanist-insets")
                .versionRef("accompanist")
            alias("accompanist.systemuicontroller")
                .to("com.google.accompanist", "accompanist-systemuicontroller")
                .versionRef("accompanist")
            alias("accompanist.pager")
                .to("com.google.accompanist", "accompanist-pager")
                .versionRef("accompanist")
            alias("accompanist.pagerindicators")
                .to("com.google.accompanist", "accompanist-pager-indicators")
                .versionRef("accompanist")
            alias("accompanist.flowlayout")
                .to("com.google.accompanist", "accompanist-flowlayout")
                .versionRef("accompanist")
            alias("accompanist.navigationanimation")
                .to("com.google.accompanist", "accompanist-navigation-animation")
                .versionRef("accompanist")

            alias("androidx.core")
                .to("androidx.core", "core-ktx")
                .version("1.8.0-rc01")

            version("androidx.appcompat", "1.4.1")
            alias("androidx.appcompat")
                .to("androidx.appcompat", "appcompat")
                .versionRef("androidx.appcompat")

            version("androidx.activity", "1.4.0")
            alias("androidx.activity")
                .to("androidx.activity", "activity-ktx")
                .versionRef("androidx.activity")
            alias("androidx.activitycompose")
                .to("androidx.activity", "activity-compose")
                .versionRef("androidx.activity")

            alias("androidx.work")
                .to("androidx.work", "work-runtime-ktx")
                .version("2.7.1")

            alias("androidx.browser")
                .to("androidx.browser", "browser")
                .version("1.4.0")

            alias("androidx.palette")
                .to("androidx.palette", "palette")
                .version("1.0.0")

            alias("androidx.media2")
                .to("androidx.media2", "media2-session")
                .version("1.2.1")

            alias("androidx.constraintlayout")
                .to("androidx.constraintlayout", "constraintlayout")
                .version("2.1.3")

            alias("androidx.cardview")
                .to("androidx.cardview", "cardview")
                .version("1.0.0")

            alias("androidx.recyclerview")
                .to("androidx.recyclerview", "recyclerview")
                .version("1.3.0-alpha01")

            alias("androidx.transition")
                .to("androidx.transition", "transition")
                .version("1.4.1")

            alias("androidx.exifinterface")
                .to("androidx.exifinterface", "exifinterface")
                .version("1.3.3")

            alias("androidx.fragment")
                .to("androidx.fragment", "fragment-ktx")
                .version("1.4.1")

            alias("androidx.securitycrypto")
                .to("androidx.security", "security-crypto")
                .version("1.1.0-alpha03")

            alias("androidx.datastore")
                .to("androidx.datastore", "datastore")
                .version("1.0.0")

            version("androidx.room", "2.5.0-alpha02")
            alias("androidx.roomruntime")
                .to("androidx.room", "room-runtime")
                .versionRef("androidx.room")
            alias("androidx.roomcompiler")
                .to("androidx.room", "room-compiler")
                .versionRef("androidx.room")
            alias("androidx.room")
                .to("androidx.room", "room-ktx")
                .versionRef("androidx.room")

            version("androidx.appsearch", "1.0.0-alpha04")
            alias("androidx.appsearch")
                .to("androidx.appsearch", "appsearch")
                .versionRef("androidx.appsearch")
            alias("androidx.appsearchcompiler")
                .to("androidx.appsearch", "appsearch-compiler")
                .versionRef("androidx.appsearch")
            alias("androidx.appsearchstorage")
                .to("androidx.appsearch", "appsearch-platform-storage")
                .versionRef("androidx.appsearch")
            bundle(
                "androidx.appsearch", listOf(
                    "androidx.appsearch",
                    "androidx.appsearchstorage"
                )
            )

            alias("androidx.navigation.compose")
                .to("androidx.navigation", "navigation-compose")
                .version("2.5.0-rc02")

            alias("materialcomponents.core")
                .to("com.google.android.material", "material")
                .version("1.6.0-beta01")
            alias("materialcomponents.composethemeadapter3")
                .to("com.google.android.material", "compose-theme-adapter-3")
                .version("1.0.6")
            alias("materialcomponents.composethemeadapter")
                .to("com.google.android.material", "compose-theme-adapter")
                .version("1.1.6")

            alias("okhttp")
                .to("com.squareup.okhttp3", "okhttp")
                .version("4.10.0")

            alias("retrofit.core")
                .to("com.squareup.retrofit2", "retrofit")
                .version("2.9.0")
            alias("retrofit.gson")
                .to("com.squareup.retrofit2", "converter-gson")
                .version("2.9.0")
            bundle(
                "retrofit",
                listOf(
                    "retrofit.core",
                    "retrofit.gson"
                )
            )

            version("coil", "2.1.0")
            alias("coil.core")
                .to("io.coil-kt", "coil")
                .versionRef("coil")
            alias("coil.svg")
                .to("io.coil-kt", "coil-svg")
                .versionRef("coil")
            alias("coil.compose")
                .to("io.coil-kt", "coil-compose")
                .versionRef("coil")

            alias("composecolorpicker")
                .to("com.godaddy.android.colorpicker", "compose-color-picker")
                .version("0.3.1")

            alias("leakcanary")
                .to("com.squareup.leakcanary", "leakcanary-android")
                .version("2.9.1")

            alias("suncalc")
                .to("org.shredzone.commons", "commons-suncalc")
                .version("3.5")

            alias("jsoup")
                .to("org.jsoup", "jsoup")
                .version("1.14.2")

            alias("commons.text")
                .to("org.apache.commons", "commons-text")
                .version("1.9")

            version("materialdialogs", "3.3.0")
            alias("materialdialogs.core")
                .to("com.afollestad.material-dialogs", "core")
                .versionRef("materialdialogs")
            alias("materialdialogs.input")
                .to("com.afollestad.material-dialogs", "input")
                .versionRef("materialdialogs")
            alias("materialdialogs.color")
                .to("com.afollestad.material-dialogs", "color")
                .versionRef("materialdialogs")
            alias("materialdialogs.bottomsheets")
                .to("com.afollestad.material-dialogs", "bottomsheets")
                .versionRef("materialdialogs")
            bundle(
                "materialdialogs", listOf(
                    "materialdialogs.core",
                    "materialdialogs.input",
                    "materialdialogs.color",
                    "materialdialogs.bottomsheets"
                )
            )

            alias("draglinearlayout")
                .to("com.jmedeisis", "draglinearlayout")
                .version("1.1.0")

            alias("viewpropertyobjectanimator")
                .to("com.bartoszlipinski", "viewpropertyobjectanimator")
                .version("1.5.0")

            alias("mathparser")
                .to("org.mariuszgromada.math", "MathParser.org-mXparser")
                .version("4.4.2")

            alias("google.auth")
                .to("com.google.auth", "google-auth-library-oauth2-http")
                .version("1.4.0")
            alias("google.apiclient")
                .to("com.google.api-client", "google-api-client-android")
                .version("1.33.2")
            alias("google.drive")
                .to("com.google.apis", "google-api-services-drive")
                .version("v3-rev197-1.25.0")
            alias("google.oauth2")
                .to("com.google.apis", "google-api-services-oauth2")
                .version("v2-rev157-1.25.0")

            alias("gson")
                .to("com.google.code.gson", "gson")
                .version("2.8.6")

            alias("guava")
                .to("com.google.guava", "guava")
                .version("30.1.1-android")

            alias("microsoft.graph")
                .to("com.microsoft.graph", "microsoft-graph")
                .version("5.31.0")
            alias("microsoft.identity")
                .to("com.microsoft.identity.client", "msal")
                .version("4.0.0")

            version("protobuf", "3.14.0")
            alias("protobuf.protoc")
                .to("com.google.protobuf", "protoc")
                .versionRef("protobuf")
            alias("protobuf.javalite")
                .to("com.google.protobuf", "protobuf-javalite")
                .versionRef("protobuf")

            version("koin", "3.1.6")
            alias("koin.android")
                .to("io.insert-koin", "koin-android")
                .versionRef("koin")
            alias("koin.androidxcompose")
                .to("io.insert-koin", "koin-androidx-compose")
                .versionRef("koin")

            alias("tinypinyin")
                .to("com.github.promeg", "tinypinyin")
                .version("2.0.2")

            alias("lottie")
                .to("com.airbnb.android", "lottie-compose")
                .version("5.0.3")
        }
    }
}
include(":notifications")
include(":accounts")
include(":appshortcuts")
include(":material-color-utilities")
include(":backup")
include(":customattrs")
