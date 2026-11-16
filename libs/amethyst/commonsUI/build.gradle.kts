import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

// `:commonsUI` is the Compose half of the shared layer: every composable,
// icon, theme, Coil fetcher and Compose-resource (strings/fonts/files) that the
// GUI front ends (Android, Desktop, iOS) render. It sits on top of `:commons`
// (headless models, state, ViewModels, relay client) and is never a dependency
// of `:cli`, which keeps Compose UI + Skiko off the CLI classpath.
// Source files keep their `com.vitorpamplona.amethyst.commons.*` packages so
// the module boundary is purely a build-graph constraint — consumers did not
// have to change a single import when the split happened.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            // Desktop start-up: lambdas, SAM conversions and string templates as compiled classes, not
            // invokedynamic call sites the JVM spins a class for on first use (~6k in a desktop start,
            // and the JIT time they drew starved the UI). Android's D8 already turns them into
            // classes. desktopApp/plans/2026-11-07-desktop-startup-performance.md
            freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class", "-Xstring-concat=inline")
        }
    }

    android {
        namespace = "com.vitorpamplona.amethyst.commons.ui"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        androidResources.enable = true

        withHostTest {
            isReturnDefaultValues = true
        }

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // iOS targets — same compile-only spike as :commons.
    //
    // The Kotlin/Native dependency cache is deliberately left ON for the iOS
    // test binaries. It used to be disabled here (disableNativeCache) because
    // Compose 1.11's prebuilt ui-uikit cache referenced UIViewLayoutRegion and
    // the test link failed under Xcode 16.4. CI and dev machines are on Xcode 26
    // now, where the cached object links fine (verified with Compose 1.12.1 /
    // Kotlin 2.4.20), and the cache is what makes the link cheap once ~/.konan
    // is warm: 60 s with it against 88 s without on an M5 Pro. If the symbol
    // error ever comes back, re-add the workaround from git history and point
    // it at the Kotlin version it was verified against.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                // The headless half. `api` because every composable here takes
                // or returns commons types (Note, User, ViewModels, states).
                api(project(":commons"))
                api(libs.quartz)
                implementation(libs.marmot)
                // The cart player's screen and controls (CartPlayer).
                implementation(libs.kiko.compose)
                implementation(libs.cordn)
                // Imported directly; see the same line in commons/build.gradle.kts.
                implementation(libs.crypto)

                // Compose Multiplatform
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui.tooling.preview)

                // Lifecycle (KMP since 2.8.0). lifecycle-runtime-compose ships
                // iOS variants; lifecycle-viewmodel-compose (the viewModel()
                // Composable helper) is Android-only and lives in jvmAndroid.
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.runtime.compose)

                // Image loading (Coil 3 - KMP). The okhttp network fetcher is
                // JVM-only and lives in jvmAndroid; iOS will pull coil-ktor
                // when that target wires its actual.
                implementation(libs.coil.compose)
                // NetworkHeaders on avatar requests (the local Blossom cache marker). Multiplatform,
                // Apache-2.0; already on the JVM classpath through coil-network-okhttp.
                implementation(libs.coil.network.core)

                // LruCache (KMP-ready)
                implementation(libs.androidx.collection)

                // Pinch-to-zoom (net.engawapg zoomable, Compose Multiplatform: Android, desktop,
                // iOS; Apache-2.0). Already in the app; the Blossom blob viewer uses it here.
                implementation(libs.zoomable)

                // Charts (Vico 3, Compose Multiplatform, Apache-2.0): the notification
                // summary builds its chart model off the main thread. `api` because the
                // model is part of that state holder's public surface.
                api(libs.vico.charts.compose)

                // Immutable collections
                api(libs.kotlinx.collections.immutable)

                // JSON trees for the LNURL-pay replies the payment stack reads.
                implementation(libs.kotlinx.serialization.json)

                // Compose Multiplatform Resources (strings, fonts, napplet shell files)
                implementation(libs.jetbrains.compose.components.resources)

                // KMP syntax highlighter (Apache-2.0) for the git code browser.
                implementation(libs.highlights)

                // Navigation 3 (JetBrains' multiplatform build, Apache-2.0: Android, desktop, iOS).
                // The app owns its back stack (navs/NavBackStacks); NavDisplay renders it, and the
                // ViewModel decorator scopes each screen's ViewModels to its entry.
                implementation(libs.jetbrains.navigation3.ui)
                implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                // Compose Desktop on the test classpath so composables can be
                // rendered headlessly to a Skia surface (ImageComposeScene) and
                // asserted on. No new third-party dependency: this is the same
                // artifact jvmMain already uses, and it rasterises in software,
                // so it needs no display.
                implementation(compose.desktop.currentOs)
                // RouteForPointerTest stubs an Account (already the app's and androidHostTest's mock lib).
                implementation(libs.mockk)
            }
        }

        // Shared JVM code for both Android and Desktop
        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())
                dependencies {
                    // Coil's OkHttp network fetcher (JVM-only). iOS will use
                    // coil-ktor when the iOS Compose UI ships.
                    implementation(libs.coil.okhttp)

                    // OkHttp for the Blossom read-auth Coil fetcher.
                    implementation(libs.okhttp)
                    // executeAsync for PdfFetcher; already on every module that ships OkHttp.
                    implementation(libs.okhttpCoroutines)

                    // Markdown rendering (richtext-commonmark). The single
                    // consumer (RenderMarkdown.kt) lives in jvmAndroid.
                    // iOS support pending Phase 3 markdown decision.
                    implementation(libs.markdown.commonmark)
                    implementation(libs.markdown.ui)
                    implementation(libs.markdown.ui.material3)
                    // commonmark (BSD-2-Clause) and the URL finder its autolink extension
                    // uses (MIT), for BareUrlExtension. Both already ship as runtime
                    // dependencies of richtext-commonmark; declared to compile against them.
                    implementation(libs.markdown.commonmark.core)
                    implementation(libs.markdown.autolink)

                    // viewModel() Compose helper. AndroidX publishes this
                    // artifact for android/jvmStubs/linuxx64Stubs but not iOS,
                    // so it stays in jvmAndroid until we either swap to the
                    // org.jetbrains.androidx.lifecycle variant or accept a
                    // platform-specific ViewModel access pattern on iOS.
                    implementation(libs.androidx.lifecycle.viewmodel.compose)

                    // ZXing core encodes the QR codes the shared QrCodeDrawer shows. Pure Java,
                    // Apache-2.0, and already linked by both apps.
                    implementation(libs.zxing)
                }
            }

        jvmMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Desktop-specific Compose
                implementation(compose.desktop.currentOs)
                implementation(libs.jetbrains.compose.ui.tooling)

                // Renders PDF pages for the shared PDF card and viewer; Android uses the OS's
                // PdfRenderer instead. Apache-2.0 (bundled fonts and CMaps: OFL, BSD, CC-BY-4.0).
                implementation(libs.pdfbox)
            }
        }

        androidMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Android-specific Compose tooling
                implementation(libs.androidx.ui.tooling.preview)
                // WindowInsetsControllerCompat, for DisappearingScaffold's immersive status bar.
                implementation(libs.androidx.core.ktx)
                // BackHandler, behind the shared PlatformBackHandler. Apache-2.0, already in the app.
                implementation(libs.androidx.activity.compose)
                // Camera permission prompt for TakePicture. Apache-2.0, already in the app.
                implementation(libs.accompanist.permissions)
                implementation(libs.accompanist.adaptive)
                // BiometricPrompt behind rememberDeviceAuthenticator. Apache-2.0, already in the app.
                implementation(libs.androidx.biometric.ktx)
            }
        }

        // iOS intermediate so iosArm64Main and iosSimulatorArm64Main share code.
        val iosMain =
            create("iosMain") {
                dependsOn(commonMain.get())
            }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)

        // Skiko-backed targets (desktop JVM + iOS) share pixel-format helpers
        // (org.jetbrains.skia.* resolves on both through Compose). Android is
        // deliberately NOT in this set — it renders through android.graphics.
        val skikoMain =
            create("skikoMain") {
                dependsOn(commonMain.get())
                dependencies {
                    // NavigationBackHandler for desktop (Esc) and iOS (swipe), behind
                    // PlatformBackHandler. Apache-2.0; Android uses AndroidX activity's BackHandler.
                    implementation(libs.jetbrains.navigationevent.compose)
                }
            }
        getByName("jvmMain").dependsOn(skikoMain)
        iosMain.dependsOn(skikoMain)

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                // SafeImeInsetsTest mocks the android.view.View its per-window cache is keyed on.
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    // Kept on the pre-split package so `Res` imports in every consumer
    // (amethyst, desktopApp, nappletHost) keep resolving unchanged.
    packageOfResClass = "com.vitorpamplona.amethyst.commons.resources"
    generateResClass = always
}

// iOS purity gate — shared task, see gradle/kmp-purity.gradle.kts.
apply(from = rootProject.file("gradle/kmp-purity.gradle.kts"))

// One commonsUI compile at a time. Both targets compile in the same Kotlin daemon, and each holds
// the whole module's IR, so with --max-workers > 1 the JVM and Android compiles ran side by side
// and needed twice the heap: CI's 4g Kotlin daemon (build.yml, test-and-build-android) ran out of
// memory on a cold build, and even 8g thrashed in full GCs. In sequence each one fits.
tasks.named("compileAndroidMain") { mustRunAfter("compileKotlinJvm") }

// NoteComposeKindCoverageTest checks that every class quartz's EventFactory builds has a feed card,
// and reads that list from EventFactory's source. quartz lives in its own repo, so the test gets the
// source from the checkout -PquartzDir builds, or else from quartz's published JVM sources jar.
val quartzSources =
    configurations.register("quartzSources") {
        isCanBeConsumed = false
        isTransitive = false
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
            attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        }
    }

dependencies {
    "quartzSources"(libs.quartz)
}

tasks.named<Test>("jvmTest") {
    val checkout = providers.gradleProperty("quartzDir").map { rootProject.file(it).resolve("quartz/src/commonMain/kotlin").path }
    val sources: Provider<String> =
        if (checkout.isPresent) {
            checkout
        } else {
            inputs.files(quartzSources)
            quartzSources.flatMap { it.elements }.map { it.single().asFile.path }
        }
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dquartz.sources=${sources.get()}") })

    // The render tests leave every ImageComposeScene.render() Image to the GC, and each one holds
    // its pixels in native Skia memory that only a Cleaner frees. Their Java side is tiny, so the
    // 512 MB heap almost never collects, and the test JVM grew to 6 GB resident (enough, beside
    // the Gradle and Kotlin daemons, for the OOM killer on a 16 GB machine). A timed G1 cycle
    // runs those Cleaners: the peak drops to under 3 GB, at no measurable cost in run time. G1 is
    // named, not left to the JVM: on a machine it deems small it would pick SerialGC and ignore these.
    jvmArgs("-XX:+UseG1GC", "-XX:G1PeriodicGCInterval=1000", "-XX:G1PeriodicGCSystemLoadThreshold=0")
}
