package de.mm20.launcher2.comms.stremio

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Shared with the isolated video process; call load/save on IO. Empty audio means no preference;
 * empty primary subtitle means disabled. Secondary choices are ordered fallbacks. */
data class StremioPlaybackSettings(
    val audio: String = Locale.getDefault().language,
    val audioFallback: String = "en",
    val subtitles: String = Locale.getDefault().language,
    val subtitleFallback: String = "en",
    val cacheGb: Int = 5,
    val autoNextEpisode: Boolean = false,
    val resolution: Int = 0,
    val maxSizeGb: Int = 0,
    val hideScreeners: Boolean = false,
    val notifyNewEpisodes: Boolean = true,
    val sharedServer: Boolean = false,
    val streamingServer: String = StremioStreamingServer.DEFAULT_ADDRESS,
) {
    val audioLanguages get() = listOf(audio, audioFallback).filter { it.isNotBlank() }.distinct()
    val subtitleLanguages get() = if (subtitles.isBlank()) emptyList() else listOf(subtitles, subtitleFallback).filter { it.isNotBlank() }.distinct()
    val cacheBytes get() = cacheGb.toLong() * 1024 * 1024 * 1024

    companion object {
        val cacheChoices = listOf(1, 2, 5, 10, 20)
        private fun file(context: Context) = File(context.applicationContext.filesDir, "stremio_playback.json")
        fun load(context: Context): StremioPlaybackSettings = runCatching {
            val j = JSONObject(file(context).readText())
            val d = StremioPlaybackSettings()
            fun lang(key: String, default: String) = j.optString(key, default).lowercase(Locale.ROOT).takeIf {
                it.isEmpty() || it in Locale.getISOLanguages().toSet()
            } ?: default
            d.copy(audio = lang("audio", d.audio), audioFallback = lang("audioFallback", d.audioFallback),
                subtitles = lang("subtitles", d.subtitles), subtitleFallback = lang("subtitleFallback", d.subtitleFallback),
                cacheGb = j.optInt("cacheGb", 5).takeIf { it in cacheChoices } ?: 5,
                autoNextEpisode = j.optBoolean("autoNextEpisode", false),
                resolution = j.optInt("resolution", 0).takeIf { it in listOf(0, 480, 720, 1080, 1440, 2160) } ?: 0,
                maxSizeGb = j.optInt("maxSizeGb", 0).takeIf { it in listOf(0, 1, 2, 5, 10, 20, 50) } ?: 0,
                hideScreeners = j.optBoolean("hideScreeners", false),
                notifyNewEpisodes = j.optBoolean("notifyNewEpisodes", true),
                sharedServer = j.optBoolean("sharedServer", false),
                streamingServer = StremioStreamingServer.normalizeAddress(j.optString("streamingServer")) ?: StremioStreamingServer.DEFAULT_ADDRESS)
        }.getOrDefault(StremioPlaybackSettings())
        @Synchronized fun save(context: Context, settings: StremioPlaybackSettings) {
            val f = file(context)
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(JSONObject().put("audio", settings.audio).put("audioFallback", settings.audioFallback)
                .put("subtitles", settings.subtitles).put("subtitleFallback", settings.subtitleFallback)
                .put("cacheGb", settings.cacheGb).put("autoNextEpisode", settings.autoNextEpisode)
                .put("resolution", settings.resolution).put("maxSizeGb", settings.maxSizeGb)
                .put("hideScreeners", settings.hideScreeners)
                .put("notifyNewEpisodes", settings.notifyNewEpisodes)
                .put("sharedServer", settings.sharedServer)
                .put("streamingServer", settings.streamingServer).toString())
            check(tmp.renameTo(f)) { "Playback preferences could not be saved" }
        }
    }
}
