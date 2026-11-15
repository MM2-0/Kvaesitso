package de.mm20.launcher2.ui.component

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.preferences.ui.AppFontSettings
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Keep one pointer down across real density updates, rather than setting slider semantics once. */
class PerAppFontDragTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inlineSliderKeepsDraggingAndPersistsOnlyOnRelease() = exercise(false)

    @Test fun dialogSliderKeepsDraggingWithoutMovingItsBounds() = exercise(true)

    private fun exercise(dialog: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = if (dialog) "font_dialog_regression" else "font_inline_regression"
        val prefs = context.getSharedPreferences("telos_app_font_scales", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putFloat("scale_$key", 1f).commit())
        val settings = AppFontSettings(context)
        settings.setLive(key, 1f)
        val writes = AtomicInteger()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == "scale_$key") writes.incrementAndGet()
        }
        var observed = 0f
        compose.setContent {
            val physical = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(physical.density, 1.3f)) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    ProvidePerAppFontScale(key) {
                        val live = LocalDensity.current.fontScale
                        SideEffect { observed = live }
                        if (dialog) PerAppFontScaleDialog(key, "Test app", {})
                        else
                            Column(Modifier.width(360.dp).fillMaxHeight()) {
                                PerAppFontScaleSetting(key)
                            }
                    }
                }
            }
        }
        compose.waitForIdle()
        settings.register(listener)
        try {
            val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            val initialBounds = slider.fetchSemanticsNode().boundsInRoot
            slider.performTouchInput {
                down(Offset(width.toFloat() * .34f, center.y))
                moveTo(Offset(width.toFloat() * .60f, center.y), delayMillis = 100)
            }
            compose.waitForIdle()
            val middle = settings.getFontScale(key)
            assertTrue("Live value must change before pointer up", middle > 1.08f)
            assertEquals(0, writes.get())
            slider.performTouchInput {
                moveTo(Offset(width.toFloat() * .88f, center.y), delayMillis = 100)
            }
            compose.waitForIdle()
            val last = settings.getFontScale(key)
            assertTrue("The same drag must survive the first density change", last > middle + .06f)
            compose.runOnIdle { assertEquals(1.3f * last, observed, .03f) }
            val finalBounds = slider.fetchSemanticsNode().boundsInRoot
            assertEquals(initialBounds.top, finalBounds.top, 1f)
            assertEquals(initialBounds.width, finalBounds.width, 1f)
            assertEquals(0, writes.get())
            slider.performTouchInput { up() }
            compose.waitForIdle()
            assertEquals(last, prefs.getFloat("scale_$key", 0f), .001f)
            assertTrue(writes.get() >= 1)
        } finally {
            settings.unregister(listener)
            settings.setLive(key, 1f)
            prefs.edit().remove("scale_$key").commit()
        }
    }
}
