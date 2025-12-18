import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        viewBinding = true
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
        }
    }
    namespace = "de.mm20.launcher2.sdk"
    
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":core:shared"))
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.kotlin)
}

tasks.dokkaHtml {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaHtml)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "de.mm20.launcher2"
            artifactId = "plugin-sdk"
            version = libs.versions.pluginSdk.get()

            artifact(javadocJar)

            pom {
                name = "Kvaesitso SDK"
                description = "Plugin SDK for the Kvaesitso launcher"
                url = "https://kvaesitso.mm20.de"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "MM2-0"
                        name = "MM2-0"
                        url = "https://github.com/MM2-0"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/MM2-0/Kvaesitso.git"
                    developerConnection = "scm:git:ssh://github.com:MM2-0/Kvaesitso.git"
                    url = "https://github.com/MM2-0/Kvaesitso/tree/main/plugins/sdk"
                }
            }

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        mavenLocal()
        val ghUser = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
        if (ghUser == "MM2-0") {
            maven {
                name = "GitHubPackages"
                url = uri("https.maven.pkg.github.com/MM2-0/Kvaesitso")
                credentials {
                    username =
                        project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                    password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["release"])
}