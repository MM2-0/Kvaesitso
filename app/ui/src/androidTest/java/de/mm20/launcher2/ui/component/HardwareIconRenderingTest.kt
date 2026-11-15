package de.mm20.launcher2.ui.component

import android.graphics.Bitmap
import android.graphics.Color
import de.mm20.launcher2.icons.BitmapLayer
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.GridLayer
import de.mm20.launcher2.icons.LauncherIconRenderSettings
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TransparentLayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real GPU bitmaps must survive later software composition of previews and folder icons. */
class HardwareIconRenderingTest {
    private fun settings(size: Int) =
        LauncherIconRenderSettings(size, Color.WHITE, Color.BLACK, 50, 50)

    @Test
    fun hardwarePreviewCanBeRenderedAgainAtAnotherSize() = runBlocking {
        val preview = StaticLauncherIcon(ColorLayer(Color.RED), TransparentLayer).hardware()
        val source = (preview.foregroundLayer as BitmapLayer).bitmap
        assertEquals(Bitmap.Config.HARDWARE, source.config)
        val rendered = preview.render(settings(48))
        assertEquals(Bitmap.Config.ARGB_8888, rendered.config)
        assertTrue(Color.alpha(rendered.getPixel(24, 24)) > 0)
        assertFalse("A preview can be shared by other consumers", source.isRecycled)
        assertSame(rendered, preview.render(settings(48)))
        val resized = preview.render(settings(96))
        assertEquals(96, resized.width)
        assertFalse(source.isRecycled)
    }

    @Test
    fun nestedFolderGridComposesHardwareLayersWithoutRecyclingTheSource() = runBlocking {
        val software = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        software.eraseColor(Color.GREEN)
        val hardware = requireNotNull(software.copy(Bitmap.Config.HARDWARE, false))
        software.recycle()
        try {
            val tile = BitmapLayer(hardware)
            val grid = GridLayer(List(4) { tile })
            val folder = StaticLauncherIcon(GridLayer(List(4) { grid }), TransparentLayer)
            val rendered = folder.render(settings(96))
            for ((x, y) in listOf(12 to 12, 60 to 12, 12 to 60, 60 to 60)) {
                assertEquals(Color.GREEN, rendered.getPixel(x, y))
            }
            assertFalse(hardware.isRecycled)
        } finally {
            hardware.recycle()
        }
    }
}
