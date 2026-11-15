package de.mm20.launcher2.ui.keyboard

import android.view.inputmethod.EditorInfo

internal fun keyboardEditorAction(customAction: Int, imeOptions: Int): Int {
    if (customAction != 0) return customAction
    if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return 0
    return (imeOptions and EditorInfo.IME_MASK_ACTION).takeUnless {
        it == EditorInfo.IME_ACTION_UNSPECIFIED || it == EditorInfo.IME_ACTION_NONE
    } ?: 0
}
