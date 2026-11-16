import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.hidden.refine) apply false
    alias(libs.plugins.dokka) apply true
}

// Dokka's generator/publication resolvers live on the root project too.
allprojects {
    val targetProject = this
    configurations.configureEach {
        val isApplicationClasspath = name.endsWith("RuntimeClasspath") || name.endsWith("CompileClasspath")
        resolutionStrategy.eachDependency {
            when {
                requested.group == "com.fasterxml.jackson.core" &&
                    requested.name in setOf("jackson-core", "jackson-databind") ->
                    useVersion(libs.versions.jacksonCore.get())
                requested.group == "com.fasterxml.jackson.core" &&
                    requested.name == "jackson-annotations" ->
                    useVersion(libs.versions.jacksonAnnotations.get())
                requested.group == "com.fasterxml.jackson.dataformat" &&
                    requested.name == "jackson-dataformat-yaml" ->
                    useVersion(libs.versions.jacksonDataformatYaml.get())
                requested.group == "org.jsoup" && requested.name == "jsoup" ->
                    useVersion(libs.versions.jsoup.get())
                requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" ->
                    useVersion(libs.versions.jose4j.get())
                requested.group == "org.jdom" && requested.name == "jdom2" ->
                    useVersion(libs.versions.jdom2.get())
                requested.group == "org.freemarker" && requested.name == "freemarker" ->
                    useVersion(libs.versions.freemarker.get())
                requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" ->
                    useVersion(libs.versions.httpclient.get())
                requested.group == "org.apache.commons" && requested.name == "commons-lang3" ->
                    useVersion(libs.versions.commonsLang3.get())
                requested.group == "com.google.guava" && requested.name == "guava" -> {
                    // A transitive -jre request must not switch an Android app to
                    // Guava's JRE artifact. Compiler/tool configurations still use JRE.
                    val androidClasspath = isApplicationClasspath && (
                        targetProject.plugins.hasPlugin("com.android.application") ||
                            targetProject.plugins.hasPlugin("com.android.library") ||
                            targetProject.plugins.hasPlugin("com.android.kotlin.multiplatform.library")
                        )
                    val suffix = if (androidClasspath) "-android" else "-jre"
                    useVersion(libs.versions.guava.get() + suffix)
                }
                requested.group == "org.bouncycastle" ->
                    useVersion(libs.versions.bouncycastle.get())
            }
        }
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.vitorpamplona.quartz:quartz"))
                .using(module("com.github.vitorpamplona.quartz:quartz:009e4c8dcb23578638f10f66ebc42c04ebaf0c88"))
                .because("Maven Central has no quartz 2.17.0; Telos APK uses the Amethyst JitPack pin")
            substitute(module("com.google.crypto.tink:tink"))
                .using(module("com.google.crypto.tink:tink-android:1.17.0"))
                .because("Quartz pulls tink JVM; Telos already ships tink-android")
        }
    }

}

subprojects {
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

dependencies {
    dokka(project(":plugins:sdk"))
    dokka(project(":core:shared"))
}
