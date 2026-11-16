pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://raw.githubusercontent.com/guardianproject/gpmaven/master") {
            content { includeGroup("info.guardianproject") }
        }
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.TeamNewPipe")
                includeGroup("cz.adaptech.tesseract4android")
                includeGroup("com.github.UnifiedPush")
                includeGroup("com.github.davotoula")
                includeGroup("com.github.lincollincol")
                includeGroup("com.github.rikkahub.jlatexmath-android")
                includeGroupByRegex("com\\.github\\.vitorpamplona(\\..*)?")
            }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        mavenCentral()
    }
}

// Dependabot maps plugin-classpath transitives to this file. Pin CVE-patched
// versions here. jackson-annotations has no 2.22.4 artifact — use 2.22.
val telosPluginCvePins = listOf(
    "com.google.guava:guava:33.8.0-jre",
    "org.jsoup:jsoup:1.24.1",
    "org.freemarker:freemarker:2.3.35",
    "org.bouncycastle:bcprov-jdk18on:1.87",
    "org.bouncycastle:bcpkix-jdk18on:1.87",
    "org.bouncycastle:bcutil-jdk18on:1.87",
    "org.jdom:jdom2:2.0.6.1",
    "org.apache.httpcomponents:httpclient:4.5.14",
    "org.bitbucket.b_c:jose4j:0.9.7",
    "org.apache.commons:commons-lang3:3.22.0",
    "com.fasterxml.jackson.core:jackson-core:2.23.0",
    "com.fasterxml.jackson.core:jackson-databind:2.23.0",
    "com.fasterxml.jackson.core:jackson-annotations:2.23",
    "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.23.0",
)

gradle.beforeProject {
    buildscript.configurations.configureEach {
        resolutionStrategy {
            force(*telosPluginCvePins.toTypedArray())
            eachDependency {
                when {
                    requested.group == "com.google.guava" && requested.name == "guava" ->
                        useVersion("33.8.0-jre")
                    requested.group == "org.jsoup" && requested.name == "jsoup" ->
                        useVersion("1.24.1")
                    requested.group == "org.freemarker" && requested.name == "freemarker" ->
                        useVersion("2.3.35")
                    requested.group == "org.bouncycastle" ->
                        useVersion("1.86")
                    requested.group == "org.jdom" && requested.name == "jdom2" ->
                        useVersion("2.0.6.1")
                    requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" ->
                        useVersion("4.5.14")
                    requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" ->
                        useVersion("0.9.7")
                    requested.group == "org.apache.commons" && requested.name == "commons-lang3" ->
                        useVersion("3.21.0")
                    requested.group == "com.fasterxml.jackson.core" &&
                        requested.name in setOf("jackson-core", "jackson-databind") ->
                        useVersion("2.22.4")
                    requested.group == "com.fasterxml.jackson.core" &&
                        requested.name == "jackson-annotations" ->
                        useVersion("2.22")
                    requested.group == "com.fasterxml.jackson.dataformat" &&
                        requested.name == "jackson-dataformat-yaml" ->
                        useVersion("2.22.4")
                }
            }
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
include(":data:database")
include(":core:permissions")
include(":core:shared")

include(":data:appshortcuts")
include(":data:customattrs")
include(":data:applications")
include(":data:calendar")
include(":data:calculator")
include(":data:themes")
include(":data:contacts")
include(":data:currencies")
include(":data:files")
include(":data:unitconverter")
include(":data:websites")
include(":data:webappshortcuts")
include(":data:wikipedia")
include(":data:widgets")
include(":data:weather")
include(":data:notifications")
include(":data:search-actions")
include(":data:searchable")
include(":data:plugins")

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
include(":libs:address-formatter")
include(":services:global-actions")
include(":services:widgets")
include(":services:favorites")

include(":plugins:sdk")
include(":data:locations")
include(":services:plugins")
include(":core:devicepose")
include(":core:profiles")
include(":data:i18n")
include(":services:feed")
include(":services:freeze")
include(":services:wallpapers")
include(":services:context-profiles")
include(":services:desktop-mode")
include(":services:applock")
include(":services:app-management")
include(":services:store")
include(":services:network")
include(":data:store")
include(":services:comms")
include(":data:comms")
include(":services:downloads")

include(":libs:appops-next")
include(":libs:shizuku-next")

include(":ui-kmp")

val isFdroidBuild = providers.gradleProperty("fdroidBuild").orNull?.toBoolean() == true
if (!isFdroidBuild) {
    include(":desktop-linux")
}
include(":services:keepasstl")

// The full Aether client is compiled from source into the Telos APK.
includeBuild("libs/amethyst") {
    dependencySubstitution {
        substitute(module("com.telos.amethyst:amethyst")).using(project(":amethyst"))
    }
}
