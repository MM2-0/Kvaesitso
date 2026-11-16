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

