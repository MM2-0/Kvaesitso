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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.Serializable

/**
 * Decoded shape of a moq-lite `catalog.json` track for a single
 * audio-room speaker. Mirrors the kixelated/moq `hang` reference
 * catalog format — the canonical shape that `@kixelated/hang`'s
 * browser watcher and the `moq-rs` Rust hang crate both produce and
 * consume — so Amethyst publishers are visible to the moq-lite browser
 * reference, and Amethyst listeners parse standards-aligned catalogs
 * from non-Amethyst publishers.
 *
 * Shape (verbatim from `kixelated/moq/rs/hang/src/catalog/`):
 *
 *   {
 *     "audio": {
 *       "renditions": {
 *         "<trackName>": {
 *           "codec": "opus",
 *           "container": { "kind": "legacy" },
 *           "sampleRate": 48000,
 *           "numberOfChannels": 1,
 *           "bitrate": 32000           // optional
 *         }
 *       }
 *     }
 *   }
 *
 * Keys are camelCase per the upstream serde `rename_all = "camelCase"`.
 * Every field except the rendition-key string is optional in the parser
 * — older / partial publishers are tolerated. Field semantics:
 *
 *   - rendition key: the moq-lite `track` name a subscriber should
 *     subscribe to for that audio rendition's frames (commonly the same
 *     string for single-rendition broadcasts). Subscribers pick a
 *     rendition (e.g. by codec / bitrate) and use this key as the
 *     Subscribe.track string.
 *   - `codec`: codec mimetype string (`"opus"`, `"mp4a.40.2"` for AAC).
 *   - `container.kind`: frame wrapper. `"legacy"` = each frame is
 *     `varint(timestamp_us) + raw_codec_payload` inside the moq-lite
 *     group. `"cmaf"` = MOOF/MDAT-fragmented MP4. We only emit/parse
 *     `legacy` today; unknown kinds are ignored at parse time.
 *   - `sampleRate` / `numberOfChannels`: PCM source params.
 */
@Immutable
@Serializable
data class RoomSpeakerCatalog(
    val audio: Audio? = null,
) {
    @Immutable
    @Serializable
    data class Audio(
        val renditions: Map<String, AudioConfig> = emptyMap(),
    )

    @Immutable
    @Serializable
    data class AudioConfig(
        val codec: String? = null,
        val container: Container? = null,
        val sampleRate: Int? = null,
        val numberOfChannels: Int? = null,
        val bitrate: Int? = null,
    ) {
        /**
         * Short single-line summary suitable for a UI tooltip /
         * subtitle. Returns null when there's nothing useful to
         * show — the caller can omit the line entirely rather than
         * render "unknown · unknown".
         */
        fun describe(): String? {
            val parts =
                buildList {
                    codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                    sampleRate?.let { add("${it / 1000}kHz") }
                    numberOfChannels?.let { add(if (it == 1) "mono" else "${it}ch") }
                }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    }

    @Immutable
    @Serializable
    data class Container(
        val kind: String? = null,
    ) {
        companion object {
            /**
             * The only `container.kind` value the Amethyst decoder
             * pipeline currently understands: each moq-lite frame is
             * `varint(timestamp_us) + raw_codec_payload`. Source:
             * `kixelated/moq/rs/hang/src/container/legacy.rs`.
             *
             * Other documented kinds — `"cmaf"` (MOOF/MDAT-fragmented
             * MP4) and a handful of in-flight experimental shapes —
             * would require a different decoder path and are
             * intentionally rejected by [primaryAudio].
             */
            const val LEGACY_KIND: String = "legacy"
        }
    }

    /**
     * First audio rendition with a [Container.LEGACY_KIND] container,
     * which is the only container layout the Amethyst decoder pipeline
     * understands today (`varint(timestamp_us) + opus_packet` per
     * frame). Returns null when no legacy rendition exists — the UI
     * surfaces "no audio info" rather than a CMAF rendition we'd try
     * to feed to our legacy decoder.
     *
     * Picks the first match in JSON iteration order
     * (kotlinx.serialization uses LinkedHashMap), so the publisher's
     * preferred legacy rendition wins. A future publisher that emits
     * CMAF-first then legacy still surfaces the legacy entry; CMAF-
     * only publishers correctly return null.
     */
    fun primaryAudio(): AudioConfig? = audio?.renditions?.values?.firstOrNull { it.container?.kind == Container.LEGACY_KIND }

    companion object {
        /**
         * Parse a UTF-8 JSON byte array delivered on the
         * `catalog.json` track. Returns null when the bytes don't
         * decode as JSON or don't match the expected shape — the
         * caller falls back to "no catalog" rather than blowing up
         * the UI.
         */
        fun parseOrNull(bytes: ByteArray): RoomSpeakerCatalog? {
            val text =
                runCatching { bytes.decodeToString() }.getOrNull()
                    ?: return null
            return runCatching { JsonMapper.fromJson<RoomSpeakerCatalog>(text) }.getOrNull()
        }
    }
}
