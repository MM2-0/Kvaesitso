package de.mm20.launcher2.ui.media.youtube

import android.content.Context
import android.os.Process
import de.mm20.launcher2.comms.youtube.YouTubeWebLinks
import de.mm20.launcher2.ktx.TelosShellEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit user action; use Shizuku and verify the current Android user's domain selections. */
internal suspend fun registerYouTubeLinks(context: Context): Boolean =
    withContext(Dispatchers.IO) {
        if (!TelosShellEngine.isShizukuAvailable(context)) return@withContext false
        val user = Process.myUid() / 100_000
        try {
            for (command in YouTubeWebLinks.registrationCommands(context.packageName, user)) {
                // Do not switch identity or replay a write when its response is ambiguous.
                if (
                    !TelosShellEngine.isShizukuAvailable() ||
                        !TelosShellEngine.execArgsResult(
                                command.toTypedArray(),
                                retryAfterFailure = false,
                                allowRoot = false,
                            )
                            .isSuccess
                )
                    return@withContext false
            }
            if (!TelosShellEngine.isShizukuAvailable()) return@withContext false
            val result =
                TelosShellEngine.execArgsResult(
                    arrayOf(
                        "/system/bin/cmd",
                        "package",
                        "get-app-links",
                        "--user",
                        user.toString(),
                        context.packageName,
                    ),
                    retryAfterFailure = false,
                    allowRoot = false,
                )
            result.isSuccess &&
                YouTubeWebLinks.registrationVerified(result.stdout) &&
                YouTubeWebLinks.allHosts.all { host ->
                    @Suppress("DEPRECATION")
                    val handler =
                        context.packageManager
                            .resolveActivity(
                                android.content
                                    .Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://$host/watch?v=dQw4w9WgXcQ"),
                                    )
                                    .addCategory(android.content.Intent.CATEGORY_BROWSABLE),
                                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                            )
                            ?.activityInfo
                    handler?.packageName == context.packageName
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
