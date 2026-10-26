package de.mm20.launcher2.search.file

import de.mm20.launcher2.serialization.YearSerializer
import de.mm20.launcher2.serialization.ZonedDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.Year
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


@Serializable
data class FileMetadata(
    /**
     * Song title, for audio files.
     */
    val title: String? = null,
    /**
     * Artist name, for audio files.
     */
    val artist: String? = null,
    /**
     * Album name, for audio files.
     */
    val album: String? = null,
    /**
     * Duration, for audio and video files.
     */
    val duration: Duration? = null,
    /**
     * Year, for media files.
     */
    @Serializable(with = YearSerializer::class)
    val year: Year? = null,
    /**
     * Date, for media files.
     */
    @Serializable(with = ZonedDateTimeSerializer::class)
    val date: ZonedDateTime? = null,
    /**
     * Dimensions in pixels, for image and video files.
     */
    val dimensions: Dimensions? = null,
    /**
     * Geolocation, for image and video files.
     */
    val location: String? = null,
    /**
     * App name, for APK files.
     */
    val appName: String? = null,
    /**
     * App package name, for APK files.
     */
    val appPackageName: String? = null,
    /**
     * App version name, for APK files.
     */
    val appVersion: String? = null,
) {
    @Serializable
    data class Dimensions(
        val width: Int,
        val height: Int,
    )
}