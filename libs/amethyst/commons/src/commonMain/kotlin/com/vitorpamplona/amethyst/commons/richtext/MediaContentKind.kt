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
package com.vitorpamplona.amethyst.commons.richtext

/**
 * Which player/viewer can render a declared blob, as resolved by
 * [RichTextParser.classifyMedia].
 *
 * The set is deliberately closed: it enumerates the renderers [BaseMediaContent] actually has
 * (`MediaUrlImage`, `MediaUrlVideo`, `MediaUrlPdf`), so "no constant fits" — a `null`
 * classification — is the honest answer for every other file type rather than a bucket some
 * caller has to invent a default for. Audio folds into [VIDEO] because both play through the
 * same pipeline; see `RichTextParser.videoExt`.
 */
enum class MediaContentKind {
    IMAGE,
    VIDEO,
    PDF,
}
