package de.mm20.launcher2.ui.media.stremio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.comms.stremio.StremioDeepLink
import de.mm20.launcher2.comms.stremio.StremioProtocol

/** Browser / share of a Stremio add-on URL or detail link opens Stremio Fork. */
class StremioAddonLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.action == Intent.ACTION_VIEW) {
            val raw = intent.dataString?.trim().orEmpty()
            val parsed = StremioProtocol.parseDeepLink(raw)
            if (parsed != null) {
                val route = SettingsDeepLinkContract.ROUTE_STREMIO
                startActivity(Intent().apply {
                    setClassName(
                        this@StremioAddonLinkActivity,
                        requireNotNull(SettingsDeepLinkContract.virtualAppActivityClassName(route)),
                    )
                    data = Uri.parse("telos-task://app/media")
                    putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, route)
                    when (parsed) {
                        is StremioDeepLink.AddonInstall ->
                            putExtra(SettingsDeepLinkContract.EXTRA_STREMIO_ADDON, parsed.url)
                        is StremioDeepLink.Detail -> {
                            putExtra(SettingsDeepLinkContract.EXTRA_STREMIO_DETAIL_TYPE, parsed.type)
                            putExtra(SettingsDeepLinkContract.EXTRA_STREMIO_DETAIL_ID, parsed.id)
                        }
                    }
                    putExtra(
                        SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST,
                        SystemClock.elapsedRealtimeNanos(),
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                })
            }
        }
        finish()
    }
}
