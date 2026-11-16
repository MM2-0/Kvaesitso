/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.cancelNotifications
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AetherStorageContextTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun clearingAetherNotificationsPreservesTheHostNotificationWithTheSameId() {
        if (Build.VERSION.SDK_INT >= 33) {
            ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${base.packageName} android.permission.POST_NOTIFICATIONS"),
            ).use { it.readBytes() }
        }
        val manager = base.getSystemService(NotificationManager::class.java)
        val channel = "aether-test-${UUID.randomUUID()}"
        val id = 19731
        manager.createNotificationChannel(NotificationChannel(channel, "Test", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(base, channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Test").build()
        try {
            manager.notify("host", id, notification)
            manager.notify(NotificationUtils.TAG, id, notification)
            val postedDeadline = SystemClock.elapsedRealtime() + 5_000
            while (manager.activeNotifications.count { it.id == id } < 2 && SystemClock.elapsedRealtime() < postedDeadline) SystemClock.sleep(50)
            assertEquals(2, manager.activeNotifications.count { it.id == id })
            manager.cancelNotifications()
            val clearedDeadline = SystemClock.elapsedRealtime() + 5_000
            while (manager.activeNotifications.any { it.tag == NotificationUtils.TAG && it.id == id } && SystemClock.elapsedRealtime() < clearedDeadline) SystemClock.sleep(50)
            assertTrue(manager.activeNotifications.any { it.tag == "host" && it.id == id })
            assertFalse(manager.activeNotifications.any { it.tag == NotificationUtils.TAG && it.id == id })
        } finally {
            manager.cancel("host", id)
            manager.cancel(NotificationUtils.TAG, id)
            manager.deleteNotificationChannel(channel)
        }
    }

    @Test
    fun encryptedAccountsSurviveReopeningAndClearingOneDoesNotEraseAnother() {
        val firstName = "storage-test-${UUID.randomUUID()}"
        val secondName = "storage-test-${UUID.randomUUID()}"
        val context = AetherStorageContext(base)
        val first = EncryptedStorage.preferences(context, firstName)
        val second = EncryptedStorage.preferences(context, secondName)
        try {
            assertTrue(first.edit().putString("test-key", "first-account-value").commit())
            assertTrue(second.edit().putString("test-key", "second-account-value").commit())
            val reopened = EncryptedStorage.preferences(AetherStorageContext(base), firstName)
            assertEquals("first-account-value", reopened.getString("test-key", null))
            assertTrue(reopened.edit().clear().commit())
            assertFalse(first.contains("test-key"))
            assertEquals("second-account-value", second.getString("test-key", null))
            assertFalse(base.getSharedPreferences(EncryptedStorage.prefsFileName(firstName), Context.MODE_PRIVATE).contains("test-key"))
            val raw = base.getSharedPreferences(AetherStorageContext.PREFERENCES_FILE, Context.MODE_PRIVATE).all
            assertFalse(raw.values.any { it == "first-account-value" || it == "second-account-value" })
        } finally {
            first.edit().clear().commit()
            second.edit().clear().commit()
        }
    }

    @Test
    fun scopedClearKeepsNewValuesAndReusingAnEditorDoesNotClearAgain() {
        val context = AetherStorageContext(base)
        val name = "editor-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            preferences.edit().putString("old", "value").commit()
            val editor = preferences.edit().putString("old", "replacement").clear()
            assertTrue(editor.commit())
            assertEquals("replacement", preferences.getString("old", null))
            preferences.edit().putBoolean("later", true).commit()
            editor.putLong("number", 42L).commit()
            assertTrue(preferences.getBoolean("later", false))
            assertEquals(42L, context.getSharedPreferences(name, Context.MODE_PRIVATE).getLong("number", 0))
            assertEquals(setOf("old", "later", "number"), preferences.all.keys)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun filesAndDatabasesAreStoredOutsideAutomaticBackupAndLauncherData() {
        val context = AetherStorageContext(base)
        assertTrue(context.filesDir.canonicalPath.startsWith(base.noBackupFilesDir.canonicalPath + File.separator))
        assertTrue(context.getDatabasePath("test.db").canonicalPath.startsWith(base.noBackupFilesDir.canonicalPath + File.separator))
        assertFalse(context.filesDir.canonicalPath.startsWith(base.filesDir.canonicalPath + File.separator))
        val databaseName = "storage-${UUID.randomUUID()}.db"
        try {
            context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use {
                it.execSQL("CREATE TABLE sample (value TEXT)")
                it.execSQL("INSERT INTO sample VALUES ('test')")
            }
            assertTrue(context.getDatabasePath(databaseName).isFile)
            assertFalse(base.getDatabasePath(databaseName).exists())
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
