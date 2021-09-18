package de.mm20.launcher2.badges

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LauncherPreferences
import kotlinx.coroutines.*

class BadgeProvider private constructor(val context: Context) {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val badges = mutableMapOf<String, MutableLiveData<Badge>>()

    init {
        if (LauncherPreferences.instance.cloudBadges) {
            addCloudBadges()
        }
        if (LauncherPreferences.instance.suspendBadges) {
            addSuspendBadges()
        }
        if (LauncherPreferences.instance.profileBadges && isAtLeastApiLevel(Build.VERSION_CODES.O)) {
            addProfileBadges()
        }
    }


    fun setBadge(key: String, badge: Badge) {
        if (badges.containsKey(key)) {
            badges[key]?.postValue(badge)
            return
        }
        badges[key] = MutableLiveData(badge)
    }

    /**
     * Updates a badge with all set values of another badge but keeps all other values
     */
    fun updateBadge(key: String,
                    badge: Badge) {
        if (badges.containsKey(key)) {
            badges[key]?.run {
                val updatedBadge = value?.also {
                    if (badge.number != null) it.number = badge.number
                    if (badge.progress != null) it.progress = badge.progress
                    if (badge.iconRes != null) it.iconRes = badge.iconRes
                    if (badge.icon != null) it.icon = badge.icon
                }
                postValue(updatedBadge)
            }
        } else {
            badges[key] = MutableLiveData(badge)
        }
    }

    fun removeBadge(key: String) {
        badges[key]?.postValue(Badge())
    }

    fun getBadge(key: String): Badge? {
        return badges[key]?.value
    }

    fun getLiveBadge(key: String): LiveData<Badge> {
        return badges[key] ?: MutableLiveData<Badge>(Badge()).also { badges[key] = it }
    }

    fun removeNotificationBadges() {
        for (k in badges.keys) {
            if (k.startsWith("app://")) {
                val badge = getBadge(k) ?: continue
                badge.number = null
                badge.progress = null
                updateBadge(k, badge)
            }
        }
    }

    fun removeSuspendBadges() {
        for ((k, v) in badges) {
            if (k.startsWith("app://") && v.value?.iconRes == R.drawable.ic_badge_suspended) {
                val badge = getBadge(k) ?: continue
                badge.iconRes = null
                updateBadge(k, badge)
            }
        }
    }

    fun addSuspendBadges() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val apps = context.packageManager.getInstalledApplications(0)
                for (app in apps) {
                    if (app.flags and ApplicationInfo.FLAG_SUSPENDED != 0) {
                        setBadge("app://${app.packageName}", Badge(iconRes = R.drawable.ic_badge_suspended))
                    }
                }
            }
        }
    }

    fun addCloudBadges() {
        setBadge("gdrive://", Badge(iconRes = R.drawable.ic_badge_gdrive))
        setBadge("onedrive://", Badge(iconRes = R.drawable.ic_badge_onedrive))
        setBadge("nextcloud://", Badge(iconRes = R.drawable.ic_badge_nextcloud))
        setBadge("owncloud://", Badge(iconRes = R.drawable.ic_badge_owncloud))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun addProfileBadges() {
        val profiles = (context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).profiles
        for (p in profiles) {
            if (p == Process.myUserHandle()) continue
            setBadge("profile://${p.getSerialNumber(context)}", Badge(
                    iconRes = R.drawable.ic_badge_workprofile
            ))
        }
    }

    fun removeCloudBadges() {
        removeBadge("gdrive://")
        removeBadge("onedrive://")
        removeBadge("nextcloud://")
        removeBadge("owncloud://")
    }

    fun removeShortcutBadges() {
        for (k in badges.keys) {
            if (k.startsWith("shortcut://")) {
                removeBadge(k)
            }
        }
    }

    companion object {
        private lateinit var instance: BadgeProvider

        fun getInstance(context: Context): BadgeProvider {
            if (!::instance.isInitialized) instance = BadgeProvider(context.applicationContext)
            return instance
        }
    }
}