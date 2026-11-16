// iOS purity gate shared by every KMP library module that targets Apple
// (`:commons`, `:commonsUI`). Apply it from the module's build
// script with
//
//     apply(from = rootProject.file("gradle/kmp-purity.gradle.kts"))
//
// It registers `verifyKmpPurity` (wired into `check`) which fails when an
// iOS-targeted source set references a JVM-only API. `verifyKmpPurity` is the
// fast Linux pre-check that runs in the lint job; the macOS iOS compile job is
// the authoritative one. Keeping the pattern table in one file means a new
// forbidden API lands in every module at once instead of drifting per copy.
// vitorpamplona/quartz keeps a copy for :quartz: add a forbidden API to both.

val verifyKmpPurity = tasks.register("verifyKmpPurity") {
    group = "verification"
    description = "Fails if iOS-targeted source sets import JVM-only deps."
    // Every source set that feeds an Apple target. Modules that don't have a
    // given directory are skipped by the exists() filter, so the list is the
    // union across modules (e.g. only :commonsUI has skikoMain today).
    val checkedDirs =
        listOf(
            "src/commonMain", "src/commonTest",
            "src/appleMain", "src/appleTest",
            "src/nativeMain", "src/nativeTest",
            "src/iosMain", "src/iosTest",
            "src/skikoMain", "src/skikoTest",
            "src/iosArm64Main", "src/iosArm64Test",
            "src/iosSimulatorArm64Main", "src/iosSimulatorArm64Test",
            "src/linuxMain", "src/linuxTest",
            "src/linuxX64Main", "src/linuxX64Test",
            "src/macosMain", "src/macosTest",
            "src/macosArm64Main", "src/macosArm64Test",
        ).map { layout.projectDirectory.dir(it).asFile }
            .filter { it.exists() }
    inputs.files(checkedDirs)
    doLast {
        // Each pattern is paired with a short hint so the failure message
        // points at the canonical KMP replacement.
        val forbidden =
            listOf(
                "com.fasterxml.jackson" to "Jackson is JVM-only — use kotlinx.serialization",
                "okhttp3" to "OkHttp is JVM-only — wrap behind expect/actual or use Ktor on iOS",
                "System.currentTimeMillis" to "use TimeUtils.now()",
                "Thread.sleep" to "use kotlinx.coroutines.delay or platform-specific actual",
                "java.util.UUID" to "use kotlin.uuid.Uuid",
                "kotlin.jvm.Synchronized" to "use a KMP lock primitive (KmpLock.withLock {} in commons)",
                // The bare call, not just the annotation: `synchronized(lock) {}` resolves
                // from kotlin-stdlib-jvm with no import, so it compiles on Android/JVM and
                // only fails at the iOS compile step. Catch it here instead.
                "synchronized(" to "`synchronized` is JVM-only — use a KMP lock primitive (KmpLock.withLock {} in commons)",
                "kotlin.jvm.Volatile" to "use kotlin.concurrent.Volatile",
            )
        val offenders =
            checkedDirs.flatMap { dir ->
                dir
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .flatMap { file ->
                        file.readLines().withIndex().mapNotNull { (idx, line) ->
                            val trimmed = line.trimStart()
                            // Skip KDoc / line-comment lines — those legitimately
                            // mention forbidden names (migration notes, doc refs).
                            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                                return@mapNotNull null
                            }
                            forbidden.firstOrNull { (pattern, _) -> line.contains(pattern) }?.let { (hit, hint) ->
                                "${file.relativeTo(rootDir)}:${idx + 1}: '$hit' — $hint"
                            }
                        }
                    }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "iOS-targeted source sets must not reference JVM-only APIs. " +
                    "Move the offending code to jvmAndroid/ or behind an expect/actual:\n  " +
                    offenders.joinToString("\n  "),
            )
        }
    }
}

tasks.named("check").configure { dependsOn(verifyKmpPurity) }
