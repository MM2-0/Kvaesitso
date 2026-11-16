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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeEditDraftTo
import com.vitorpamplona.amethyst.commons.model.rooms.roomFor
import com.vitorpamplona.amethyst.commons.model.textNoteModifications
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.broadcast
import com.vitorpamplona.amethyst.commons.resources.concord_ban_user
import com.vitorpamplona.amethyst.commons.resources.concord_ban_user_body
import com.vitorpamplona.amethyst.commons.resources.concord_ban_user_title
import com.vitorpamplona.amethyst.commons.resources.concord_make_admin
import com.vitorpamplona.amethyst.commons.resources.concord_remove_admin
import com.vitorpamplona.amethyst.commons.resources.copied_note_id_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copied_note_text_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copied_user_id_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.edit_draft
import com.vitorpamplona.amethyst.commons.resources.quick_action_block
import com.vitorpamplona.amethyst.commons.resources.quick_action_block_dialog_btn
import com.vitorpamplona.amethyst.commons.resources.quick_action_copy_note_id
import com.vitorpamplona.amethyst.commons.resources.quick_action_copy_text
import com.vitorpamplona.amethyst.commons.resources.quick_action_copy_user_id
import com.vitorpamplona.amethyst.commons.resources.quick_action_delete
import com.vitorpamplona.amethyst.commons.resources.quick_action_delete_dialog_btn
import com.vitorpamplona.amethyst.commons.resources.quick_action_dont_show_again_button
import com.vitorpamplona.amethyst.commons.resources.quick_action_follow
import com.vitorpamplona.amethyst.commons.resources.quick_action_mute_thread
import com.vitorpamplona.amethyst.commons.resources.quick_action_report
import com.vitorpamplona.amethyst.commons.resources.quick_action_request_deletion_alert_body
import com.vitorpamplona.amethyst.commons.resources.quick_action_request_deletion_alert_body_channel
import com.vitorpamplona.amethyst.commons.resources.quick_action_request_deletion_alert_body_group
import com.vitorpamplona.amethyst.commons.resources.quick_action_request_deletion_alert_body_private
import com.vitorpamplona.amethyst.commons.resources.quick_action_request_deletion_alert_title
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.quick_action_share_browser_link
import com.vitorpamplona.amethyst.commons.resources.quick_action_unfollow
import com.vitorpamplona.amethyst.commons.resources.quick_action_unmute_thread
import com.vitorpamplona.amethyst.commons.resources.report_dialog_block_hide_user_btn
import com.vitorpamplona.amethyst.commons.resources.report_dialog_blocking_a_user
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.report.ReportNoteDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.LightRedColor
import com.vitorpamplona.amethyst.commons.ui.theme.QuickActionPopupShadow
import com.vitorpamplona.amethyst.commons.ui.theme.SmallestBorder
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.secondaryButtonBackground
import com.vitorpamplona.amethyst.commons.util.njumpLink
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.bounties.bountyBaseReward
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs

/** [color] with its HSL lightness raised by [amount], opaque, as `ColorUtils` computed it. */
private fun lightenColor(
    color: Color,
    amount: Float,
): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2f

    var hue = 0f
    var saturation = 0f
    if (delta != 0f) {
        hue =
            when (max) {
                r -> ((g - b) / delta).mod(6f)
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            } * 60f
        saturation = delta / (1f - abs(2f * lightness - 1f))
    }

    return Color.hsl(
        hue = hue.mod(360f),
        saturation = saturation.coerceIn(0f, 1f),
        lightness = (lightness + amount).coerceIn(0f, 1f),
    )
}

val externalLinkForUser = { user: User ->
    njumpLink(user.toNProfile())
}

val nipLink = { nipNumber: String ->
    "https://nostrhub.io/$nipNumber"
}

val graspLink = { graspNumber: String ->
    "https://gitworkshop.dev/danconwaydev.com/grasp/tree/master/$graspNumber.md"
}

val externalLinkForNote = { note: Note ->
    if (note is AddressableNote) {
        when {
            note.event?.bountyBaseReward() != null -> "https://nostrbounties.com/b/${note.toNAddr()}"
            note.event is FollowSetEvent -> "https://listr.lol/a/${note.toNAddr()}"
            note.event is StarterPackEvent -> "https://following.space/d/${note.address.dTag}?p=${note.address.pubKeyHex}"
            else -> njumpLink(note.toNAddr())
        }
    } else {
        njumpLink(note.toNEvent())
    }
}

@Composable
fun LongPressToQuickAction(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    content: @Composable (() -> Unit) -> Unit,
) {
    val popupExpanded = remember { mutableStateOf(false) }

    // Box anchors the Popup to the note card; otherwise its parent resolves to the
    // enclosing LazyColumn and `Alignment.Center` centers on the whole list.
    Box {
        content { popupExpanded.value = true }

        if (popupExpanded.value) {
            NoteQuickActionMenu(
                note = baseNote,
                onDismiss = { popupExpanded.value = false },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun NoteQuickActionMenu(
    note: Note,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NoteQuickActionMenu(
        note = note,
        onDismiss = onDismiss,
        onWantsToEditDraft = {
            nav.nav {
                routeEditDraftTo(note, accountViewModel.account)
            }
        },
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun NoteQuickActionMenu(
    note: Note,
    onDismiss: () -> Unit,
    onWantsToEditDraft: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val showDeleteAlertDialog = remember { mutableStateOf(false) }
    val showBlockAlertDialog = remember { mutableStateOf(false) }
    val showReportDialog = remember { mutableStateOf(false) }

    RenderMainPopup(
        accountViewModel,
        note,
        onDismiss,
        showBlockAlertDialog,
        showDeleteAlertDialog,
        showReportDialog,
        onWantsToEditDraft,
    )

    if (showDeleteAlertDialog.value) {
        DeleteAlertDialog(note, accountViewModel) {
            showDeleteAlertDialog.value = false
            onDismiss()
        }
    }

    if (showBlockAlertDialog.value) {
        BlockAlertDialog(note, accountViewModel) {
            showBlockAlertDialog.value = false
            onDismiss()
        }
    }

    if (showReportDialog.value) {
        ReportNoteDialog(note, accountViewModel) {
            showReportDialog.value = false
            onDismiss()
        }
    }
}

@Composable
private fun RenderMainPopup(
    accountViewModel: AccountViewModel,
    note: Note,
    onDismiss: () -> Unit,
    showBlockAlertDialog: MutableState<Boolean>,
    showDeleteAlertDialog: MutableState<Boolean>,
    showReportDialog: MutableState<Boolean>,
    onWantsToEditDraft: () -> Unit,
) {
    Popup(onDismissRequest = onDismiss, alignment = Alignment.Center) {
        val backgroundColor =
            if (MaterialTheme.colorScheme.isLight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryButtonBackground
            }

        Card(
            modifier = QuickActionPopupShadow,
            shape = SmallestBorder,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
        ) {
            CardBody(accountViewModel, note, onDismiss, showBlockAlertDialog, showDeleteAlertDialog, showReportDialog, onWantsToEditDraft)
        }
    }
}

@Composable
fun CardBody(
    accountViewModel: AccountViewModel,
    note: Note,
    onDismiss: () -> Unit,
    showBlockAlertDialog: MutableState<Boolean>,
    showDeleteAlertDialog: MutableState<Boolean>,
    showReportDialog: MutableState<Boolean>,
    onWantsToEditDraft: () -> Unit,
) {
    val quickActionShareBrowserLinkStr = stringRes(Res.string.quick_action_share_browser_link)
    val quickActionShareStr = stringRes(Res.string.quick_action_share)
    val notice = rememberShortNotice()
    val sharer = rememberTextSharer()
    val primaryLight = lightenColor(MaterialTheme.colorScheme.primary, 0.1f)
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val showToast = { stringRes: StringResource ->
        scope.launch {
            notice.show(loadStringRes(stringRes))
        }
    }

    val isOwnNote = accountViewModel.isLoggedUser(note.author)
    val isFollowingUser = !isOwnNote && accountViewModel.isFollowing(note.author)

    // Concord moderation: only present when this account may actually act.
    val canConcordBan = remember(note) { accountViewModel.account.concord.concordBanTarget(note) != null }
    val concordAdmin = remember(note) { accountViewModel.account.concord.concordAdminTarget(note) }
    val showConcordBanDialog = remember { mutableStateOf(false) }

    if (showConcordBanDialog.value) {
        QuickActionAlertDialogOneButton(
            title = stringRes(Res.string.concord_ban_user_title),
            textContent = stringRes(Res.string.concord_ban_user_body),
            buttonIcon = MaterialSymbols.Gavel,
            buttonText = stringRes(Res.string.concord_ban_user),
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor = LightRedColor,
                    contentColor = Color.White,
                ),
            onClickDoOnce = {
                accountViewModel.banConcordMember(note)
                showConcordBanDialog.value = false
                onDismiss()
            },
            onDismiss = { showConcordBanDialog.value = false },
        )
    }

    // "Copy Text" copies the version on screen: an edited post renders its newest modification
    // by default (EditState.updateModifications), and the 3-dot menu already copies that one.
    // Reading `edits` is a hard-referenced in-memory fold, so no cache scan here.
    val noteVersionToCopy = remember(note) { note.textNoteModifications().lastOrNull() ?: note }

    // When the rendered note was translated, tapping Copy Text opens a chooser
    // (Copy Original / Copy Translated) on top of this popup; the popup stays up
    // until the flow resolves so the chooser survives in composition.
    val copyNoteText =
        copyNoteTextAction(
            accountViewModel = accountViewModel,
            onCopied = {
                showToast(Res.string.copied_note_text_to_clipboard)
                onDismiss()
            },
            onDismiss = onDismiss,
        )

    Column(modifier = Modifier.width(IntrinsicSize.Min)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            NoteQuickActionItem(
                icon = MaterialSymbols.ContentCopy,
                label = stringRes(Res.string.quick_action_copy_text),
            ) {
                copyNoteText(note, noteVersionToCopy)
            }
            VerticalDivider(color = primaryLight)
            NoteQuickActionItem(
                MaterialSymbols.AlternateEmail,
                stringRes(Res.string.quick_action_copy_user_id),
            ) {
                note.author?.let {
                    scope.launch {
                        clipboardManager.setText(it.toNostrUri())
                        showToast(Res.string.copied_user_id_to_clipboard)
                        onDismiss()
                    }
                }
            }
            VerticalDivider(color = primaryLight)
            NoteQuickActionItem(
                MaterialSymbols.FormatQuote,
                stringRes(Res.string.quick_action_copy_note_id),
            ) {
                scope.launch {
                    clipboardManager.setText(note.toNostrUri())
                    showToast(Res.string.copied_note_id_to_clipboard)
                    onDismiss()
                }
            }

            if (!isOwnNote) {
                VerticalDivider(color = primaryLight)

                NoteQuickActionItem(
                    MaterialSymbols.Block,
                    stringRes(Res.string.quick_action_block),
                ) {
                    if (accountViewModel.account.settings.hideBlockAlertDialog) {
                        note.author?.let { accountViewModel.hide(it) }
                        onDismiss()
                    } else {
                        showBlockAlertDialog.value = true
                    }
                }

                VerticalDivider(color = primaryLight)

                val isMuted = accountViewModel.isThreadMutedFor(note)
                // Every NIP-28 message shares one thread root — the channel — so "Mute thread" on a
                // public chat can only ever hide that channel's ENTIRE content, and invisibly: the
                // Messages row has no such check, so the room still lists while its messages vanish.
                // "Mute notifications" owns silencing a public chat now. Unmute stays reachable so
                // anyone already caught by a legacy mute can escape from the message in front of them.
                if (note.event !is ChannelMessageEvent || isMuted) {
                    NoteQuickActionItem(
                        MaterialSymbols.AutoMirrored.VolumeOff,
                        stringRes(
                            if (isMuted) {
                                Res.string.quick_action_unmute_thread
                            } else {
                                Res.string.quick_action_mute_thread
                            },
                        ),
                    ) {
                        if (isMuted) {
                            accountViewModel.unmuteThread(note)
                        } else {
                            accountViewModel.muteThread(note)
                        }
                        onDismiss()
                    }
                }
            }
        }
        HorizontalDivider(
            color = primaryLight,
        )
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (isOwnNote) {
                NoteQuickActionItem(
                    MaterialSymbols.Delete,
                    stringRes(Res.string.quick_action_delete),
                ) {
                    if (accountViewModel.account.settings.hideDeleteRequestDialog) {
                        accountViewModel.deleteOwn(note)
                        onDismiss()
                    } else {
                        showDeleteAlertDialog.value = true
                    }
                }
            } else if (isFollowingUser) {
                NoteQuickActionItem(
                    MaterialSymbols.PersonRemove,
                    stringRes(Res.string.quick_action_unfollow),
                ) {
                    accountViewModel.unfollow(note.author!!)
                    onDismiss()
                }
            } else {
                NoteQuickActionItem(
                    MaterialSymbols.PersonAdd,
                    stringRes(Res.string.quick_action_follow),
                ) {
                    accountViewModel.follow(note.author!!)
                    onDismiss()
                }
            }

            // Rumors are rebroadcast as their delivering gift wrap; hidden
            // when the wrap is unknown (the unsigned rumor must never be
            // published).
            if (accountViewModel.canBroadcast(note)) {
                VerticalDivider(color = primaryLight)
                NoteQuickActionItem(
                    icon = MaterialSymbols.Dns,
                    label = stringRes(Res.string.broadcast),
                ) {
                    accountViewModel.broadcast(note)
                    // showSelectTextDialog = true
                    onDismiss()
                }
            }
            VerticalDivider(color = primaryLight)
            if (isOwnNote && note.isDraft()) {
                NoteQuickActionItem(
                    MaterialSymbols.Edit,
                    stringRes(Res.string.edit_draft),
                ) {
                    onWantsToEditDraft()
                }
            } else if (note.isPrivateRumor()) {
                // No external share link for private rumors: nobody can
                // resolve the id from relays and sharing it leaks the id.
            } else {
                NoteQuickActionItem(
                    icon = MaterialSymbols.Share,
                    label = stringRes(Res.string.quick_action_share),
                ) {
                    sharer.share(externalLinkForNote(note), quickActionShareBrowserLinkStr, quickActionShareStr)
                    onDismiss()
                }
            }

            if (!isOwnNote) {
                VerticalDivider(color = primaryLight)

                NoteQuickActionItem(
                    MaterialSymbols.Report,
                    stringRes(Res.string.quick_action_report),
                ) {
                    showReportDialog.value = true
                }
            }

            if (concordAdmin != null) {
                VerticalDivider(color = primaryLight)

                val isAdmin = concordAdmin.third
                NoteQuickActionItem(
                    MaterialSymbols.Shield,
                    stringRes(if (isAdmin) Res.string.concord_remove_admin else Res.string.concord_make_admin),
                ) {
                    accountViewModel.toggleConcordAdmin(note)
                    onDismiss()
                }
            }

            if (canConcordBan) {
                VerticalDivider(color = primaryLight)

                NoteQuickActionItem(
                    MaterialSymbols.Gavel,
                    stringRes(Res.string.concord_ban_user),
                ) {
                    showConcordBanDialog.value = true
                }
            }
        }
    }
}

@Composable
fun NoteQuickActionItem(
    icon: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .size(70.dp)
                .clickable { onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            symbol = icon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(24.dp)
                    .padding(bottom = 5.dp),
            tint = Color.White,
        )
        Text(text = label, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center)
    }
}

/**
 * What a deletion actually does for [note]: a public NIP-09 to relays, a request sent
 * inside its room (a Marmot group or a Concord channel), or a gift-wrapped request to a
 * NIP-17 conversation. The public wording ("deleted from the relays you are connected
 * to") is wrong for the last two.
 */
fun deletionRequestBody(
    note: Note,
    accountViewModel: AccountViewModel,
): StringResource =
    when (note.roomFor(accountViewModel.account)) {
        is ConcordChannel -> Res.string.quick_action_request_deletion_alert_body_channel
        null ->
            if (note.isPrivateRumor()) {
                Res.string.quick_action_request_deletion_alert_body_private
            } else {
                Res.string.quick_action_request_deletion_alert_body
            }
        else -> Res.string.quick_action_request_deletion_alert_body_group
    }

@Composable
fun DeleteAlertDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialog(
        title = stringRes(Res.string.quick_action_request_deletion_alert_title),
        textContent = stringRes(deletionRequestBody(note, accountViewModel)),
        buttonIcon = MaterialSymbols.Delete,
        buttonText = stringRes(Res.string.quick_action_delete_dialog_btn),
        onClickDoOnce = {
            accountViewModel.deleteOwn(note)
            onDismiss()
        },
        onClickDontShowAgain = {
            accountViewModel.deleteOwn(note)
            accountViewModel.account.settings.setHideDeleteRequestDialog()
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun BlockAlertDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) = QuickActionAlertDialog(
    title = stringRes(Res.string.report_dialog_block_hide_user_btn),
    textContent = stringRes(Res.string.report_dialog_blocking_a_user),
    buttonIcon = MaterialSymbols.Block,
    buttonText = stringRes(Res.string.quick_action_block_dialog_btn),
    buttonColors =
        ButtonDefaults.buttonColors(
            containerColor = LightRedColor,
            contentColor = Color.White,
        ),
    onClickDoOnce = {
        note.author?.let { accountViewModel.hide(it) }
        onDismiss()
    },
    onClickDontShowAgain = {
        note.author?.let { accountViewModel.hide(it) }
        accountViewModel.account.settings.setHideBlockAlertDialog()
        onDismiss()
    },
    onDismiss = onDismiss,
)

@Composable
fun QuickActionAlertDialog(
    title: String,
    textContent: String,
    buttonIcon: MaterialSymbol,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onClickDontShowAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialog(
        title = title,
        textContent = textContent,
        icon = {
            Icon(
                symbol = buttonIcon,
                contentDescription = null,
            )
        },
        buttonText = buttonText,
        buttonColors = buttonColors,
        onClickDoOnce = onClickDoOnce,
        onClickDontShowAgain = onClickDontShowAgain,
        onDismiss = onDismiss,
    )
}

@Composable
fun QuickActionAlertDialog(
    title: String,
    textContent: String,
    icon: @Composable () -> Unit,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onClickDontShowAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(textContent) },
        confirmButton = {
            Row(
                modifier =
                    Modifier
                        .padding(all = 8.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onClickDontShowAgain) {
                    Text(stringRes(Res.string.quick_action_dont_show_again_button))
                }
                Button(
                    onClick = onClickDoOnce,
                    colors = buttonColors,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icon()
                        Spacer(Modifier.width(8.dp))
                        Text(buttonText)
                    }
                }
            }
        },
    )
}

@Composable
fun QuickActionAlertDialogOneButton(
    title: String,
    textContent: String,
    buttonIcon: MaterialSymbol,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialogOneButton(
        title = title,
        textContent = textContent,
        icon = {
            Icon(
                symbol = buttonIcon,
                contentDescription = null,
            )
        },
        buttonText = buttonText,
        buttonColors = buttonColors,
        onClickDoOnce = onClickDoOnce,
        onDismiss = onDismiss,
    )
}

@Composable
fun QuickActionAlertDialogOneButton(
    title: String,
    textContent: String,
    icon: @Composable () -> Unit,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(textContent) },
        confirmButton = {
            Row(
                modifier =
                    Modifier
                        .padding(all = 8.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    onClick = onClickDoOnce,
                    colors = buttonColors,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icon()
                        Spacer(Modifier.width(8.dp))
                        Text(buttonText)
                    }
                }
            }
        },
    )
}
