package de.mm20.launcher2.comms.stremio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Optional new-episode notices for saved series, checked when Stremio Fork is opened. */
object StremioLibraryNotifier {
    private const val CHANNEL = "stremio_library"
    private const val PREFS = "stremio_upcoming_notified"
    private const val KEY_IDS = "ids"
    const val MAX_SERIES = 40

    suspend fun checkOnResume(
        context: Context,
        library: List<LibraryEntry>,
        addons: List<Addon>,
        client: StremioClient,
    ) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!StremioPlaybackSettings.load(app).notifyNewEpisodes) return@withContext
        val nm = NotificationManagerCompat.from(app)
        if (!nm.areNotificationsEnabled()) return@withContext
        val series = library.filter { it.type.equals("series", true) }.take(MAX_SERIES)
        if (series.isEmpty()) return@withContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val limit = Semaphore(3)
        val aired = ArrayList<Pair<LibraryEntry, StremioUpcoming.DatedEpisode>>()
        for (entry in series) {
            val meta = limit.withPermit { fetchMeta(addons, client, entry) } ?: continue
            for (ep in StremioUpcoming.airedInLastDays(meta.videos, 1)) {
                val key = "${entry.id}|${ep.video.id}"
                if (key in seen) continue
                seen += key
                aired += entry to ep
            }
        }
        if (aired.isEmpty()) {
            prefs.edit().putStringSet(KEY_IDS, seen).apply()
            return@withContext
        }
        ensureChannel(app)
        val open = PendingIntent.getActivity(
            app, 22,
            Intent().setClassName(app.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_STREMIO)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val glyph = BaseR.drawable.ic_glyph_stremio
        aired.take(5).forEachIndexed { i, (entry, ep) ->
            val title = ep.video.title.ifBlank {
                listOfNotNull(ep.video.season?.let { "S$it" }, ep.video.episode?.let { "E$it" }).joinToString("")
            }
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(glyph)
                .setContentTitle(app.getString(I18nR.string.au22_stremio_episode_aired))
                .setContentText(app.getString(I18nR.string.au22_stremio_episode_aired_text, entry.name, title))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(0x5E10 + i, n) }
        }
        prefs.edit().putStringSet(KEY_IDS, seen).apply()
    }

    private suspend fun fetchMeta(addons: List<Addon>, client: StremioClient, entry: LibraryEntry): Meta? {
        for (a in addons.filter { it.manifest.supports("meta", entry.type, entry.id) }) {
            val meta = runCatching { client.meta(a, entry.type, entry.id) }.getOrNull()
            if (meta != null) return meta
        }
        return null
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(I18nR.string.au22_stremio_upcoming), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
