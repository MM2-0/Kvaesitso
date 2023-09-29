// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    kotlin("plugin.serialization") version libs.versions.kotlin apply false
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin apply false
    id("com.google.devtools.ksp") version "1.9.0-1.0.13" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.2")
        classpath(libs.kotlin.gradle)
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
        jcenter() // For tinypinyin
    }
}

tasks.create<Delete>("clean") {
    delete(rootProject.buildDir)
}

apply(from = "docs/deps-graph.gradle")