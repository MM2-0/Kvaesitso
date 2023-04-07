package de.mm20.launcher2.ui.launcher.sheets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.launcher.widgets.picker.PickAppWidgetActivity
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.ExternalWidget
import de.mm20.launcher2.widgets.FavoritesWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import kotlin.math.roundToInt

class BindAndConfigureAppWidgetActivity : Activity() {
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetHost = AppWidgetHost(this, 44203)
        appWidgetManager = AppWidgetManager.getInstance(this)

        val appWidgetProviderInfo = intent.getParcelableExtra<AppWidgetProviderInfo>(
            ExtraAppWidgetProviderInfo
        )
        if (appWidgetProviderInfo == null) {
            finish()
            return
        }

        val widgetId = appWidgetHost.allocateAppWidgetId()

        val canBind =
            appWidgetManager.bindAppWidgetIdIfAllowed(
                widgetId,
                appWidgetProviderInfo.profile,
                appWidgetProviderInfo.provider,
                null
            )

        if (canBind) {
            configureAppWidget(appWidgetProviderInfo, widgetId)
        } else {
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
                        appWidgetProviderInfo.provider
                    )
                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,
                        appWidgetProviderInfo.profile
                    )
                }, PickAppWidgetActivity.RequestCodeBind
            )
        }
    }

    private fun configureAppWidget(widget: AppWidgetProviderInfo, appWidgetId: Int) {
        if (widget.configure != null) {
            appWidgetHost.startAppWidgetConfigureActivityForResult(
                this,
                appWidgetId,
                0,
                PickAppWidgetActivity.RequestCodeConfigure,
                null
            )
        } else {
            finishWithResult(appWidgetId)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RequestCodeBind -> {
                val appWidgetId =
                    data?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: return
                if (resultCode == RESULT_OK) {
                    val widget = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    configureAppWidget(widget, appWidgetId)
                } else {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    cancel()
                }
            }

            RequestCodeConfigure -> {
                val appWidgetId =
                    data?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: return cancel()
                if (resultCode == RESULT_OK) {
                    finishWithResult(appWidgetId)
                } else {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    cancel()
                }
            }
        }
    }

    private fun finishWithResult(widgetId: Int) {
        val data = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        data.putExtra(ExtraAppWidgetProviderInfo, intent.getParcelableExtra<AppWidgetProviderInfo>(ExtraAppWidgetProviderInfo))
        setResult(RESULT_OK, data)
        finish()
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        const val RequestCodeConfigure = 1
        const val RequestCodeBind = 2
        const val ExtraAppWidgetProviderInfo = "extra_app_widget_provider_info"
    }
}

private class BindAndConfigureAppWidgetContract(
) : ActivityResultContract<AppWidgetProviderInfo, Widget?>() {
    override fun createIntent(context: Context, input: AppWidgetProviderInfo): Intent {
        return Intent(context, BindAndConfigureAppWidgetActivity::class.java).apply {
            putExtra(BindAndConfigureAppWidgetActivity.ExtraAppWidgetProviderInfo, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Widget? {
        if (resultCode == Activity.RESULT_OK) {
            val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
            val widgetProviderInfo = intent?.extras?.getParcelable<AppWidgetProviderInfo>(
                BindAndConfigureAppWidgetActivity.ExtraAppWidgetProviderInfo
            )

            if (widgetId != null && widgetProviderInfo != null) {
                return ExternalWidget(
                    height = widgetProviderInfo.minHeight,
                    widgetId = widgetId,
                    widgetProviderInfo = widgetProviderInfo,
                )
            }
        }
        return null
    }

}

@Composable
fun WidgetPickerSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val viewModel: WidgetPickerSheetVM = viewModel(factory = WidgetPickerSheetVM.Factory)

    val bindAppWidgetStarter =
        rememberLauncherForActivityResult(BindAndConfigureAppWidgetContract()) {
            if (it != null) {
                viewModel.pickWidget(it)
                onDismiss()
            }
        }


    val appWidgetGroups by viewModel.appWidgetGroups.collectAsState(emptyList())
    val expandAllGroups by viewModel.expandAllGroups.collectAsState(false)

    val colorSurface = MaterialTheme.colorScheme.surface

    val query by viewModel.searchQuery.collectAsState("")

    BottomSheetDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.widget_add_widget))
        }) {
        val builtIn by viewModel.builtInWidgets.collectAsState(emptyList())
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            stickyHeader {
                SearchBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.5f to colorSurface,
                                    0.5f to colorSurface.copy(alpha = 0f),
                                )
                            )
                        }
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    query = query,
                    onQueryChange = { viewModel.search(it) },
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = {
                        Text(stringResource(R.string.search_bar_placeholder))
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.search("") }) {
                                Icon(Icons.Rounded.Clear, null)
                            }
                        }
                    }
                ) {
                }
            }
            items(builtIn) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    onClick = {
                        viewModel.pickWidget(it)
                        onDismiss()
                    }) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (it) {
                                is WeatherWidget -> Icons.Rounded.LightMode
                                is CalendarWidget -> Icons.Rounded.Today
                                is MusicWidget -> Icons.Rounded.MusicNote
                                is FavoritesWidget -> Icons.Rounded.Star
                                else -> Icons.Rounded.Widgets
                            },
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Text(
                            text = it.loadLabel(context),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
            for (group in appWidgetGroups) {
                val expanded = viewModel.expandedGroup.value == group.packageName || expandAllGroups
                item(
                    key = group.packageName,
                ) {
                    val background by animateColorAsState(
                        if (expanded) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent,
                        label = "background"
                    )
                    val textColor by animateColorAsState(
                        if (expanded) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        label = "textColor"
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(background)
                            .clickable(enabled = !expandAllGroups) {
                                viewModel.toggleGroup(group.packageName)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .animateItemPlacement(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = group.appName,
                            color = textColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val rotate by animateFloatAsState(
                            if (expanded) 180f else 0f, label = "expandIcon"
                        )
                        if (!expandAllGroups) {
                            Icon(
                                modifier = Modifier.rotate(rotate),
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                }
                if (expanded) {
                    items(
                        group.widgets,
                        key = { it }
                    ) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .animateItemPlacement(),
                            onClick = {
                                bindAppWidgetStarter.launch(it)
                            }) {
                            val previewImage = remember(it.provider) {
                                it.loadPreviewImage(context, (160f * density.density).roundToInt())
                            }
                            val icon = remember(it.provider) {
                                it.loadIcon(context, (160f * density.density).roundToInt())
                            }
                            Column {
                                if (previewImage != null) {
                                    AsyncImage(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(it.minHeight.dp.coerceIn(60.dp, 200.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(16.dp),
                                        model = previewImage, contentDescription = null
                                    )
                                }

                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 16.dp)
                                            .size(24.dp)
                                    )
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = it.loadLabel(context.packageManager),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (it.profile != Process.myUserHandle()) {
                                        Icon(
                                            modifier = Modifier
                                                .padding(start = 16.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                .padding(4.dp),
                                            imageVector = Icons.Rounded.Work,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}