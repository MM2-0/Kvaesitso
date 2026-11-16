plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.jetbrainsComposeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.googleKsp) apply false
}

val appVersion = libs.versions.app.get()

// quartz from a JitPack build of one commit (`quartzJitpack` in the catalog) instead of its release,
// for every module and for marmot's and cordn's own dependency on it, so only one quartz is on any
// classpath. A local checkout (-PquartzDir) is substituted in settings and takes precedence.
val quartzJitpack =
    libs.versions.quartzJitpack
        .get()
        .takeIf { it.isNotBlank() && !providers.gradleProperty("quartzDir").isPresent }

allprojects {
    version = appVersion

    configurations.configureEach {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
        if (quartzJitpack != null) {
            resolutionStrategy.dependencySubstitution {
                substitute(module("com.vitorpamplona.quartz:quartz"))
                    .using(module("com.github.vitorpamplona.quartz:quartz:$quartzJitpack"))
                    .because("quartzJitpack in gradle/libs.versions.toml")
            }
        }
    }

    // JitPack rewrites the published module metadata and drops each file's classifier: the sources and
    // metadata variants point at `quartz-jvm-<v>.jar` where the files it serves are
    // `quartz-jvm-<v>-sources.jar` and `quartz-iosarm64-<v>-metadata.jar`. Point them back, so the KMP
    // metadata compiles (iOS) and anything reading the sources resolve. quartz's only while
    // quartzJitpack is set; libschnorr256k1-kotlin's always, since quartz itself takes it from JitPack
    // until 1.0.1 is on Maven Central.
    val jitpackModules =
        buildList {
            if (quartzJitpack != null) add("com.github.vitorpamplona.quartz" to "quartz")
            add("com.github.vitorpamplona.libschnorr256k1-kotlin" to "schnorr256k1-kotlin")
        }
    jitpackModules.forEach { (group, artifact) ->
        mapOf(
            "$artifact-jvm" to listOf("jvmSourcesElements-published" to "sources"),
            "$artifact-android" to listOf("androidSourcesElements-published" to "sources"),
            "$artifact-iosarm64" to
                listOf("iosArm64SourcesElements-published" to "sources", "iosArm64MetadataElements-published" to "metadata"),
            "$artifact-iossimulatorarm64" to
                listOf("iosSimulatorArm64SourcesElements-published" to "sources", "iosSimulatorArm64MetadataElements-published" to "metadata"),
        ).forEach { (module, variants) ->
            dependencies.components.withModule("$group:$module") {
                val version = id.version
                variants.forEach { (variant, classifier) ->
                    withVariant(variant) {
                        withFiles {
                            removeAllFiles()
                            addFile("$module-$version-$classifier.jar")
                        }
                    }
                }
            }
        }
    }

}
