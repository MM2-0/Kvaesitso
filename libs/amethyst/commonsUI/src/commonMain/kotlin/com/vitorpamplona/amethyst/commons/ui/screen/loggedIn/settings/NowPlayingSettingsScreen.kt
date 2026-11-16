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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Tunestr
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingAccess
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettingsState
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserStatuses
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.now_playing_access_granted
import com.vitorpamplona.amethyst.commons.resources.now_playing_access_needed
import com.vitorpamplona.amethyst.commons.resources.now_playing_access_needed_title
import com.vitorpamplona.amethyst.commons.resources.now_playing_apps
import com.vitorpamplona.amethyst.commons.resources.now_playing_apps_empty
import com.vitorpamplona.amethyst.commons.resources.now_playing_description
import com.vitorpamplona.amethyst.commons.resources.now_playing_grant_access
import com.vitorpamplona.amethyst.commons.resources.now_playing_manage_access
import com.vitorpamplona.amethyst.commons.resources.now_playing_preview_example
import com.vitorpamplona.amethyst.commons.resources.now_playing_preview_live
import com.vitorpamplona.amethyst.commons.resources.now_playing_preview_off
import com.vitorpamplona.amethyst.commons.resources.now_playing_preview_sample
import com.vitorpamplona.amethyst.commons.resources.now_playing_preview_title
import com.vitorpamplona.amethyst.commons.resources.now_playing_preview_you
import com.vitorpamplona.amethyst.commons.resources.now_playing_section_sources
import com.vitorpamplona.amethyst.commons.resources.now_playing_settings
import com.vitorpamplona.amethyst.commons.resources.now_playing_share_in_app
import com.vitorpamplona.amethyst.commons.resources.now_playing_share_in_app_description
import com.vitorpamplona.amethyst.commons.resources.now_playing_share_other_apps
import com.vitorpamplona.amethyst.commons.resources.now_playing_share_other_apps_description
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * Settings for sharing what the user listens to as their NIP-38 music status.
 *
 * [access] is the platform permission other apps' playback needs (Android: notification access);
 * null where none is needed. [appIcon] draws a real app icon where the platform can (Android);
 * without it each app gets a colored monogram.
 */
@Composable
fun NowPlayingSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    access: NowPlayingAccess? = null,
    appIcon: (@Composable (appId: String, label: String) -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.now_playing_settings), nav)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            NowPlayingSettingsContent(
                state = accountViewModel.account.nowPlayingSettings,
                access = access,
                appIcon = appIcon,
                preview = { isOn -> AccountStatusPreview(accountViewModel, nav, isOn) },
            )
        }
    }
}

/**
 * The body of the settings screen: a preview of the status as others see it, the two sources,
 * notification access, and the per-app list. It does not scroll, so front ends with their own
 * settings layout (desktop) can embed it. [showInApp] hides the in-app source where the app has
 * no music or podcast player of its own; [preview] replaces the anonymous preview card with one
 * showing the account (it receives whether sharing is on at all).
 */
@Composable
fun NowPlayingSettingsContent(
    state: NowPlayingSettingsState,
    access: NowPlayingAccess?,
    modifier: Modifier = Modifier,
    showInApp: Boolean = true,
    appIcon: (@Composable (appId: String, label: String) -> Unit)? = null,
    preview: @Composable (isOn: Boolean) -> Unit = { isOn ->
        NowPlayingPreviewCard(
            avatar = { MonogramBadge(stringRes(Res.string.now_playing_preview_you), size = 44, shape = CircleShape) },
            name = stringRes(Res.string.now_playing_preview_you),
            liveStatus = null,
            isOn = isOn,
        )
    },
) {
    val settings by state.flow.collectAsState()
    val isOn = (showInApp && settings.shareInApp) || settings.shareOtherApps
    // One check per resume for both the confirmation row and the alert card.
    val accessGranted = access == null || rememberAccessGranted(access)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringRes(Res.string.now_playing_preview_title))
            preview(isOn)
            Text(
                text = stringRes(Res.string.now_playing_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        SettingsSection(Res.string.now_playing_section_sources) {
            if (showInApp) {
                SettingsSwitchTile(
                    icon = MaterialSymbols.Headphones,
                    title = Res.string.now_playing_share_in_app,
                    description = Res.string.now_playing_share_in_app_description,
                    checked = settings.shareInApp,
                    onCheckedChange = state::setShareInApp,
                )
                SettingsDivider()
            }
            SettingsSwitchTile(
                icon = MaterialSymbols.Apps,
                title = Res.string.now_playing_share_other_apps,
                description = Res.string.now_playing_share_other_apps_description,
                checked = settings.shareOtherApps,
                onCheckedChange = state::setShareOtherApps,
            )
            if (settings.shareOtherApps && access != null && accessGranted) {
                AccessGrantedRow(access)
            }
        }

        if (settings.shareOtherApps && access != null && !accessGranted) {
            AccessNeededCard(access)
        }

        if (settings.shareOtherApps) {
            SettingsSection(Res.string.now_playing_apps) {
                if (settings.knownApps.isEmpty()) {
                    Text(
                        text = stringRes(Res.string.now_playing_apps_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    )
                } else {
                    settings.knownApps.entries
                        .sortedBy { it.value.lowercase() }
                        .forEachIndexed { index, (id, label) ->
                            if (index > 0) SettingsDivider()
                            AppToggleRow(
                                id = id,
                                label = label,
                                checked = id !in settings.blockedApps,
                                appIcon = appIcon,
                                onCheckedChange = { allowed -> state.setAppBlocked(id, !allowed) },
                            )
                        }
                }
            }
        }
    }
}

/** The account's own avatar, name and live music status, exactly as other users see them. */
@Composable
private fun AccountStatusPreview(
    accountViewModel: AccountViewModel,
    nav: INav,
    isOn: Boolean,
) {
    val user = remember(accountViewModel) { accountViewModel.account.userProfile() }
    val name by observeUserName(user, accountViewModel)
    val statuses by observeUserStatuses(user, accountViewModel)
    val music = statuses.firstOrNull { it.address.dTag == UserStatusEvent.MUSIC }

    val avatar = @Composable { ClickableUserPicture(user, 44.dp, accountViewModel) }

    if (music == null) {
        NowPlayingPreviewCard(avatar, name, liveStatus = null, isOn = isOn)
    } else {
        LiveMusicStatus(music, accountViewModel) { text ->
            NowPlayingPreviewCard(avatar, name, liveStatus = text, isOn = isOn)
        }
    }
}

@Composable
private fun LiveMusicStatus(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    content: @Composable (String?) -> Unit,
) {
    val noteState by observeNote(note, accountViewModel)
    content(
        noteState.note.event
            ?.content
            ?.ifBlank { null },
    )
}

/**
 * A miniature of how the status renders under the user's name in feeds: the avatar, the name and
 * the music line with the same icon and color the real status uses. Shows [liveStatus] when one
 * is published, otherwise an example, dimmed while sharing is off.
 */
@Composable
fun NowPlayingPreviewCard(
    avatar: @Composable () -> Unit,
    name: String,
    liveStatus: String?,
    isOn: Boolean,
) {
    val contentAlpha = if (liveStatus != null || isOn) 1f else 0.55f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sized, not clipped: the avatar rounds itself, and the follow mark and user cards it
            // draws sit in the corners of its square box, which a circular clip would cut off.
            Box(Modifier.size(44.dp)) { avatar() }

            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = CustomHashTagIcons.Tunestr,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.placeholderText.copy(alpha = contentAlpha),
                    )
                    Text(
                        text = liveStatus ?: stringRes(Res.string.now_playing_preview_example),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.placeholderText.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            StatusChip(
                when {
                    liveStatus != null -> PreviewChip.LIVE
                    isOn -> PreviewChip.SAMPLE
                    else -> PreviewChip.OFF
                },
            )
        }
    }
}

private enum class PreviewChip { LIVE, SAMPLE, OFF }

@Composable
private fun StatusChip(chip: PreviewChip) {
    val (container, content) =
        when (chip) {
            PreviewChip.LIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        }
    val label =
        when (chip) {
            PreviewChip.LIVE -> Res.string.now_playing_preview_live
            PreviewChip.SAMPLE -> Res.string.now_playing_preview_sample
            PreviewChip.OFF -> Res.string.now_playing_preview_off
        }

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(container)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chip == PreviewChip.LIVE) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(content))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = stringRes(label),
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

/** Sub-row under the "Other apps" tile once access is granted: a quiet confirmation + Manage. */
@Composable
private fun AccessGrantedRow(access: NowPlayingAccess) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 68.dp, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(
            symbol = MaterialSymbols.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringRes(Res.string.now_playing_access_granted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        TextButton(onClick = access::requestAccess) {
            Text(stringRes(Res.string.now_playing_manage_access))
        }
    }
}

/** The one thing blocking other-app sharing, as a tonal card with a single clear action. */
@Composable
private fun AccessNeededCard(access: NowPlayingAccess) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        // A light wash of the error color rather than the full errorContainer: it flags the one
        // step left without shouting louder than the rest of the screen, in either theme.
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    SymbolIcon(
                        symbol = MaterialSymbols.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringRes(Res.string.now_playing_access_needed_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            Text(
                text = stringRes(Res.string.now_playing_access_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = access::requestAccess,
                modifier = Modifier.align(Alignment.End),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringRes(Res.string.now_playing_grant_access))
            }
        }
    }
}

/** The user grants access in system settings and comes back, so this re-checks on every resume. */
@Composable
private fun rememberAccessGranted(access: NowPlayingAccess): Boolean {
    var granted by remember(access) { mutableStateOf(access.hasAccess()) }
    LifecycleResumeEffect(access) {
        granted = access.hasAccess()
        onPauseOrDispose {}
    }
    return granted
}

@Composable
private fun AppToggleRow(
    id: String,
    label: String,
    checked: Boolean,
    appIcon: (@Composable (appId: String, label: String) -> Unit)?,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            if (appIcon != null) appIcon(id, label) else MonogramBadge(label, size = 36, shape = RoundedCornerShape(10.dp))
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 16.dp, end = 12.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The package name tells apart two apps with the same name; skip it when it only
            // repeats the name (desktop player ids such as `spotify` or `vlc`).
            if (!label.lowercase().filterNot { it.isWhitespace() }.contains(id.lowercase())) {
                Text(
                    text = id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// The accent palette of the Messages settings, so app badges read as the same family.
private val MONOGRAM_COLORS =
    listOf(
        Color(0xFF2EBD85),
        Color(0xFFF6A609),
        Color(0xFF2E90FA),
        Color(0xFF9E77ED),
        Color(0xFF5B6AD0),
        Color(0xFFEC4899),
        Color(0xFF14B8A6),
        Color(0xFFEF4444),
        Color(0xFF06B6D4),
    )

/** A tinted badge with the first letter of [label], colored by the label so it stays stable. */
@Composable
fun MonogramBadge(
    label: String,
    size: Int,
    shape: Shape,
) {
    val accent = MONOGRAM_COLORS[(label.hashCode() and Int.MAX_VALUE) % MONOGRAM_COLORS.size]
    Box(
        modifier = Modifier.size(size.dp).clip(shape).background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.trim().take(1).uppercase(),
            style = if (size >= 40) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
