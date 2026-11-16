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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_definition_available_on
import com.vitorpamplona.amethyst.commons.resources.app_definition_by
import com.vitorpamplona.amethyst.commons.resources.app_definition_categories
import com.vitorpamplona.amethyst.commons.resources.app_definition_handles
import com.vitorpamplona.amethyst.commons.resources.app_definition_implements
import com.vitorpamplona.amethyst.commons.resources.app_definition_kind_app
import com.vitorpamplona.amethyst.commons.resources.app_definition_nip
import com.vitorpamplona.amethyst.commons.resources.app_definition_recommend
import com.vitorpamplona.amethyst.commons.resources.app_definition_recommended
import com.vitorpamplona.amethyst.commons.resources.app_definition_related
import com.vitorpamplona.amethyst.commons.resources.app_definition_supported_nips
import com.vitorpamplona.amethyst.commons.resources.app_definition_via
import com.vitorpamplona.amethyst.commons.resources.platform_android
import com.vitorpamplona.amethyst.commons.resources.platform_ios
import com.vitorpamplona.amethyst.commons.resources.platform_web
import com.vitorpamplona.amethyst.commons.resources.profile_banner
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.components.zoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LinkIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableImageDialog
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.KindChip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindDisplayName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip89AppHandlers.PlatformType
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppMetadata
import com.vitorpamplona.quartz.nip89AppHandlers.definition.tags.SupportedNipTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun RenderAppDefinition(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? AppDefinitionEvent ?: return

    var metadata by remember { mutableStateOf<AppMetadata?>(null) }

    LaunchedEffect(key1 = noteEvent) {
        withContext(Dispatchers.IO) { metadata = noteEvent.appMetaData() }
    }

    metadata?.let { theAppMetadata ->
        Box {
            val clipboardManager = LocalClipboard.current
            val scope = rememberCoroutineScope()
            val uri = LocalUriHandler.current

            if (!theAppMetadata.banner.isNullOrBlank()) {
                var zoomImageDialogOpen by remember { mutableStateOf(false) }
                val bannerSource = rememberZoomSourceAnchor()

                AsyncImage(
                    model = theAppMetadata.banner,
                    contentDescription = stringRes(id = Res.string.profile_image),
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(125.dp)
                            .zoomSourceAnchor(bannerSource)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    theAppMetadata.banner?.let {
                                        scope.launch {
                                            clipboardManager.setText(it)
                                        }
                                    }
                                },
                            ),
                )

                if (zoomImageDialogOpen) {
                    ZoomableImageDialog(
                        imageUrl = RichTextParser.parseImageOrVideo(theAppMetadata.banner!!),
                        source = bannerSource,
                        onDismiss = { zoomImageDialogOpen = false },
                        accountViewModel = accountViewModel,
                    )
                }
            } else {
                Image(
                    painter = painterRes(Res.drawable.profile_banner, 6),
                    contentDescription = stringRes(id = Res.string.profile_banner),
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(125.dp),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 75.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    var zoomImageDialogOpen by remember { mutableStateOf(false) }
                    val pictureSource = rememberZoomSourceAnchor()
                    Box(Modifier.size(100.dp)) {
                        theAppMetadata.picture?.let { picture ->
                            AsyncImage(
                                model = picture,
                                contentDescription = theAppMetadata.name,
                                contentScale = ContentScale.FillWidth,
                                modifier =
                                    Modifier
                                        .border(
                                            3.dp,
                                            MaterialTheme.colorScheme.background,
                                            CircleShape,
                                        ).clip(shape = CircleShape)
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                        .zoomSourceAnchor(pictureSource)
                                        .combinedClickable(
                                            onClick = { zoomImageDialogOpen = true },
                                            onLongClick = {
                                                scope.launch {
                                                    clipboardManager.setText(picture)
                                                }
                                            },
                                        ),
                            )

                            if (zoomImageDialogOpen) {
                                ZoomableImageDialog(
                                    imageUrl = RichTextParser.parseImageOrVideo(theAppMetadata.picture!!),
                                    source = pictureSource,
                                    onDismiss = { zoomImageDialogOpen = false },
                                    accountViewModel = accountViewModel,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        // Cancels the surrounding Column's horizontal padding so
                        // the button's right edge lines up with the banner's.
                        modifier = Modifier.padding(bottom = 3.dp).offset(x = 10.dp),
                    ) {
                        if (accountViewModel.account.isWriteable()) {
                            RecommendAppButton(noteEvent, note, accountViewModel)
                        }
                    }
                }

                val name = remember(theAppMetadata) { theAppMetadata.anyName() }
                name?.let {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 7.dp),
                    ) {
                        CreateTextWithEmoji(
                            text = it,
                            tags =
                                remember(note) {
                                    (note.event?.tags ?: emptyArray()).toImmutableListOfLists()
                                },
                            fontWeight = FontWeight.Bold,
                            fontSize = 25.sp,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    ByAuthorChip(noteEvent.pubKey, accountViewModel, nav)

                    val client = remember(noteEvent) { noteEvent.client()?.name }
                    if (!client.isNullOrBlank()) {
                        Text(
                            text = stringRes(Res.string.app_definition_via, client),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.placeholderText,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                val website = remember(theAppMetadata) { theAppMetadata.website }
                if (!website.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinkIcon(Size16Modifier, MaterialTheme.colorScheme.placeholderText)

                        ClickableTextPrimary(
                            text = website.removePrefix("https://"),
                            onClick = { website.let { runCatching { uri.openUri(it) } } },
                            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
                        )
                    }
                }

                theAppMetadata.about?.let {
                    Row(
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
                    ) {
                        val tags =
                            remember(note) {
                                note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList
                            }
                        val bgColor = MaterialTheme.colorScheme.background
                        val backgroundColor = remember { mutableStateOf(bgColor) }
                        TranslatableRichTextViewer(
                            content = it,
                            canPreview = false,
                            quotesLeft = 1,
                            tags = tags,
                            backgroundColor = backgroundColor,
                            id = note.idHex,
                            callbackUri = note.toNostrUri(),
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }

                val categories = remember(noteEvent) { noteEvent.categories().distinct() }
                if (categories.isNotEmpty()) {
                    SectionLabel(stringRes(Res.string.app_definition_categories))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        categories.forEach { CategoryChip(it) }
                    }
                }

                val relatedAddresses = remember(noteEvent) { noteEvent.relatedAddresses() }
                if (relatedAddresses.isNotEmpty()) {
                    SectionLabel(stringRes(Res.string.app_definition_related))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        relatedAddresses.forEach { aTag ->
                            RelatedEventRow(aTag, accountViewModel, nav)
                        }
                    }
                }

                val platforms = remember(noteEvent) { noteEvent.platformLinks().map { it.platform }.distinct() }
                if (platforms.isNotEmpty()) {
                    SectionLabel(stringRes(Res.string.app_definition_available_on))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        platforms.forEach { PlatformChip(it) }
                    }
                }

                val supportedKinds = remember(noteEvent) { noteEvent.supportedKinds() }
                if (supportedKinds.isNotEmpty()) {
                    SupportedKindsSection(supportedKinds)
                }

                val supportedNips = remember(noteEvent) { noteEvent.supportedNips() }
                if (supportedNips.isNotEmpty()) {
                    SupportedNipsSection(supportedNips)
                }
            }
        }
    }
}

private const val VISIBLE_SUPPORTED_NIP_LIMIT = 8

/** A section header matching the metrics used by the platforms/handles rows. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * NIPs the app declares it supports (NostrHub-style `i` tags). Shows a handful of
 * chips inline with a "+N" overflow that opens a bottom sheet listing every NIP,
 * each one opening its spec.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportedNipsSection(nips: List<SupportedNipTag>) {
    var showAllSheet by rememberSaveable { mutableStateOf(false) }
    val visible = remember(nips) { nips.take(VISIBLE_SUPPORTED_NIP_LIMIT) }
    val overflow = (nips.size - VISIBLE_SUPPORTED_NIP_LIMIT).coerceAtLeast(0)
    val uri = LocalUriHandler.current

    SectionLabel(stringRes(Res.string.app_definition_implements))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp, bottom = 5.dp),
    ) {
        visible.forEach { nip ->
            NipChip(nip.nip) { runCatching { uri.openUri(nip.url) } }
        }
        if (overflow > 0) {
            OverflowChip(overflow) { showAllSheet = true }
        }
    }

    if (showAllSheet) {
        AllNipsSheet(nips = nips, onDismiss = { showAllSheet = false })
    }
}

/**
 * Kinds the app handles. Mirrors [SupportedNipsSection]: a few chips inline with a
 * "+N" overflow that opens a bottom sheet listing every handled kind.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupportedKindsSection(kinds: List<Int>) {
    var showAllSheet by rememberSaveable { mutableStateOf(false) }
    val visible = remember(kinds) { kinds.take(VISIBLE_SUPPORTED_KIND_LIMIT) }
    val overflow = (kinds.size - VISIBLE_SUPPORTED_KIND_LIMIT).coerceAtLeast(0)

    SectionLabel(stringRes(Res.string.app_definition_handles))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp, bottom = 5.dp),
    ) {
        visible.forEach { KindChip(it) }
        if (overflow > 0) {
            OverflowChip(overflow) { showAllSheet = true }
        }
    }

    if (showAllSheet) {
        AllKindsSheet(kinds = kinds, onDismiss = { showAllSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AllKindsSheet(
    kinds: List<Int>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringRes(Res.string.app_definition_handles),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            kinds.forEach { KindChip(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AllNipsSheet(
    nips: List<SupportedNipTag>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringRes(Res.string.app_definition_supported_nips),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            nips.forEach { nip ->
                NipChip(nip.nip) {
                    val target = nip.url
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        runCatching { uri.openUri(target) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NipChip(
    nip: String,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = stringRes(Res.string.app_definition_nip, nip),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryChip(category: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Tappable row for an `a`-tag reference (source repo, store listing, NIP, ...).
 * These are vouched for by the handler's author, so we show the referenced event's
 * author avatar plus its real name (the app/repo/NIP title rather than the raw
 * d-tag), falling back to the d-tag only when the event hasn't loaded yet.
 */
@Composable
private fun RelatedEventRow(
    aTag: ATag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val address = remember(aTag) { Address(aTag.kind, aTag.pubKeyHex, aTag.dTag) }
    val shape = RoundedCornerShape(8.dp)

    LoadAddressableNote(address) { note ->
        if (note == null) return@LoadAddressableNote

        val noteState by observeNote(note, accountViewModel)
        val name =
            remember(noteState) {
                relatedEventName(noteState.note.event)?.ifBlank { null } ?: aTag.dTag
            }

        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable { routeFor(note, accountViewModel.account)?.let { nav.nav(it) } },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                noteState.note.author?.let { author ->
                    BaseUserPicture(author, 26.dp, accountViewModel)
                    Spacer(modifier = Modifier.size(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = kindLabel(aTag.kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.placeholderText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The human title for a related addressable event, by kind, or null if unknown. */
private fun relatedEventName(event: Event?): String? =
    when (event) {
        is SoftwareApplicationEvent -> event.name()
        is GitRepositoryEvent -> event.name()
        is NipTextEvent -> event.title()
        else -> null
    }

/** Short kind label for a related reference (e.g. "App" for a software application). */
@Composable
private fun kindLabel(kind: Int): String =
    if (kind == SoftwareApplicationEvent.KIND) {
        stringRes(Res.string.app_definition_kind_app)
    } else {
        val resId = kindDisplayName(kind)
        if (resId != null) stringRes(resId) else (KindNames.nameFor(kind) ?: "k$kind")
    }

private const val VISIBLE_SUPPORTED_KIND_LIMIT = 12

/** Same shape and metrics as [KindChip] so it lines up with the kind chips. */
@Composable
private fun OverflowChip(
    count: Int,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = "+$count",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlatformChip(platform: String) {
    val name =
        when (platform) {
            PlatformType.WEB.code -> stringRes(Res.string.platform_web)
            PlatformType.ANDROID.code -> stringRes(Res.string.platform_android)
            PlatformType.IOS.code -> stringRes(Res.string.platform_ios)
            else -> platform
        }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * "by <picture> <name>" pill identifying the pubkey that published the app
 * definition, so users can tell an app from a friend apart from a spammer
 * impersonating the real one. The picture carries the following-checkmark
 * overlay for people the account follows. Tapping opens the author's profile.
 */
@Composable
fun ByAuthorChip(
    authorHex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(baseUserHex = authorHex) { author ->
        if (author == null) return@LoadUser

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { nav.nav(routeFor(author)) },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            ) {
                Text(
                    text = stringRes(Res.string.app_definition_by),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(5.dp))
                BaseUserPicture(author, 18.dp, accountViewModel)
                Spacer(modifier = Modifier.size(5.dp))
                ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                    UsernameDisplay(
                        baseUser = author,
                        fontWeight = FontWeight.SemiBold,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    }
}

/**
 * Shows whether the logged-in user publicly recommends this app (NIP-89,
 * kind 31989) and toggles the recommendation on tap.
 */
@Composable
private fun RecommendAppButton(
    noteEvent: AppDefinitionEvent,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val myRecommendations by accountViewModel.account.appRecommendations.flow
        .collectAsStateWithLifecycle()

    val isRecommended =
        remember(myRecommendations, noteEvent) {
            val address = noteEvent.addressTag()
            myRecommendations.any { event ->
                event.recommendationAddresses().any { it == address }
            }
        }

    val compactHeight = Modifier.height(32.dp)
    val compactPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)

    if (isRecommended) {
        OutlinedButton(
            onClick = {
                accountViewModel.launchSigner {
                    val account = accountViewModel.account
                    account.appRecommendations.unrecommendApp(noteEvent.address(), account)
                }
            },
            modifier = compactHeight,
            contentPadding = compactPadding,
        ) {
            Text(stringRes(Res.string.app_definition_recommended), style = MaterialTheme.typography.labelMedium)
        }
    } else {
        Button(
            enabled = noteEvent.supportedKinds().isNotEmpty(),
            onClick = {
                accountViewModel.launchSigner {
                    val account = accountViewModel.account
                    account.appRecommendations.recommendApp(noteEvent, note.relayHintUrl(), account)
                }
            },
            modifier = compactHeight,
            contentPadding = compactPadding,
        ) {
            Text(stringRes(Res.string.app_definition_recommend), style = MaterialTheme.typography.labelMedium)
        }
    }
}
