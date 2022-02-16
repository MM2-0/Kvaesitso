package de.mm20.launcher2.ui.launcher.widgets.picker

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.MdcLauncherTheme
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.component.ProvideIconShape
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject

class PickAppWidgetActivity : BaseActivity() {

    private val dataStore: LauncherDataStore by inject()
    private val viewModel by viewModels<PickAppWidgetVM>()

    private lateinit var widgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetHost = AppWidgetHost(this, 44203)
        appWidgetManager = AppWidgetManager.getInstance(this)

        val availableWidgets = viewModel.getAvailableWidgets(this)
        val selectedAppWidget = viewModel.selectedAppWidget
        setContent {
            MdcLauncherTheme {
                val cardStyle by remember {
                    dataStore.data.map { it.cards }.distinctUntilChanged()
                }.collectAsState(
                    Settings.CardSettings.getDefaultInstance()
                )
                val iconShape by remember {
                    dataStore.data.map {
                        if (it.easterEgg) Settings.IconSettings.IconShape.EasterEgg
                        else it.icons.shape
                    }.distinctUntilChanged()
                }.collectAsState(Settings.IconSettings.IconShape.Circle)

                val favoritesEnabled by remember {
                    dataStore.data.map { it.favorites.enabled }.distinctUntilChanged()
                }.collectAsState(true)

                CompositionLocalProvider(
                    LocalCardStyle provides cardStyle,
                    LocalFavoritesEnabled provides favoritesEnabled
                ) {
                    ProvideIconShape(iconShape) {
                        val available by availableWidgets.observeAsState()
                        val selected by selectedAppWidget.observeAsState()
                        val widgets = available
                        if (selected == null) {
                            if (widgets != null) {
                                AppWidgetList(
                                    modifier = Modifier.fillMaxSize(),
                                    widgets = widgets,
                                    onWidgetSelected = {
                                        selectAppWidget(it)
                                    }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun selectAppWidget(widget: AppWidgetProviderInfo) {
        val appWidgetId = widgetHost.allocateAppWidgetId()
        viewModel.selectAppWidget(widget, appWidgetId)
        configureWidget()
    }

    private fun configureWidget() {
        val appWidgetId = viewModel.appWidgetId.value ?: return
        val widget = viewModel.selectedAppWidget.value ?: return
        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, widget.provider)
        Log.d("MM20", "Can bind: $canBind")
        if (canBind) {
            if (widget.configure != null) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                intent.component = widget.configure
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                widgetHost.startAppWidgetConfigureActivityForResult(
                    this,
                    appWidgetId,
                    0,
                    RequestCodeConfigure,
                    null
                )
            } else {
                finishWithResult(appWidgetId)
            }
        } else {
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, widget.provider)
            }, RequestCodeBind)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            RequestCodeBind -> {
                if (resultCode == RESULT_OK) {
                    configureWidget()
                } else {
                    viewModel.appWidgetId.value?.let { widgetHost.deleteAppWidgetId(it) }
                    cancel()
                }
            }
            RequestCodeConfigure -> {
                val widgetId = viewModel.appWidgetId.value ?: return cancel()
                if (resultCode == RESULT_OK) {
                    finishWithResult(widgetId)
                } else {
                    widgetHost.deleteAppWidgetId(widgetId)
                    cancel()
                }
            }
        }
    }

    private fun finishWithResult(widgetId: Int) {
        val data = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
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
    }
}