package de.mm20.launcher2.ui.theme.colors

import android.R
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color

@RequiresApi(api = Build.VERSION_CODES.S)
class SystemColorScheme(context: Context) : ColorScheme() {
    override val neutral1 = ColorSwatch(
        shade0 = Color(context.getColor(R.color.system_neutral1_0)),
        shade10 = Color(context.getColor(R.color.system_neutral1_10)),
        shade50 = Color(context.getColor(R.color.system_neutral1_50)),
        shade100 = Color(context.getColor(R.color.system_neutral1_100)),
        shade200 = Color(context.getColor(R.color.system_neutral1_200)),
        shade300 = Color(context.getColor(R.color.system_neutral1_300)),
        shade400 = Color(context.getColor(R.color.system_neutral1_400)),
        shade500 = Color(context.getColor(R.color.system_neutral1_500)),
        shade600 = Color(context.getColor(R.color.system_neutral1_600)),
        shade700 = Color(context.getColor(R.color.system_neutral1_700)),
        shade800 = Color(context.getColor(R.color.system_neutral1_800)),
        shade900 = Color(context.getColor(R.color.system_neutral1_900)),
        shade1000 = Color(context.getColor(R.color.system_neutral1_1000)),
    )

    override val neutral2 = ColorSwatch(
        shade0 = Color(context.getColor(R.color.system_neutral2_0)),
        shade10 = Color(context.getColor(R.color.system_neutral2_10)),
        shade50 = Color(context.getColor(R.color.system_neutral2_50)),
        shade100 = Color(context.getColor(R.color.system_neutral2_100)),
        shade200 = Color(context.getColor(R.color.system_neutral2_200)),
        shade300 = Color(context.getColor(R.color.system_neutral2_300)),
        shade400 = Color(context.getColor(R.color.system_neutral2_400)),
        shade500 = Color(context.getColor(R.color.system_neutral2_500)),
        shade600 = Color(context.getColor(R.color.system_neutral2_600)),
        shade700 = Color(context.getColor(R.color.system_neutral2_700)),
        shade800 = Color(context.getColor(R.color.system_neutral2_800)),
        shade900 = Color(context.getColor(R.color.system_neutral2_900)),
        shade1000 = Color(context.getColor(R.color.system_neutral2_1000)),
    )

    override val accent1 = ColorSwatch(
        shade0 = Color(context.getColor(R.color.system_accent1_0)),
        shade10 = Color(context.getColor(R.color.system_accent1_10)),
        shade50 = Color(context.getColor(R.color.system_accent1_50)),
        shade100 = Color(context.getColor(R.color.system_accent1_100)),
        shade200 = Color(context.getColor(R.color.system_accent1_200)),
        shade300 = Color(context.getColor(R.color.system_accent1_300)),
        shade400 = Color(context.getColor(R.color.system_accent1_400)),
        shade500 = Color(context.getColor(R.color.system_accent1_500)),
        shade600 = Color(context.getColor(R.color.system_accent1_600)),
        shade700 = Color(context.getColor(R.color.system_accent1_700)),
        shade800 = Color(context.getColor(R.color.system_accent1_800)),
        shade900 = Color(context.getColor(R.color.system_accent1_900)),
        shade1000 = Color(context.getColor(R.color.system_accent1_1000)),
    )

    override val accent2 = ColorSwatch(
        shade0 = Color(context.getColor(R.color.system_accent2_0)),
        shade10 = Color(context.getColor(R.color.system_accent2_10)),
        shade50 = Color(context.getColor(R.color.system_accent2_50)),
        shade100 = Color(context.getColor(R.color.system_accent2_100)),
        shade200 = Color(context.getColor(R.color.system_accent2_200)),
        shade300 = Color(context.getColor(R.color.system_accent2_300)),
        shade400 = Color(context.getColor(R.color.system_accent2_400)),
        shade500 = Color(context.getColor(R.color.system_accent2_500)),
        shade600 = Color(context.getColor(R.color.system_accent2_600)),
        shade700 = Color(context.getColor(R.color.system_accent2_700)),
        shade800 = Color(context.getColor(R.color.system_accent2_800)),
        shade900 = Color(context.getColor(R.color.system_accent2_900)),
        shade1000 = Color(context.getColor(R.color.system_accent2_1000)),
    )

    override val accent3 = ColorSwatch(
        shade0 = Color(context.getColor(R.color.system_accent3_0)),
        shade10 = Color(context.getColor(R.color.system_accent3_10)),
        shade50 = Color(context.getColor(R.color.system_accent3_50)),
        shade100 = Color(context.getColor(R.color.system_accent3_100)),
        shade200 = Color(context.getColor(R.color.system_accent3_200)),
        shade300 = Color(context.getColor(R.color.system_accent3_300)),
        shade400 = Color(context.getColor(R.color.system_accent3_400)),
        shade500 = Color(context.getColor(R.color.system_accent3_500)),
        shade600 = Color(context.getColor(R.color.system_accent3_600)),
        shade700 = Color(context.getColor(R.color.system_accent3_700)),
        shade800 = Color(context.getColor(R.color.system_accent3_800)),
        shade900 = Color(context.getColor(R.color.system_accent3_900)),
        shade1000 = Color(context.getColor(R.color.system_accent3_1000)),
    )
}