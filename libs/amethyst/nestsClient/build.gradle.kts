import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "com.vitorpamplona.nestsclient"
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

        withHostTest {}
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                api(libs.quartz)
                implementation(libs.quic)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(libs.okhttp)
                    implementation(libs.okhttpCoroutines)
                }
            }

        jvmMain {
            dependsOn(jvmAndroid)
        }

        androidMain {
            dependsOn(jvmAndroid)
            // Kwik QUIC + Flupke HTTP/3 dependencies are NOT yet declared.
            // See KwikWebTransportFactory.kt for the integration plan and
            // validated Maven coordinates / minimum versions before adding.
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.secp256k1.kmp.jni.jvm)
                // JNA bindings + bundled libopus.so used by the cross-stack
                // interop tests (T16). The Android targets keep their
                // existing `MediaCodecOpusEncoder/Decoder`; only JVM
                // tests need a host-side codec, and `club.minnced:opus-java`
                // ships natives for linux-x86-64 / aarch64 / darwin / win32.
                // No Android dependency is added. opus-java-api declares
                // JNA as runtime-scope; Kotlin needs it at compile time to
                // resolve the `tomp2p.opuswrapper.Opus extends com.sun.jna.Library`
                // supertype, so pull it explicitly.
                implementation("club.minnced:opus-java:1.1.1")
                implementation(libs.jna)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }
    }
}

// Forward the nostrnests interop opt-in property from the Gradle JVM
// to test workers. Without this, `-DnestsInterop=true` on the Gradle
// command line never reaches `NostrNestsHarness.isEnabled()` (which
// reads it via `System.getProperty`), so every interop test silently
// skips. See `nestsClient/src/jvmTest/.../interop/NostrNestsHarness.kt`.
tasks.withType<Test>().configureEach {
    System.getProperty("nestsInterop")?.let { systemProperty("nestsInterop", it) }
    System.getProperty("nestsInteropRev")?.let { systemProperty("nestsInteropRev", it) }
    System.getProperty("nestsInteropMoqRev")?.let { systemProperty("nestsInteropMoqRev", it) }
    System.getProperty("nestsInteropExternal")?.let { systemProperty("nestsInteropExternal", it) }
    System.getProperty("nestsInteropDebug")?.let { systemProperty("nestsInteropDebug", it) }
    // Opt-in for tests that hit the real nostrnests.com infrastructure
    // (see NostrnestsProdAudioTransmissionTest). Forwarded the same way
    // the harness flags are.
    System.getProperty("nestsProd")?.let { systemProperty("nestsProd", it) }
    System.getProperty("nestsProdEndpoint")?.let { systemProperty("nestsProdEndpoint", it) }
    System.getProperty("nestsProdAuth")?.let { systemProperty("nestsProdAuth", it) }
    // Cross-stack interop (Hang/Rust) opt-in. Forwarded the same way as
    // -DnestsInterop. See nestsClient/plans/2026-05-06-cross-stack-interop-test.md.
    System.getProperty("nestsHangInterop")?.let { systemProperty("nestsHangInterop", it) }
    // Separate gate for the Kotlin↔Kotlin diagnostic test (used to
    // bisect wire-format bugs). Runs in a fresh JVM without the
    // 5 native-subprocess scenarios; flakes if mixed in.
    System.getProperty("nestsHangInteropDiagnostic")?.let {
        systemProperty("nestsHangInteropDiagnostic", it)
    }
}

// ---- Cross-stack interop: Rust sidecar build + binary path forwarding -------
//
// Phase 1 of the interop plan ships the workspace at `nestsClient/tests/hang-interop/`
// with three stub binaries (hang-listen, hang-publish, udp-loss-shim).
// `interopBuildHangSidecars` runs `cargo build --release` against it and
// resolves the upstream `moq-relay` + `moq-token` binaries via
// `cargo install`, caching everything under
// `~/.cache/amethyst-nests-interop/hang-interop-cargo/` so reruns are
// fast. Binary paths are forwarded to test workers as system properties.
//
// Opt-in only: Phase 1 just verifies the harness can boot a relay; the
// actual interop scenarios land in Phase 2 once `hang-listen` /
// `hang-publish` have real subscribe/publish loops. See
// `nestsClient/plans/2026-05-06-cross-stack-interop-test.md` for the
// full plan and the pinned upstream versions in `nestsClient/tests/hang-interop/REV`.

val hangInteropDir = rootProject.layout.projectDirectory.dir("nestsClient/tests/hang-interop")
val hangInteropCacheDir =
    layout.projectDirectory
        .dir(System.getProperty("user.home") ?: "/tmp")
        .dir(".cache/amethyst-nests-interop/hang-interop-cargo")

// Versions are duplicated from nestsClient/tests/hang-interop/REV so Gradle has them
// at configuration time; bumping requires touching both files.
val moqRelayVersion = "0.10.25"
val moqTokenCliVersion = "0.5.23"

val interopInstallMoqRelay = tasks.register<Exec>("interopInstallMoqRelay") {
    description = "cargo install moq-relay $moqRelayVersion (interop)"
    group = "interop"
    commandLine(
        "cargo", "install",
        "moq-relay",
        "--version", moqRelayVersion,
        "--root", hangInteropCacheDir.asFile.absolutePath,
        "--locked",
    )
    // See `interopBuildSidecars` — a transitive `*-sys` crate (rustls'
    // aws-lc-sys) builds a C library via CMake whose CMakeLists.txt
    // predates CMake 4's minimum-version floor.
    environment("CMAKE_POLICY_VERSION_MINIMUM", "3.5")
    val installed =
        hangInteropCacheDir.dir("bin").file(
            if (org.gradle.internal.os.OperatingSystem.current().isWindows) "moq-relay.exe" else "moq-relay",
        )
    outputs.file(installed)
    outputs.cacheIf { true }
    onlyIf { !installed.asFile.exists() }
    doFirst { hangInteropCacheDir.asFile.mkdirs() }
}

val interopInstallMoqTokenCli = tasks.register<Exec>("interopInstallMoqTokenCli") {
    description = "cargo install moq-token-cli $moqTokenCliVersion (interop)"
    group = "interop"
    commandLine(
        "cargo", "install",
        "moq-token-cli",
        "--version", moqTokenCliVersion,
        "--root", hangInteropCacheDir.asFile.absolutePath,
        "--locked",
    )
    // See `interopBuildSidecars` — CMake 4 vs. a stale bundled
    // CMakeLists.txt in a transitive `*-sys` crate.
    environment("CMAKE_POLICY_VERSION_MINIMUM", "3.5")
    val installed =
        hangInteropCacheDir.dir("bin").file(
            if (org.gradle.internal.os.OperatingSystem.current().isWindows) "moq-token-cli.exe" else "moq-token-cli",
        )
    outputs.file(installed)
    outputs.cacheIf { true }
    onlyIf { !installed.asFile.exists() }
    doFirst { hangInteropCacheDir.asFile.mkdirs() }
}

val interopBuildSidecars = tasks.register<Exec>("interopBuildSidecars") {
    description = "cargo build --release for nestsClient/tests/hang-interop sidecars"
    group = "interop"
    workingDir = hangInteropDir.asFile
    commandLine("cargo", "build", "--release")
    // `audiopus_sys` bundles libopus, whose CMakeLists.txt declares
    // `cmake_minimum_required(VERSION <3.5)`. CMake 4.x removed
    // compatibility with that, so the build script panics. CMake reads
    // this env var (added in 3.31) as the floor policy version, which
    // lets the stale libopus config run unchanged. Harmless on older
    // CMake that predates CMake 4's removal.
    environment("CMAKE_POLICY_VERSION_MINIMUM", "3.5")
    // Track only manifests + sources; the `target/` subtree is the
    // output, including it as an input would mark the task always
    // out-of-date.
    val sidecarSources =
        fileTree(hangInteropDir.asFile) {
            include("Cargo.toml", "Cargo.lock")
            include("hang-listen/**", "hang-publish/**", "udp-loss-shim/**")
            exclude("**/target/**")
        }
    inputs.files(sidecarSources)
    outputs.dir(hangInteropDir.dir("target/release"))
}

val interopBuildHangSidecars = tasks.register("interopBuildHangSidecars") {
    description = "Build all hang-interop binaries (sidecars + moq-relay + moq-token)."
    group = "interop"
    dependsOn(interopBuildSidecars, interopInstallMoqRelay, interopInstallMoqTokenCli)
}

tasks.withType<Test>().configureEach {
    val isHangInterop = System.getProperty("nestsHangInterop") == "true"
    if (isHangInterop) {
        dependsOn(interopBuildHangSidecars)
    }
    val sidecarRelease = hangInteropDir.dir("target/release").asFile
    val cargoBin = hangInteropCacheDir.dir("bin").asFile
    systemProperty("nestsHangInteropSidecarsDir", sidecarRelease.absolutePath)
    systemProperty("nestsHangInteropCargoBinDir", cargoBin.absolutePath)
    // `club.minnced:opus-java` 1.1.1 ships only an x86_64 darwin dylib,
    // so its in-jar loader reports unsupported on Apple Silicon. Hand
    // JNA an extra search directory so a Homebrew-installed arm64
    // libopus (`brew install opus`, /opt/homebrew/opt/opus/lib) wins
    // over the bundled binary. Append rather than replace to preserve
    // any caller-supplied path; the existing system search still covers
    // other platforms. Silently skipped if Homebrew isn't installed —
    // tests on Intel mac / linux take the in-jar path unchanged.
    val brewOpusLib = file("/opt/homebrew/opt/opus/lib")
    if (brewOpusLib.exists()) {
        val existing = System.getProperty("jna.library.path")
        val combined =
            if (existing.isNullOrEmpty()) brewOpusLib.absolutePath else "$existing:${brewOpusLib.absolutePath}"
        systemProperty("jna.library.path", combined)
    }
    // Per-method moq-relay trace log dir for the routing-race
    // investigation (plan 2026-05-07-moq-relay-routing-investigation.md).
    // Off by default; opt in via -DnestsHangInteropTraceRelay=true so a
    // routine sweep doesn't generate ~MBs of trace per run.
    if (System.getProperty("nestsHangInteropTraceRelay") == "true") {
        val relayLogDir =
            layout.buildDirectory
                .dir("relay-logs")
                .get()
                .asFile
        systemProperty("nestsHangInteropRelayLogDir", relayLogDir.absolutePath)
    }
}

// ---- Cross-stack interop: BROWSER (Phase 4 of T16) --------------------------
//
// Adds the bun + Playwright + headless Chromium harness at
// `nestsClient/tests/browser-interop/`. Mirrors the hang-interop wiring above
// but with bun/npx subprocesses instead of cargo. Opt-in via
// `-DnestsBrowserInterop=true`. See:
//   nestsClient/plans/2026-05-06-phase4-browser-harness.md
//
// Two tasks:
//   - interopBuildBrowserHarness     — `bun install` + `bun build` of
//     listen.ts/publish.ts → dist/, plus copying static .html files.
//   - interopInstallPlaywrightChromium — `npx playwright install
//     --with-deps chromium`. Skipped if a Chromium build already lives
//     in `~/.cache/ms-playwright/`.
//
// We also forward the `bun` and `npx` binaries to be configurable via
// env so CI can override them; defaults pick up the standard install
// paths the agents/host runner ship with.

val browserInteropDir =
    rootProject.layout.projectDirectory.dir("nestsClient/tests/browser-interop")

// `bun` lives at `/root/.bun/bin/bun` on the agent runner. CI may put it
// elsewhere; allow override via env / system property. Falls back to
// `bun` on PATH if the well-known path isn't executable.
fun resolveBunBinary(): String {
    val explicit = System.getenv("BUN_BIN") ?: System.getProperty("bunBin")
    if (explicit != null) return explicit
    val agentPath = "/root/.bun/bin/bun"
    return if (File(agentPath).canExecute()) agentPath else "bun"
}

fun resolveNpxBinary(): String =
    System.getenv("NPX_BIN") ?: System.getProperty("npxBin") ?: "npx"

val interopBuildBrowserHarness = tasks.register<Exec>("interopBuildBrowserHarness") {
    description = "bun install && bun build for the browser interop harness"
    group = "interop"
    workingDir = browserInteropDir.asFile
    val bun = resolveBunBinary()
    // Single bash invocation so `&&` short-circuits on a failed install.
    // The trailing `cp` step copies the static HTML pages into dist/
    // alongside the bundled JS — bun's bundler doesn't carry .html.
    commandLine(
        "bash", "-c",
        "$bun install && $bun build src/listen.ts src/publish.ts --outdir dist --target browser && cp src/listen.html src/publish.html dist/",
    )
    inputs.files(
        fileTree(browserInteropDir.asFile) {
            include("package.json", "tsconfig.json", "playwright.config.ts", "src/**/*")
        },
    )
    outputs.dir(browserInteropDir.dir("dist"))
}

val interopInstallPlaywrightChromium = tasks.register<Exec>("interopInstallPlaywrightChromium") {
    description = "Install Playwright Chromium + dependencies for the browser interop harness"
    group = "interop"
    workingDir = browserInteropDir.asFile
    val npx = resolveNpxBinary()
    // `--with-deps` needs sudo on a fresh runner; on the agent host
    // Chromium is already pre-installed via apt so the system-package
    // step is a no-op. Use the plain `install` form when --with-deps
    // would error (e.g. unprivileged container) — fall back at runtime.
    commandLine("bash", "-c", "$npx playwright install chromium")
    onlyIf {
        // Skip if a Chromium build is already present in the Playwright
        // cache. The cache path is normally ~/.cache/ms-playwright/, but
        // the agent runner sets PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers
        // and ships chromium pre-installed there. Honour the env var so
        // we don't redundantly download.
        val explicit = System.getenv("PLAYWRIGHT_BROWSERS_PATH")
        val candidates =
            if (explicit != null) {
                listOf(File(explicit))
            } else {
                val home = System.getProperty("user.home") ?: return@onlyIf true
                listOf(File(home, ".cache/ms-playwright"))
            }
        val hasChromium =
            candidates.any { dir ->
                dir.exists() &&
                    dir.listFiles()?.any { it.name.startsWith("chromium-") || it.name == "chromium" } == true
            }
        !hasChromium
    }
}

tasks.withType<Test>().configureEach {
    val isBrowserInterop = System.getProperty("nestsBrowserInterop") == "true"
    if (isBrowserInterop) {
        dependsOn(interopBuildBrowserHarness, interopInstallPlaywrightChromium)
        // Browser scenarios reuse the moq-relay subprocess that
        // hang-interop boots, so the Rust sidecars must be built too.
        dependsOn(interopBuildHangSidecars)
    }
    systemProperty(
        "nestsBrowserInteropHarnessDir",
        browserInteropDir.asFile.absolutePath,
    )
    System.getProperty("nestsBrowserInterop")?.let {
        systemProperty("nestsBrowserInterop", it)
    }
}
