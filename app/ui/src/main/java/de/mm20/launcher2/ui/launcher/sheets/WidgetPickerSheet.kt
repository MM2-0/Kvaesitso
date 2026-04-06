package de.mm20.launcher2.ui.launcher.sheets

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.ktx.animateShapeAsState
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.AppWidgetConfig
import de.mm20.launcher2.widgets.AppsWidget
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import java.util.UUID
import kotlin.math.roundToInt

class BindAndConfigureAppWidgetActivity : Activity() {
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager

    private var appWidgetId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetHost = AppWidgetHost(this, 44203)
        appWidgetManager = AppWidgetManager.getInstance(this)

        val appWidgetProviderInfo = intent.getParcelableExtra<AppWidgetProviderInfo>(
            ExtraAppWidgetProviderInfo
        )
        if (appWidgetProviderInfo == null) {
            Log.e("MM20", "No app widget provider info provided, canceling")
            finish()
            return
        }

        val widgetId = appWidgetHost.allocateAppWidgetId().also {
            appWidgetId = it
        }

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
                }, RequestCodeBind
            )
        }
    }

    private fun configureAppWidget(widget: AppWidgetProviderInfo, appWidgetId: Int) {
        if (widget.configure != null) {
            appWidgetHost.startAppWidgetConfigureActivityForResult(
                this,
                appWidgetId,
                0,
                RequestCodeConfigure,
                getConfigurationOptions(),
            )
        } else {
            finishWithResult(appWidgetId)
        }
    }

    private fun getConfigurationOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < 34) {
            return null
        }
        return ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            .toBundle()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RequestCodeBind -> {
                val appWidgetId = appWidgetId
                if (appWidgetId == null) {
                    Log.e("MM20", "No app widget id provided, canceling")
                    cancel()
                    return
                }
                if (resultCode == RESULT_OK) {
                    val widget = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    configureAppWidget(widget, appWidgetId)
                } else {
                    Log.w("MM20", "Widget binding was canceled, widget will not be added")
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    cancel()
                }
            }

            RequestCodeConfigure -> {
                val appWidgetId = appWidgetId
                if (appWidgetId == null) {
                    Log.e("MM20", "No app widget id provided, canceling")
                    cancel()
                    return
                }
                if (resultCode == RESULT_OK) {
                    finishWithResult(appWidgetId)
                } else {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    Log.w("MM20", "Widget configuration was canceled, widget will not be added")
                    cancel()
                }
            }

            else -> {
                Log.w("MM20", "Unknown request code $requestCode")
                cancel()
            }
        }
    }

    private fun finishWithResult(widgetId: Int) {
        val data = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        data.putExtra(
            ExtraAppWidgetProviderInfo,
            intent.getParcelableExtra<AppWidgetProviderInfo>(ExtraAppWidgetProviderInfo)
        )
        setResult(RESULT_OK, data)
        appWidgetId = null
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
    private val density: Density,
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
                return AppWidget(
                    id = UUID.randomUUID(),
                    config = AppWidgetConfig(
                        height = with(density) { widgetProviderInfo.minHeight.toDp() }.value.toInt(),
                        width = with(density) { widgetProviderInfo.minWidth.toDp() }.value.toInt(),
                        widgetId = widgetId,
                    ),
                )
            } else {
                Log.e(
                    "MM20",
                    "Could not parse widget result: widgetId=$widgetId, widgetProviderInfo=$widgetProviderInfo"
                )
            }
        } else {
            Log.e("MM20", "Widget result was not OK")
        }
        return null
    }

}

@Composable
fun WidgetPickerSheet(
    expanded: Boolean,
    includeBuiltinWidgets: Boolean = true,
    title: String = stringResource(R.string.widget_pick_widget),
    onWidgetSelected: (Widget) -> Unit,
    onDismiss: () -> Unit
) {

    DismissableBottomSheet(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val viewModel: WidgetPickerSheetVM = viewModel(factory = WidgetPickerSheetVM.Factory)

        val bindAppWidgetStarter =
            rememberLauncherForActivityResult(BindAndConfigureAppWidgetContract(density)) {
                if (it != null) {
                    onWidgetSelected(it)
                    onDismiss()
                }
            }

        val xs = MaterialTheme.shapes.extraSmall
        val md = MaterialTheme.shapes.medium

        val topShape = md.copy(
            bottomStart = xs.bottomStart,
            bottomEnd = xs.bottomEnd,
        )
        val bottomShape = md.copy(
            topStart = xs.topStart,
            topEnd = xs.topEnd,
        )
        val middleShape = xs
        val singleShape = md


        val appWidgetGroups by viewModel.appWidgetGroups.collectAsState(emptyList())
        val expandAllGroups by viewModel.expandAllGroups.collectAsState(false)

        val colorSurface = MaterialTheme.colorScheme.surfaceContainerLow

        val query by viewModel.searchQuery.collectAsState("")

        val builtIn by viewModel.builtInWidgets.collectAsState(emptyList())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            stickyHeader {
                DockedSearchBar(
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
                        .padding(bottom = 16.dp),
                    expanded = false,
                    onExpandedChange = {},
                    inputField = {
                        SearchBarDefaults.InputField(
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.search_24px),
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(
                                        modifier = Modifier.offset(16.dp),
                                        onClick = { viewModel.search("") }
                                    ) {
                                        Icon(painterResource(R.drawable.close_24px), null)
                                    }
                                }
                            },
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            query = query,
                            onQueryChange = {
                                viewModel.search(it)
                            },
                            placeholder = {
                                Text(stringResource(R.string.search_bar_placeholder))
                            },
                        )
                    }
                ) {
                }
            }
            if (includeBuiltinWidgets) {
                itemsIndexed(builtIn) { i, it ->
                    val shape = when {
                        builtIn.size == 1 -> singleShape
                        i == 0 -> topShape
                        i == builtIn.lastIndex -> bottomShape
                        else -> middleShape
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                MaterialTheme.colorScheme.surfaceBright,
                            )
                            .clickable {
                                val id = UUID.randomUUID()
                                val widget = when (it.type) {
                                    WeatherWidget.Type -> WeatherWidget(id)
                                    CalendarWidget.Type -> CalendarWidget(id)
                                    MusicWidget.Type -> MusicWidget(id)
                                    AppsWidget.Type -> AppsWidget(id)
                                    NotesWidget.Type -> NotesWidget(id)
                                    else -> return@clickable
                                }
                                onWidgetSelected(widget)
                                onDismiss()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    when (it.type) {
                                        WeatherWidget.Type -> R.drawable.light_mode_24px
                                        CalendarWidget.Type -> R.drawable.today_24px
                                        MusicWidget.Type -> R.drawable.music_note_24px
                                        AppsWidget.Type -> R.drawable.apps_24px
                                        NotesWidget.Type -> R.drawable.sticky_note_2_24px
                                        else -> R.drawable.widgets_24px
                                    }
                                ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = it.label,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            for (group in appWidgetGroups) {
                val expanded = viewModel.expandedGroup.value == group.packageName || expandAllGroups
                item(
                    key = group.packageName,
                ) {
                    val shape by animateShapeAsState(
                        if (expanded && group.widgets.isNotEmpty()) topShape else singleShape
                    )
                    val icon = remember(group.packageName) {
                        try {
                            context.packageManager.getApplicationIcon(group.packageName)
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                    Row(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceBright)
                            .clickable(enabled = !expandAllGroups) {
                                viewModel.toggleGroup(group.packageName)
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(icon).crossfade(false).build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp),

                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = group.appName,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val rotate by animateFloatAsState(
                            if (expanded) 180f else 0f, label = "expandIcon"
                        )
                        if (!expandAllGroups) {
                            Icon(
                                modifier = Modifier.rotate(rotate),
                                painter = painterResource(R.drawable.keyboard_arrow_down_24px),
                                contentDescription = null
                            )
                        }
                    }
                }
                if (expanded) {
                    itemsIndexed(
                        group.widgets,
                        key = { _, it -> it }
                    ) { i, it ->
                        val shape = if (i == group.widgets.lastIndex) bottomShape else middleShape
                        val previewImage = remember(it.provider) {
                            it.loadPreviewImage(context, (160f * density.density).roundToInt())
                        }
                        val icon = remember(it.provider) {
                            it.loadIcon(context, (160f * density.density).roundToInt())
                        }
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceBright)
                                .clickable {
                                    bindAppWidgetStarter.launch(it)
                                }
                        ) {
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
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = it.loadLabel(context.packageManager),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isAtLeastApiLevel(31)) {
                                        val description = it.loadDescription(context)
                                        if (!description.isNullOrBlank()) {
                                            Text(description.toString(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (it.profile != Process.myUserHandle()) {
                                    Icon(
                                        modifier = Modifier
                                            .padding(start = 16.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                                            .padding(4.dp),
                                        painter = painterResource(R.drawable.enterprise_24px),
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