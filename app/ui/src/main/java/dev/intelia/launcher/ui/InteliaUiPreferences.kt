package dev.intelia.launcher.ui

import android.content.Context
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.backup.BackupArchive
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

@Serializable
data class InteliaUiState(val version: Int = 1, val theme: Int = 0, val showClock: Boolean = true, val reduceMotion: Boolean = false,
    val wallpaper: Int = 0, val arc: List<String> = emptyList(), val spaces: Map<String, Int> = emptyMap(), val hand: Int = 0, val island: Boolean = false,
    val icons: Int = 0, val iconPack: String = "", val iconOverrides: Map<String, Int> = emptyMap(),
    val palette: Int = 0, val typeface: Int = 0, val showDate: Boolean = true)

object InteliaUiCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(state: InteliaUiState): String = json.encodeToString(state)
    fun decode(text: String): InteliaUiState {
        val state = json.decodeFromString<InteliaUiState>(text)
        require(state.version == 1 && state.theme in 0..2 && state.wallpaper in 0..2 && validArc(state.arc) && validExtensions(state)) { "Unsupported Intelia UI settings" }
        return state
    }
    fun validArc(keys: List<String>) = keys.size <= 5 && keys.distinct().size == keys.size && keys.all { it.isNotBlank() && it.length <= 512 && it.none(Char::isISOControl) }
    fun validExtensions(state: InteliaUiState) = state.hand in 0..2 && state.icons in 0..3 && state.palette in 0..3 && state.typeface in 0..1 &&
        state.iconPack.length <= 255 && state.iconPack.all { it.isLetterOrDigit() || it == '.' || it == '_' } &&
        state.iconOverrides.size <= 128 && state.iconOverrides.all { (key, value) ->
            key.startsWith("app://") && key.length <= 512 && key.none(Char::isISOControl) && value in 0..3
        } && state.spaces.size <= 128 && state.spaces.all { (key, value) ->
        key.isNotBlank() && key.length <= 512 && key.none(Char::isISOControl) && value in 1..4
    }
}

class InteliaUiPreferences(context: Context) : Backupable {
    private val preferences = context.getSharedPreferences("intelia-ui", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val extensions = runCatching { InteliaUiCodec.decode(preferences.getString("extensions", null) ?: "{}") }.getOrDefault(InteliaUiState())
    private val mutable = MutableStateFlow(InteliaUiState(
        theme = preferences.getInt("theme", 0).coerceIn(0, 2),
        showClock = preferences.getBoolean("clock", true),
        reduceMotion = preferences.getBoolean("motion", false),
        wallpaper = preferences.getInt("wallpaper", 0).coerceIn(0, 2),
        arc = runCatching { InteliaUiCodec.decode(preferences.getString("arc", null) ?: "{}").arc }.getOrDefault(emptyList()),
        spaces = extensions.spaces, hand = extensions.hand, island = extensions.island,
        icons = extensions.icons, iconPack = extensions.iconPack, iconOverrides = extensions.iconOverrides,
        palette = extensions.palette, typeface = extensions.typeface, showDate = extensions.showDate,
    ))
    val state = mutable.asStateFlow()

    suspend fun update(transform: (InteliaUiState) -> InteliaUiState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(mutable.value)
            require(next.theme in 0..2 && next.wallpaper in 0..2 && InteliaUiCodec.validArc(next.arc) && InteliaUiCodec.validExtensions(next))
            if (!preferences.edit().putInt("theme", next.theme).putBoolean("clock", next.showClock)
                    .putBoolean("motion", next.reduceMotion).putInt("wallpaper", next.wallpaper)
                    .putString("arc", InteliaUiCodec.encode(InteliaUiState(arc = next.arc)))
                    .putString("extensions", InteliaUiCodec.encode(next)).commit()) throw IOException("Could not save Intelia settings")
            mutable.value = next
        }
    }

    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
        mutex.withLock { File(toDir, "intelia-ui.json").writeText(InteliaUiCodec.encode(mutable.value)) }
    }

    override suspend fun restore(fromDir: File) {
        val file = File(fromDir, "intelia-ui.json")
        if (!file.exists()) return
        val decoded = withContext(Dispatchers.IO) {
            file.inputStream().use { InteliaUiCodec.decode(BackupArchive.readLimited(it, 1024 * 1024).toString(Charsets.UTF_8)) }
        }
        // Restoring appearance must not silently enable a global overlay.
        update { decoded.copy(island = false) }
    }
}

val inteliaUiModule = module {
    single { InteliaUiPreferences(androidContext()) }
    single { InteliaIconEngine(androidContext(), get()) }
    factory<Backupable>(named<InteliaUiPreferences>()) { get<InteliaUiPreferences>() }
}
