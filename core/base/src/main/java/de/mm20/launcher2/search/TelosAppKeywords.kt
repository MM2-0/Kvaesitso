package de.mm20.launcher2.search

/**
 * Extra search keywords of the Telos virtual apps (matched in addition to the label, with the same
 * Greek-aware matching). Keyed by the domain of the virtual app key (the part before "://").
 */
object TelosAppKeywords {

    private val byDomain: Map<String, List<String>> = mapOf(
        "telos_youtube_app" to listOf("youtube", "yt", "video", "γιουτουμπ", "γιουτούμπ", "βίντεο"),
        "telos_youtube_music_app" to listOf("youtube", "youtube music", "yt music", "γιουτουμπ", "γιουτούμπ", "μουσική", "τραγούδια"),
        "telos_store_app" to listOf("store", "apps", "updates", "github", "fdroid", "εφαρμογές", "ενημερώσεις", "κατάστημα"),
        "telos_phone_app" to listOf("dialer", "calls", "call", "contacts", "τηλέφωνο", "κλήσεις", "επαφές"),
        "telos_messages_app" to listOf("sms", "mms", "text", "chat", "μηνύματα"),
        "telos_media_app" to listOf(
            "media", "hub", "music", "radio", "video", "player", "audio", "movies", "films", "songs", "stations", "mousiki", "tainies", "radiofono", "vinteo",
            "μέσα", "πολυμέσα", "μουσική", "ραδιόφωνο", "βίντεο", "ταινίες"
        ),
        "telos_stremio_app" to listOf(
            "stremio", "stremio fork", "addons", "add-ons", "catalog", "torrentio", "cinemeta", "series", "movies",
            "στρέμιο", "στρεμιο", "πρόσθετα", "σειρές", "ταινίες",
        ),
        "telos_tv_app" to listOf(
            "tv", "television", "tileorasi", "tileorash", "tileoras", "thleorasi", "iptv", "live tv", "kanalia", "stream", "channels",
            "τηλεόραση", "τηλεοραση", "κανάλια", "καναλια", "live"
        ),
        "telos_radio_app" to listOf("fm", "stations", "ραδιόφωνο", "ραδιοφωνικοί σταθμοί"),
        "telos_music_app" to listOf("songs", "player", "audio", "tracks", "μουσική", "τραγούδια"),
        "telos_video_app" to listOf("movies", "player", "films", "βίντεο", "ταινίες"),
        "telos_viewer_app" to listOf(
            "photos", "gallery", "pictures", "images", "camera roll", "albums",
            "φωτογραφίες", "γκαλερί", "εικόνες", "άλμπουμ",
            "viewer", "documents", "docs", "pdf", "office", "word", "excel", "powerpoint", "presentations", "spreadsheets", "ebook", "epub", "reader", "files",
            "προβολή", "έγγραφα", "αρχεία", "παρουσιάσεις", "λογιστικά φύλλα", "αναγνώστης",
        ),
        "telos_files_app" to listOf("file manager", "folders", "storage", "αρχεία", "φάκελοι"),
        "telos_calculator_app" to listOf("calc", "math", "αριθμομηχανή", "υπολογιστής"),
        "telos_notes_app" to listOf("memo", "notebook", "todo", "σημειώσεις"),
        "telos_ermis_app" to listOf("email", "mail", "gmail", "inbox", "fairmail", "ermis", "ηλεκτρονικό ταχυδρομείο", "αλληλογραφία"),
        "telos_calendar_app" to listOf("events", "agenda", "schedule", "ημερολόγιο", "εκδηλώσεις"),
        "telos_downloads_app" to listOf("download", "downloader", "λήψεις", "κατεβάσματα"),
        "telos_network_app" to listOf("nextdns", "vpn", "dns", "firewall", "wifi", "internet", "δίκτυο", "τείχος προστασίας"),
        "telos_system_app" to listOf(
            "system", "cpu", "ram", "wifi", "ip", "processes", "σύστημα", "επεξεργαστής",
            "shizuku", "shizuko", "shizukou", "shizukku", "shizukuu", "sizuku", "sizuuku", "shizuk",
            "σιζούκου", "σιζουκου", "σιζούκο", "σιζουκο", "σιζούκκου", "σίζουκου",
        ),
        "telos_trash_app" to listOf("trash", "bin", "recycle", "κάδος", "ανακύκλωση"),
        "telos_voice_recorder_app" to listOf("recorder", "recording", "dictaphone", "audio", "καταγραφή ήχου", "εγγραφή φωνής"),
        "telos_akou_app" to listOf("akou", "shazam", "identify", "song finder", "music recognition", "άκου", "αναγνώριση μουσικής"),
        "telos_sync_app" to listOf("sync", "syncthing", "decentralized", "peers", "backup", "συγχρονισμός", "αρχεία"),
        "telos_backup_app" to listOf("backup", "restore", "titanium", "swift", "cloud", "αντίγραφα", "επαναφορά"),
        "telos_keyboard_app" to listOf("keyboard", "ime", "typing", "input", "gboard", "florisboard", "anysoftkeyboard", "heliboard", "futo", "πληκτρολόγιο", "πληκτρολογιο", "πληκτρολογηση", "pliktrologio"),
        "telos_keepass_app" to listOf("keepass", "password", "passwords", "manager", "autofill", "vault", "κωδικοί", "κωδικοι", "ασφάλεια"),
        "telos_aether_app" to listOf("aether", "nostr", "relays", "zaps", "lightning", "damus", "amethyst", "decentralized", "crypto", "αιθέρας", "αιθερας"),
        "telos_magnifier_app" to listOf("magnifier", "zoom", "zoom in", "μεγεθυντικός", "φακός"),
        "telos_flashlight_app" to listOf("flashlight", "torch", "siren", "morse", "φακός", "σειρήνα", "μορς"),
        "telos_clock_app" to listOf("clock", "alarm", "time", "world clock", "timer", "stopwatch", "night clock", "ρολόι", "ρολοι", "ξυπνητήρι", "ώρα", "roloi", "xypnitiri", "ora", "χρονόμετρο", "αντίστροφη μέτρηση"),
        "telos_weather_app" to listOf("weather", "forecast", "breezy", "today weather", "radar", "aqi", "uv", "rain", "temperature", "καιρός", "καιρος", "πρόγνωση", "προγνωση", "βροχή", "θερμοκρασία", "kairos", "prognosi"),
        "telos_help_app" to listOf("help", "tutorial", "guide", "faq", "βοήθεια", "βοηθεια", "οδηγός", "οδηγος"),
    )

    private val englishNames = mapOf(
        "telos_phone_app" to "phone",
        "telos_messages_app" to "messages",
        "telos_radio_app" to "radio",
        "telos_music_app" to "music",
        "telos_youtube_app" to "YouTube",
        "telos_youtube_music_app" to "YouTube Music",
        "telos_video_app" to "video",
        "telos_tv_app" to "TV",
        "telos_stremio_app" to "Stremio Fork",
        "telos_photos_app" to "photos",
        "telos_viewer_app" to "viewer",
        "telos_files_app" to "files",
        "telos_calculator_app" to "calculator",
        "telos_voice_recorder_app" to "Voice recorder",
        "telos_screen_recorder_app" to "Screen recorder",
        "telos_screenshot_app" to "screenshot",
        "telos_notes_app" to "notes",
        "telos_ermis_app" to "Ermis",
        "telos_calendar_app" to "calendar",
        "telos_downloads_app" to "downloads",
        "telos_media_app" to "media",
        "telos_network_app" to "network",
        "telos_store_app" to "store",
        "telos_system_app" to "system",
        "telos_trash_app" to "trash",
        "telos_akou_app" to "Akou",
        "telos_sync_app" to "Sync",
        "telos_backup_app" to "Backup",
        "telos_keyboard_app" to "Keyboard",
        "telos_keepass_app" to "KeePassTL",
        "telos_magnifier_app" to "Magnifier",
        "telos_flashlight_app" to "Flashlight",
        "telos_clock_app" to "Clock",
        "telos_weather_app" to "Weather",
        "telos_help_app" to "Help",
    )

    fun forDomain(domain: String): List<String> {
        val name = englishNames[domain] ?: domain.takeIf {
            it.startsWith("telos_") && it.endsWith("_app")
        }?.removePrefix("telos_")?.removeSuffix("_app")?.replace('_', ' ')
            ?: return byDomain[domain].orEmpty()
        return (listOf("telos", "τέλος", "τελος", name, "telos $name") + byDomain[domain].orEmpty()).distinct()
    }

    /** [key] is a virtual app key such as `telos_photos_app://photos`. */
    fun forKey(key: String): List<String> = forDomain(key.substringBefore("://"))
}
