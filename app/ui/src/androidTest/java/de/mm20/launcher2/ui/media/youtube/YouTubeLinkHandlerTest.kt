package de.mm20.launcher2.ui.media.youtube

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.comms.youtube.YouTubeWebLinks
import org.junit.Assert.*
import org.junit.Test

class YouTubeLinkHandlerTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Suppress("DEPRECATION")
    @Test
    fun browserIntentsResolveToTheCorrectExportedHandler() {
        for (host in YouTubeWebLinks.allHosts) {
            val handlers =
                context.packageManager
                    .queryIntentActivities(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://$host/watch?v=dQw4w9WgXcQ"))
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .setPackage(context.packageName),
                        PackageManager.MATCH_DEFAULT_ONLY,
                    )
                    .map { it.activityInfo }
            assertEquals(host, 1, handlers.size)
            assertTrue(handlers.single().exported)
            assertTrue(
                handlers
                    .single()
                    .name
                    .endsWith(
                        if (host == "music.youtube.com") "YouTubeMusicLinkHandler"
                        else "YouTubeLinkHandler"
                    )
            )
        }
    }

    @Test
    fun musicSearchUsesStableMediaTaskAndFreshRequestIdentity() {
        val url = "https://music.youtube.com/search?q=Artist%20Song"
        val first = telosYouTubeIntent(context, url, true)
        val second = telosYouTubeIntent(context, url, true)
        assertEquals("de.mm20.launcher2.ui.settings.TelosApp_media", first.component?.className)
        assertEquals("telos-task://app/media", first.dataString)
        assertEquals(
            SettingsDeepLinkContract.ROUTE_YOUTUBE_MUSIC,
            first.getStringExtra(SettingsDeepLinkContract.EXTRA_ROUTE),
        )
        assertEquals(url, first.getStringExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK))
        assertNotEquals(
            first.getLongExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST, 0),
            second.getLongExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST, 0),
        )
        assertTrue(
            runCatching { telosYouTubeIntent(context, "https://evil.example/watch", false) }
                .isFailure
        )
    }

    @Test
    fun shellDomainSelectionRegistersEveryDeclaredYouTubeHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(args: List<String>): String =
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(args.joinToString(" "))
                )
                .bufferedReader()
                .use { it.readText() }
        val user = android.os.Process.myUid() / 100_000
        val commands = YouTubeWebLinks.registrationCommands(context.packageName, user)
        try {
            commands.forEach { shell(it) }
            val report =
                shell(
                    listOf(
                        "/system/bin/cmd",
                        "package",
                        "get-app-links",
                        "--user",
                        user.toString(),
                        context.packageName,
                    )
                )
            assertTrue(report, YouTubeWebLinks.registrationVerified(report))
            for (host in YouTubeWebLinks.allHosts) {
                @Suppress("DEPRECATION")
                val chosen =
                    context.packageManager
                        .resolveActivity(
                            Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://$host/watch?v=dQw4w9WgXcQ"),
                                )
                                .addCategory(Intent.CATEGORY_BROWSABLE),
                            PackageManager.MATCH_DEFAULT_ONLY,
                        )
                        ?.activityInfo
                assertEquals(host, context.packageName, chosen?.packageName)
            }
        } finally {
            shell(
                listOf(
                    "/system/bin/cmd",
                    "package",
                    "set-app-links",
                    "--package",
                    context.packageName,
                    "0",
                ) + YouTubeWebLinks.allHosts
            )
            shell(
                listOf(
                    "/system/bin/cmd",
                    "package",
                    "set-app-links-user-selection",
                    "--user",
                    user.toString(),
                    "--package",
                    context.packageName,
                    "false",
                ) + YouTubeWebLinks.allHosts
            )
        }
    }
}
