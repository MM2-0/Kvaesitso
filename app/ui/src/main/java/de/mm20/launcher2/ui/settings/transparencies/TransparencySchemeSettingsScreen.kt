package de.mm20.launcher2.ui.settings.transparencies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.themes.transparencies.Transparencies
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LocalIconShape
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.surfaceColorAtElevation
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.theme.transparency.transparencySchemeOf
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.util.UUID
import kotlin.math.round

@Serializable
data class TransparencySchemeSettingsRoute(
    val id: String
)

@Composable
fun TransparencySchemeSettingsScreen(themeId: UUID) {
    val viewModel: TransparencySchemesSettingsScreenVM = viewModel()

    val context = LocalContext.current

    val wallpaperColors by wallpaperColorsAsState()

    val theme by remember(
        viewModel,
        themeId
    ) { viewModel.getTransparencies(themeId) }.collectAsStateWithLifecycle(null)


    var editName by remember { mutableStateOf(false) }

    if (editName) {
        var name by remember(theme) { mutableStateOf(theme?.name ?: "") }
        AlertDialog(
            onDismissRequest = { editName = false },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTransparencies(theme!!.copy(name = name))
                        editName = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        )
    }

    PreferenceScreen(
        title = {
            Text(
                theme?.name ?: "",
                modifier = Modifier.clickable {
                    editName = true
                },
            )
        },
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/customization/color-schemes",
    ) {
        if (theme == null) return@PreferenceScreen

        item {
            PreferenceCategory {
                TransparenciesPreview(theme!!)
                TransparencyPreference(
                    title = "Background",
                    value = theme!!.background,
                    defaultValue = 0.85f,
                    onValueChange = { viewModel.updateTransparencies(theme!!.copy(background = it)) }
                )
                TransparencyPreference(
                    title = "Surface",
                    value = theme!!.surface,
                    defaultValue = 1f,
                    onValueChange = { viewModel.updateTransparencies(theme!!.copy(surface = it)) }
                )
                TransparencyPreference(
                    title = "Elevated Surface",
                    value = theme!!.elevatedSurface,
                    defaultValue = 1f,
                    onValueChange = { viewModel.updateTransparencies(theme!!.copy(elevatedSurface = it)) }
                )
            }
        }
    }
}

@Composable
private fun TransparenciesPreview(
    theme: Transparencies,
) {
    val transparencyScheme = transparencySchemeOf(theme)

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .checkerboard(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    12.dp,
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer.copy(transparencyScheme.background),
                        MaterialTheme.shapes.large.copy(
                            bottomStart = CornerSize(0f),
                            bottomEnd = CornerSize(0f),
                        )
                    ),
            ) {
                val xs = MaterialTheme.shapes.extraSmall
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp, start = 12.dp, end = 12.dp, bottom = 2.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer.copy(transparencyScheme.surface),
                            MaterialTheme.shapes.medium.copy(
                                bottomStart = xs.bottomStart,
                                bottomEnd = xs.bottomStart,
                            ),
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(LocalGridSettings.current.columnCount) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(LocalGridSettings.current.iconSize.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        LocalIconShape.current,
                                    )
                            )
                        }
                    }
                }
            }
        }
        val elevatedSurface = MaterialTheme.colorScheme
            .surfaceColorAtElevation(8.dp + LocalAbsoluteTonalElevation.current)
            .copy(alpha = transparencyScheme.elevatedSurface)
        Box(
            modifier = Modifier
                .padding(end = 24.dp)
                .shadow(
                    if (transparencyScheme.elevatedSurface < 1f) 0.dp else 8.dp,
                    MaterialTheme.shapes.medium,
                    clip = true
                )
                .background(elevatedSurface)
                .align(Alignment.CenterEnd)
                .size(144.dp),
        )
    }
}

@Composable
private fun TransparencyPreference(
    title: String,
    value: Float?,
    defaultValue: Float,
    onValueChange: (Float?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 20.dp)
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .checkerboard(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    12.dp,
                )
                .padding(8.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = value ?: defaultValue),
                    MaterialTheme.shapes.extraSmall
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    modifier = Modifier.weight(1f),
                    value = value ?: defaultValue,
                    onValueChange = {
                        val roundedValue = round(it * 100) / 100
                        onValueChange(roundedValue)
                    },
                    valueRange = 0f..1f,
                )
                val format = remember {
                    DecimalFormat().apply {
                        maximumFractionDigits = 2
                        minimumFractionDigits = 0
                    }
                }
                Text(
                    text = format.format(value ?: defaultValue),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .widthIn(min = 48.dp),
                )
            }
        }
    }
}