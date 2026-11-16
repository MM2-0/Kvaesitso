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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

/**
 * Which NIP-71 kind a video upload is published as. Only videos are affected — whether an upload
 * is a picture (NIP-68 kind 20) or a video is decided by the file's mime type and can't be
 * overridden.
 *
 * The composer picks this from the feed it was opened on, so a post always lands in the feed the
 * user was standing in (or shared to): the Shorts feed reads kind 22, the Longs feed reads kind 21
 * and the Video feed reads both.
 */
enum class VideoPostKind {
    /** Derive from the video's dimensions: portrait -> kind 22, landscape -> kind 21. */
    AUTO,

    /** Always NIP-71 kind 22 (short-form video), regardless of orientation. */
    SHORT,

    /** Always NIP-71 kind 21 (normal video), regardless of orientation. */
    NORMAL,

    ;

    /** True when a video of [dim] should be published as a NIP-71 kind 22 short. */
    fun isShort(dim: DimensionTag): Boolean =
        when (this) {
            SHORT -> true
            NORMAL -> false
            AUTO -> dim.height > dim.width
        }
}
