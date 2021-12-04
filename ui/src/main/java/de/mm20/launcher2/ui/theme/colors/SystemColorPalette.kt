package de.mm20.launcher2.ui.theme.colors

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color

@RequiresApi(api = Build.VERSION_CODES.S)
class SystemColorPalette(context: Context) : ColorPalette() {
    override val neutral = ColorSwatch(
        shade100 = Color(context.getColor(android.R.color.system_neutral1_0)),
        shade99 = Color(context.getColor(android.R.color.system_neutral1_10)),
        shade95 = Color(context.getColor(android.R.color.system_neutral1_50)),
        shade90 = Color(context.getColor(android.R.color.system_neutral1_100)),
        shade80 = Color(context.getColor(android.R.color.system_neutral1_200)),
        shade70 = Color(context.getColor(android.R.color.system_neutral1_300)),
        shade60 = Color(context.getColor(android.R.color.system_neutral1_400)),
        shade50 = Color(context.getColor(android.R.color.system_neutral1_500)),
        shade40 = Color(context.getColor(android.R.color.system_neutral1_600)),
        shade30 = Color(context.getColor(android.R.color.system_neutral1_700)),
        shade20 = Color(context.getColor(android.R.color.system_neutral1_800)),
        shade10 = Color(context.getColor(android.R.color.system_neutral1_900)),
        shade0 = Color(context.getColor(android.R.color.system_neutral1_1000)),
    )

    override val neutralVariant = ColorSwatch(
        shade100 = Color(context.getColor(android.R.color.system_neutral2_0)),
        shade99 = Color(context.getColor(android.R.color.system_neutral2_10)),
        shade95 = Color(context.getColor(android.R.color.system_neutral2_50)),
        shade90 = Color(context.getColor(android.R.color.system_neutral2_100)),
        shade80 = Color(context.getColor(android.R.color.system_neutral2_200)),
        shade70 = Color(context.getColor(android.R.color.system_neutral2_300)),
        shade60 = Color(context.getColor(android.R.color.system_neutral2_400)),
        shade50 = Color(context.getColor(android.R.color.system_neutral2_500)),
        shade40 = Color(context.getColor(android.R.color.system_neutral2_600)),
        shade30 = Color(context.getColor(android.R.color.system_neutral2_700)),
        shade20 = Color(context.getColor(android.R.color.system_neutral2_800)),
        shade10 = Color(context.getColor(android.R.color.system_neutral2_900)),
        shade0 = Color(context.getColor(android.R.color.system_neutral2_1000)),
    )

    override val primary = ColorSwatch(
        shade100 = Color(context.getColor(android.R.color.system_accent1_0)),
        shade99 = Color(context.getColor(android.R.color.system_accent1_10)),
        shade95 = Color(context.getColor(android.R.color.system_accent1_50)),
        shade90 = Color(context.getColor(android.R.color.system_accent1_100)),
        shade80 = Color(context.getColor(android.R.color.system_accent1_200)),
        shade70 = Color(context.getColor(android.R.color.system_accent1_300)),
        shade60 = Color(context.getColor(android.R.color.system_accent1_400)),
        shade50 = Color(context.getColor(android.R.color.system_accent1_500)),
        shade40 = Color(context.getColor(android.R.color.system_accent1_600)),
        shade30 = Color(context.getColor(android.R.color.system_accent1_700)),
        shade20 = Color(context.getColor(android.R.color.system_accent1_800)),
        shade10 = Color(context.getColor(android.R.color.system_accent1_900)),
        shade0 = Color(context.getColor(android.R.color.system_accent1_1000)),
    )

    override val secondary = ColorSwatch(
        shade100 = Color(context.getColor(android.R.color.system_accent2_0)),
        shade99 = Color(context.getColor(android.R.color.system_accent2_10)),
        shade95 = Color(context.getColor(android.R.color.system_accent2_50)),
        shade90 = Color(context.getColor(android.R.color.system_accent2_100)),
        shade80 = Color(context.getColor(android.R.color.system_accent2_200)),
        shade70 = Color(context.getColor(android.R.color.system_accent2_300)),
        shade60 = Color(context.getColor(android.R.color.system_accent2_400)),
        shade50 = Color(context.getColor(android.R.color.system_accent2_500)),
        shade40 = Color(context.getColor(android.R.color.system_accent2_600)),
        shade30 = Color(context.getColor(android.R.color.system_accent2_700)),
        shade20 = Color(context.getColor(android.R.color.system_accent2_800)),
        shade10 = Color(context.getColor(android.R.color.system_accent2_900)),
        shade0 = Color(context.getColor(android.R.color.system_accent2_1000)),
    )

    override val tertiary = ColorSwatch(
        shade100 = Color(context.getColor(android.R.color.system_accent3_0)),
        shade99 = Color(context.getColor(android.R.color.system_accent3_10)),
        shade95 = Color(context.getColor(android.R.color.system_accent3_50)),
        shade90 = Color(context.getColor(android.R.color.system_accent3_100)),
        shade80 = Color(context.getColor(android.R.color.system_accent3_200)),
        shade70 = Color(context.getColor(android.R.color.system_accent3_300)),
        shade60 = Color(context.getColor(android.R.color.system_accent3_400)),
        shade50 = Color(context.getColor(android.R.color.system_accent3_500)),
        shade40 = Color(context.getColor(android.R.color.system_accent3_600)),
        shade30 = Color(context.getColor(android.R.color.system_accent3_700)),
        shade20 = Color(context.getColor(android.R.color.system_accent3_800)),
        shade10 = Color(context.getColor(android.R.color.system_accent3_900)),
        shade0 = Color(context.getColor(android.R.color.system_accent3_1000)),
    )
}