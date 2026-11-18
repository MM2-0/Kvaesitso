package de.mm20.launcher2.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import de.mm20.launcher2.preferences.migrations.Migration2
import de.mm20.launcher2.preferences.migrations.Migration3
import de.mm20.launcher2.preferences.migrations.Migration4
import de.mm20.launcher2.preferences.migrations.Migration5
import de.mm20.launcher2.preferences.migrations.Migration6
import de.mm20.launcher2.preferences.migrations.Migration7
import de.mm20.launcher2.preferences.migrations.Migration8
import de.mm20.launcher2.preferences.migrations.Migration9
import de.mm20.launcher2.preferences.migrations.Migration10
import de.mm20.launcher2.preferences.migrations.Migration11
import de.mm20.launcher2.preferences.migrations.Migration12
import de.mm20.launcher2.settings.BaseSettings
import java.io.File

internal class LauncherDataStore(
    private val context: Context,
): BaseSettings<LauncherSettingsData>(
    context,
    fileName = "settings.json",
    serializer = LauncherSettingsDataSerializer(context),
    migrations = listOf(
        Migration2(),
        Migration3(),
        Migration4(),
        Migration5(),
        Migration6(),
        Migration7(),
        Migration8(),
        Migration9(),
        Migration10(),
        Migration11(),
        Migration12(),
    ),
    // Last line of defense: if the file genuinely can't be read despite the serializer's
    // unknown-value tolerance, snapshot it before DataStore overwrites it with the replacement.
    // Losing every setting is bad enough; losing them UNRECOVERABLY (as the previous plain
    // ReplaceFileCorruptionHandler did) must never happen - the snapshot keeps the data around
    // for a fixed build (or a manual restore) to recover.
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        Log.e("MM20", "settings.json is unreadable, resetting to defaults", exception)
        try {
            val file = File(context.filesDir, "datastore/settings.json")
            if (file.exists()) {
                file.copyTo(File(context.filesDir, "datastore/settings.json.corrupt"), overwrite = true)
            }
        } catch (e: Exception) {
            Log.e("MM20", "Could not back up corrupt settings.json", e)
        }
        LauncherSettingsData()
    }
) {

    // Backups are plain files that can end up anywhere. Passwords and keys are encrypted with a key
    // that exists only on this device (useless elsewhere), and the hidden contacts are private.
    override fun forBackup(data: LauncherSettingsData): LauncherSettingsData = data.copy(
        freeze = data.freeze.copy(freezeMaintenance = emptyMap(), freezeScreenOffPending = emptyMap(),
            freezeScreenOffBoot = -1, freezeRecentsThawed = emptySet(), freezeCompanionHolds = emptyMap(), freezeSystemRiskApproved = emptySet()),
        comms = data.comms.copy(
            tmdbApiKeyEnc = "",
            subtitleApiKeyEnc = "",
            subtitlePasswordEnc = "",
            sipPasswordEnc = "",
            remotePhonebookPasswordEnc = "",
            hiddenNumbers = emptyMap(),
            protectedCallNumbers = emptyMap(),
            lastDialpadDigits = "",
        ),
        // the PIN hash of the custom lock stays on this device: a plain backup file must not carry
        // something that can be brute-forced offline
        protection = data.protection.copy(
            protectionCustomLockHashed = null,
            protectionUseCustomLock = false,
        ),
    )

    override fun forRestore(restored: LauncherSettingsData, current: LauncherSettingsData): LauncherSettingsData {
        // keep what this device already has: its passwords and its hidden contacts
        val c = current.comms
        val comms = restored.comms.copy(
            tmdbApiKeyEnc = c.tmdbApiKeyEnc,
            subtitleApiKeyEnc = c.subtitleApiKeyEnc,
            subtitlePasswordEnc = c.subtitlePasswordEnc,
            sipPasswordEnc = c.sipPasswordEnc,
            remotePhonebookPasswordEnc = c.remotePhonebookPasswordEnc,
            hiddenNumbers = c.hiddenNumbers,
            protectedCallNumbers = c.protectedCallNumbers,
            lastDialpadDigits = "",
        )
        // no account without its password: it would only fail to register
        return restored.copy(
            // keep this device's own PIN lock (a backup never carries it)
            protection = restored.protection.copy(
                protectionCustomLockHashed = current.protection.protectionCustomLockHashed,
                protectionUseCustomLock = current.protection.protectionUseCustomLock,
            ),
            freeze = restored.freeze.copy(freezeMaintenance = current.freeze.freezeMaintenance,
                freezeScreenOffPending = current.freeze.freezeScreenOffPending,
                freezeScreenOffBoot = current.freeze.freezeScreenOffBoot,
                freezeRecentsThawed = current.freeze.freezeRecentsThawed,
                freezeCompanionHolds = current.freeze.freezeCompanionHolds,
                freezeSystemRiskApproved = current.freeze.freezeSystemRiskApproved),
            comms = comms.copy(
                sipEnabled = comms.sipEnabled && comms.sipPasswordEnc.isNotBlank(),
                remotePhonebookEnabled = comms.remotePhonebookEnabled && comms.remotePhonebookPasswordEnc.isNotBlank(),
            )
        )
    }

    val data
        get() = context.dataStore.data

    suspend fun updateAndWait(block: (LauncherSettingsData) -> LauncherSettingsData) {
        updateDataAndWait(block)
    }

    fun update(block: (LauncherSettingsData) -> LauncherSettingsData) {
        updateData(block)
    }
}
