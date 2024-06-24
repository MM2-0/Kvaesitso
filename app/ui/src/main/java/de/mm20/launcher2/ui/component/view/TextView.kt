package de.mm20.launcher2.ui.component.view

import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.SystemFontFamily
import androidx.compose.ui.text.style.TextAlign
import de.mm20.launcher2.ktx.isAtLeastApiLevel

@Composable
internal fun ComposeTextView(
    view: TextView,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    Text(
        text = view.text.toString(),
        color = Color(view.textColors.defaultColor),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = with(density) {
                view.textSize.toSp()
            },
            fontWeight = if (isAtLeastApiLevel(28)) {
                FontWeight(view.typeface.weight)
            } else if (view.typeface.isBold) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
            fontStyle = if (view.typeface.isItalic) {
                FontStyle.Italic
            } else {
                FontStyle.Normal
            },
        ),
        modifier = modifier,
        textAlign = when (view.textAlignment) {
            TextView.TEXT_ALIGNMENT_CENTER -> TextAlign.Center
            TextView.TEXT_ALIGNMENT_TEXT_START -> TextAlign.Start
            TextView.TEXT_ALIGNMENT_TEXT_END -> TextAlign.End
            else -> TextAlign.Start
        }
    )
}