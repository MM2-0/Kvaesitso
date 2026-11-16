package de.mm20.launcher2.search
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.content.res.Resources
/** Display labels are localized; domains and persisted keys never change with language. */
object TelosAppLabels {
    fun resourceForDomain(domain: String): Int? = when (domain) {
        "telos_clock_app" -> I18nR.string.preference_screen_clockwidget
        "telos_akou_app" -> I18nR.string.telos_akou_app_label
        "telos_magnifier_app" -> I18nR.string.telos_magnifier_app_label
        "telos_flashlight_app" -> I18nR.string.telos_flashlight_app_label
        "telos_phone_app" -> I18nR.string.virtual_app_phone
        "telos_messages_app" -> I18nR.string.hc_messages
        "telos_radio_app" -> I18nR.string.hc_radio
        "telos_music_app" -> I18nR.string.au_music_title
        "telos_youtube_app" -> I18nR.string.telos_youtube_label
        "telos_youtube_music_app" -> I18nR.string.telos_youtube_music_label
        "telos_video_app" -> I18nR.string.au9_mediahub_space_video
        "telos_photos_app" -> I18nR.string.hc_photos
        "telos_viewer_app" -> I18nR.string.virtual_app_viewer
        "telos_files_app" -> I18nR.string.pdft_files
        "telos_calculator_app" -> I18nR.string.calculator_title
        "telos_voice_recorder_app" -> I18nR.string.voice_title
        "telos_camera_app" -> I18nR.string.camera_title
        "telos_screen_recorder_app" -> I18nR.string.screenrec_title
        "telos_screenshot_app" -> I18nR.string.screenshot_title
        "telos_notes_app" -> I18nR.string.notes_title
        "telos_ermis_app" -> I18nR.string.ermis_title
        "telos_calendar_app" -> I18nR.string.backup_group_calendar
        "telos_downloads_app" -> I18nR.string.dl_title
        "telos_media_app" -> I18nR.string.dl_m_tab
        "telos_network_app" -> I18nR.string.hc_network
        "telos_store_app" -> I18nR.string.hf_store_title
        "telos_system_app" -> I18nR.string.telos_system_label
        "telos_weather_app" -> I18nR.string.widget_name_weather
        "telos_trash_app" -> I18nR.string.telos_trash_title
        "telos_tv_app" -> I18nR.string.au12_tvui_title
        "telos_stremio_app" -> I18nR.string.au22_stremio_label
        "telos_sync_app" -> I18nR.string.notes_sync_settings
        "telos_backup_app" -> I18nR.string.preference_backup
        "telos_keyboard_app" -> I18nR.string.settings_keyboard
        "telos_keepass_app" -> I18nR.string.keepass_title
        "telos_aether_app" -> I18nR.string.aether_title
        "telos_fit_app" -> I18nR.string.fit_title
        "telos_help_app" -> I18nR.string.telos_help_title
        else -> null
    }

    fun forDomain(context: Context, domain: String): String =
        resourceForDomain(domain)?.let { context.getString(it) } ?: domain

    fun forKey(resources: Resources, key: String): String? =
        resourceForDomain(key.substringBefore("://"))?.let { resources.getString(it) }
}
