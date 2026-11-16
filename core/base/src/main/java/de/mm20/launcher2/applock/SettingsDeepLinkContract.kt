package de.mm20.launcher2.applock

/** Cross-module contract for opening Settings directly to a specific App Lock screen from a
 * lower module (e.g. a notification's PendingIntent) that can't depend on :app:ui directly -
 * mirrors [de.mm20.launcher2.webapp.WebAppLaunchContract]'s shape. */
object SettingsDeepLinkContract {
    const val ACTIVITY_CLASS_NAME = "de.mm20.launcher2.ui.settings.SettingsActivity"
    const val EXTRA_ROUTE = "de.mm20.launcher2.settings.ROUTE"
    const val ROUTE_INTRUDER_PHOTOS = "settings/applock/intruderphotos"
    /** Opens the Store dashboard directly - used by the virtual "Telos Store" app drawer entry. */
    const val ROUTE_STORE = "settings/store"
    // === TELOS_PENDING_REVIEW_START: comms_virtual_apps ===
    const val ROUTE_COMMS = "settings/comms"
    const val EXTRA_COMMS_TAB = "de.mm20.launcher2.settings.COMMS_TAB"
    const val EXTRA_DIAL_NUMBER = "de.mm20.launcher2.settings.DIAL_NUMBER"
    /** Text to put in the message field of Telos Messages */
    const val EXTRA_SMS_BODY = "de.mm20.launcher2.settings.SMS_BODY"
    /** Pictures or videos (content addresses) to attach to a message in Telos Messages */
    const val EXTRA_SMS_ATTACHMENTS = "de.mm20.launcher2.settings.SMS_ATTACHMENTS"
    const val ROUTE_RADIO = "settings/radio"
    const val ROUTE_MUSIC = "settings/music"
    const val ROUTE_YOUTUBE = "settings/youtube"
    const val ROUTE_YOUTUBE_MUSIC = "settings/youtube_music"
    const val EXTRA_YOUTUBE_LINK = "de.mm20.launcher2.settings.YOUTUBE_LINK"
    const val EXTRA_YOUTUBE_LINK_REQUEST = "de.mm20.launcher2.settings.YOUTUBE_LINK_REQUEST"
    const val ROUTE_VIDEO = "settings/video"
    /** Telos TV space inside Telos Media */
    const val ROUTE_TV = "settings/tv"
    /** Stremio Fork space inside Telos Media */
    const val ROUTE_STREMIO = "settings/stremio"
    /** Add-on manifest URL or stremio:// link to install in Stremio Fork */
    const val EXTRA_STREMIO_ADDON = "de.mm20.launcher2.settings.STREMIO_ADDON"
    /** Telos Media: music, radio, video and TV spaces in one app */
    const val ROUTE_MEDIA = "settings/media_hub"
    /** Space to open in Telos Media: music, radio, video or tv (default: the last used one) */
    const val EXTRA_MEDIA_SPACE = "de.mm20.launcher2.settings.MEDIA_SPACE"
    /** Optional: id of a TV channel that Telos Media should show in the TV player (space "tv"); playback is already started by the sender */
    const val EXTRA_MEDIA_CHANNEL = "de.mm20.launcher2.settings.MEDIA_CHANNEL"
    /** An address or an obtainium:// link to add in the Store */
    const val EXTRA_STORE_URL = "de.mm20.launcher2.settings.STORE_URL"
    const val ROUTE_PHOTOS = "settings/photos"
    /** Telos Viewer: library of documents (PDF, Office, text, e-books) */
    const val ROUTE_VIEWER = "settings/viewer"
    const val ROUTE_FILES = "settings/files"
    const val ROUTE_CALCULATOR = "settings/calculator"
    const val ROUTE_VOICE_RECORDER = "settings/voice_recorder"
    const val ROUTE_KEYBOARD = "settings/keyboard"
    const val ROUTE_KEEPASS = "settings/keepass"
    const val ROUTE_CAMERA = "settings/camera"
    const val ROUTE_SCREEN_RECORDER = "settings/screen_recorder"
    const val ROUTE_SCREENSHOT = "settings/screenshot"
    const val ROUTE_NOTES = "settings/notes"
    const val ROUTE_ERMIS = "settings/ermis"
    const val ROUTE_AETHER = "settings/aether"
    const val ROUTE_CALENDAR = "settings/calendar"
    const val ROUTE_DOWNLOADS = "settings/downloads"
    const val ROUTE_NETWORK = "settings/network"
    const val ROUTE_NETWORK_SPEED_TEST = "settings/network/speedtest"
    const val ROUTE_NETWORK_PRIVATE_DNS = "settings/network/private_dns"
    const val ROUTE_SYSTEM = "settings/system"
    /** Optional System tab: `shield`, `shizuku`, `apps`, `tweaks`. Empty opens the dashboard. */
    const val EXTRA_SYSTEM_TAB = "de.mm20.launcher2.settings.SYSTEM_TAB"
    const val ROUTE_WEATHER = "settings/weather"
    const val ROUTE_TRASH = "settings/trash"
    const val ROUTE_SYNC = "settings/sync"
    const val ROUTE_BACKUP = "settings/backup"
    const val ROUTE_FIT = "settings/fit"
    /** Telos Help */
    const val ROUTE_TELOS_PAGE = "settings/telos_page"
    const val EXTRA_TELOS_PAGE_KEY = "de.mm20.launcher2.settings.TELOS_PAGE_KEY"
    const val EXTRA_HELP_TOPIC = "de.mm20.launcher2.settings.HELP_TOPIC"
    /** Links (string array) to offer in the add sheet of Telos Downloads, from a share or an Open with */
    const val EXTRA_DOWNLOAD_URLS = "de.mm20.launcher2.settings.DOWNLOAD_URLS"
    /** Stable identity for one Recents task per built-in app, independent of launch payload. */
    fun virtualAppTaskId(route: String?, commsTab: String? = null): String? = when (route) {
        ROUTE_COMMS -> if (commsTab == "messages") "messages" else "phone"
        ROUTE_MEDIA, ROUTE_MUSIC, ROUTE_RADIO, ROUTE_VIDEO, ROUTE_TV, ROUTE_STREMIO, ROUTE_YOUTUBE, ROUTE_YOUTUBE_MUSIC -> "media"
        ROUTE_PHOTOS, ROUTE_VIEWER -> "viewer"
        ROUTE_FILES -> "files"
        ROUTE_STORE -> "store"
        ROUTE_NETWORK, ROUTE_NETWORK_SPEED_TEST, ROUTE_NETWORK_PRIVATE_DNS -> "network"
        ROUTE_SYSTEM -> "system"
        ROUTE_WEATHER -> "weather"
        ROUTE_TRASH -> "trash"
        ROUTE_CALCULATOR -> "calculator"
        ROUTE_VOICE_RECORDER -> "voice_recorder"
        ROUTE_CAMERA -> "camera"
        ROUTE_SCREEN_RECORDER -> "screen_recorder"
        ROUTE_SCREENSHOT -> "screenshot"
        ROUTE_NOTES -> "notes"
        ROUTE_ERMIS -> "ermis"
        ROUTE_CALENDAR -> "calendar"
        ROUTE_DOWNLOADS -> "downloads"
        ROUTE_SYNC -> "sync"
        ROUTE_BACKUP -> "backup"
        ROUTE_FIT -> "fit"
        ROUTE_KEYBOARD -> "keyboard"
        ROUTE_KEEPASS -> "keepass"
        ROUTE_AETHER -> "aether"
        "settings/clock" -> "clock"
        "settings/akou" -> "akou"
        "settings/magnifier" -> "magnifier"
        "settings/flashlight" -> "flashlight"
        else -> null
    }
    /** Alias metadata gives OEM Recents a stable label/icon even without TaskDescription support. */
    fun virtualAppActivityClassName(route: String?, commsTab: String? = null): String? =
        virtualAppTaskId(route, commsTab)?.let { "de.mm20.launcher2.ui.settings.TelosApp_$it" }
    // === TELOS_PENDING_REVIEW_END: comms_virtual_apps ===
}
