package de.mm20.launcher2.ui.launcher.widgets.picker

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.ktx.toDp

@Composable
fun AppWidgetList(
    modifier: Modifier = Modifier,
    widgets: List<AppWidgetProviderInfo>,
    onWidgetSelected: (AppWidgetProviderInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val density = (LocalDensity.current.density * 160).toInt()
    LazyColumn(
        modifier = modifier
    ) {
        items(widgets) {
            key(it.provider.toShortString()) {
                LauncherCard(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .clickable {
                                onWidgetSelected(it)
                            }
                            .padding(16.dp),
                    ) {
                        val label = remember { it.loadLabel(context.packageManager) }
                        Text(text = label, style = MaterialTheme.typography.titleMedium)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {

                            val image: Drawable? = remember {
                                it.loadPreviewImage(context, density) ?: it.loadIcon(
                                    context,
                                    density
                                )
                            }

                            if (image != null) {

                                val mod =
                                    if (image.intrinsicWidth > 0 && image.intrinsicHeight > 0) {
                                        Modifier
                                            .heightIn(max = image.intrinsicHeight.toDp())
                                            .widthIn(max = image.intrinsicWidth.toDp())
                                            .aspectRatio(
                                                image.intrinsicWidth.toFloat() / image.intrinsicHeight.toFloat(),
                                                matchHeightConstraintsFirst = true
                                            )
                                    } else {
                                        Modifier.size(64.dp)
                                    }

                                Canvas(
                                    modifier = mod
                                ) {
                                    drawIntoCanvas {
                                        val aspectRatio =
                                            image.intrinsicWidth / image.intrinsicHeight


                                        image.setBounds(
                                            0,
                                            0,
                                            size.width.toInt(),
                                            size.height.toInt(),
                                        )
                                        image.draw(it.nativeCanvas)
                                    }
                                }
                            }
                        }
                        if (isAtLeastApiLevel(31)) {
                            val description = remember { it.loadDescription(context)?.toString() }
                            if (description != null) {
                                Text(text = description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}