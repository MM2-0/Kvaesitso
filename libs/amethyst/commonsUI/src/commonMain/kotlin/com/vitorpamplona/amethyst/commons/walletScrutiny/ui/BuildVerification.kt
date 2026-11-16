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
package com.vitorpamplona.amethyst.commons.walletScrutiny.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.build_files_checked
import com.vitorpamplona.amethyst.commons.resources.build_hide_report
import com.vitorpamplona.amethyst.commons.resources.build_show_report
import com.vitorpamplona.amethyst.commons.resources.build_verdict_ftbfs
import com.vitorpamplona.amethyst.commons.resources.build_verdict_nosource
import com.vitorpamplona.amethyst.commons.resources.build_verdict_not_reproducible
import com.vitorpamplona.amethyst.commons.resources.build_verdict_notag
import com.vitorpamplona.amethyst.commons.resources.build_verdict_obfuscated
import com.vitorpamplona.amethyst.commons.resources.build_verdict_reproducible
import com.vitorpamplona.amethyst.commons.resources.build_verdict_spam
import com.vitorpamplona.amethyst.commons.resources.build_verdict_unknown
import com.vitorpamplona.amethyst.commons.resources.build_verdict_warning
import com.vitorpamplona.amethyst.commons.resources.build_verification_label
import com.vitorpamplona.amethyst.commons.resources.build_verified_by
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.walletScrutiny.VerdictTone
import com.vitorpamplona.amethyst.commons.walletScrutiny.verdictToneOf
import com.vitorpamplona.amethyst.commons.walletScrutiny.verifiedReleaseLine
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.BuildVerificationEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatus

/**
 * Entry for a WalletScrutiny reproducible-build verification (kind 30301): the verdict first and
 * largest, because it is the one thing a reader came for, then the release it covers (product id,
 * version, platform), the verifier, and the markdown report folded away behind a button — reports
 * run to pages of build logs and would otherwise bury the feed.
 */
@Composable
fun RenderBuildVerification(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? BuildVerificationEvent ?: return

    val status = remember(noteEvent) { noteEvent.status() }
    val statusCode = remember(noteEvent) { noteEvent.statusCode() }
    val release = remember(noteEvent) { verifiedReleaseLine(noteEvent.productId(), noteEvent.version(), noteEvent.platform()) }
    val description = remember(noteEvent) { noteEvent.description() }
    val report = remember(noteEvent) { noteEvent.report() }
    val fileCount = remember(noteEvent) { noteEvent.hashes().size }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                symbol = MaterialSymbols.Security,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringRes(Res.string.build_verification_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        VerdictBanner(status, statusCode, Modifier.padding(top = 6.dp))

        release?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (fileCount > 0) {
            Text(
                text = pluralStringRes(Res.plurals.build_files_checked, fileCount, fileCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text = stringRes(Res.string.build_verified_by),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
            )
            Spacer(Modifier.width(5.dp))
            UserPicture(userHex = noteEvent.pubKey, size = 20.dp, accountViewModel = accountViewModel, nav = nav)
            Spacer(Modifier.width(5.dp))
            LoadUser(baseUserHex = noteEvent.pubKey) { user ->
                if (user != null) {
                    UsernameDisplay(user, fontWeight = FontWeight.Normal, accountViewModel = accountViewModel)
                }
            }
        }

        if (!makeItShort && report != null) {
            var expanded by remember(noteEvent.id) { mutableStateOf(false) }

            TextButton(onClick = { expanded = !expanded }) {
                Text(stringRes(if (expanded) Res.string.build_hide_report else Res.string.build_show_report))
            }

            if (expanded) {
                val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }
                LocalRichTextPlatform.current.Markdown(
                    content = report,
                    tags = tags,
                    canPreview = canPreview,
                    quotesLeft = quotesLeft,
                    backgroundColor = backgroundColor,
                    callbackUri = baseNote.toNostrUri(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

/** The verdict, in the tone [verdictToneOf] gives it, on a tinted band the reader cannot miss. */
@Composable
private fun VerdictBanner(
    status: BuildStatus?,
    statusCode: String?,
    modifier: Modifier = Modifier,
) {
    val tone = verdictToneOf(status)
    val color =
        when (tone) {
            VerdictTone.TRUSTED -> MaterialTheme.colorScheme.allGoodColor
            VerdictTone.FAILED -> MaterialTheme.colorScheme.error
            VerdictTone.CAUTION -> MaterialTheme.colorScheme.warningColor
            VerdictTone.UNKNOWN -> MaterialTheme.colorScheme.grayText
        }
    val symbol: MaterialSymbol =
        when (tone) {
            VerdictTone.TRUSTED -> MaterialSymbols.CheckCircle
            VerdictTone.FAILED -> MaterialSymbols.Cancel
            VerdictTone.CAUTION -> MaterialSymbols.Warning
            VerdictTone.UNKNOWN -> MaterialSymbols.Info
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(SmallBorder)
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = color,
            filled = true,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = verdictLabel(status, statusCode),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun verdictLabel(
    status: BuildStatus?,
    statusCode: String?,
): String =
    when (status) {
        BuildStatus.REPRODUCIBLE -> stringRes(Res.string.build_verdict_reproducible)
        BuildStatus.NOT_REPRODUCIBLE -> stringRes(Res.string.build_verdict_not_reproducible)
        BuildStatus.FTBFS -> stringRes(Res.string.build_verdict_ftbfs)
        BuildStatus.SPAM -> stringRes(Res.string.build_verdict_spam)
        BuildStatus.NOTAG -> stringRes(Res.string.build_verdict_notag)
        BuildStatus.NOSOURCE -> stringRes(Res.string.build_verdict_nosource)
        BuildStatus.WARNING -> stringRes(Res.string.build_verdict_warning)
        BuildStatus.OBFUSCATED -> stringRes(Res.string.build_verdict_obfuscated)
        null -> stringRes(Res.string.build_verdict_unknown, statusCode.orEmpty())
    }
