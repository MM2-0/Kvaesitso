// === TELOS_PENDING_REVIEW_START: comms_virtual_apps ===
package de.mm20.launcher2.data.comms

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.VirtualAppSerializer
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.TelosAppKeywords
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.VirtualAppProvider
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.TransparentLayer

internal class VirtualPhoneApp(private val context: Context) : Application {

    override val key: String = "$Domain://phone"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualPhoneApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
                putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "recents")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_phone_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF0A2472.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_phone_app"
    }
}

internal class VirtualMessagesApp(private val context: Context) : Application {

    override val key: String = "$Domain://messages"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualMessagesApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
                putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "messages")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_messages_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF00897B.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_messages_app"
    }
}

internal class VirtualRadioApp(private val context: Context) : Application {

    override val key: String = "$Domain://radio"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualRadioApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_RADIO)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_radio_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF2E9E3F.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_radio_app"
    }
}

internal class VirtualMusicApp(private val context: Context) : Application {

    override val key: String = "$Domain://music"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualMusicApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_music_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF7B2FC9.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_music_app"
    }
}

internal class VirtualYouTubeApp(private val context: Context) : Application {

    override val key: String = "$Domain://youtube"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualYouTubeApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_YOUTUBE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_youtube_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF00796B.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_youtube_app"
    }
}

internal class VirtualYouTubeMusicApp(private val context: Context) : Application {

    override val key: String = "$Domain://youtube_music"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualYouTubeMusicApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_YOUTUBE_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_youtube_music_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF7B2FC9.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_youtube_music_app"
    }
}

internal class VirtualVideoApp(private val context: Context) : Application {

    override val key: String = "$Domain://video"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualVideoApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_VIDEO)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_video_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFFE53935.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_video_app"
    }
}

internal class VirtualTvApp(private val context: Context) : Application {

    override val key: String = "$Domain://tv"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualTvApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_TV)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_tv_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF3949AB.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_tv_app"
    }
}

internal class VirtualStremioApp(private val context: Context) : Application {

    override val key: String = "$Domain://stremio"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualStremioApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_STREMIO)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_stremio_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF7C4DFF.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_stremio_app"
    }
}

internal class VirtualPhotosApp(private val context: Context) : Application {

    override val key: String = "$Domain://photos"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualPhotosApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_PHOTOS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_photos_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFFE91E63.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_photos_app"
    }
}

internal class VirtualViewerApp(private val context: Context) : Application {

    override val key: String = "$Domain://viewer"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualViewerApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_VIEWER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_viewer_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF3F6BE8.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_viewer_app"
    }
}

internal class VirtualFilesApp(private val context: Context) : Application {

    override val key: String = "$Domain://files"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualFilesApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_FILES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_files_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFFF57C00.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_files_app"
    }
}

internal class VirtualCalculatorApp(private val context: Context) : Application {

    override val key: String = "$Domain://calculator"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualCalculatorApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_CALCULATOR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_calculator_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF37474F.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_calculator_app"
    }
}

internal class VirtualVoiceRecorderApp(private val context: Context) : Application {

    override val key: String = "$Domain://voice_recorder"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualVoiceRecorderApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_VOICE_RECORDER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_voice_recorder_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF00838F.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_voice_recorder_app"
    }
}

internal class VirtualCameraApp(private val context: Context) : Application {

    override val key: String = "$Domain://camera"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualCameraApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_CAMERA)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_camera_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFFE65100.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_camera_app"
    }
}

internal class VirtualScreenRecorderApp(private val context: Context) : Application {

    override val key: String = "$Domain://screen_recorder"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualScreenRecorderApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_SCREEN_RECORDER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_screen_recorder_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFFF4511E.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_screen_recorder_app"
    }
}

internal class VirtualScreenshotApp(private val context: Context) : Application {

    override val key: String = "$Domain://screenshot"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualScreenshotApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_SCREENSHOT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_screenshot_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF1A6DFF.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_screenshot_app"
    }
}

internal class VirtualNotesApp(private val context: Context) : Application {

    override val key: String = "$Domain://notes"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualNotesApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_NOTES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_notes_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFFF9A825.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_notes_app"
    }
}

internal class VirtualErmisApp(private val context: Context) : Application {

    override val key: String = "$Domain://ermis"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualErmisApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null
    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}
    override val canShareApk: Boolean = false
    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_ERMIS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_messages_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFFD93025.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_ermis_app"
    }
}

internal class VirtualCalendarApp(private val context: Context) : Application {

    override val key: String = "$Domain://calendar"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualCalendarApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_calendar_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF1E88E5.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_calendar_app"
    }
}

internal class VirtualDownloadsApp(private val context: Context) : Application {

    override val key: String = "$Domain://downloads"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualDownloadsApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_DOWNLOADS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_downloads_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF5E35B1.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_downloads_app"
    }
}

internal class VirtualMediaApp(private val context: Context) : Application {

    override val key: String = "$Domain://media"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualMediaApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_MEDIA)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_media_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF5E35B1.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_media_app"
    }
}

internal class VirtualNetworkApp(private val context: Context) : Application {

    override val key: String = "$Domain://network"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualNetworkApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_NETWORK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_network_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF0B6B4D.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_network_app"
    }
}

internal class VirtualSystemApp(private val context: Context) : Application {

    override val key: String = "$Domain://system"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualSystemApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_SYSTEM)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            de.mm20.launcher2.search.TelosAppIcons.forDomain(Domain)
                ?: de.mm20.launcher2.base.R.drawable.ic_app_system_full,
        ) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = TransparentLayer,
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_system_app"
    }
}

internal class VirtualHelpApp(private val context: Context) : Application {
    override val key: String = "telos_help_app://help"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forKey(key)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified
    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualHelpApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null
    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}
    override val canShareApk: Boolean = false
    override fun overrideLabel(label: String): SavableSearchable = this
    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_TELOS_PAGE)
                putExtra(SettingsDeepLinkContract.EXTRA_TELOS_PAGE_KEY, key)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (_: Exception) {
            false
        }
    }
    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.help_24px) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF102A43.toInt()), 1f),
        )
    }
    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_help_app"
    }
}

internal class VirtualBackupApp(private val context: Context) : Application {

    override val key: String = "$Domain://backup"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualBackupApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_BACKUP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            de.mm20.launcher2.search.TelosAppIcons.forDomain(Domain)
                ?: de.mm20.launcher2.base.R.drawable.ic_app_backup_full,
        ) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF102A43.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_backup_app"
    }
}

internal class VirtualKeyboardApp(private val context: Context) : Application {

    override val key: String = "$Domain://keyboard"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualKeyboardApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_KEYBOARD)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            de.mm20.launcher2.search.TelosAppIcons.forDomain(Domain)
                ?: de.mm20.launcher2.base.R.drawable.ic_app_keyboard_full,
        ) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF102A43.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_keyboard_app"
    }
}

internal class VirtualKeePassApp(private val context: Context) : Application {

    override val key: String = "$Domain://keepass"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualKeePassApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_KEEPASS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            de.mm20.launcher2.search.TelosAppIcons.forDomain(Domain)
                ?: de.mm20.launcher2.base.R.drawable.ic_app_keepass_full,
        ) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF102A43.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_keepass_app"
    }
}

internal class VirtualAetherApp(private val context: Context) : Application {

    override val key: String = "$Domain://aether"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualAetherApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_AETHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            de.mm20.launcher2.search.TelosAppIcons.forDomain(Domain)
                ?: de.mm20.launcher2.base.R.drawable.ic_app_aether_full,
        ) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(android.graphics.drawable.ColorDrawable(0xFF102A43.toInt()), 1f),
        )
    }

    override fun getSerializer(): SearchableSerializer = VirtualAppSerializer()

    companion object {
        const val Domain = "telos_aether_app"
    }
}

internal class CommsVirtualAppProvider(private val context: Context) : VirtualAppProvider {
    override fun getVirtualApps(): List<Application> = listOf(
        VirtualPhoneApp(context),
        VirtualMessagesApp(context),
        VirtualMediaApp(context),
        VirtualYouTubeApp(context),
        VirtualYouTubeMusicApp(context),
        VirtualRadioApp(context),
        VirtualMusicApp(context),
        VirtualVideoApp(context),
        VirtualTvApp(context),
        VirtualStremioApp(context),
        VirtualViewerApp(context),
        VirtualFilesApp(context),
        VirtualCalculatorApp(context),
        VirtualVoiceRecorderApp(context),
        VirtualCameraApp(context),
        VirtualScreenRecorderApp(context),
        VirtualScreenshotApp(context),
        VirtualNotesApp(context),
        VirtualErmisApp(context),
        VirtualCalendarApp(context),
        VirtualDownloadsApp(context),
        VirtualNetworkApp(context),
        VirtualSystemApp(context),
        VirtualBackupApp(context),
        VirtualKeyboardApp(context),
        VirtualKeePassApp(context),
        VirtualAetherApp(context),
        VirtualHelpApp(context),
    )
}
// === TELOS_PENDING_REVIEW_END: comms_virtual_apps ===
