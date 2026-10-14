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
data class InteliaUiState(val version: Int = 1, val theme: Int = 0, val showClock: Boolean = true, val reduceMotion: Boolean = false, val wallpaper: Int = 0, val arc: List<String> = emptyList())

object InteliaUiCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(state: InteliaUiState): String = json.encodeToString(state)
    fun decode(text: String): InteliaUiState {
        val state = json.decodeFromString<InteliaUiState>(text)
        require(state.version == 1 && state.theme in 0..2 && state.wallpaper in 0..2 && validArc(state.arc)) { "Unsupported Intelia UI settings" }
        return state
    }
    fun validArc(keys: List<String>) = keys.size <= 5 && keys.distinct().size == keys.size && keys.all { it.isNotBlank() && it.length <= 512 && it.none(Char::isISOControl) }
}

class InteliaUiPreferences(context: Context) : Backupable {
    private val preferences = context.getSharedPreferences("intelia-ui", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(InteliaUiState(
        theme = preferences.getInt("theme", 0).coerceIn(0, 2),
        showClock = preferences.getBoolean("clock", true),
        reduceMotion = preferences.getBoolean("motion", false),
        wallpaper = preferences.getInt("wallpaper", 0).coerceIn(0, 2),
        arc = runCatching { InteliaUiCodec.decode(preferences.getString("arc", null) ?: "{}").arc }.getOrDefault(emptyList()),
    ))
    val state = mutable.asStateFlow()

    suspend fun update(transform: (InteliaUiState) -> InteliaUiState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(mutable.value)
            require(next.theme in 0..2 && next.wallpaper in 0..2 && InteliaUiCodec.validArc(next.arc))
            if (!preferences.edit().putInt("theme", next.theme).putBoolean("clock", next.showClock)
                    .putBoolean("motion", next.reduceMotion).putInt("wallpaper", next.wallpaper)
                    .putString("arc", InteliaUiCodec.encode(InteliaUiState(arc = next.arc))).commit()) throw IOException("Could not save Intelia settings")
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
            file.inputStream().use { InteliaUiCodec.decode(BackupArchive.readLimited(it, 4096).toString(Charsets.UTF_8)) }
        }
        update { decoded }
    }
}

val inteliaUiModule = module {
    single { InteliaUiPreferences(androidContext()) }
    factory<Backupable>(named<InteliaUiPreferences>()) { get<InteliaUiPreferences>() }
}
