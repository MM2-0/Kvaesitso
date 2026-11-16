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
package com.vitorpamplona.amethyst

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import androidx.core.content.getSystemService
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlInterner
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizedUrls
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Suppress("SENSELESS_COMPARISON")
val isDebug = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "benchmark"

data class MemorySnapshot(
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val nativeHeapUsedMb: Long,
    val imageCacheUsedMb: Long,
    val imageCacheMaxMb: Long,
    val imageDiskUsedMb: Long,
    val imageDiskMaxMb: Long,
    val noteCount: Int,
    val userCount: Int,
    val addressableCount: Int,
    val chatroomCount: Int,
    val memoryClassMb: Int,
) {
    val heapFraction: Float get() = heapUsedMb.toFloat() / heapMaxMb.coerceAtLeast(1L).toFloat()
}

fun collectMemorySnapshot(context: Context): MemorySnapshot {
    val rt = Runtime.getRuntime()
    val heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
    val heapMaxMb = rt.maxMemory() / 1_048_576L
    val nativeHeapUsedMb = Debug.getNativeHeapAllocatedSize() / 1_048_576L

    val app = Amethyst.instance
    val imageCacheUsedMb = app.memoryCache.size / 1_048_576L
    val imageCacheMaxMb = app.memoryCache.maxSize / 1_048_576L
    val imageDiskUsedMb = app.diskCache.size / 1_048_576L
    val imageDiskMaxMb = app.diskCache.maxSize / 1_048_576L

    val activityManager: ActivityManager? = context.getSystemService()
    val isLargeHeap = (context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0
    val memoryClassMb =
        activityManager?.let {
            if (isLargeHeap) it.largeMemoryClass else it.memoryClass
        } ?: 0

    return MemorySnapshot(
        heapUsedMb = heapUsedMb,
        heapMaxMb = heapMaxMb,
        nativeHeapUsedMb = nativeHeapUsedMb,
        imageCacheUsedMb = imageCacheUsedMb,
        imageCacheMaxMb = imageCacheMaxMb,
        imageDiskUsedMb = imageDiskUsedMb,
        imageDiskMaxMb = imageDiskMaxMb,
        noteCount = LocalCache.notes.size(),
        userCount = LocalCache.users.size(),
        addressableCount = LocalCache.addressables.size(),
        chatroomCount = LocalCache.chatroomList.size(),
        memoryClassMb = memoryClassMb,
    )
}

private const val STATE_DUMP_TAG = "STATE DUMP"

fun debugState(context: Context) {
    // Everything below is logged at DEBUG, and every argument is built eagerly (the
    // eager Log.d overload, not the lambda one). Gate on the level that would drop
    // those lines, because the arguments are the expensive part: nine materialising
    // LargeCache.filter scans over notes/addressables/users/channels, plus three
    // passes calling Event.countMemory() — which walks every tag of every cached
    // event. MainActivity.onPause() calls this unconditionally, so without the gate
    // a release build (minLevel WARN) did all of that on every backgrounding and
    // threw the result away. Benchmark builds sit at INFO and paid it too.
    if (Log.minLevel > LogLevel.DEBUG) return

    val totalMemoryMb = Runtime.getRuntime().totalMemory() / (1024 * 1024)
    val freeMemoryMb = Runtime.getRuntime().freeMemory() / (1024 * 1024)
    val maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)

    val jvmHeapAllocatedMb = totalMemoryMb - freeMemoryMb

    Log.d(STATE_DUMP_TAG) { "Total Heap Allocated: $jvmHeapAllocatedMb/$maxMemoryMb MB" }

    val nativeHeap = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
    val maxNative = Debug.getNativeHeapSize() / (1024 * 1024)

    Log.d(STATE_DUMP_TAG) { "Total Native Heap Allocated: $nativeHeap/$maxNative MB" }

    val activityManager: ActivityManager? = context.getSystemService()
    if (activityManager != null) {
        val isLargeHeap = (context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0
        val memClass = if (isLargeHeap) activityManager.largeMemoryClass else activityManager.memoryClass

        Log.d(STATE_DUMP_TAG) { "Memory Class $memClass MB (largeHeap $isLargeHeap)" }
    }

    Log.d(
        STATE_DUMP_TAG,
        "Indexed Relays: " +
            Amethyst.instance.cache.relayHints.relayDB
                .size() + "/" + normalizedUrls.size() + " (interned " + RelayUrlInterner.Default.size() + ")",
    )

    Log.d(STATE_DUMP_TAG) { "Image Disk Cache ${(Amethyst.instance.diskCache.size) / (1024 * 1024)}/${(Amethyst.instance.diskCache.maxSize) / (1024 * 1024)} MB" }
    Log.d(STATE_DUMP_TAG) { "Image Memory Cache ${(Amethyst.instance.memoryCache.size) / (1024 * 1024)}/${(Amethyst.instance.memoryCache.maxSize) / (1024 * 1024)} MB" }

    Log.d(
        STATE_DUMP_TAG,
        "Notes: " +
            LocalCache.notes.filter { _, it -> it.flowSet != null }.size +
            " / " +
            LocalCache.notes.filter { _, it -> it.event != null }.size +
            " / " +
            LocalCache.notes.size(),
    )
    Log.d(
        STATE_DUMP_TAG,
        "Addressables: " +
            LocalCache.addressables.filter { _, it -> it.flowSet != null }.size +
            " / " +
            LocalCache.addressables.filter { _, it -> it.event != null }.size +
            " / " +
            LocalCache.addressables.size(),
    )
    Log.d(
        STATE_DUMP_TAG,
        "Users: " +
            LocalCache.users.filter { _, it -> it.metadataOrNull() != null }.size +
            " / " +
            LocalCache.users.size(),
    )
    Log.d(
        STATE_DUMP_TAG,
        "Public Chat Channels: " +
            LocalCache.publicChatChannels.filter { _, it -> it.flowSet != null }.size +
            " / " +
            LocalCache.publicChatChannels.size() +
            " / " +
            LocalCache.publicChatChannels.values().sumOf { it.notes.size() },
    )
    Log.d(
        STATE_DUMP_TAG,
        "Live Chat Channels: " +
            LocalCache.liveChatChannels.filter { _, it -> it.flowSet != null }.size +
            " / " +
            LocalCache.liveChatChannels.size() +
            " / " +
            LocalCache.liveChatChannels.values().sumOf { it.notes.size() },
    )
    Log.d(
        STATE_DUMP_TAG,
        "Ephemeral Chat Channels: " +
            LocalCache.ephemeralChannels.filter { _, it -> it.flowSet != null }.size +
            " / " +
            LocalCache.ephemeralChannels.size() +
            " / " +
            LocalCache.ephemeralChannels.values().sumOf { it.notes.size() },
    )
    LocalCache.chatroomList.forEach { key, room ->
        Log.d(STATE_DUMP_TAG) {
            "Private Chats $key: " +
                room.rooms.size() +
                " / " +
                room.rooms.sumOf { _, value -> value.messages.size }
        }
    }
    Log.d(
        STATE_DUMP_TAG,
        "Deletion Events: " +
            LocalCache.deletionIndex.size(),
    )
    Log.d(
        STATE_DUMP_TAG,
        "Observables: " +
            LocalCache.observables.size(),
    )

    Log.d(
        STATE_DUMP_TAG,
        "Spam: " +
            LocalCache.antiSpam.spamMessages.size() + " / " + LocalCache.antiSpam.recentEventIds.size() + " / " + LocalCache.antiSpam.recentAddressables.size(),
    )

    Log.d(
        STATE_DUMP_TAG,
        "Memory used by Events: " +
            LocalCache.notes.sumOf { _, note -> note.event?.countMemory() ?: 0 } / (1024 * 1024) +
            " MB",
    )

    val qttNotes = LocalCache.notes.countByGroup { _, it -> it.event?.kind }
    val qttAddressables = LocalCache.addressables.countByGroup { _, it -> it.event?.kind }

    val bytesNotes =
        LocalCache.notes
            .sumByGroup(groupMap = { _, it -> it.event?.kind }, sumOf = { _, it -> it.event?.countMemory()?.toLong() ?: 0L })
    val bytesAddressables =
        LocalCache.addressables
            .sumByGroup(groupMap = { _, it -> it.event?.kind }, sumOf = { _, it -> it.event?.countMemory()?.toLong() ?: 0L })

    qttNotes.toList().sortedByDescending { bytesNotes[it.first] }.forEach { (kind, qtt) ->
        Log.d(STATE_DUMP_TAG) { "Kind ${kind.toString().padStart(5,' ')}:\t${qtt.toString().padStart(6,' ')} elements\t${bytesNotes[kind]?.div((1024 * 1024))}MB " }
    }
    qttAddressables.toList().sortedByDescending { bytesNotes[it.first] }.forEach { (kind, qtt) ->
        Log.d(STATE_DUMP_TAG) { "Kind ${kind.toString().padStart(5,' ')}:\t${qtt.toString().padStart(6,' ')} elements\t${bytesAddressables[kind]?.div((1024 * 1024))}MB " }
    }
}

fun debug(
    tag: String,
    debugMessage: String,
) {
    if (isDebug) {
        Log.d(tag, debugMessage)
    }
}

inline fun debug(
    tag: String,
    debugMessage: () -> String,
) {
    if (isDebug) {
        Log.d(tag, debugMessage())
    }
}

fun Event.countMemory(): Int =
    7 * pointerSizeInBytes + // 7 fields, 4 bytes each reference (32bit)
        12 + // createdAt + kind
        id.bytesUsedInMemory() +
        pubKey.bytesUsedInMemory() +
        tags.sumOf { pointerSizeInBytes + it.sumOf { pointerSizeInBytes + it.bytesUsedInMemory() } } +
        content.bytesUsedInMemory() +
        sig.bytesUsedInMemory()
