pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeModule("com.github.UnifiedPush", "android-connector")
            }
        }
    }
}

// Included builds have independent project and plugin classpaths. The host's
// constraints cannot protect this build or its standalone validation tasks.
val telosSecurityPins = mapOf(
    "org.freemarker:freemarker" to "2.3.35",
    "org.jsoup:jsoup" to "1.24.1",
    "org.jdom:jdom2" to "2.0.6.1",
    "org.bitbucket.b_c:jose4j" to "0.9.7",
    "org.apache.httpcomponents:httpclient" to "4.5.14",
    "org.apache.commons:commons-lang3" to "3.22.0",
    "com.fasterxml.jackson.core:jackson-core" to "2.23.0",
    "com.fasterxml.jackson.core:jackson-databind" to "2.23.0",
    "com.fasterxml.jackson.core:jackson-annotations" to "2.23",
    "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml" to "2.23.0",
    "io.opentelemetry:opentelemetry-api" to "1.68.0",
    "io.opentelemetry:opentelemetry-context" to "1.68.0",
)
gradle.beforeProject {
    val targetProject = this
    fun org.gradle.api.artifacts.Configuration.secureTelosClasspath() {
        val applicationClasspath = name.endsWith("RuntimeClasspath") || name.endsWith("CompileClasspath")
        resolutionStrategy.eachDependency {
            when {
                requested.group == "org.bouncycastle" -> useVersion("1.87")
                requested.group == "com.google.guava" && requested.name == "guava" -> {
                    val androidClasspath = applicationClasspath && (
                        targetProject.plugins.hasPlugin("com.android.library") ||
                            targetProject.plugins.hasPlugin("com.android.kotlin.multiplatform.library")
                        )
                    useVersion(if (androidClasspath) "33.8.0-android" else "33.8.0-jre")
                }
                else -> telosSecurityPins["${requested.group}:${requested.name}"]?.let { useVersion(it) }
            }
        }
    }
    buildscript.configurations.configureEach { secureTelosClasspath() }
    configurations.configureEach { secureTelosClasspath() }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // The groups only JitPack builds (straight from GitHub). Google and Maven Central never have
    // them, so they are told not to be asked: Gradle tried both first, for 404s, and when a
    // rate-limited Central answered one with a 429 it disabled Central for the rest of the build
    // and every other dependency failed to resolve. A JitPack group missing here still resolves,
    // only through those wasted lookups first; mavenLocal() keeps serving a locally published fork.
    val jitpackOnly =
        listOf(
            "cc.ekblad", // cli's TOML parser, 4koma
            "cc.ekblad.konbini", // 4koma's parser combinators
            "com.github.UnifiedPush",
            "com.github.davotoula",
            "com.github.lincollincol",
            "com.github.rikkahub.jlatexmath-android",
            "com.github.vitorpamplona.compose-richtext",
            "com.github.vitorpamplona.quartz", // a quartz commit, while quartzJitpack is set
            "com.github.vitorpamplona.libschnorr256k1-kotlin", // quartz's pure-Kotlin secp256k1, from JitPack for now
        )
    repositories {
        google { content { jitpackOnly.forEach { excludeGroup(it) } } }
        mavenCentral { content { jitpackOnly.forEach { excludeGroup(it) } } }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "TelosAmethyst"
include(":amethyst")
include(":nappletHost")
include(":geode")
include(":commons")
include(":commonsUI")
include(":nestsClient")

