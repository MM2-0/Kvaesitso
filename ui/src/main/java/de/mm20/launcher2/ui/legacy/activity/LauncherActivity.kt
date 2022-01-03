package de.mm20.launcher2.ui.legacy.activity

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
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.animation.doOnEnd
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.list.listItems
import com.jmedeisis.draglinearlayout.DragLinearLayout
import de.mm20.launcher2.icons.DynamicIconController
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.isBrightColor
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.transition.OneShotLayoutTransition
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.databinding.ActivityLauncherBinding
import de.mm20.launcher2.ui.launcher.modals.EditFavoritesView
import de.mm20.launcher2.ui.launcher.modals.HiddenItemsView
import de.mm20.launcher2.ui.launcher.search.SearchViewModel
import de.mm20.launcher2.ui.legacy.component.WidgetView
import de.mm20.launcher2.ui.legacy.helper.ThemeHelper
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetType
import de.mm20.launcher2.widgets.WidgetViewModel
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.*
import kotlin.math.roundToInt


class LauncherActivity : BaseActivity() {

    /**
     * True if the search result list is visible
     */
    private var searchVisibility = false
        set(value) {
            field = value
            windowBackgroundBlur = value
        }

    private lateinit var widgetHost: AppWidgetHost
    private val widgets = mutableListOf<Widget>()

    private lateinit var overlayView: ViewGroupOverlay

    private val widgetViewModel: WidgetViewModel by viewModel()

    private val searchViewModel: SearchViewModel by viewModels()

    private val preferences = LauncherPreferences.instance

    private var windowBackgroundBlur: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!isAtLeastApiLevel(31)) return
            window.attributes = window.attributes.also {
                if (value) {
                    it.blurBehindRadius = (32 * dp).toInt()
                    it.flags = it.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                } else {
                    it.blurBehindRadius = 0
                    it.flags = it.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                }
            }
        }

    private var widgetEditMode = false
        set(value) {
            field = value
            if (value) {
                binding.widgetSpacer.visibility = View.GONE
                binding.clockWidget.visibility = View.GONE
                binding.searchBar.setRightIcon(R.drawable.ic_done)
                binding.scrollView.setOnTouchListener(null)
                for (v in binding.widgetList.iterator()) {
                    if (v is WidgetView) {
                        v.editMode = true
                        v.onResizeModeChange = {
                            OneShotLayoutTransition.run(binding.widgetList)
                            OneShotLayoutTransition.run(binding.widgetContainer)
                        }
                    }
                }
                OneShotLayoutTransition.run(binding.widgetList)
                OneShotLayoutTransition.run(binding.widgetContainer)
                OneShotLayoutTransition.run(binding.scrollContainer)
                binding.fabEditWidget.apply {
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
                binding.searchBar.visibility = View.INVISIBLE
                binding.editWidgetToolbar
                    .animate()
                    .translationY(0f)
                    .alpha(1f)
                    .withStartAction {
                        binding.editWidgetToolbar.visibility = View.VISIBLE
                    }
                    .start()
            } else {
                widgetViewModel.saveWidgets(widgets)
                binding.widgetSpacer.visibility = View.VISIBLE
                binding.widgetList.layoutTransition = ChangingLayoutTransition()
                binding.widgetContainer.layoutTransition = ChangingLayoutTransition()
                binding.scrollContainer.layoutTransition = ChangingLayoutTransition()
                binding.searchBar.setRightIcon(R.drawable.ic_more_vert)
                binding.scrollView.setOnTouchListener(scrollViewOnTouchListener)
                binding.clockWidget.visibility = View.VISIBLE
                for (v in binding.widgetList.iterator()) {
                    if (v is WidgetView) {
                        v.editMode = false
                        v.layoutTransition = ChangingLayoutTransition()
                    }
                }
                binding.fabEditWidget.apply {
                    setIconResource(R.drawable.ic_edit)
                    setText(R.string.menu_edit_widgets)
                    setOnClickListener {
                        widgetEditMode = true
                    }
                }
                window.statusBarColor = Color.TRANSPARENT

                updateSystemBarAppearance()

                binding.searchBar.visibility = View.VISIBLE
                binding.editWidgetToolbar
                    .animate()
                    .translationY(-binding.editWidgetToolbar.height.toFloat())
                    .alpha(0f)
                    .withEndAction {
                        binding.editWidgetToolbar.visibility = View.GONE
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

    private lateinit var binding: ActivityLauncherBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val iconRepository: IconRepository by inject()
        iconRepository.recreate()
        ThemeHelper.applyTheme(theme)

        if (LauncherPreferences.instance.firstRunVersion < 1) {
            LauncherPreferences.instance.firstRunVersion = 1
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLauncherBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        overlayView = binding.rootView.overlay

        if (LauncherPreferences.instance.dimWallpaper) {
            binding.dimWallpaper.setBackgroundColor(getColor(R.color.wallpaper_dim))
        }

        binding.scrollContainer.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        binding.searchContainer.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        binding.widgetContainer.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        val params = binding.widgetSpacer.layoutParams as LinearLayout.LayoutParams
        params.topMargin = Point().also { windowManager.defaultDisplay.getSize(it) }.y
        binding.widgetSpacer.layoutParams = params
        binding.container.doOnLayout {
            adjustWidgetSpace()
        }
        initWidgets()
        binding.scrollView.setOnTouchListener(scrollViewOnTouchListener)
        binding.scrollView.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, oldScrollY: Int ->
            when {
                /* Hide searchbar*/
                scrollY > oldScrollY && ((searchVisibility && scrollY > binding.searchBar.height) || widgetEditMode ||
                        scrollY > binding.widgetSpacer.height + binding.searchBar.height
                        + (binding.widgetSpacer.layoutParams as LinearLayout.LayoutParams).topMargin) -> {
                    var newTransY = binding.searchBar.translationY - scrollY + oldScrollY
                    if (newTransY < -binding.searchBar.height.toFloat() * 1.5f) {
                        newTransY = -binding.searchBar.height.toFloat() * 1.5f
                    }
                    binding.searchBar.translationY = newTransY
                }
                /* Show searchbar*/
                scrollY < oldScrollY -> {
                    var newTransY = binding.searchBar.translationY - scrollY + oldScrollY
                    if (newTransY > 0f) {
                        newTransY = 0f
                    }
                    binding.searchBar.translationY = newTransY
                }
            }
            if (scrollY > 0 && (searchVisibility || widgetEditMode ||
                        scrollY > binding.widgetSpacer.height
                        + (binding.widgetSpacer.layoutParams as LinearLayout.LayoutParams).topMargin)
            ) {
                binding.searchBar.raise()
            } else binding.searchBar.drop()
            if (scrollY == 0) {
                if (!searchVisibility) {
                    binding.searchBar.hide()
                    windowBackgroundBlur = false
                }
            } else {
                binding.searchBar.show()
                if (!searchVisibility) {
                    windowBackgroundBlur = true
                }
            }

        }

        binding.searchBar.onRightIconClick = onRightIconClick@{
            if (widgetEditMode) widgetEditMode = false
            else {
                val menu = PopupMenu(this, it)
                menu.inflate(R.menu.menu_launcher)
                menu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_item_settings -> {
                            startActivity(Intent(this, SettingsActivity::class.java))
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
                            val view = HiddenItemsView(this)
                            MaterialDialog(this, BottomSheet(LayoutMode.MATCH_PARENT))
                                .show {
                                    title(R.string.menu_hidden_items)
                                    customView(view = view)
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
                        R.id.menu_item_settings_old -> {
                            finish()
                            startActivity(Intent().also {
                                it.component = ComponentName(
                                    packageName,
                                    "de.mm20.launcher2.activity.SettingsActivity"
                                )
                                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    }
                    true
                }
                menu.show()
            }
        }
        binding.searchBar.setOnTouchListener { _, _ ->
            if (!searchVisibility) showSearch()
            false
        }

        binding.searchBar.onSearchQueryChanged = {
            search(it)
        }
        binding.widgetList.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        binding.fabEditWidget.setOnClickListener {
            widgetEditMode = true
        }

        binding.editWidgetToolbar.apply {
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
        binding.widgetList.removeAllViews()
        val params = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        params.topMargin = (8 * dp).roundToInt()
        for (w in widgets) {
            val view = WidgetView(this)
            view.layoutTransition = ChangingLayoutTransition()
            view.layoutParams = params
            if (view.setWidget(w, widgetHost)) {
                binding.widgetList.addDragView(view, view.getDragHandle())
                view.onRemove = {
                    OneShotLayoutTransition.run(binding.widgetList)
                    OneShotLayoutTransition.run(binding.widgetContainer)
                    binding.widgetList.removeDragView(view)
                    removeWidget(view.widget)
                }
                view.onResizeModeChange = {
                    OneShotLayoutTransition.run(binding.widgetList)
                    OneShotLayoutTransition.run(binding.widgetContainer)
                }
            }
        }

        binding.widgetList.setOnViewSwapListener { _, firstPosition, _, secondPosition ->
            Collections.swap(widgets, firstPosition, secondPosition)
        }
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
        val firstWidget = binding.clockWidget
        val m = binding.scrollContainer.paddingTop +
                (firstWidget.layoutParams as LinearLayout.LayoutParams).run { topMargin + bottomMargin }
        val params = binding.widgetSpacer.layoutParams as LinearLayout.LayoutParams
        params.topMargin =
            binding.scrollView.height - firstWidget.measuredHeight - m - binding.widgetContainer.paddingTop - binding.widgetSpacer.height
        binding.widgetSpacer.layoutParams = params
    }


    private fun search(text: String) {
        searchViewModel.search(text)
        if (binding.webSearchViewSpacer.tag != "measured" || binding.webSearchViewSpacer.height == 0) {
            val webSearchView = binding.searchBar.getWebSearchView()
            webSearchView.doOnNextLayout {
                binding.webSearchViewSpacer.layoutParams = binding.webSearchViewSpacer.layoutParams
                    .apply { height = webSearchView.height }
                binding.webSearchViewSpacer.tag = "measured"
            }
        }
        binding.webSearchViewSpacer.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
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
            binding.searchContainer.visibility = View.GONE
            binding.widgetContainer.visibility = View.VISIBLE
        }
        set.playTogether(
            ObjectAnimator.ofFloat(binding.widgetContainer, "translationY", 0f),
            ObjectAnimator.ofInt(binding.scrollView, "scrollY", 0),
            ObjectAnimator.ofFloat(
                binding.searchContainer, "translationY", 0f,
                if (binding.scrollView.scrollY > binding.searchContainer.height / 2f) -binding.searchContainer.height.toFloat() else binding.scrollView.height.toFloat()
            )
        )
        set.start()
        binding.scrollView.scrollTo(0, 0)
        binding.searchBar.hide()
        if (!binding.searchBar.getSearchQuery().isEmpty()) binding.searchBar.setSearchQuery("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
    }

    private fun showSearch() {

        searchVisibility = true
        binding.searchBar.show()
        binding.searchContainer.visibility = View.VISIBLE
        binding.widgetContainer.visibility = View.GONE
        val set = AnimatorSet()
        set.duration = 300
        set.doOnEnd {
            search("")
        }
        set.playTogether(
            ObjectAnimator.ofFloat(
                binding.widgetContainer,
                "translationY",
                binding.scrollView.height.toFloat()
            ),
            ObjectAnimator.ofInt(binding.scrollView, "scrollY", 0),
            ObjectAnimator.ofFloat(
                binding.searchContainer,
                "translationY",
                binding.scrollView.height.toFloat(),
                0f
            )
        )
        set.start()
    }

    override fun onBackPressed() {
        if (widgetEditMode) widgetEditMode = false
        if (searchVisibility) hideSearch()
        else ObjectAnimator.ofInt(binding.scrollView, "scrollY", 0).setDuration(200).start()


    }

    override fun onResume() {
        super.onResume()
        ActivityStarter.resume()
        ActivityStarter.create(binding.rootView)
        binding.activityStartOverlay.visibility = View.INVISIBLE

        search(binding.searchBar.getSearchQuery())

        updateSystemBarAppearance()

        binding.container.doOnNextLayout {
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
        preferences.doOnPreferenceChange("is_light_wallpaper", action = themeListener)
        widgetHost.startListening()
    }

    override fun onPause() {
        super.onPause()
        ActivityStarter.pause()
    }

    override fun onStop() {
        super.onStop()
        try {
            widgetHost.stopListening()
        } catch (e: NullPointerException) {
            Log.e("MM20", Log.getStackTraceString(e))
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        onBackPressed()
        adjustWidgetSpace()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        search(binding.searchBar.getSearchQuery())
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

                binding.widgetList.addDragView(view, view.getDragHandle())
                view.onRemove = {
                    OneShotLayoutTransition.run(binding.widgetList)
                    OneShotLayoutTransition.run(binding.widgetContainer)
                    binding.widgetList.removeDragView(view)
                    removeWidget(view.widget)
                }
                view.onResizeModeChange = {
                    OneShotLayoutTransition.run(binding.widgetList)
                    OneShotLayoutTransition.run(binding.widgetContainer)
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
                    binding.scrollView.scrollY == 0 -> {
                        if (event.historySize > 0) {
                            val dY = event.y - event.getHistoricalY(0)
                            val newTransY = 0.4f * dY + binding.container.translationY
                            if (newTransY > 0 && newTransY < binding.searchBar.height) {
                                binding.container.translationY = newTransY
                                binding.searchBar.show()
                            } else if (newTransY <= 0) {
                                binding.container.translationY = 0f
                            } else {
                                binding.container.translationY = binding.searchBar.height.toFloat()

                            }
                            windowBackgroundBlur =
                                searchVisibility || newTransY > 0.6 * binding.searchBar.height

                            if (binding.container.translationY == 0f) return@onTouch false
                        }
                    }
                    binding.scrollView.scrollY == binding.scrollContainer.height - binding.scrollView.height && searchVisibility -> {
                        if (event.historySize > 0) {
                            val dY = event.y - event.getHistoricalY(0)
                            val newTransY = 0.4f * dY + binding.container.translationY

                            if (newTransY <= 0 && newTransY > -binding.searchBar.height) {
                                binding.container.translationY = newTransY
                                binding.searchBar.show()
                            } else if (newTransY > 0) {
                                binding.container.translationY = 0f
                            } else {
                                binding.container.translationY = -binding.searchBar.height.toFloat()
                            }

                            if (binding.container.translationY == 0f) return@onTouch false
                        }
                    }
                    else -> return@onTouch false
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (binding.container.translationY >= binding.searchBar.height * 0.6) toggleSearch()
                if (binding.container.translationY <= -binding.searchBar.height) hideSearch()
                binding.container.animate().translationY(0f).setDuration(200).start()
                if (!searchVisibility && binding.scrollView.scrollY == 0) binding.searchBar.hide()
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