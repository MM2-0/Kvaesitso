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
package com.vitorpamplona.amethyst.commons.video.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.feeds.SupportedContent
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.video.SUPPORTED_VIDEO_FEED_MIME_TYPES_SET
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent

class VideoFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    val videoFeedSupport =
        SupportedContent(
            blockedUrls = listOf("youtu.be", "youtube.com"),
            mimeTypes = SUPPORTED_VIDEO_FEED_MIME_TYPES_SET,
            supportedFileExtensions = (RichTextParser.videoExt + RichTextParser.imageExt).toSet(),
        )

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultStoriesFollowList.value.code

    override fun limit() = 300

    fun followList(): TopFilter = account.settings.defaultStoriesFollowList.value

    fun TopFilter.isMuteList() = this is TopFilter.MuteList

    fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val notes =
            LocalCache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, params)
            } +
                LocalCache.addressables.filterIntoSet(AddressableNormalVideoEvent.KIND) { _, it ->
                    acceptableEvent(it, params)
                } +
                LocalCache.addressables.filterIntoSet(AddressableShortVideoEvent.KIND) { _, it ->
                    acceptableEvent(it, params)
                }

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = buildFilterParams(account)

        return collection.filterTo(HashSet()) { acceptableEvent(it, params) }
    }

    fun acceptableUrls(
        baseUrls: List<String>,
        mimeType: String?,
    ) = baseUrls.any { videoFeedSupport.acceptableUrl(it, mimeType) }

    fun acceptableVideoiMetas(iMetas: List<VideoMeta>): Boolean = iMetas.any { videoFeedSupport.acceptableUrl(it.url, it.mimeType) }

    fun acceptablePictureiMetas(iMetas: List<PictureMeta>): Boolean = iMetas.any { videoFeedSupport.acceptableUrl(it.url, it.mimeType) }

    fun acceptanceEvent(noteEvent: PictureEvent) = acceptablePictureiMetas(noteEvent.imetaTags())

    fun acceptanceEvent(noteEvent: FileMetadataEvent) = acceptableUrls(noteEvent.urls(), noteEvent.mimeType())

    fun acceptanceEvent(noteEvent: AddressableShortVideoEvent) = acceptableVideoiMetas(noteEvent.imetaTags())

    fun acceptanceEvent(noteEvent: AddressableNormalVideoEvent) = acceptableVideoiMetas(noteEvent.imetaTags())

    fun acceptanceEvent(noteEvent: VideoNormalEvent) = acceptableVideoiMetas(noteEvent.imetaTags())

    fun acceptanceEvent(noteEvent: VideoShortEvent) = acceptableVideoiMetas(noteEvent.imetaTags())

    fun acceptableEvent(
        note: Note,
        params: FilterByListParams,
    ): Boolean {
        val noteEvent = note.event

        if (noteEvent is AddressableEvent && note !is AddressableNote) {
            return false
        }

        return (
            (noteEvent is FileMetadataEvent && acceptanceEvent(noteEvent)) ||
                (noteEvent is AddressableShortVideoEvent && acceptanceEvent(noteEvent)) ||
                (noteEvent is AddressableNormalVideoEvent && acceptanceEvent(noteEvent)) ||
                (noteEvent is VideoNormalEvent && acceptanceEvent(noteEvent)) ||
                (noteEvent is VideoShortEvent && acceptanceEvent(noteEvent)) ||
                (noteEvent is PictureEvent && acceptanceEvent(noteEvent))
        ) &&
            params.match(noteEvent, note.relays) &&
            (params.isHiddenList || account.isAcceptable(note))
    }

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveStoriesFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
