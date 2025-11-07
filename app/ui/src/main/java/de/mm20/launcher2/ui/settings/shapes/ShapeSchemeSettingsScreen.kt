package de.mm20.launcher2.ui.settings.shapes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.themes.shapes.CornerStyle
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.ktx.withCorners
import de.mm20.launcher2.ui.theme.shapes.shapesOf
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import de.mm20.launcher2.themes.shapes.Shape as ThemeShape

@Composable
fun ShapeSchemeSettingsScreen(themeId: UUID) {
    val viewModel: ShapeSchemesSettingsScreenVM = viewModel()

    val context = LocalContext.current

    val theme by remember(
        viewModel,
        themeId
    ) { viewModel.getShapes(themeId) }.collectAsStateWithLifecycle(null)
    val previewShapes = theme?.let { shapesOf(it) }


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
                        viewModel.updateShapes(theme!!.copy(name = name))
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
        if (theme == null || previewShapes == null) return@PreferenceScreen
        val baseShape = theme!!.baseShape

        item {
            PreferenceCategory {
                ShapePreference(
                    title = stringResource(R.string.preference_shapes_base),
                    shape = baseShape,
                    baseShape = baseShape,
                    factor = 1f,
                    onValueChange = {
                        viewModel.updateShapes(
                            theme!!.copy(
                                baseShape = it ?: ThemeShape(
                                    corners = CornerStyle.Rounded,
                                    radii = intArrayOf(12, 12, 12, 12)
                                )
                            )
                        )
                    },
                    titleTextStyle = MaterialTheme.typography.titleMedium
                )
            }
        }

        item {
            PreferenceCategory {
                ShapePreview(
                    previewShapes = previewShapes,
                ) {
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.extraSmall.withCorners(
                                        topStart = false,
                                        topEnd = false
                                    )
                                )
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .height(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.extraSmall.withCorners(
                                        bottomStart = false,
                                        bottomEnd = false
                                    )
                                )
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .width(IntrinsicSize.Max),
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.more_vert_24px), null)
                                },
                                text = { Text("Menu") },
                                onClick = { })
                        }
                    }
                }
                ShapePreference(
                    title = "Extra small",
                    shape = theme!!.extraSmall,
                    baseShape = baseShape,
                    factor = 1f / 3f,
                    onValueChange = {
                        viewModel.updateShapes(
                            theme!!.copy(extraSmall = it)
                        )
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ShapePreview(
                    previewShapes = previewShapes,
                ) {
                    FilterChip(
                        onClick = {},
                        label = {
                            Text("Chip")
                        },
                        selected = false,
                    )
                }
                ShapePreference(
                    title = "Small",
                    shape = theme!!.small,
                    baseShape = baseShape,
                    factor = 2f / 3f,
                    onValueChange = {
                        viewModel.updateShapes(
                            theme!!.copy(small = it)
                        )
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ShapePreview(
                    previewShapes = previewShapes,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .width(200.dp)
                            .height(48.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.shapes.medium
                            )
                    ) {
                        Icon(
                            painterResource(R.drawable.search_24px),
                            null,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                ShapePreference(
                    title = "Medium",
                    shape = theme!!.medium,
                    baseShape = baseShape,
                    factor = 1f,
                    onValueChange = {
                        viewModel.updateShapes(
                            theme!!.copy(medium = it)
                        )
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ShapePreview(
                    previewShapes = previewShapes,
                ) {
                    FloatingActionButton(onClick = {}) {
                        Icon(painterResource(R.drawable.edit_24px), null)
                    }
                }
                ShapePreference(
                    title = "Large",
                    shape = theme!!.large,
                    baseShape = baseShape,
                    factor = 4f / 3f,
                    onValueChange = {
                        viewModel.updateShapes(
                            theme!!.copy(large = it)
                        )
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ShapePreference(
                    title = "Large increased",
                    shape = theme!!.largeIncreased,
                    baseShape = baseShape,
                    factor = 5f / 3f,
                    onValueChange = {
                        viewModel.updateShapes(
                            theme!!.copy(largeIncreased = it)
                        )
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ShapePreview(
                    previewShapes = previewShapes,
                ) {
                    Surface(
                        shape = BottomSheetDefaults.ExpandedShape,
                        color = BottomSheetDefaults.ContainerColor,
                        shadowElevation = BottomSheetDefaults.Elevation,
                        tonalElevation = BottomSheetDefaults.Elevation,
                        modifier = Modifier
                            .width(250.dp)
                            .height(144.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            BottomSheetDefaults.DragHandle()
                        }
                    }
                }
                ShapePreference(
                    title = "Extra large",
                    shape = theme!!.extraLarge,
                    baseShape = baseShape,
                    factor = 7f / 3f,
                    onValueChange = {
                        viewModel.updateShapes(
                            theme!!.copy(extraLarge = it)
                        )
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ShapePreference(
                    title = "Extra large increased",
                    shape = theme!!.extraLargeIncreased,
                    baseShape = baseShape,
                    factor = 8f / 3f,
                    onValueChange = {
                        viewModel.updateShapes(
                            theme!!.copy(extraLargeIncreased = it)
                        )
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ShapePreference(
                    title = "Extra extra large",
                    shape = theme!!.extraExtraLarge,
                    baseShape = baseShape,
                    factor = 12f / 3f,
                    onValueChange = {
                        viewModel.updateShapes(
                            theme!!.copy(extraExtraLarge = it)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ShapePreference(
    title: String,
    shape: ThemeShape?,
    baseShape: ThemeShape,
    factor: Float = 1f,
    onValueChange: (ThemeShape?) -> Unit,
    titleTextStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
) {
    var showDialog by remember { mutableStateOf(false) }

    val f = min(1f, factor)
    val topStart =
        (shape?.radii?.get(0)?.div(factor) ?: baseShape.radii?.get(0)?.toFloat() ?: 12f) * f
    val topEnd =
        (shape?.radii?.get(1)?.div(factor) ?: baseShape.radii?.get(1)?.toFloat() ?: 12f) * f
    val bottomEnd =
        (shape?.radii?.get(2)?.div(factor) ?: baseShape.radii?.get(2)?.toFloat() ?: 12f) * f
    val bottomStart =
        (shape?.radii?.get(3)?.div(factor) ?: baseShape.radii?.get(3)?.toFloat() ?: 12f) * f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                onClick = { showDialog = true },
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 20.dp)
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(min(48.dp, factor * 48.dp))
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        if ((shape?.corners ?: baseShape.corners
                            ?: CornerStyle.Rounded) == CornerStyle.Cut
                        ) {
                            CutCornerShape(
                                topStart = topStart.dp,
                                topEnd = topEnd.dp,
                                bottomEnd = bottomEnd.dp,
                                bottomStart = bottomStart.dp
                            )
                        } else {
                            RoundedCornerShape(
                                topStart = topStart.dp,
                                topEnd = topEnd.dp,
                                bottomEnd = bottomEnd.dp,
                                bottomStart = bottomStart.dp
                            )
                        }
                    )
            )
        }


        Text(
            title,
            style = titleTextStyle,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }

    if (showDialog) {
        val maxRadius = (24 * factor).toInt()

        val baseTopStart = ((baseShape.radii?.get(0) ?: 12) * factor).toInt()
        val baseTopEnd = ((baseShape.radii?.get(1) ?: 12) * factor).toInt()
        val baseBottomEnd = ((baseShape.radii?.get(2) ?: 12) * factor).toInt()
        val baseBottomStart = ((baseShape.radii?.get(3) ?: 12) * factor).toInt()


        var currentCornerStyle by remember(shape) { mutableStateOf(shape?.corners) }
        var currentTopStart by remember(shape) { mutableStateOf(shape?.radii?.get(0)) }
        var currentTopEnd by remember(shape) { mutableStateOf(shape?.radii?.get(1)) }
        var currentBottomEnd by remember(shape) { mutableStateOf(shape?.radii?.get(2)) }
        var currentBottomStart by remember(shape) { mutableStateOf(shape?.radii?.get(3)) }

        val actualCornerStyle = currentCornerStyle ?: baseShape.corners ?: CornerStyle.Rounded
        val actualTopStart = currentTopStart ?: baseTopStart
        val actualTopEnd = currentTopEnd ?: baseTopEnd
        val actualBottomEnd = currentBottomEnd ?: baseBottomEnd
        val actualBottomStart = currentBottomStart ?: baseBottomStart

        BottomSheetDialog(
            onDismissRequest = {
                showDialog = false
                onValueChange(
                    ThemeShape(
                        corners = currentCornerStyle,
                        radii = if (currentTopStart != null || currentTopEnd != null || currentBottomEnd != null || currentBottomStart != null) {
                            intArrayOf(
                                currentTopStart ?: baseTopStart,
                                currentTopEnd ?: baseTopEnd,
                                currentBottomEnd ?: baseBottomEnd,
                                currentBottomStart ?: baseBottomStart,
                            )
                        } else null
                    )
                )
            }) {

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val previewShape = if ((currentCornerStyle ?: baseShape.corners
                    ?: CornerStyle.Rounded) == CornerStyle.Rounded
                ) {
                    RoundedCornerShape(
                        topStart = (currentTopStart ?: baseTopStart).dp,
                        topEnd = (currentTopEnd ?: baseTopEnd).dp,
                        bottomEnd = (currentBottomEnd ?: baseBottomEnd).dp,
                        bottomStart = (currentBottomStart ?: baseBottomStart).dp

                    )
                } else {
                    CutCornerShape(
                        topStart = (currentTopStart ?: baseTopStart).dp,
                        topEnd = (currentTopEnd ?: baseTopEnd).dp,
                        bottomEnd = (currentBottomEnd ?: baseBottomEnd).dp,
                        bottomStart = (currentBottomStart ?: baseBottomStart).dp
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(max(96, maxRadius * 2).dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, previewShape)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            previewShape
                        )
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = actualCornerStyle == CornerStyle.Rounded,
                        onClick = {
                            currentCornerStyle = CornerStyle.Rounded
                        },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = {
                            SegmentedButtonDefaults.Icon(
                                active = actualCornerStyle == CornerStyle.Rounded,
                            ) {
                                Icon(
                                    painterResource(R.drawable.rounded_corner_alt_24px),
                                    null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.preference_cards_shape_rounded))
                    }
                    SegmentedButton(
                        selected = actualCornerStyle == CornerStyle.Cut,
                        onClick = {
                            currentCornerStyle = CornerStyle.Cut
                        },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = {
                            SegmentedButtonDefaults.Icon(
                                active = actualCornerStyle == CornerStyle.Cut,
                            ) {
                                Icon(
                                    painterResource(R.drawable.cut_corner_20px),
                                    null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.preference_cards_shape_cut))
                    }
                }


                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.rounded_corner_24px),
                        null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .rotate(-90f)
                    )
                    Slider(
                        modifier = Modifier
                            .weight(1f),
                        value = actualTopStart.toFloat(),
                        onValueChange = {
                            currentTopStart = it.toInt()
                        },
                        valueRange = 0f..maxRadius.toFloat(),
                        steps = maxRadius + 1
                    )
                    Text(
                        text = actualTopStart.toString(),
                        modifier = Modifier.width(32.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.rounded_corner_24px),
                        null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Slider(
                        modifier = Modifier
                            .weight(1f),
                        value = actualTopEnd.toFloat(),
                        onValueChange = {
                            currentTopEnd = it.toInt()
                        },
                        valueRange = 0f..maxRadius.toFloat(),
                        steps = maxRadius + 1,
                    )
                    Text(
                        text = actualTopEnd.toString(),
                        modifier = Modifier.width(32.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.rounded_corner_24px),
                        null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .rotate(90f)
                    )
                    Slider(
                        modifier = Modifier
                            .weight(1f),
                        value = actualBottomEnd.toFloat(),
                        onValueChange = {
                            currentBottomEnd = it.toInt()
                        },
                        valueRange = 0f..maxRadius.toFloat(),
                        steps = maxRadius + 1,
                    )
                    Text(
                        text = actualBottomEnd.toString(),
                        modifier = Modifier.width(32.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.rounded_corner_24px),
                        null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .rotate(180f)
                    )
                    Slider(
                        modifier = Modifier
                            .weight(1f),
                        value = actualBottomStart.toFloat(),
                        onValueChange = {
                            currentBottomStart = it.toInt()
                        },
                        valueRange = 0f..maxRadius.toFloat(),
                        steps = maxRadius + 1,
                    )
                    Text(
                        text = actualBottomStart.toString(),
                        modifier = Modifier.width(32.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )

                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp)
                )

                TextButton(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.End),
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                    onClick = {
                        currentTopStart = null
                        currentTopEnd = null
                        currentBottomEnd = null
                        currentBottomStart = null
                        currentCornerStyle = null
                        onValueChange(null)
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.restart_alt_20px), null,
                        modifier = Modifier
                            .padding(ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize)
                    )
                    Text(stringResource(R.string.preference_restore_default))
                }
            }
        }
    }
}

@Composable
private fun ShapePreview(
    previewShapes: Shapes,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MaterialTheme(
            shapes = previewShapes
        ) {
            content()
        }
    }
}