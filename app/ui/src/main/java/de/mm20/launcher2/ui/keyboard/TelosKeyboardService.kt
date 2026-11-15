package de.mm20.launcher2.ui.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.ui.theme.LauncherTheme

enum class KeyboardLanguage {
    Greek,
    English,
}

enum class KeyboardMode {
    Letters,
    Numbers,
    Symbols,
    Emojis,
    Clipboard,
}

object TelosKeyboardState {
    var isIncognitoMode by mutableStateOf(false)
    internal var privateEditor by mutableStateOf(false)
    val privateMode: Boolean
        get() = isIncognitoMode || privateEditor

    var currentLanguage by mutableStateOf(KeyboardLanguage.Greek)
    var keyboardMode by mutableStateOf(KeyboardMode.Letters)
    var isShifted by mutableStateOf(false)
    var isCapsLock by mutableStateOf(false)
    var lastClipboardText by mutableStateOf("")
}

class TelosKeyboardService :
    InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val mLifecycleRegistry = LifecycleRegistry(this)
    private val mSavedStateRegistryController = SavedStateRegistryController.create(this)
    private val mViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = mViewModelStore

    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        // Compose + SavedStateRegistry require attach before restore; skipping this
        // crashes the IME the first time the system binds Telos Keyboard.
        mSavedStateRegistryController.performAttach()
        mSavedStateRegistryController.performRestore(null)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        // onCreateInputView can be called again after a config change; LifecycleRegistry
        // throws if START/RESUME are re-entered from an already-resumed state.
        advanceLifecycleToResumed()
        // The window recomposer looks up owners from the IME decor, not only its input view.
        window?.window?.decorView?.let(::installViewOwners)
        composeView?.let { old ->
            old.disposeComposition()
            (old.parent as? ViewGroup)?.removeView(old)
        }
        val view =
            ComposeView(this).also { host ->
                installViewOwners(host)
                host.setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                val keyboardContent: @Composable () -> Unit = {
                    TelosKeyboardView(
                        onKeyTap = ::handleKeyTap,
                        onBackspace = ::handleBackspace,
                        onEnter = ::handleEnter,
                        onSpace = ::handleSpace,
                        onToggleIncognito = ::toggleIncognito,
                        onSwitchLanguage = ::switchLanguage,
                        onPasteText = ::handlePasteText,
                        onVibrate = ::vibrateKey,
                    )
                }
                host.setContent {
                    LauncherTheme(
                        wallpaperGlass = false,
                        loadingContent = {
                            MaterialTheme(
                                colorScheme =
                                    if (androidx.compose.foundation.isSystemInDarkTheme())
                                        darkColorScheme()
                                    else lightColorScheme()
                            ) {
                                keyboardContent()
                            }
                        },
                    ) {
                        keyboardContent()
                    }
                }
                composeView = host
            }
        return view
    }

    private fun installViewOwners(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        advanceLifecycleToResumed()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    private fun advanceLifecycleToResumed() {
        if (!mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (!mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        TelosKeyboardBuffer.clear()
        TelosKeyboardState.lastClipboardText = ""
        TelosKeyboardState.privateEditor = false
        val info = attribute ?: return
        TelosKeyboardState.keyboardMode =
            when (info.inputType and EditorInfo.TYPE_MASK_CLASS) {
                EditorInfo.TYPE_CLASS_NUMBER,
                EditorInfo.TYPE_CLASS_PHONE,
                EditorInfo.TYPE_CLASS_DATETIME -> KeyboardMode.Numbers
                else -> KeyboardMode.Letters
            }
        val isPassword =
            (info.inputType and EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT &&
                ((info.inputType and EditorInfo.TYPE_MASK_VARIATION) ==
                    EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                    (info.inputType and EditorInfo.TYPE_MASK_VARIATION) ==
                        EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    (info.inputType and EditorInfo.TYPE_MASK_VARIATION) ==
                        EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)

        val noLearning = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        val numericPassword =
            (info.inputType and EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_NUMBER &&
                (info.inputType and EditorInfo.TYPE_MASK_VARIATION) ==
                    EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
        TelosKeyboardState.privateEditor = isPassword || numericPassword || noLearning
        if (!TelosKeyboardState.privateMode) updateClipboardSnippet()
    }

    override fun onFinishInput() {
        TelosKeyboardBuffer.clear()
        TelosKeyboardState.lastClipboardText = ""
        TelosKeyboardState.privateEditor = false
        super.onFinishInput()
    }

    private fun handleKeyTap(char: String) {
        val ic = currentInputConnection ?: return
        val textToInsert =
            when {
                TelosKeyboardState.isShifted || TelosKeyboardState.isCapsLock -> char.uppercase()
                else -> char.lowercase()
            }
        ic.commitText(textToInsert, 1)
        if (TelosKeyboardState.privateMode) TelosKeyboardBuffer.clear()
        else TelosKeyboardBuffer.appendChar(textToInsert, TelosKeyboardState.currentLanguage)

        if (TelosKeyboardState.isShifted && !TelosKeyboardState.isCapsLock) {
            TelosKeyboardState.isShifted = false
        }
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        TelosKeyboardBuffer.backspace(TelosKeyboardState.currentLanguage)
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        TelosKeyboardBuffer.clear()
        val info = currentInputEditorInfo
        val action = keyboardEditorAction(info?.actionId ?: 0, info?.imeOptions ?: 0)
        if (action != 0) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun handleSpace() {
        currentInputConnection?.commitText(" ", 1)
        if (TelosKeyboardBuffer.currentWordBuffer.isNotBlank()) {
            TelosKeyboardDictionary.learnWord(
                TelosKeyboardBuffer.currentWordBuffer,
                TelosKeyboardState.privateMode,
            )
        }
        TelosKeyboardBuffer.clear()
    }

    private fun handlePasteText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun toggleIncognito() {
        TelosKeyboardState.isIncognitoMode = !TelosKeyboardState.isIncognitoMode
        if (TelosKeyboardState.privateMode) {
            TelosKeyboardBuffer.clear()
            TelosKeyboardState.lastClipboardText = ""
        }
    }

    private fun switchLanguage() {
        TelosKeyboardState.currentLanguage =
            if (TelosKeyboardState.currentLanguage == KeyboardLanguage.Greek)
                KeyboardLanguage.English
            else KeyboardLanguage.Greek
    }

    private fun updateClipboardSnippet() {
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.text?.toString()
                if (!text.isNullOrBlank()) {
                    TelosKeyboardState.lastClipboardText = text.take(120)
                }
            }
        }
    }

    private fun vibrateKey() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION") v?.vibrate(15)
            }
        }
    }

    override fun onDestroy() {
        composeView?.disposeComposition()
        composeView = null
        TelosKeyboardBuffer.clear()
        TelosKeyboardState.lastClipboardText = ""
        if (mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (mLifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        if (
            mLifecycleRegistry.currentState != Lifecycle.State.INITIALIZED &&
                mLifecycleRegistry.currentState != Lifecycle.State.DESTROYED
        ) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        mViewModelStore.clear()
        super.onDestroy()
    }
}

@Composable
fun TelosKeyboardView(
    onKeyTap: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onToggleIncognito: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onPasteText: (String) -> Unit,
    onVibrate: () -> Unit,
) {
    val state = TelosKeyboardState
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(modifier = Modifier.fillMaxWidth().background(surfaceColor).padding(bottom = 6.dp)) {
        // Toolbar / Action Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onVibrate()
                        state.keyboardMode =
                            if (state.keyboardMode == KeyboardMode.Emojis) KeyboardMode.Letters
                            else KeyboardMode.Emojis
                    }
                ) {
                    Text(
                        if (state.keyboardMode == KeyboardMode.Emojis) "ABC" else "😊",
                        fontSize = 18.sp,
                    )
                }

                IconButton(
                    onClick = {
                        onVibrate()
                        state.keyboardMode =
                            if (state.keyboardMode == KeyboardMode.Clipboard) KeyboardMode.Letters
                            else KeyboardMode.Clipboard
                    }
                ) {
                    Icon(
                        painterResource(BaseR.drawable.content_copy_24px),
                        contentDescription = "Clipboard",
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Incognito Privacy Mode Indicator
                IconButton(
                    onClick = {
                        onVibrate()
                        onToggleIncognito()
                    }
                ) {
                    Icon(
                        painterResource(BaseR.drawable.lock_24px),
                        contentDescription = "Incognito",
                        tint =
                            if (state.privateMode) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Quick Clipboard Snippet Bar
            if (state.lastClipboardText.isNotBlank()) {
                Surface(
                    modifier =
                        Modifier.weight(1f)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onVibrate()
                                onPasteText(state.lastClipboardText)
                            },
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = "📋 " + state.lastClipboardText,
                        maxLines = 1,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            Text(
                text = if (state.currentLanguage == KeyboardLanguage.Greek) "ΕΛ" else "EN",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = primaryColor,
                modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onSwitchLanguage()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Auto-Correction / Suggestion Bar
        if (TelosKeyboardBuffer.activeSuggestions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(TelosKeyboardBuffer.activeSuggestions.toList()) { sugWord ->
                    Surface(
                        modifier =
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                onVibrate()
                                onPasteText("$sugWord ")
                                TelosKeyboardDictionary.learnWord(sugWord, state.privateMode)
                                TelosKeyboardBuffer.clear()
                            },
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = sugWord,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Main Keyboard Area
            when (state.keyboardMode) {
                KeyboardMode.Letters ->
                    LetterKeyboardGrid(
                        onKeyTap,
                        onBackspace,
                        onEnter,
                        onSpace,
                        onVibrate,
                        onSwitchLanguage,
                    )
                KeyboardMode.Numbers ->
                    SymbolKeyboardGrid(
                        isSymbols = false,
                        onKeyTap,
                        onBackspace,
                        onEnter,
                        onSpace,
                        onVibrate,
                    )
                KeyboardMode.Symbols ->
                    SymbolKeyboardGrid(
                        isSymbols = true,
                        onKeyTap,
                        onBackspace,
                        onEnter,
                        onSpace,
                        onVibrate,
                    )
                KeyboardMode.Emojis ->
                    EmojiKeyboardGrid { emoji ->
                        onVibrate()
                        onKeyTap(emoji)
                    }
                KeyboardMode.Clipboard ->
                    ClipboardPanel(state.lastClipboardText, onPasteText, onVibrate)
            }

            // A full-size pointer-input overlay hides the keys from hit testing. Direct key
            // input owns these gestures; the old heuristic overlay also inserted fixed words.

        }
    }
}

@Composable
fun LetterKeyboardGrid(
    onKeyTap: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onVibrate: () -> Unit,
    onSwitchLanguage: () -> Unit,
) {
    val state = TelosKeyboardState
    val rows =
        if (state.currentLanguage == KeyboardLanguage.Greek) {
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("ς", "ε", "ρ", "τ", "υ", "θ", "ι", "ο", "π"),
                listOf("α", "σ", "δ", "φ", "γ", "η", "ξ", "κ", "λ"),
                listOf("ζ", "χ", "ψ", "ω", "β", "ν", "μ"),
            )
        } else {
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m"),
            )
        }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Number Row 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rows[0].forEach { num ->
                KeyboardKey(text = num, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(num)
                }
            }
        }

        // Row 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rows[1].forEach { char ->
                KeyboardKey(text = char, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(char)
                }
            }
        }

        // Row 2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rows[2].forEach { char ->
                KeyboardKey(text = char, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(char)
                }
            }
        }

        // Row 3 (Shift + Letters + Backspace)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shift Key
            Surface(
                modifier =
                    Modifier.weight(1.3f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            if (state.isShifted && !state.isCapsLock) {
                                state.isCapsLock = true
                            } else {
                                state.isShifted = !state.isShifted
                                state.isCapsLock = false
                            }
                        },
                color =
                    if (state.isCapsLock || state.isShifted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (state.isCapsLock) "⇪" else "⇧",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            rows[3].forEach { char ->
                KeyboardKey(text = char, modifier = Modifier.weight(1f)) {
                    onVibrate()
                    onKeyTap(char)
                }
            }

            // Backspace Key
            Surface(
                modifier =
                    Modifier.weight(1.3f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onBackspace()
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⌫", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom Control Row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ?123 Mode Toggle
            Surface(
                modifier =
                    Modifier.weight(1.5f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            state.keyboardMode = KeyboardMode.Numbers
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("?123", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Globe Language Switcher
            Surface(
                modifier =
                    Modifier.weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onSwitchLanguage()
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🌐", fontSize = 16.sp)
                }
            }

            // Comma
            KeyboardKey(text = ",", modifier = Modifier.weight(1f)) {
                onVibrate()
                onKeyTap(",")
            }

            // Spacebar
            Surface(
                modifier =
                    Modifier.weight(4.5f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onSpace()
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (state.currentLanguage == KeyboardLanguage.Greek) "Ελληνικά"
                        else "English",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Period
            KeyboardKey(text = ".", modifier = Modifier.weight(1f)) {
                onVibrate()
                onKeyTap(".")
            }

            // Enter Key
            Surface(
                modifier =
                    Modifier.weight(1.8f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onEnter()
                        },
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "↵",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
fun KeyboardKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val state = TelosKeyboardState
    val displayText =
        when {
            state.isShifted || state.isCapsLock -> text.uppercase()
            else -> text.lowercase()
        }

    Surface(
        modifier =
            modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        border =
            androidx.compose.foundation.BorderStroke(
                0.7.dp,
                de.mm20.launcher2.ui.component.TelosCyber.NeonCyan.copy(alpha = 0.35f),
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun SymbolKeyboardGrid(
    isSymbols: Boolean,
    onKeyTap: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit,
    onVibrate: () -> Unit,
) {
    val state = TelosKeyboardState
    val rows =
        if (!isSymbols) {
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
                listOf("=\\<", "*", "\"", "'", ":", ";", "!", "?", "€", "£"),
            )
        } else {
            listOf(
                listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
                listOf("£", "¥", "$", "¢", "^", "°", "=", "{", "}", "\\"),
                listOf("%", "©", "®", "™", "✓", "[", "]", "<", ">", "§"),
            )
        }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { sym ->
                    KeyboardKey(text = sym, modifier = Modifier.weight(1f)) {
                        onVibrate()
                        onKeyTap(sym)
                    }
                }
            }
        }

        // Bottom Row for Symbols Mode
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier =
                    Modifier.weight(1.5f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            state.keyboardMode = KeyboardMode.Letters
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("ABC", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                modifier =
                    Modifier.weight(1.5f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            state.keyboardMode =
                                if (isSymbols) KeyboardMode.Numbers else KeyboardMode.Symbols
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (isSymbols) "123" else "=\\<",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Surface(
                modifier =
                    Modifier.weight(4f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onSpace()
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("Space", fontSize = 12.sp) }
            }

            Surface(
                modifier =
                    Modifier.weight(1.5f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onVibrate()
                            onBackspace()
                        },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("⌫", fontSize = 18.sp) }
            }
        }
    }
}

@Composable
fun EmojiKeyboardGrid(onSelectEmoji: (String) -> Unit) {
    val emojis =
        listOf(
            "😀",
            "😃",
            "😄",
            "😁",
            "😆",
            "😅",
            "😂",
            "🤣",
            "🥲",
            "🥹",
            "☺️",
            "😊",
            "😇",
            "🙂",
            "🙃",
            "😉",
            "😌",
            "😍",
            "🥰",
            "😘",
            "😗",
            "😙",
            "😚",
            "😋",
            "😛",
            "😝",
            "😜",
            "🤪",
            "🤨",
            "🧐",
            "🤓",
            "😎",
            "🥸",
            "🤩",
            "🥳",
            "😏",
            "😒",
            "😞",
            "😔",
            "😟",
            "😕",
            "🙁",
            "☹️",
            "😣",
            "😖",
            "😫",
            "😩",
            "🥺",
            "😢",
            "😭",
            "😮‍💨",
            "😤",
            "😠",
            "😡",
            "🤬",
            "🤯",
            "😳",
            "🥶",
            "😱",
            "😨",
            "😰",
            "😥",
            "😓",
            "🤗",
            "🤔",
            "🫣",
            "🤭",
            "🫢",
            "🫡",
            "🤫",
            "🫠",
            "🤥",
            "😶",
            "😶‍🌫️",
            "😐",
            "😑",
            "😬",
            "🫨",
            "🫠",
            "🇬🇷",
            "👍",
            "👎",
            "👏",
            "🙌",
            "🫶",
            "❤️",
            "🔥",
            "✨",
            "💯",
            "🎉",
            "🚀",
            "💡",
            "⚽",
            "🇬🇷",
            "🇬🇷",
            "🇬🇷",
        )

    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(emojis) { emoji ->
            Box(
                modifier =
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        onSelectEmoji(emoji)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 22.sp)
            }
        }
    }
}

@Composable
fun ClipboardPanel(lastSnippet: String, onPaste: (String) -> Unit, onVibrate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "📋 Telos Clipboard Manager",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (lastSnippet.isNotBlank()) {
            Surface(
                modifier =
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        onVibrate()
                        onPaste(lastSnippet)
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = lastSnippet,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                "No recent text copied",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
