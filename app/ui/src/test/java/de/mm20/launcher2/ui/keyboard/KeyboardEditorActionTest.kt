package de.mm20.launcher2.ui.keyboard

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardEditorActionTest {
    @Test
    fun searchDoneAndSendUseTheEditorsStandardAction() {
        for (action in
            listOf(
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_SEND,
            )) {
            assertEquals(action, keyboardEditorAction(0, action))
        }
    }

    @Test
    fun customAndMultilineEditorsKeepTheirRequestedBehavior() {
        assertEquals(42, keyboardEditorAction(42, EditorInfo.IME_ACTION_DONE))
        assertEquals(0, keyboardEditorAction(0, EditorInfo.IME_ACTION_NONE))
        assertEquals(
            0,
            keyboardEditorAction(
                0,
                EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION,
            ),
        )
    }
}
