import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.googleKsp)
}

val hostVersion = Properties().apply {
    rootProject.file("../../version.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.vitorpamplona.amethyst"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "VERSION_NAME", "\"${hostVersion.getProperty("versionName", libs.versions.app.get())}\"")
        buildConfigField("int", "VERSION_CODE", hostVersion.getProperty("versionCode", libs.versions.appCode.get()))
        buildConfigField("String", "RELEASE_NOTES_ID", "\"bcaae03cfea84ac9f8f045202a3b8ee643c9fd51c9c674a87acadd4a03e16046\"")
        manifestPlaceholders["memtagMode"] = "async"
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_CASTING_AVAILABLE", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_CASTING_AVAILABLE", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "**/libscrypt.dylib")
        jniLibs.keepDebugSymbols += listOf("**/libarti_android.so", "**/libzxingcpp_android.so")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            // JVM tests load the host JNI build, never an Android ABI binary.
            test.systemProperty("java.library.path", "$projectDir/src/test/native-libs/x86_64-linux")
            project.findProperty("amethyst.arti.integration")?.let {
                test.systemProperty("amethyst.arti.integration", it.toString())
            }
        }
    }
}

kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_21)

ksp { arg("appfunctions:aggregateAppFunctions", "true") }

configurations.configureEach {
    resolutionStrategy {
        val tink = "com.google.crypto.tink:tink-android:1.24.0"
        force(tink)
        dependencySubstitution {
            substitute(module("com.google.crypto.tink:tink")).using(module(tink))
        }
    }
}


dependencies {
    implementation(platform(libs.androidx.compose.bom))

    // Compose composition tracing — DEBUG ONLY, profiling aid (not shipped). Makes each
    // recomposition show up as a NAMED slice in Perfetto system traces so we can see which
    // composable recomposes (e.g. during the cold-start feed first-paint). All Apache-2.0.
    // Usage: runtime-enable, then capture a Perfetto trace with the `track_event` data source:
    //   adb shell am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING \
    //     -n com.vitorpamplona.amethyst.debug/androidx.tracing.perfetto.TracingReceiver
    debugImplementation(libs.androidx.compose.runtime.tracing)
    debugImplementation(libs.androidx.tracing.perfetto)
    debugImplementation(libs.androidx.tracing.perfetto.binary)

    implementation(libs.quartz)
    implementation(libs.marmot)
    implementation(libs.cordn)
    implementation(project(":commons"))
    implementation(project(":commonsUI"))
    implementation(project(":nestsClient"))
    // Agent text stream previews: marmot-kmp's raw-QUIC binding plus the QUIC
    // stack under it (for the certificate validator it requires).
    implementation(libs.marmot.quic)
    implementation(libs.quic)
    implementation(project(":nappletHost"))
    // Compose Multiplatform resources runtime, so app-side screens that share a
    // string with a commons renderer can read commons' generated `Res` directly
    // instead of duplicating the key in the Android res tree.
    implementation(libs.jetbrains.compose.components.resources)
    implementation(libs.androidx.core.ktx)

    // Installs assets/dexopt/baseline.prof on first run. Play applies the profile at
    // install time via the .dm, but F-Droid builds have no store-side profile delivery
    // and no Cloud Profiles at all — there, this library is the only thing that gets
    // the shipped baseline profile into ART.
    implementation(libs.androidx.profileinstaller)

    // Profile produced by :baselineprofile from a real cold-start + ingest journey.
    implementation(libs.androidx.activity.compose)

    // Hardened WebView host for sandboxed napplet/nsite rendering (origin-restricted message bridge).
    implementation(libs.androidx.webkit)

    // Client side of the cross-process UI embedding: renders the sandboxed browser surface (hosted in
    // the keyless `:napplet` process) inside a Compose component in the main app.
    implementation(libs.androidx.privacysandbox.ui.core)
    implementation(libs.androidx.privacysandbox.ui.client)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Needs this to open gallery / image upload
    implementation(libs.androidx.fragment.ktx)

    // Material 3 Design
    implementation(libs.androidx.material3)

    // Adaptive Layout / Two Pane
    implementation(libs.androidx.material3.windowSize)
    implementation(libs.accompanist.adaptive)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Zoomable images
    implementation(libs.zoomable)

    // Biometrics
    implementation(libs.androidx.biometric.ktx)

    // Background Work
    implementation(libs.androidx.work.runtime.ktx)

    // Reads workouts from Android Health Connect (Samsung Health, Google Fit, Fitbit, Garmin, …)
    implementation(libs.androidx.health.connect.client)

    // Websockets API
    implementation(libs.okhttp)
    implementation(libs.okhttpCoroutines)

    // Encrypted Key Storage
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.androidx.datastore.preferences)

    // view videos
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui.compose.material3)
    implementation(libs.androidx.media3.session)

    // important for proxy / tor
    implementation(libs.androidx.media3.datasource.okhttp)

    // Load images from the web.
    implementation(libs.coil.compose)
    // view gifs
    implementation(libs.coil.gif)
    // view svgs
    implementation(libs.coil.svg)
    // enables network for coil
    implementation(libs.coil.okhttp)
    // loads thumbnails for media3
    // TODO: Replace this to the FrameExtractor in media 3
    // when FrameExtractor accepts custom data sources.
    implementation(libs.coil.video)

    // Permission to upload pictures:
    implementation(libs.accompanist.permissions)

    // For QR generation
    // ZXing core encodes the QR codes we display. Decoding is zxing-cpp, which we build
    // from source ourselves -- see tools/zxing-cpp-build -- rather than pulling a prebuilt
    // AAR nobody in this tree could verify; the .so lives in src/main/jniLibs and its
    // Kotlin wrapper is vendored at src/main/java/zxingcpp.
    implementation(libs.zxing)

    // OpenStreetMap tiles for road event location maps (kind 1315/1316)
    implementation(libs.osmdroid.android)

    // Markdown
    // implementation "com.halilibo.compose-richtext:richtext-ui:0.16.0"
    // implementation "com.halilibo.compose-richtext:richtext-ui-material:0.16.0"
    // implementation "com.halilibo.compose-richtext:richtext-commonmark:0.16.0"

    // Markdown (With fix for full-image bleeds)
    implementation(libs.markdown.ui)
    implementation(libs.markdown.ui.material3)
    implementation(libs.markdown.commonmark)

    // Syntax highlighting for the git repository code browser (Apache-2.0)
    implementation(libs.highlights)

    // LaTeX math rendering ($...$ and $$...$$ inline equations)
    implementation(libs.jlatexmath.android)
    implementation(libs.jlatexmath.font.greek)
    implementation(libs.jlatexmath.font.cyrillic)

    // Language picker and Theme chooser
    implementation(libs.androidx.appcompat)

    // Dynamically adjust between phone and tablet UI
    implementation(libs.androidx.window.core.android)

    // Local model for language identification
    "playImplementation"(libs.google.mlkit.language.id)

    // Google services model the translate text
    "playImplementation"(libs.google.mlkit.translate)

    // On-device AI writing assistance (Gemini Nano via AICore)
    "playImplementation"(libs.google.mlkit.genai.proofreading)
    "playImplementation"(libs.google.mlkit.genai.prompt)
    "playImplementation"(libs.google.mlkit.genai.rewriting)

    // On-device alt-text suggestions: genai image description (preferred, descriptive sentences)
    // with image-labeling as a keyword-join fallback for devices without AICore.
    "playImplementation"(libs.google.mlkit.genai.image.description)

    // PushNotifications
    "playImplementation"(platform(libs.firebase.bom))
    "playImplementation"(libs.firebase.messaging)

    // PushNotifications(FDroid)
    "fdroidImplementation"(libs.unifiedpush)

    // Google Cast SDK — Chromecast support. Play flavor only because the
    // framework hard-depends on Google Play services, which is unavailable
    // on de-Googled / GrapheneOS devices that ship the F-Droid build.
    "playImplementation"(libs.play.services.cast.framework)

    // androidx.appfunctions — Gemini App Functions adapter. Pre-stable
    // (alpha) as of May 2026 — scoped to the play channel so the F-Droid
    // build stays free of Google AI dependencies. Surface is an
    // AppFunctionService registered in amethyst/src/play/AndroidManifest.xml,
    // generated at compile time by the KSP-driven appfunctions-compiler.
    "playImplementation"(libs.androidx.appfunctions)
    "playImplementation"(libs.androidx.appfunctions.service)
    "kspPlay"(libs.androidx.appfunctions.compiler)

    // Charts
    implementation(libs.vico.charts.compose)
    implementation(libs.vico.charts.m3)

    // Waveform visualizer
    implementation(libs.audiowaveform)

    // Video compression lib
    implementation(libs.abedElazizShe.video.compressor.fork)
    // Image compression lib
    implementation(libs.zelory.image.compressor)

    // EXIF metadata stripping
    implementation(libs.androidx.exifinterface)

    // WebRTC for voice/video calls
    implementation(libs.stream.webrtc.android)

    // Cbor for cashuB format
    implementation(libs.kotlinx.serialization.cbor)

    // Kotlin serialization for the times where we need the Json tree and performance is not that important.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.secp256k1.kmp.jni.jvm)

    // In-process Nostr relay (geode) so unit tests that drive a real
    // NostrClient talk to an embedded relay instead of a public one. Same
    // wiring quartz uses for its jvmAndroidTest source set: the engine, its
    // testFixtures (RelayClientTest base, preload/publish helpers) and the
    // JVM SQLite driver the in-memory EventStore needs on a host JVM.
    testImplementation(project(":geode"))
    testImplementation(testFixtures(project(":geode")))
    testImplementation(libs.androidx.sqlite.bundled.jvm)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)

    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
}
