/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.mm20.launcher2.ui.aether

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the separately installed production APK, so force-stop really tests process death. */
@SdkSuppress(minSdkVersion = 29)
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
            nodes().firstOrNull(predicate)?.let { return it }
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

    private fun openSettings() {
        click(awaitNode("account menu") { it.contentDescription?.toString() == "Your Profile Picture" })
        click(awaitNode("Settings") { it.text?.toString() == "Settings" })
        awaitNode("settings search") { it.text?.toString() == "Search settings" }
    }

    private fun screenshot(name: String) {
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
        // A public key only: no signing, publication, private accounts or external client APK.
        shell("svc wifi disable")
        shell("svc data disable")
        try {
            shell("pm grant $target android.permission.POST_NOTIFICATIONS")
            openAether()
            val field = awaitNode("login key field") { it.isEditable }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "npub10xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqpkge6d")
            }))
            shell("input keyevent KEYCODE_BACK")
            nodes().firstOrNull { it.className?.toString() == "android.widget.CheckBox" && !it.isChecked }?.let(::click)
            screenshot("login")
            click(awaitNode("Login") { it.text?.toString() == "Login" && it.isEnabled })
            awaitNode("logged-in account") { it.contentDescription?.toString() == "Your Profile Picture" }
            screenshot("feed")
            openSettings()
            screenshot("settings")
            shell("input keyevent KEYCODE_HOME")
            shell("am force-stop $target")
            openAether()
            openSettings()
            screenshot("restored-settings")
        } finally {
            shell("svc wifi enable")
            shell("svc data enable")
        }
    }
}
