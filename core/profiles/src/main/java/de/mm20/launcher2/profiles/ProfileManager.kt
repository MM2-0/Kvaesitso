package de.mm20.launcher2.profiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ProfileWithState(
    val profile: Profile,
    val state: Profile.State,
)

class ProfileManager(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
) {
    private val userManager = context.getSystemService<UserManager>()!!
    private val launcherApps = context.getSystemService<LauncherApps>()!!

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val profileStates: MutableStateFlow<List<ProfileWithState>> =
        MutableStateFlow(emptyList())

    /**
     * List of profiles that are active and unlocked.
     */
    val activeProfiles: Flow<List<Profile>> = profileStates.map {
        it.mapNotNull {
            if (it.state.hidden) null else it.profile
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    val profiles: Flow<List<Profile>> = profileStates.map {
        it.map { it.profile }
    }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scope.launch {
                    refreshProfiles()
                }
            }
        }
        context.registerReceiver(
            receiver, IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
                if (isAtLeastApiLevel(34)) {
                    addAction(Intent.ACTION_PROFILE_ADDED)
                    addAction(Intent.ACTION_PROFILE_REMOVED)
                }
                if (isAtLeastApiLevel(31)) {
                    addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                    addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
                }

            }
        )
        scope.launch {
            if (isAtLeastApiLevel(35)) {
                permissionsManager.hasPermission(PermissionGroup.ManageProfiles).collectLatest {
                    refreshProfiles()
                }
            } else {
                refreshProfiles()
            }
        }
    }

    private val mutex = Mutex()
    private suspend fun refreshProfiles() {
        mutex.withLock {
            val profiles = mutableListOf<ProfileWithState>()

            for (userHandle in launcherApps.profiles) {
                profiles.add(
                    ProfileWithState(
                        Profile(
                            type = getProfileType(userHandle),
                            userHandle = userHandle,
                            serial = userManager.getSerialNumberForUser(userHandle),
                        ),
                        getProfileState(userHandle),
                    )
                )
            }
            profileStates.value = profiles
        }
    }

    fun getProfile(userHandle: UserHandle): Flow<Profile?> {
        return profileStates.map {
            it.find { it.profile.userHandle == userHandle }?.profile
        }
    }

    fun getProfileState(profile: Profile?): Flow<Profile.State?> {
        return profileStates.map { profiles ->
            profiles.find { it.profile == profile }?.state
        }
    }

    private fun getProfileType(userHandle: UserHandle): Profile.Type {
        if (isAtLeastApiLevel(35)) {
            val launcherUserInfo = launcherApps.getLauncherUserInfo(userHandle)
            return when (launcherUserInfo?.userType) {
                UserManager.USER_TYPE_PROFILE_PRIVATE -> Profile.Type.Private
                UserManager.USER_TYPE_PROFILE_MANAGED -> Profile.Type.Work
                else -> Profile.Type.Personal

            }
        }
        return if (userHandle == Process.myUserHandle()) Profile.Type.Personal else Profile.Type.Work
    }

    private fun getProfileState(userHandle: UserHandle): Profile.State {
        return Profile.State(
            locked = !userManager.isUserUnlocked(userHandle),
            hidden = !userManager.isUserRunning(userHandle),
        )
    }

    @RequiresApi(28)
    fun unlockProfile(profile: Profile) {
        userManager.requestQuietModeEnabled(false, profile.userHandle)
    }

    @RequiresApi(28)
    fun lockProfile(profile: Profile) {
        userManager.requestQuietModeEnabled(true, profile.userHandle)
    }

}