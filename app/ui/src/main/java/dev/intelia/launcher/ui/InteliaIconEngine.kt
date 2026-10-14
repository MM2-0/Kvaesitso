package dev.intelia.launcher.ui

import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.icons.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A small adapter over the existing pack importer/renderer, shared by Compose and Pulse.
 * Only appearance changes: the component and launch identity never come from an icon pack. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class InteliaIconEngine(context: Context, private val packs: IconPackManager) {
    private val context = context.applicationContext
    val placeholder: Bitmap by lazy { this.context.packageManager.defaultActivityIcon.toBitmap(48, 48) }
    private val mutex = Mutex()
    private val importMutex = Mutex()
    private var importedGeneration = -1L
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val generation = MutableStateFlow(0L)
    val revision = generation.asStateFlow()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            cache.evictAll(); generation.value += 1
        }
    }
    init {
        ContextCompat.registerReceiver(this.context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    val installedPacks: Flow<List<IconPack>> = revision.flatMapLatest {
        flow { ensurePacks(); emitAll(packs.getInstalledIconPacks()) }
    }
    private suspend fun ensurePacks() = importMutex.withLock {
        val current = revision.value
        if (importedGeneration != current) {
            packs.updateIconPacks()
            importedGeneration = current
        }
    }

    suspend fun load(component: ComponentName, key: String, state: InteliaUiState): Bitmap = withContext(Dispatchers.IO) {
        val mode = state.iconOverrides[key] ?: state.icons
        val dark = when (state.theme) { 1 -> true; 2 -> false; else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
        val fg = if (dark) 0xffeeeeee.toInt() else 0xff242424.toInt()
        val bg = if (dark) 0xff292b2d.toInt() else 0xffeeeff0.toInt()
        val cacheKey = "$key:$mode:${state.iconPack}:$dark:${revision.value}"
        mutex.withLock {
            cache.get(cacheKey)?.let { return@withLock it }
            val system = runCatching { context.packageManager.getActivityIcon(component).mutate() }
                .getOrElse { runCatching { context.packageManager.getApplicationIcon(component.packageName).mutate() }
                    .getOrElse { context.packageManager.defaultActivityIcon.mutate() } }
            val pack = if (mode == 3 && state.iconPack.isNotBlank()) {
                try { ensurePacks(); packs.getIcon(state.iconPack, component.packageName, component.className, allowThemed = true) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { null }
            } else null
            val static = when (pack) {
                is StaticLauncherIcon -> pack
                is DynamicLauncherIcon -> pack.getIcon(System.currentTimeMillis())
                else -> null
            }
            val source = static?.render(LauncherIconRenderSettings(128, fg, bg, if (dark) 90 else 15, if (dark) 20 else 95))
                ?: (if (mode == 2 && Build.VERSION.SDK_INT >= 33) (system as? AdaptiveIconDrawable)?.monochrome?.mutate() else null)
                    ?.apply { setTint(fg) }?.toBitmap(128, 128) ?: system.toBitmap(128, 128)
            val bitmap = if (mode == 1 || mode == 2) Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { result ->
                val canvas = Canvas(result)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = bg
                canvas.drawRoundRect(0f, 0f, 128f, 128f, 30f, 30f, paint)
                if (mode == 2) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                // Preserve the actual app mark; no guessed symbols or random substitutes.
                canvas.drawBitmap(source, null, Rect(14, 14, 114, 114), paint)
            } else source
            cache.put(cacheKey, bitmap)
            bitmap
        }
    }
}
