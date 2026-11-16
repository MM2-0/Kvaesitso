import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// `:commons` is the HEADLESS half of the shared layer: domain models, state
// holders, ViewModels, the relay client, services. It is consumed by every
// front end including the headless `:cli`, so it must never depend on Compose
// UI (ui / foundation / material3), Coil, Compose resources or Skiko — those
// live in `:commonsUI`, which sits on top of this module. Only the Compose
// *runtime* (@Stable/@Immutable + snapshot state) is allowed here.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    // Kept on purpose even though no @Composable lives here anymore: the
    // Compose compiler stamps @StabilityInferred on every class it compiles,
    // which is what lets the apps' composables treat unannotated commons
    // classes (TopFilter, TorSettings, ProfileBroadcastStatus, …) as stable.
    // Measured with Compose compiler reports on full recompiles (2026-09-12):
    // removing this plugin turns 20→28 composable params unstable in
    // :commonsUI, 33→65 in :desktopApp and 90→149 in :amethyst. Inference is
    // also self-maintaining, unlike hand-written @Immutable annotations that
    // silently lie once a `var` is added — so this stays.
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.serialization)
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
        namespace = "com.vitorpamplona.amethyst.commons"
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

    // iOS targets — Phase 2 spike. Compile-only for now (no framework binary
    // configured yet). Reveals which transitive deps need iOS variants and
    // which commonMain files still reach for platform-only APIs.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.quartz)
                // The headless cart runtime that plays 128x128 fantasy-console carts in notes:
                // kiko-player runs one in real time and brings kiko itself. `api` because
                // CartPlayerViewModel hands its KikoPlayer to the screen in commonsUI.
                api(libs.kiko.player)
                // The MLS bindings: Marmot (MarmotManager and the group pipeline) and cordn.
                implementation(libs.marmot)
                implementation(libs.cordn)

                // Imported directly (sha256, AESGCM, RandomInstance, MLS types), so declared directly.
                // Through quartz's api alone, a -PcryptoKmpDir / -PmlsKmpDir checkout never reaches
                // the common metadata compile: it does not see an included build that it reaches
                // only transitively through a module.
                implementation(libs.crypto)
                implementation(libs.mls.kmp)

                // Compose *runtime* only — @Stable/@Immutable annotations and
                // snapshot state (mutableStateOf, State) used by state holders.
                // No ui / foundation / material3 here: that is :commonsUI.
                implementation(libs.jetbrains.compose.runtime)

                // Lifecycle ViewModel (KMP since 2.8.0, ships iOS variants).
                // The Compose-side helpers (lifecycle-runtime-compose,
                // viewModel()) live in :commonsUI.
                implementation(libs.androidx.lifecycle.viewmodel)

                // LruCache (KMP-ready)
                implementation(libs.androidx.collection)

                // okio (KMP, Apache-2.0) for service/image/DeferredDeleteFileSystem,
                // the ForwardingFileSystem the apps wrap Coil's disk cache in. It
                // used to arrive transitively through Coil; with Coil in
                // :commonsUI the Apple targets lost it (JVM still saw it via
                // OkHttp), so declare the dependency the file actually has. `api`
                // because voice recordings cross module boundaries as okio Paths.
                api(libs.okio)

                // DataStore (KMP, Apache-2.0) — the preference storage layer.
                // Publishes android/jvm/ios/linux/macos variants plus common
                // metadata, so the stores under model/preferences/ are shared
                // rather than duplicated per front end. Uses the okio-based
                // `createWithPath` factory in common; the `java.io.File`
                // overloads are jvmAndroid-only.
                implementation(libs.androidx.datastore.preferences)

                // Immutable collections
                api(libs.kotlinx.collections.immutable)

                // JSON for custom-feed definitions (KMP — replaces Jackson
                // for the one commonMain serializer that was blocking iOS).
                implementation(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        // Shared JVM code for both Android and Desktop
        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())
                dependencies {
                    // Audio-rooms ViewModel needs the listener orchestration +
                    // audio pipeline types (NestsListener, AudioRoomPlayer,
                    // AudioPlayer interface). The :nestsClient module is
                    // jvmAndroid-only today (its QUIC + Opus + AudioRecord/Track
                    // stacks are JVM-bound), so the dep lives here, not in
                    // commonMain. iOS will need an audio-rooms reroute when
                    // Phase 5 lands.
                    implementation(project(":nestsClient"))

                    // QuicLogBridge routes quic-kmp's QuicLog into Quartz's Log.
                    implementation(libs.quic)

                    // OkHttp (+ coroutines bridge) for the link-preview fetcher
                    // (service/preview/UrlPreview). JVM-only; iOS will swap to
                    // Ktor when its UI ships.
                    implementation(libs.okhttp)
                    implementation(libs.okhttpCoroutines)

                    // HTML parser behind the front page's resolve + boundary check (frontpage/):
                    // the same parser the Nostr Observer validates with. MIT.
                    implementation(libs.jsoup)

                    // Chess engine behind ChessEngine's JVM/Android actual: move validation and
                    // legal move generation for live NIP-64 games. Moved here from quartz, whose
                    // protocol code only carries PGN/SAN.
                    // NOTE: 1.0.4+ uses Java 21's removeLast() which crashes on Android API < 34
                    // TODO: Test if 1.0.0 works, or fork library to fix
                    implementation(libs.kchesslib)
                }
            }

        jvmMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Secure key storage via OS keychain (macOS/Windows/Linux)
                implementation(libs.java.keyring)

                // EXIF stripping for image uploads (used by service/upload/MediaCompressor).
                implementation(libs.commons.imaging)

                // APK inspection for `amy releases publish` (appupdate/ApkInspector): signature
                // verification, signer certificate and manifest fields. Google's own library,
                // Apache-2.0, the version the Android Gradle plugin ships.
                implementation(libs.apksig)

                // Image re-encode + progressive downscale (used by service/upload/ImageReencoder).
                // Pure-Java, MIT. See docs/plans/2026-06-08-feat-desktop-image-compression-plan.md.
                implementation(libs.thumbnailator)

                // Native OS notification bridges — Nucleus per-OS JNI shims.
                // macOS: UNUserNotificationCenter. Windows: WinRT Toasts. Linux: freedesktop D-Bus.
                // Only the matching-OS module's native lib loads at runtime; the others
                // stay dormant on the classpath.
                implementation("io.github.kdroidfilter:nucleus.notification-macos:1.15.7")
                implementation("io.github.kdroidfilter:nucleus.notification-windows:1.15.7")
                implementation("io.github.kdroidfilter:nucleus.notification-linux:1.15.7")
            }
        }

        androidMain {
            dependsOn(jvmAndroid)
            dependencies {
                // androidx.core KTX (Bitmap.scale, prefs.edit {}) used by the
                // Android actuals. Was reaching us transitively through the
                // Compose UI artifacts before the :commonsUI split.
                implementation(libs.androidx.core.ktx)

                // Secure key storage talks to the AndroidKeyStore directly through
                // SecretEncryption; androidx.security.crypto is gone from this module.
            }
        }

        // iOS intermediate so iosArm64Main and iosSimulatorArm64Main share code.
        val iosMain =
            create("iosMain") {
                dependsOn(commonMain.get())
            }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)

                // Bitcoin secp256k1 bindings
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }

        // jvmTest needs the JVM secp256k1 bindings whenever a test
        // exercises a real signer (e.g. UploadOrchestratorTest, which
        // signs Blossom auth events end-to-end).
        getByName("jvmTest") {
            dependencies {
                implementation(libs.secp256k1.kmp.jni.jvm)
                // RouteForPointerTest stubs an Account.
                implementation(libs.mockk)
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

// JVM tests run AWT-backed code (ImageIO, Thumbnailator, BufferedImage) — pin
// headless mode so a stray Toolkit.getDefaultToolkit() in a transitive dep
// never bounces the macOS Dock during CI/local test runs.
tasks.withType<Test>().configureEach {
    if (name == "jvmTest") {
        jvmArgs("-Djava.awt.headless=true")
        // ~550 test classes in one JVM made this suite the critical path of a pre-push run after
        // any :commons edit (165 s). Two forks: 116-129 s, peaking at 1.2 GB for both, green on
        // every run. The tests bind port 0, so forks don't collide on ports. They do share
        // ~/.amethyst/tmp, so a test must not list that folder: unique file names don't help, it
        // sees the other fork's files. One fork on a 2-core machine, where a second would only
        // contend.
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2)
    }
}

// iOS purity gate — shared task, see gradle/kmp-purity.gradle.kts.
apply(from = rootProject.file("gradle/kmp-purity.gradle.kts"))
