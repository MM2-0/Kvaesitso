package de.mm20.launcher2.ui.legacy.search

import android.app.Notification
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Process
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.graphics.alpha
import androidx.lifecycle.*
import androidx.transition.Scene
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.castToOrNull
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.getBadgeIcon
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.notifications.NotificationService
import de.mm20.launcher2.search.data.AppInstallation
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ApplicationDetailRepresentation : Representation, KoinComponent {

    private val iconRepository: IconRepository by inject()
    private val badgeProvider: BadgeProvider by inject()

    override fun getScene(
        rootView: SearchableView,
        searchable: Searchable,
        previousRepresentation: Int?
    ): Scene {
        val application = searchable as Application
        val context = rootView.context as AppCompatActivity
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_application_detail, context)
        scene.setEnterAction {
            with(rootView) {
                setOnClickListener(null)
                setOnLongClickListener(null)
                findViewById<TextView>(R.id.appName).text = application.label
                findViewById<LauncherIconView>(R.id.icon).apply {
                    badge = badgeProvider.getLiveBadge(application.badgeKey)
                    shape = LauncherIconView.getDefaultShape(context)
                    icon = iconRepository.getIconIfCached(application)
                    lifecycleScope.launch {
                        iconRepository.getIcon(application, (84 * rootView.dp).toInt())
                            .collectLatest {
                                icon = it
                            }
                    }
                }
                findViewById<SwipeCardView>(R.id.appCard).also {
                    it.leftAction = FavoriteSwipeAction(context, application)
                    it.rightAction = HideSwipeAction(context, application)
                }
                val appInfo = findViewById<TextView>(R.id.appInfo)
                appInfo.text = if (application !is AppInstallation) {
                    context.getString(
                        R.string.app_info,
                        application.version ?: "",
                        application.`package`
                    )
                } else {
                    val callback = object : PackageInstaller.SessionCallback() {
                        override fun onActiveChanged(p0: Int, p1: Boolean) {
                        }

                        override fun onFinished(sessionId: Int, success: Boolean) {
                            if (sessionId == application.session.sessionId) {
                                context.packageManager.packageInstaller.unregisterSessionCallback(
                                    this
                                )
                            }
                        }

                        override fun onBadgingChanged(p0: Int) {
                        }

                        override fun onCreated(p0: Int) {
                        }

                        override fun onProgressChanged(sessionId: Int, progress: Float) {
                            if (sessionId == application.session.sessionId) {
                                appInfo.text = context.getString(
                                    R.string.installation_in_progress,
                                    (progress * 100).roundToInt()
                                )
                            }
                        }
                    }
                    context.packageManager.packageInstaller.registerSessionCallback(callback)
                    context.getString(
                        R.string.installation_in_progress,
                        (application.session.progress * 100).roundToInt()
                    )
                }

                val appShortcuts = findViewById<ChipGroup>(R.id.appShortcuts)
                appShortcuts.layoutTransition = ChangingLayoutTransition()
                setupShortcuts(appShortcuts, application)

                val toolbar = findViewById<ToolbarView>(R.id.appToolbar)
                setupToolbar(this, toolbar, application)

            }
        }

        return scene
    }

    private fun setupToolbar(rootView: SearchableView, toolbar: ToolbarView, app: Application) {
        val context = rootView.context
        toolbar.clear()

        val backAction =
            ToolbarAction(R.drawable.ic_arrow_back, context.getString(R.string.menu_back))
        backAction.clickAction = {
            rootView.back()
        }
        toolbar.addAction(backAction, ToolbarView.PLACEMENT_START)

        if (app !is AppInstallation) {
            val favAction = FavoriteToolbarAction(context, app)
            toolbar.addAction(favAction, ToolbarView.PLACEMENT_END)
        }

        if (app !is AppInstallation) {
            val infoAction =
                ToolbarAction(R.drawable.ic_info_outline, context.getString(R.string.menu_app_info))
            infoAction.clickAction = {
                val launcherApps = context.getSystemService<LauncherApps>()!!
                launcherApps.startAppDetailsActivity(
                    ComponentName(app.`package`, app.activity),
                    app.castToOrNull<LauncherApp>()?.getUser() ?: Process.myUserHandle(),
                    null,
                    null
                )
            }
            toolbar.addAction(infoAction, ToolbarView.PLACEMENT_END)
        }

        val shareAction = ToolbarAction(R.drawable.ic_share, context.getString(R.string.menu_share))
        val storeDetails = app.getStoreDetails(context)
        if (app !is AppInstallation) {
            if (storeDetails == null) {
                shareAction.clickAction = {
                    shareApk(context, app)
                }
            } else {
                shareAction.subActions.add(ToolbarSubaction(
                    context.getString(R.string.share_menu_store_link, storeDetails.label)
                ) { shareLink(context, storeDetails.url) })

                shareAction.subActions.add(ToolbarSubaction(
                    context.getString(R.string.share_menu_apk_file)
                ) { shareApk(context, app) })
            }
            toolbar.addAction(shareAction, ToolbarView.PLACEMENT_END)
        } else {
            if (storeDetails != null) {
                shareAction.clickAction = {
                    shareLink(context, storeDetails.url)
                }
                toolbar.addAction(shareAction, ToolbarView.PLACEMENT_END)
            }
        }

        if (app !is AppInstallation) {
            if (app.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val uninstallAction =
                    ToolbarAction(R.drawable.ic_delete, context.getString(R.string.menu_uninstall))
                uninstallAction.clickAction = {
                    val intent = Intent(Intent.ACTION_DELETE)
                    intent.data = Uri.parse("package:" + app.`package`)
                    context.startActivity(intent)
                    rootView.back()
                }
                toolbar.addAction(uninstallAction, ToolbarView.PLACEMENT_END)
            }

            val hideAction = VisibilityToolbarAction(context, app)
            toolbar.addAction(hideAction, ToolbarView.PLACEMENT_END)
        }
    }

    private fun setupShortcuts(appShortcuts: ChipGroup, app: Application) {
        val context = appShortcuts.context
        appShortcuts.removeAllViews()
        val ns = NotificationService.getInstance()
        val notifications = ns?.getNotifications(app.`package`)
        notifications?.forEach {
            var title = it.notification.tickerText
            if (title.isNullOrBlank()) {
                title = it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
            }
            if (title.isNullOrBlank()) {
                title = it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
            }
            if (title == null) title = ""
            if (!NotificationCompat.isGroupSummary(it.notification)) {
                val view = Chip(context)
                view.text = title
                view.chipIcon =
                    createShortcutDrawable(getNotificationChipIcon(context, it.notification))
                view.chipStrokeWidth = 1 * context.dp
                view.chipStrokeColor = ContextCompat.getColorStateList(context, R.color.chip_stroke)
                view.chipBackgroundColor =
                    ContextCompat.getColorStateList(context, R.color.chip_background)
                view.setTextAppearanceResource(R.style.ChipTextAppearance)
                view.closeIconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        R.color.text_color_secondary
                    )
                )

                view.isCloseIconVisible = it.isClearable

                view.setOnClickListener { _ ->
                    try {
                        it.notification.contentIntent?.send()
                    } catch (e: PendingIntent.CanceledException) {
                    }
                }
                view.setOnCloseIconClickListener { _ ->
                    ns.cancelNotification(it.key)
                    appShortcuts.removeView(view)
                }
                appShortcuts.addView(view)
            }

        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            if (launcherApps.hasShortcutHostPermission()) {
                val shortcuts = app.shortcuts

                val repository: FavoritesRepository by inject()

                var count = 0
                for (si in shortcuts) {
                    if (count > 4) break
                    count++
                    val view = Chip(context)
                    view.text = si.label

                    view.chipIcon = createShortcutDrawable(
                        launcherApps.getShortcutBadgedIconDrawable(
                            si.launcherShortcut,
                            context.resources.displayMetrics.densityDpi
                        )
                    )

                    view.chipIconSize = 24 * context.dp

                    view.chipIconTint = null

                    view.chipStrokeWidth = 1 * context.dp
                    view.chipStrokeColor =
                        ContextCompat.getColorStateList(context, R.color.chip_stroke)
                    view.chipBackgroundColor =
                        ContextCompat.getColorStateList(context, R.color.chip_background)
                    view.setTextAppearanceResource(R.style.ChipTextAppearance)
                    view.closeIcon = context.getDrawable(R.drawable.ic_star_solid)
                    view.closeIconTint = ColorStateList.valueOf(
                        ContextCompat.getColor(
                            context,
                            R.color.text_color_primary
                        )
                    )
                    val isPinned = repository.isPinned(si).asLiveData()

                    isPinned.observe(context as LifecycleOwner, Observer {
                        view.isCloseIconVisible = isPinned.value == true
                    })

                    view.setOnClickListener {
                        ActivityStarter.start(context, view)
                        launcherApps.startShortcut(si.launcherShortcut, null, null)
                    }
                    view.setOnLongClickListener {
                        if (isPinned.value == true) {
                            repository.unpinItem(si)
                        } else {
                            repository.pinItem(si)
                        }
                        true
                    }
                    view.setOnCloseIconClickListener {
                        repository.unpinItem(si)
                        view.isCloseIconVisible = false
                    }
                    appShortcuts.addView(view)
                }
            }
        }
    }

    private fun createShortcutDrawable(drawable: Drawable?): Drawable {
        val bgShape = ShapeDrawable(OvalShape()).apply {
            paint.color = 0xFFF5F5F5.toInt()
        }
        if (drawable == null) return bgShape
        return LayerDrawable(
            arrayOf(
                bgShape,
                drawable
            )
        )
    }

    private fun getNotificationChipIcon(context: Context, notification: Notification): Drawable? {
        return notification.getBadgeIcon(context, context.packageName)?.let {
            val _4dp = (4 * context.dp).roundToInt()
            return@let LayerDrawable(arrayOf(
                ShapeDrawable(
                    OvalShape()
                ).apply {
                    colorFilter = PorterDuffColorFilter(
                        ContextCompat.getColor(context, R.color.shortcut_icon_background),
                        PorterDuff.Mode.SRC_ATOP
                    )
                },
                it.apply {
                    colorFilter = PorterDuffColorFilter(
                        notification.color.takeIf { it.alpha > 0 }
                            ?: ContextCompat.getColor(context, R.color.text_color_secondary),
                        PorterDuff.Mode.SRC_ATOP
                    )
                }
            )).apply {
                setLayerInset(1, _4dp, _4dp, _4dp, _4dp)
            }
        }
    }

    private fun shareLink(context: Context, storeUrl: String) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_TEXT, storeUrl)
        shareIntent.type = "text/plain"
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    private fun shareApk(context: Context, app: Application) {
        val handler = Handler()
        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage(context.getString(R.string.dialog_wait))
        progressDialog.show()
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val info = context.packageManager
                    .getApplicationInfo(app.`package`, 0)
                val file = java.io.File(info.publicSourceDir)
                val fileCopy = java.io.File(
                    context.cacheDir,
                    "${app.`package`}-${app.version}.apk"
                )
                try {
                    file.copyTo(fileCopy, false)
                } catch (e: FileAlreadyExistsException) {
                    // Do nothing. If the file is already there we don't have to copy it again.
                }
                handler.post {
                    progressDialog.hide()
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val uri = FileProvider.getUriForFile(
                        context,
                        context.applicationContext.packageName + ".fileprovider",
                        fileCopy
                    )
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    shareIntent.type = "application/vnd.android.package-archive"
                    context.startActivity(Intent.createChooser(shareIntent, null))
                }
            } catch (e: PackageManager.NameNotFoundException) {
                CrashReporter.logException(e)
            }
        }
    }


}
