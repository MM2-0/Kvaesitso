package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import de.mm20.launcher2.preferences.ui.AppFontSettings

private val LocalPerAppBaseDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Applies the saved per-app font scale to [LocalDensity] for the whole subtree. The slider UI only
 * stored the value; screens never read it.
 */
@Composable
fun ProvidePerAppFontScale(appKey: String?, content: @Composable () -> Unit) {
    val key = appKey?.takeIf { it.isNotBlank() }
    if (key == null) {
        content()
        return
    }
    val context = LocalContext.current
    val settings = remember(context) { AppFontSettings(context) }
    val scale by settings.scaleFlow(key).collectAsState(initial = settings.getFontScale(key))
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalPerAppBaseDensity provides base,
        LocalDensity provides Density(base.density, base.fontScale * scale),
        content = content,
    )
}

/**
 * Keep pointer input and slider geometry on the inherited system/launcher density while dragging.
 */
@Composable
internal fun PerAppFontControls(content: @Composable () -> Unit) {
    val base = LocalPerAppBaseDensity.current ?: LocalDensity.current
    CompositionLocalProvider(
        LocalPerAppBaseDensity provides base,
        LocalDensity provides base,
        content = content,
    )
}

@Composable
internal fun PerAppFontPreview(
    scale: Float,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val base = LocalDensity.current
    val text =
        androidx.compose.ui.res.stringResource(de.mm20.launcher2.i18n.R.string.per_app_font_preview)
    val style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val reserved =
            remember(text, style, width, base, measurer) {
                measurer
                    .measure(
                        text,
                        style = style,
                        density = Density(base.density, base.fontScale * AppFontSettings.MAX),
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = width),
                    )
                    .size
                    .height
            }
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.heightIn(min = with(base) { reserved.toDp() })
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, base.fontScale * scale)
            ) {
                androidx.compose.material3.Text(text, style = style)
            }
        }
    }
}

fun telosFontKeyFromPageKey(pageKey: String): String? {
    val slug =
        pageKey.substringAfter("://", "").ifBlank {
            pageKey.substringAfter("telos_").substringBefore("_app")
        }
    return when (slug) {
        "phone",
        "messages" -> slug
        "media",
        "radio",
        "music",
        "video" -> "media"
        "photos",
        "viewer" -> "viewer"
        "files" -> "files"
        "calculator" -> "calculator"
        "notes" -> "notes"
        "calendar" -> "calendar"
        "downloads" -> "downloads"
        "network" -> "network"
        "system" -> "system"
        "voice_recorder",
        "voice" -> "voice_recorder"
        "screen_recorder" -> "screen_recorder"
        "screenshot" -> "screenshot"
        "sync" -> "sync"
        "backup" -> "backup"
        "weather" -> "weather"
        "store" -> "store"
        "camera" -> "camera"
        "clock" -> "clock"
        "ermis" -> "ermis"
        "flashlight" -> "flashlight"
        else -> slug.ifBlank { null }
    }
}
