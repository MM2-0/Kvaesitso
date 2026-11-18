/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.mm20.launcher2.ui.aether

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Build
import org.junit.Assume.assumeTrue
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the separately installed production APK, so force-stop really tests process death. */
@androidx.annotation.RequiresApi(29)
class AetherEmbeddedClientTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val target = "com.dimitris.telos.debug"

    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || result.size >= 1024) return
            result.add(node)
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(automation.rootInActiveWindow)
        return result
    }

    private fun awaitNode(label: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val until = SystemClock.uptimeMillis() + 60_000
        while (SystemClock.uptimeMillis() < until) {
            val current = nodes()
            current.firstOrNull { it.isVisibleToUser && predicate(it) }?.let { return it }
            // The upstream F-Droid first-run flow offers an optional push distributor.
            // Keep the isolated offline test opted out, through its actual UI.
            if (current.any { it.text?.toString() == "Push Notification Setup" }) {
                current.firstOrNull { it.text?.toString() == "Don't show again" }?.let(::click)
            }
            SystemClock.sleep(300)
        }
        screenshot("failure")
        error("Missing $label; window=" + nodes().mapNotNull { it.text ?: it.contentDescription }.joinToString().take(4000))
    }

    private fun click(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            current = current.parent
        }
        error("No clickable ancestor for ${node.text ?: node.contentDescription}")
    }

    private fun openAether() {
        val result = shell("am start -W -n $target/de.mm20.launcher2.ui.settings.SettingsActivity --es de.mm20.launcher2.settings.ROUTE settings/aether")
        assertTrue(result, !result.contains("Error:"))
    }

    private fun scrollToText(text: String): AccessibilityNodeInfo {
        repeat(20) {
            nodes().firstOrNull { it.text?.toString() == text }?.let { return it }
            nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(250)
        }
        return awaitNode(text) { it.text?.toString() == text }
    }

    private fun openAccounts() {
        click(awaitNode("account menu") { it.contentDescription?.toString() == "Your Profile Picture" })
        click(scrollToText("Accounts"))
        awaitNode("account switcher") { it.text?.toString() == "Select Account" }
    }

    private fun login(publicKey: String) {
        val field = awaitNode("login key field") { it.isEditable }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, publicKey)
        }))
        if (shell("dumpsys input_method").contains("mInputShown=true")) {
            shell("input keyevent KEYCODE_BACK")
        }
        click(awaitNode("Login") { it.text?.toString() == "Login" && it.isEnabled })
        awaitNode("logged-in account") { it.contentDescription?.toString() == "Your Profile Picture" }
    }

    private fun openSettings() {
        click(awaitNode("account menu") { it.contentDescription?.toString() == "Your Profile Picture" })
        click(scrollToText("Settings"))
        awaitNode("settings search") { it.text?.toString() == "Search settings" }
    }

    private fun screenshot(name: String) {
        // Let the existing account crossfade and host entry animation settle.
        SystemClock.sleep(400)
        val bitmap = automation.takeScreenshot() ?: return
        val resolver = instrumentation.context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "aether-$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TelosAetherValidation")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }) ?: error("Unable to create screenshot")
        try {
            resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun embeddedLoginSettingsAndAccountSurviveProcessDeath() {
        assumeTrue("MediaStore screenshot export requires Android 10", Build.VERSION.SDK_INT >= 29)
        // A public key only: no signing, publication, private accounts or external client APK.
        val originalFont = shell("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        val originalAutoRotate = shell("settings get system accelerometer_rotation").trim().toIntOrNull() ?: 1
        val originalRotation = shell("settings get system user_rotation").trim().toIntOrNull() ?: 0
        shell("svc wifi disable")
        shell("svc data disable")
        try {
            shell("pm grant $target android.permission.POST_NOTIFICATIONS")
            openAether()
            awaitNode("initial login form") { it.isEditable }
            screenshot("login")
            login("npub10xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqpkge6d")
            screenshot("feed")
            openAccounts()
            click(awaitNode("add account") { it.text?.toString() == "Add New Account" })
            login("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z")
            openAccounts()
            awaitNode("first stored account") { it.text?.toString() == "npub10xl…vqpkge6d" }
            awaitNode("second stored account") { it.text?.toString() == "npub1gcx…wqlfnj5z" }
            screenshot("multiple-accounts")
            click(awaitNode("switch to first account") { it.text?.toString() == "npub10xl…vqpkge6d" })
            // Switching accounts recreates the client shell and dismisses its sheet.
            awaitNode("switched account") { it.contentDescription?.toString() == "Your Profile Picture" }
            openSettings()
            screenshot("settings")
            shell("settings put system font_scale 2.0")
            SystemClock.sleep(1_000)
            awaitNode("large-font settings") { it.text?.toString() == "Search settings" }
            screenshot("settings-large-font")
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation 1")
            SystemClock.sleep(1_000)
            awaitNode("landscape settings") { it.text?.toString() == "Search settings" }
            screenshot("settings-large-font-landscape")
            shell("settings put system user_rotation $originalRotation")
            shell("settings put system accelerometer_rotation $originalAutoRotate")
            shell("settings put system font_scale $originalFont")
            shell("input keyevent KEYCODE_HOME")
            shell("am force-stop $target")
            openAether()
            openAccounts()
            awaitNode("persisted first account") { it.text?.toString() == "npub10xl…vqpkge6d" }
            awaitNode("persisted second account") { it.text?.toString() == "npub1gcx…wqlfnj5z" }
            screenshot("restored-accounts")
        } finally {
            shell("settings put system user_rotation $originalRotation")
            shell("settings put system accelerometer_rotation $originalAutoRotate")
            shell("settings put system font_scale $originalFont")
            shell("svc wifi enable")
            shell("svc data enable")
        }
    }
}
