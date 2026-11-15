package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.mm20.launcher2.ui.keyboard.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KeyboardTouchTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun physicalKeyTapsReachGreekEnglishAndNumberKeys() {
        val typed = StringBuilder()
        TelosKeyboardState.currentLanguage = KeyboardLanguage.Greek
        TelosKeyboardState.keyboardMode = KeyboardMode.Letters
        TelosKeyboardState.isShifted = false
        TelosKeyboardState.isCapsLock = false
        TelosKeyboardState.lastClipboardText = ""
        TelosKeyboardBuffer.clear()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.testTag("ime_panel")) {
                        TelosKeyboardView(
                            onKeyTap = { typed.append(it) },
                            onBackspace = {},
                            onEnter = {},
                            onSpace = {},
                            onToggleIncognito = {},
                            onSwitchLanguage = {},
                            onPasteText = { typed.append(it) },
                            onVibrate = {},
                        )
                    }
                }
            }
        }
        // Use real pointer input: semantic performClick bypasses the broken gesture overlay.
        compose.onNodeWithText("α").performTouchInput { click() }
        compose.runOnIdle { assertEquals("α", typed.toString()) }
        val panel = compose.onNodeWithTag("ime_panel").fetchSemanticsNode().boundsInRoot
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "A normal keyboard must not expand its overlay over the whole window",
            panel.height < window.height * .75f,
        )
        compose.runOnIdle { TelosKeyboardState.currentLanguage = KeyboardLanguage.English }
        compose.onNodeWithText("q").performTouchInput { click() }
        compose.onNodeWithText("?123").performTouchInput { click() }
        compose.onNodeWithText("1").performTouchInput { click() }
        compose.runOnIdle { assertEquals("αq1", typed.toString()) }
    }
}
