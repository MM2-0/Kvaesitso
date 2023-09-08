package de.mm20.launcher2.icons.compat

import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import android.util.AttributeSet
import android.util.Log
import android.util.Xml
import android.view.InflateException
import androidx.core.content.res.ResourcesCompat
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.icons.ClockLayer
import de.mm20.launcher2.icons.ClockSublayer
import de.mm20.launcher2.icons.ClockSublayerRole
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedClockLayer
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.skipToNextTag
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException


data class AdaptiveIconDrawableCompat(
    val background: Drawable,
    val foreground: Drawable,
    val monochrome: Drawable?,
) {

    companion object {
        fun from(adaptiveIconDrawable: AdaptiveIconDrawable): AdaptiveIconDrawableCompat {
            return AdaptiveIconDrawableCompat(
                background = adaptiveIconDrawable.background,
                foreground = adaptiveIconDrawable.foreground,
                monochrome = if (isAtLeastApiLevel(33)) adaptiveIconDrawable.monochrome else null,
            )
        }

        fun from(resources: Resources, resId: Int): AdaptiveIconDrawableCompat? {
            if (isAtLeastApiLevel(33)) {
                return try {
                    val drawable = ResourcesCompat.getDrawable(resources, resId, null)
                    if (drawable is AdaptiveIconDrawable) {
                        from(drawable)
                    } else {
                        null
                    }
                } catch (e: Resources.NotFoundException) {
                    null
                }
            }

            var xmlParser: XmlResourceParser? = null

            try {
                xmlParser = resources.getXml(resId)
                val attrs = Xml.asAttributeSet(xmlParser)
                if (!xmlParser.skipToNextTag()) return null

                if (xmlParser.name != "adaptive-icon") {
                    return null
                }

                var background: Drawable? = null
                var foreground: Drawable? = null
                var monochrome: Drawable? = null

                while (xmlParser.skipToNextTag()) {
                    when (xmlParser.name) {
                        "monochrome" -> {
                            monochrome = parseLayer(resources, xmlParser, attrs)
                        }

                        "background" -> {
                            background = parseLayer(resources, xmlParser, attrs)
                        }

                        "foreground" -> {
                            foreground = parseLayer(resources, xmlParser, attrs)
                        }
                    }
                }
                if (foreground != null && background != null) {
                    return AdaptiveIconDrawableCompat(
                        background = background,
                        foreground = foreground,
                        monochrome = monochrome,
                    )
                }
            } catch (e: Resources.NotFoundException) {
                return null
            } catch (e: IOException) {
                return null
            } catch (e: XmlPullParserException) {
                return null
            } finally {
                xmlParser?.close()
            }
            return null
        }

        @Throws(
            XmlPullParserException::class,
            IOException::class,
            Resources.NotFoundException::class
        )
        private fun parseLayer(
            resources: Resources,
            parser: XmlResourceParser,
            attrs: AttributeSet
        ): Drawable? {
            val drawableId = parser.getAttributeResourceValue(
                "http://schemas.android.com/apk/res/android",
                "drawable",
                0
            )

            if (drawableId != 0) {
                return ResourcesCompat.getDrawable(resources, drawableId, null)
            }
            if (!parser.skipToNextTag()) return null
            return try {
                Drawable.createFromXmlInner(resources, parser, attrs)
            } catch (e: InflateException) {
                CrashReporter.logException(e)
                null
            }
        }
    }
}

fun AdaptiveIconDrawableCompat.toLauncherIcon(
    themed: Boolean = false,
    clock: ClockIconConfig? = null,
): StaticLauncherIcon {
    val clockForeground = (if (themed) monochrome else foreground) as? LayerDrawable
    if (clock != null && clockForeground != null) {
        val clockLayers = (0 until clockForeground.numberOfLayers).map {
            val drw = clockForeground.getDrawable(it)
            ClockSublayer(
                drawable = drw,
                role = when (it) {
                    clock.hourLayer -> ClockSublayerRole.Hour
                    clock.minuteLayer -> ClockSublayerRole.Minute
                    clock.secondLayer -> ClockSublayerRole.Second
                    else -> ClockSublayerRole.Static
                }
            )
        }
        if (themed) {
            return StaticLauncherIcon(
                foregroundLayer = TintedClockLayer(
                    defaultHour = clock.defaultHour,
                    defaultMinute = clock.defaultMinute,
                    defaultSecond = clock.defaultSecond,
                    sublayers = clockLayers,
                    scale = 1.5f,
                ),
                backgroundLayer = ColorLayer(),
            )
        }
        return StaticLauncherIcon(
            foregroundLayer = ClockLayer(
                defaultHour = clock.defaultHour,
                defaultMinute = clock.defaultMinute,
                defaultSecond = clock.defaultSecond,
                sublayers = clockLayers,
                scale = 1.5f,
            ),
            backgroundLayer = StaticIconLayer(
                icon = this.background,
                scale = 1.5f,
            )
        )
    }

    if (themed && this.monochrome != null) {
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                scale = 1.5f,
                icon = this.monochrome,
            ),
            backgroundLayer = ColorLayer()
        )
    } else {
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(
                scale = 1.5f,
                icon = this.foreground,
            ),
            backgroundLayer = StaticIconLayer(
                scale = 1.5f,
                icon = this.background,
            )
        )
    }
}

data class ClockIconConfig(
    val hourLayer: Int,
    val minuteLayer: Int,
    val secondLayer: Int,
    val defaultHour: Int,
    val defaultMinute: Int,
    val defaultSecond: Int,
)