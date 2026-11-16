/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copy_text
import com.vitorpamplona.amethyst.commons.resources.copy_text_original
import com.vitorpamplona.amethyst.commons.resources.copy_text_translated
import com.vitorpamplona.amethyst.commons.ui.components.LocalTranslationPlatform
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.note.types.displayedNoteText
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

/** Both texts of a translated note, held while the user picks which one to copy. */
@Immutable
data class CopyTextChoice(
    val original: String,
    val translated: String,
)

/**
 * The "Copy Text" flow shared by every menu that copies an event's text.
 *
 * The copy menus sit far from the `TranslatableRichTextViewer` that rendered (and possibly
 * translated) the note, so instead of plumbing the translated string down the hierarchy this
 * flow re-derives it from [cachedTranslation]: the process-wide translation cache keyed by
 * (content, language settings). By the time any copy menu is reachable the note has been
 * rendered, which is what populated that cache — so a hit means the user is looking at a
 * translation and gets a chooser (Copy Original / Copy Translated); a miss copies directly.
 *
 * What gets copied — and what the cache is keyed on — is [displayedNoteText], the same string
 * the viewer rendered, not the raw event content: a NIP-14 subject is part of what the user is
 * reading and of what was translated.
 *
 * Returns the click handler for the menu entry, taking the note the menu belongs to and the
 * version of it the screen is showing (the same note unless the post was edited — the body
 * comes from the version, the subject from the note itself, exactly as the viewer composes
 * them). [onCopied] runs after the text lands on the
 * clipboard, [onDismiss] when the chooser is cancelled without copying **or** when the note
 * can't be decrypted at all (a read-only account, a refused signer) so the menu still closes
 * instead of hanging on a copy that will never happen. Callers must keep their menu in
 * composition until one of the two runs, because the chooser dialog is emitted from this
 * composable.
 */
@Composable
fun copyNoteTextAction(
    accountViewModel: AccountViewModel,
    onCopied: () -> Unit,
    onDismiss: () -> Unit,
): (note: Note, versionShown: Note) -> Unit {
    val clipboardManager = LocalClipboard.current
    val translator = LocalTranslationPlatform.current
    val scope = rememberCoroutineScope()
    val choice = remember { mutableStateOf<CopyTextChoice?>(null) }

    val copy: (String) -> Unit = { text ->
        scope.launch {
            clipboardManager.setText(text)
            onCopied()
        }
    }

    choice.value?.let { options ->
        CopyTextChooserDialog(
            onCopyOriginal = {
                choice.value = null
                copy(options.original)
            },
            onCopyTranslated = {
                choice.value = null
                copy(options.translated)
            },
            onDismiss = {
                choice.value = null
                onDismiss()
            },
        )
    }

    return { note, versionShown ->
        accountViewModel.decryptOrNull(versionShown) { decrypted ->
            if (decrypted == null) {
                onDismiss()
            } else {
                val original = displayedNoteText(note, decrypted)
                val translated = translator.cachedTranslation(original, accountViewModel)
                if (translated == null) {
                    copy(original)
                } else {
                    choice.value = CopyTextChoice(original, translated)
                }
            }
        }
    }
}

@Composable
fun CopyTextChooserDialog(
    onCopyOriginal: () -> Unit,
    onCopyTranslated: () -> Unit,
    onDismiss: () -> Unit,
) {
    M3ActionDialog(
        title = stringRes(Res.string.copy_text),
        onDismiss = onDismiss,
    ) {
        M3ActionSection {
            M3ActionRow(
                icon = MaterialSymbols.ContentCopy,
                text = stringRes(Res.string.copy_text_original),
                onClick = onCopyOriginal,
            )
            M3ActionRow(
                icon = MaterialSymbols.Translate,
                text = stringRes(Res.string.copy_text_translated),
                onClick = onCopyTranslated,
            )
        }
    }
}
