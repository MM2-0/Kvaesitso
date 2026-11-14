package de.mm20.launcher2.ui.media.youtube

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.comms.youtube.YouTubeWebLinks

internal fun telosYouTubeIntent(context: Context, url: String, music: Boolean): Intent {
    require(YouTubeWebLinks.musicTarget(url) != null)
    val route =
        if (music) SettingsDeepLinkContract.ROUTE_YOUTUBE_MUSIC
        else SettingsDeepLinkContract.ROUTE_YOUTUBE
    return Intent().apply {
        setClassName(
            context,
            requireNotNull(SettingsDeepLinkContract.virtualAppActivityClassName(route)),
        )
        data = Uri.parse("telos-task://app/media")
        putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, route)
        putExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK, url)
        putExtra(
            SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST,
            SystemClock.elapsedRealtimeNanos(),
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    }
}

/** Browsable aliases validate incoming URLs and enter the normal locked, themed Media task. */
class YouTubeLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { url ->
                YouTubeWebLinks.musicTarget(url)?.let { music ->
                    startActivity(telosYouTubeIntent(this, url, music))
                }
            }
        }
        finish()
    }
}
