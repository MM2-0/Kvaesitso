package de.mm20.launcher2.ui.component

import android.content.ComponentName
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.database.databaseModule
import de.mm20.launcher2.preferences.preferencesModule
import de.mm20.launcher2.themes.themesModule
import de.mm20.launcher2.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/** Select and bind the production IME, show its actual window and type through InputConnection. */
class KeyboardImeTest {
    @Test
    fun firstImeWindowOpensAndTypesAgainAfterHideAndShow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        fun shell(command: String) =
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
                .bufferedReader()
                .use { it.readText().trim() }
        val ime = ComponentName(context, TelosKeyboardService::class.java).flattenToShortString()
        val previous = shell("settings get secure default_input_method")
        val hardwareSetting = shell("settings get secure show_ime_with_hard_keyboard")
        val ownedKoin = GlobalContext.getOrNull() == null
        if (ownedKoin)
            startKoin {
                androidContext(context.applicationContext)
                modules(preferencesModule, databaseModule, themesModule)
            }
        TelosKeyboardState.currentLanguage = KeyboardLanguage.Greek
        TelosKeyboardState.isShifted = false
        TelosKeyboardState.isCapsLock = false
        val oldInfo = automation.serviceInfo
        val oldFlags = oldInfo.flags
        oldInfo.flags =
            oldFlags or
                android.accessibilityservice.AccessibilityServiceInfo
                    .FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = oldInfo
        var scenario: ActivityScenario<ComponentActivity>? = null
        try {
            val registered = shell("ime list -s")
            assertTrue(
                "Production IME is missing from the test APK: $registered",
                registered.lines().any { it.trim() == ime },
            )
            val enabled = shell("ime enable ${quote(ime)}")
            val selected = shell("ime set ${quote(ime)}")
            assertEquals(
                "$enabled; $selected",
                ime,
                shell("settings get secure default_input_method"),
            )
            shell("settings put secure show_ime_with_hard_keyboard 1")
            val active = ActivityScenario.launch(ComponentActivity::class.java)
            scenario = active
            lateinit var editor: EditText
            active.onActivity { activity ->
                editor = EditText(activity)
                activity.setContentView(editor)
                activity.window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
                editor.requestFocus()
                editor.post {
                    activity
                        .getSystemService(InputMethodManager::class.java)
                        .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            fun tapKey(label: String) {
                val until = SystemClock.uptimeMillis() + 15000
                var bounds: Rect? = null
                var nextShow = 0L
                while (bounds == null && SystemClock.uptimeMillis() < until) {
                    if (SystemClock.uptimeMillis() >= nextShow) {
                        active.onActivity { activity ->
                            activity
                                .getSystemService(InputMethodManager::class.java)
                                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                        }
                        nextShow = SystemClock.uptimeMillis() + 500
                    }
                    for (window in automation.windows) {
                        if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                        val node =
                            window.root?.findAccessibilityNodeInfosByText(label)?.firstOrNull()
                        if (node != null) bounds = Rect().also(node::getBoundsInScreen)
                    }
                    if (bounds == null) SystemClock.sleep(100)
                }
                val target = requireNotNull(bounds) { "Production IME did not show key $label" }
                val downTime = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event =
                        MotionEvent.obtain(
                            downTime,
                            SystemClock.uptimeMillis(),
                            action,
                            target.exactCenterX(),
                            target.exactCenterY(),
                            0,
                        )
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try {
                        assertTrue(automation.injectInputEvent(event, true))
                    } finally {
                        event.recycle()
                    }
                    SystemClock.sleep(30)
                }
                instrumentation.waitForIdleSync()
            }
            fun awaitText(expected: String) {
                val deadline = SystemClock.uptimeMillis() + 5000
                var actual = ""
                while (SystemClock.uptimeMillis() < deadline) {
                    active.onActivity { actual = editor.text.toString() }
                    if (actual == expected) return
                    SystemClock.sleep(50)
                }
                assertEquals(expected, actual)
            }
            tapKey("α")
            awaitText("α")
            active.onActivity { activity ->
                activity
                    .getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(editor.windowToken, 0)
            }
            SystemClock.sleep(300)
            active.onActivity { activity ->
                activity
                    .getSystemService(InputMethodManager::class.java)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            tapKey("σ")
            awaitText("ασ")
        } finally {
            scenario?.close()
            if (previous.isNotBlank() && previous != "null" && previous != ime)
                shell("ime set ${quote(previous)}")
            if (previous != ime) shell("ime disable ${quote(ime)}")
            if (hardwareSetting == "null")
                shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard ${quote(hardwareSetting)}")
            val info = automation.serviceInfo
            info.flags = oldFlags
            automation.serviceInfo = info
            instrumentation.waitForIdleSync()
            if (ownedKoin) stopKoin()
        }
    }
}
