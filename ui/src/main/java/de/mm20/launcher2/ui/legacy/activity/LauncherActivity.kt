package de.mm20.launcher2.ui.legacy.activity

import android.Manifest
import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewGroupOverlay
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.list.listItems
import com.jmedeisis.draglinearlayout.DragLinearLayout
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.icons.DynamicIconController
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.isBrightColor
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.SearchViewModel
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.transition.OneShotLayoutTransition
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.component.EditFavoritesView
import de.mm20.launcher2.ui.legacy.component.WidgetView
import de.mm20.launcher2.ui.legacy.helper.WallpaperBlur
import de.mm20.launcher2.ui.legacy.search.SearchGridView
import de.mm20.launcher2.ui.legacy.widget.LauncherWidget
import de.mm20.launcher2.weather.WeatherViewModel
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetType
import de.mm20.launcher2.widgets.WidgetViewModel
import kotlinx.android.synthetic.main.activity_launcher.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.*
import kotlin.math.roundToInt


class LauncherActivity : AppCompatActivity() {

    /**
     * True if the search result list is visible
     */
    private var searchVisibility = false

    private lateinit var widgetHost: AppWidgetHost
    private val widgets = mutableListOf<Widget>()

    private lateinit var overlayView: ViewGroupOverlay

    private val searchViewModel: SearchViewModel by viewModel()
    private val widgetViewModel: WidgetViewModel by viewModel()
    private val favoritesViewModel: FavoritesViewModel by viewModel()

    private val preferences = LauncherPreferences.instance

    private var widgetEditMode = false
        set(value) {
            field = value
            if (value) {
                widgetSpacer.visibility = View.GONE
                smartWidget.visibility = View.GONE
                searchBar.setRightIcon(R.drawable.ic_done)
                scrollView.setOnTouchListener(null)
                for (v in widgetList.iterator()) {
                    if (v is WidgetView) {
                        v.editMode = true
                        v.onResizeModeChange = {
                            OneShotLayoutTransition.run(widgetList)
                            OneShotLayoutTransition.run(widgetContainer)
                        }
                    }
                }
                OneShotLayoutTransition.run(widgetList)
                OneShotLayoutTransition.run(widgetContainer)
                OneShotLayoutTransition.run(scrollContainer)
                fabEditWidget.apply {
                    setIconResource(R.drawable.ic_add)
                    setText(R.string.widget_add_widget)
                    setOnClickListener {
                        addWidget()
                    }
                }
                val statusBarColor = TypedValue().also {
                    theme.resolveAttribute(
                        R.attr.colorSurface,
                        it,
                        true
                    )
                }.data
                window.statusBarColor = statusBarColor
                if (statusBarColor.isBrightColor()) {
                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = true
                }
                searchBar.visibility = View.INVISIBLE
                editWidgetToolbar
                    .animate()
                    .translationY(0f)
                    .alpha(1f)
                    .withStartAction {
                        editWidgetToolbar.visibility = View.VISIBLE
                    }
                    .start()
            } else {
                widgetViewModel.saveWidgets(widgets)
                widgetSpacer.visibility = View.VISIBLE
                widgetList.layoutTransition = ChangingLayoutTransition()
                widgetContainer.layoutTransition = ChangingLayoutTransition()
                scrollContainer.layoutTransition = ChangingLayoutTransition()
                searchBar.setRightIcon(R.drawable.ic_more_vert)
                scrollView.setOnTouchListener(scrollViewOnTouchListener)
                smartWidget.visibility = View.VISIBLE
                for (v in widgetList.iterator()) {
                    if (v is WidgetView) {
                        v.editMode = false
                        v.layoutTransition = ChangingLayoutTransition()
                    }
                }
                fabEditWidget.apply {
                    setIconResource(R.drawable.ic_edit)
                    setText(R.string.menu_edit_widgets)
                    setOnClickListener {
                        widgetEditMode = true
                    }
                }
                window.statusBarColor = Color.TRANSPARENT

                updateSystemBarAppearance()

                searchBar.visibility = View.VISIBLE
                editWidgetToolbar
                    .animate()
                    .translationY(-editWidgetToolbar.height.toFloat())
                    .alpha(0f)
                    .withEndAction {
                        editWidgetToolbar.visibility = View.GONE
                    }
                    .start()
            }
        }

    private fun updateSystemBarAppearance() {
        val allowLightSystemBars = allowsLightSystemBars()
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightNavigationBars =
            allowLightSystemBars && preferences.lightNavBar
        insetsController.isAppearanceLightStatusBars =
            allowLightSystemBars && preferences.lightStatusBar
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LauncherPreferences.instance.firstRunVersion < 1) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), PermissionsManager.ALL
            )
            LauncherPreferences.instance.firstRunVersion = 1
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_launcher)


        overlayView = rootView.overlay



        scrollContainer.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        searchContainer.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        widgetContainer.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        val params = widgetSpacer.layoutParams as LinearLayout.LayoutParams
        params.topMargin = Point().also { windowManager.defaultDisplay.getSize(it) }.y
        widgetSpacer.layoutParams = params
        container.doOnNextLayout {
            adjustWidgetSpace()
        }
        initWidgets()
        if (preferences.blurCards && preferences.cardOpacity < 0xFF) {
            container.viewTreeObserver.addOnPreDrawListener {
                blurView.invalidate()
                true
            }
        }
        scrollView.setOnTouchListener(scrollViewOnTouchListener)
        scrollView.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, oldScrollY: Int ->
            when {
                /* Hide searchbar*/
                scrollY > oldScrollY && ((searchVisibility && scrollY > searchBar.height) || widgetEditMode ||
                        scrollY > widgetSpacer.height + searchBar.height
                        + (widgetSpacer.layoutParams as LinearLayout.LayoutParams).topMargin) -> {
                    var newTransY = searchBar.translationY - scrollY + oldScrollY
                    if (newTransY < -searchBar.height.toFloat() * 1.5f) {
                        newTransY = -searchBar.height.toFloat() * 1.5f
                    }
                    searchBar.translationY = newTransY
                }
                /* Show searchbar*/
                scrollY < oldScrollY -> {
                    var newTransY = searchBar.translationY - scrollY + oldScrollY
                    if (newTransY > 0f) {
                        newTransY = 0f
                    }
                    searchBar.translationY = newTransY
                }
            }
            if (scrollY > 0 && (searchVisibility || widgetEditMode ||
                        scrollY > widgetSpacer.height
                        + (widgetSpacer.layoutParams as LinearLayout.LayoutParams).topMargin)
            ) {
                searchBar.raise()
            } else searchBar.drop()
            if (scrollY == 0) {
                smartWidget.translucent = true
                if (!searchVisibility) searchBar.hide()
            } else {
                smartWidget.translucent = false
                searchBar.show()
            }

        }

        searchBar.onRightIconClick = onRightIconClick@{
            if (widgetEditMode) widgetEditMode = false
            else {
                val menu = PopupMenu(this, it)
                menu.inflate(R.menu.menu_launcher)
                menu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_item_settings -> {
                            finish()
                            startActivity(Intent().also {
                                it.component = ComponentName(packageName, "de.mm20.launcher2.activity.SettingsActivity")
                                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                        R.id.menu_item_wallpaper -> {
                            startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SET_WALLPAPER),
                                    null
                                )
                            )
                        }
                        R.id.menu_item_hidden -> {
                            val layout = NestedScrollView(this)
                            layout.clipChildren = false
                            layout.layoutParams = ViewGroup.LayoutParams(
                                MATCH_PARENT,
                                WRAP_CONTENT
                            )
                            val hiddenItemsGrid = SearchGridView(this)
                            hiddenItemsGrid.layoutParams = FrameLayout.LayoutParams(
                                MATCH_PARENT,
                                WRAP_CONTENT
                            ).apply {
                                setMargins((8 * dp).toInt())
                            }
                            hiddenItemsGrid.columnCount =
                                resources.getInteger(R.integer.config_columnCount)
                            val hiddenItems = favoritesViewModel.hiddenItems
                            hiddenItems.observe(this) {
                                hiddenItemsGrid.submitItems(it)
                            }
                            layout.addView(hiddenItemsGrid)
                            MaterialDialog(this, BottomSheet(LayoutMode.MATCH_PARENT))
                                .show {
                                    title(R.string.menu_hidden_items)
                                    customView(view = layout)
                                    negativeButton(R.string.close) { dismiss() }
                                }
                            //hiddenAppsActivated = true
                        }
                        R.id.menu_item_edit_favs -> {
                            val view = EditFavoritesView(this@LauncherActivity)
                            MaterialDialog(this, BottomSheet(LayoutMode.MATCH_PARENT)).show {
                                customView(view = view)
                                title(res = R.string.menu_item_edit_favs)
                                positiveButton(res = R.string.close) {
                                    it.dismiss()
                                }
                                onDismiss {
                                    view.save()
                                }
                            }
                        }
                    }
                    true
                }
                menu.show()
            }
        }
        searchBar.setOnTouchListener { _, _ ->
            if (!searchVisibility) showSearch()
            false
        }

        searchBar.onSearchQueryChanged = {
            search(it)
        }
        widgetList.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        fabEditWidget.setOnClickListener {
            widgetEditMode = true
        }

        editWidgetToolbar.apply {
            navigationIcon =
                ContextCompat.getDrawable(this@LauncherActivity, R.drawable.ic_done)?.apply {
                    setTint(ContextCompat.getColor(this@LauncherActivity, R.color.icon_color))
                }
            setNavigationOnClickListener {
                widgetEditMode = false
            }
        }

        val dynamicIconController: DynamicIconController by inject()

        lifecycle.addObserver(dynamicIconController)

        lifecycleScope.launch {
            widgets.addAll(widgetViewModel.getWidgets())
            initWidgets()
        }
    }


    private fun initWidgets() {
        widgetHost = AppWidgetHost(applicationContext, 0xacab)
        widgetList.removeAllViews()
        val params = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        params.topMargin = (8 * dp).roundToInt()
        for (w in widgets) {
            val view = WidgetView(this)
            view.layoutTransition = ChangingLayoutTransition()
            view.layoutParams = params
            if (view.setWidget(w, widgetHost)) {
                widgetList.addDragView(view, view.getDragHandle())
                view.onRemove = {
                    OneShotLayoutTransition.run(widgetList)
                    OneShotLayoutTransition.run(widgetContainer)
                    widgetList.removeDragView(view)
                    removeWidget(view.widget)
                }
                view.onResizeModeChange = {
                    OneShotLayoutTransition.run(widgetList)
                    OneShotLayoutTransition.run(widgetContainer)
                }
            }
        }

        widgetList.setOnViewSwapListener { _, firstPosition, _, secondPosition ->
            Collections.swap(widgets, firstPosition, secondPosition)
        }
        updateWidgets()
    }

    private fun addWidget() {
        val usedWidgets = widgets.filter { it.type == WidgetType.INTERNAL }.map { it.data }
        val internalWidgets =
            widgetViewModel.getInternalWidgets().filter { !usedWidgets.contains(it.data) }
        if (internalWidgets.isNotEmpty()) {
            MaterialDialog(this).show {
                val widgetList =
                    this@LauncherActivity.findViewById<DragLinearLayout>(R.id.widgetList)
                val widgetContainer =
                    this@LauncherActivity.findViewById<LinearLayout>(R.id.widgetContainer)
                title(R.string.widget_add_widget)
                listItems(items = internalWidgets.map { it.label }) { dialog, index, _ ->
                    val widget = internalWidgets[index]
                    val view = WidgetView(this@LauncherActivity)
                    val params = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    params.topMargin = (8 * dp).roundToInt()
                    view.layoutParams = params
                    if (view.setWidget(widget, widgetHost)) {
                        view.editMode = true
                        widgetList.addDragView(view, view.getDragHandle())
                        view.onRemove = {
                            OneShotLayoutTransition.run(widgetList)
                            OneShotLayoutTransition.run(widgetContainer)
                            widgetList.removeDragView(view)
                            removeWidget(view.widget)
                        }
                        view.onResizeModeChange = {
                            OneShotLayoutTransition.run(widgetList)
                            OneShotLayoutTransition.run(widgetContainer)
                        }
                        widgets.add(widget)
                    }
                    dialog.dismiss()
                }
                @Suppress("DEPRECATION") // I don't care that neutral buttons are discouraged.
                neutralButton(R.string.widget_add_external) {
                    val appWidgetId = widgetHost.allocateAppWidgetId()
                    val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                    pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET)
                    it.dismiss()
                }
            }
        } else {
            val appWidgetId = widgetHost.allocateAppWidgetId()
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET)
        }
    }

    private fun removeWidget(widget: Widget?) {
        widget ?: return
        widgets.remove(widget)
        val id = widget.data.toIntOrNull() ?: return
        widgetHost.deleteAppWidgetId(id)
    }

    private fun adjustWidgetSpace() {
        val firstWidget = smartWidget
        if (firstWidget == null) {
            val m = scrollContainer.paddingTop
            val params = widgetSpacer.layoutParams as LinearLayout.LayoutParams
            params.topMargin =
                scrollView.height - m - widgetContainer.paddingTop - widgetSpacer.height
            widgetSpacer.layoutParams = params
            return
        }
        val m = scrollContainer.paddingTop +
                (firstWidget.layoutParams as LinearLayout.LayoutParams).run { topMargin + bottomMargin }
        val params = widgetSpacer.layoutParams as LinearLayout.LayoutParams
        params.topMargin =
            scrollView.height - firstWidget.measuredHeight - m - widgetContainer.paddingTop - widgetSpacer.height
        widgetSpacer.layoutParams = params
    }


    private fun search(text: String) {
        searchViewModel.search(text)
        if (webSearchViewSpacer.tag != "measured" || webSearchViewSpacer.height == 0) {
            val webSearchView = searchBar.getWebSearchView()
            webSearchView.doOnNextLayout {
                webSearchViewSpacer.layoutParams = webSearchViewSpacer.layoutParams
                    .apply { height = webSearchView.height }
                webSearchViewSpacer.tag = "measured"
            }
        }
        webSearchViewSpacer.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun toggleSearch() {
        if (searchVisibility) {
            hideSearch()
        } else {
            showSearch()
        }
    }

    private fun hideSearch() {

        searchVisibility = false
        val set = AnimatorSet()
        set.duration = 300
        set.doOnEnd {
            searchContainer.visibility = View.GONE
            widgetContainer.visibility = View.VISIBLE
        }
        set.playTogether(
            ObjectAnimator.ofFloat(widgetContainer, "translationY", 0f),
            ObjectAnimator.ofInt(scrollView, "scrollY", 0),
            ObjectAnimator.ofFloat(
                searchContainer, "translationY", 0f,
                if (scrollView.scrollY > searchContainer.height / 2f) -searchContainer.height.toFloat() else scrollView.height.toFloat()
            )
        )
        set.start()
        scrollView.scrollTo(0, 0)
        searchBar.hide()
        if (!searchBar.getSearchQuery().isEmpty()) searchBar.setSearchQuery("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchBar.windowToken, 0)
    }

    private fun showSearch() {

        searchVisibility = true
        searchBar.show()
        searchContainer.visibility = View.VISIBLE
        widgetContainer.visibility = View.GONE
        val set = AnimatorSet()
        set.duration = 300
        set.doOnEnd {
            search("")
        }
        set.playTogether(
            ObjectAnimator.ofFloat(widgetContainer, "translationY", scrollView.height.toFloat()),
            ObjectAnimator.ofInt(scrollView, "scrollY", 0),
            ObjectAnimator.ofFloat(searchContainer, "translationY", scrollView.height.toFloat(), 0f)
        )
        set.start()
    }

    override fun onBackPressed() {
        if (widgetEditMode) widgetEditMode = false
        if (searchVisibility) hideSearch()
        else ObjectAnimator.ofInt(scrollView, "scrollY", 0).setDuration(200).start()


    }

    override fun onResume() {
        super.onResume()
        ActivityStarter.resume()
        ActivityStarter.create(rootView)
        activityStartOverlay.visibility = View.INVISIBLE

        val widgetViewModel by viewModels<WidgetViewModel>()
        widgetViewModel.requestCalendarUpdate()
        search(searchBar.getSearchQuery())
        updateWidgets()

        updateSystemBarAppearance()

        container.doOnNextLayout {
            WallpaperManager.getInstance(this).setWallpaperOffsets(it.windowToken, 0.5f, 0.5f)
        }

        if (!LauncherPreferences.instance.hasRequestedNotificationPermission && !hasNotificationListenerPermission()) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    R.string.notification_permission_activity_not_found,
                    Toast.LENGTH_LONG
                ).show()
            }
            LauncherPreferences.instance.hasRequestedNotificationPermission = true
        }


        //getSystemService(Context.INPUT_METHOD_SERVICE)
        //        .castTo<InputMethodManager>()
        //        .hideSoftInputFromWindow(currentFocus?.windowToken, 0)

        //overridePendingTransition(R.anim.app_to_launcher_in, R.anim.app_to_launcher_out)
    }

    private fun allowsLightSystemBars(): Boolean {
        val dimWallpaper = LauncherPreferences.instance.dimWallpaper
        val isDarkTheme = resources.getBoolean(R.bool.is_dark_theme)
        return !(isDarkTheme && dimWallpaper)
    }

    private fun hasNotificationListenerPermission(): Boolean {
        val listeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        for (listener in listeners) {
            if (listener == packageName) return true
        }
        return false
    }

    private val themeListener = { key: String ->
        recreate()
    }

    override fun onStart() {
        super.onStart()
        if (preferences.blurCards && preferences.cardOpacity < 0xFF) {
            lifecycleScope.launch {
                val wallpaper = withContext(Dispatchers.IO) {
                    WallpaperBlur.getCachedBitmap(this@LauncherActivity)
                }
                WallpaperBlur.blurredWallpaper = wallpaper
            }
        }
        preferences.doOnPreferenceChange("is_light_wallpaper", action = themeListener)
        widgetHost.startListening()
    }

    override fun onPause() {
        super.onPause()
        ActivityStarter.pause()
    }

    override fun onStop() {
        super.onStop()
        WallpaperBlur.blurredWallpaper?.takeIf { !it.isRecycled }?.recycle()
        WallpaperBlur.blurredWallpaper = null
        try {
            widgetHost.stopListening()
        } catch (e: NullPointerException) {
            Log.e("MM20", Log.getStackTraceString(e))
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        onBackPressed()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        search(searchBar.getSearchQuery())
        when (requestCode) {
            PermissionsManager.LOCATION -> {
                ViewModelProvider(this).get(WeatherViewModel::class.java).requestUpdate(this)
            }
            PermissionsManager.CALENDAR -> {
                widgetViewModel.requestCalendarUpdate()
            }
            PermissionsManager.ALL -> {
                ViewModelProvider(this).get(WeatherViewModel::class.java).requestUpdate(this)
                widgetViewModel.requestCalendarUpdate()
                search(searchBar.getSearchQuery())
            }
        }
    }

    private fun updateWidgets() {
        var topWidget: LauncherWidget? = null
        var topWidgetRanking = 0
        var topWidgetView: WidgetView? = null
        for (widget in widgetList.iterator()) {
            if (widget is WidgetView) {
                widget.update()
                if (topWidgetRanking < widget.widgetView?.compactViewRanking ?: 0) {
                    topWidget = widget.widgetView
                    topWidgetRanking = widget.widgetView?.compactViewRanking ?: 0
                    topWidgetView = widget
                }
            }
        }
        val compactView = topWidget?.compactView
        compactView?.update()
        compactView?.goToParent = {
            ObjectAnimator.ofFloat(
                scrollView, "scrollY", topWidgetView?.top?.toFloat()
                    ?: 0f
            ).start()
        }
        smartWidget.compactView = compactView
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return
        if (requestCode == REQUEST_PICK_APPWIDGET) {
            val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (widgetId == -1) return
            if (resultCode == Activity.RESULT_OK) {
                val appWidget = AppWidgetManager.getInstance(applicationContext)
                    .getAppWidgetInfo(widgetId) ?: return
                if (appWidget.configure != null) {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    intent.component = appWidget.configure
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    startActivityForResult(intent, REQUEST_BIND_APPWIDGET)
                } else {
                    onActivityResult(REQUEST_BIND_APPWIDGET, Activity.RESULT_OK, data)
                }
            } else {
                widgetHost.deleteAppWidgetId(widgetId)
            }
        }
        if (requestCode == REQUEST_CREATE_APPWIDGET) {
            val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (widgetId == -1) return
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            startActivityForResult(intent, REQUEST_BIND_APPWIDGET)
        }
        if (requestCode == REQUEST_BIND_APPWIDGET && resultCode == Activity.RESULT_OK) {
            val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (widgetId == -1) return
            val appWidget = AppWidgetManager.getInstance(applicationContext)
                .getAppWidgetInfo(widgetId) ?: return
            val widget = Widget(
                type = WidgetType.THIRD_PARTY,
                data = widgetId.toString(),
                height = appWidget.minHeight
            )
            val params = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            params.topMargin = (8 * dp).roundToInt()
            val view = WidgetView(this)
            view.layoutParams = params
            if (view.setWidget(widget, widgetHost)) {
                view.editMode = true

                widgetList.addDragView(view, view.getDragHandle())
                view.onRemove = {
                    OneShotLayoutTransition.run(widgetList)
                    OneShotLayoutTransition.run(widgetContainer)
                    widgetList.removeDragView(view)
                    removeWidget(view.widget)
                }
                view.onResizeModeChange = {
                    OneShotLayoutTransition.run(widgetList)
                    OneShotLayoutTransition.run(widgetContainer)
                }
                widgets.add(widget)
            }
        }
    }

    private val scrollViewOnTouchListener: (View, MotionEvent) -> Boolean = onTouch@{ _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> true
            MotionEvent.ACTION_MOVE -> {
                when {
                    scrollView.scrollY == 0 -> {
                        if (container.translationY >= searchBar.height) {
                            return@onTouch false
                        }
                        if (event.historySize > 0) {
                            val dY = event.y - event.getHistoricalY(0)
                            val newTransY = 0.4f * dY + container.translationY
                            if (newTransY > 0) {
                                container.translationY = newTransY
                                searchBar.show()
                            } else {
                                container.translationY = 0f
                            }

                            if (container.translationY == 0f) return@onTouch false
                        }
                    }
                    scrollView.scrollY == scrollContainer.height - scrollView.height && searchVisibility -> {
                        if (container.translationY <= -searchBar.height) {
                            return@onTouch false
                        }
                        if (event.historySize > 0) {
                            val dY = event.y - event.getHistoricalY(0)
                            val newTransY = 0.4f * dY + container.translationY
                            container.translationY =
                                if (newTransY <= 0) newTransY
                                else 0f
                            if (container.translationY == 0f) return@onTouch false
                        }
                    }
                    else -> return@onTouch false
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (container.translationY >= searchBar.height) toggleSearch()
                if (container.translationY <= -searchBar.height) hideSearch()
                container.animate().translationY(0f).setDuration(200).start()
                if (!searchVisibility && scrollView.scrollY == 0) searchBar.hide()
                false
            }
            else -> false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityStarter.destroy()
    }

    companion object {
        const val REQUEST_PICK_APPWIDGET = 4412
        const val REQUEST_CREATE_APPWIDGET = 4460
        const val REQUEST_BIND_APPWIDGET = 4124
    }
}