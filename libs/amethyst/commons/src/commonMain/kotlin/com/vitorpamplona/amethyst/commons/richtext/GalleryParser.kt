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

import kotlinx.collections.immutable.toImmutableList

data class ParagraphImageAnalysis(
    val imageCount: Int,
    val isImageOnly: Boolean,
    val hasMultipleImages: Boolean,
)

class GalleryParser {
    fun analyzeParagraphImages(paragraph: ParagraphState): ParagraphImageAnalysis {
        var imageCount = 0
        var hasNonWhitespaceNonImageContent = false

        paragraph.words.forEach { word ->
            when (word) {
                is ImageSegment, is Base64Segment -> {
                    imageCount++
                }

                is VideoSegment -> {
                    hasNonWhitespaceNonImageContent = true
                }

                // Videos are not images
                is RegularTextSegment -> {
                    if (word.segmentText.isNotBlank()) {
                        hasNonWhitespaceNonImageContent = true
                    }
                }

                else -> {
                    hasNonWhitespaceNonImageContent = true
                } // Links, emojis, etc.
            }
        }

        val isImageOnly = imageCount > 0 && !hasNonWhitespaceNonImageContent
        val hasMultipleImages = imageCount > 1

        return ParagraphImageAnalysis(
            imageCount = imageCount,
            isImageOnly = isImageOnly,
            hasMultipleImages = hasMultipleImages,
        )
    }

    fun collectConsecutiveImageParagraphs(
        paragraphs: List<ParagraphState>,
        startIndex: Int,
    ): Pair<List<ParagraphState>, Int> {
        val imageParagraphs = mutableListOf<ParagraphState>()
        var j = startIndex

        while (j < paragraphs.size) {
            val currentParagraph = paragraphs[j]
            val words = currentParagraph.words

            // Fast path for empty check
            if (words.isEmpty()) {
                j++
                continue
            }

            // Check for single whitespace word
            if (words.size == 1) {
                val firstWord = words.first()
                if (firstWord is RegularTextSegment && firstWord.segmentText.isBlank()) {
                    j++
                    continue
                }
            }

            // Check if it's an image-only paragraph using unified analysis
            val analysis = analyzeParagraphImages(currentParagraph)
            if (analysis.isImageOnly) {
                imageParagraphs.add(currentParagraph)
                j++
            } else {
                break
            }
        }

        return imageParagraphs to j
    }

    fun processParagraphs(paragraphs: List<ParagraphState>): List<ParagraphState> {
        val result = mutableListOf<ParagraphState>()

        var paragraphIndex = 0
        while (paragraphIndex < paragraphs.size) {
            val paragraph = paragraphs[paragraphIndex]

            if (paragraph.words.isEmpty()) {
                // Empty paragraph - render normally with FlowRow (will render nothing)
                result.add(paragraph)
                paragraphIndex++
            } else {
                val analysis = analyzeParagraphImages(paragraph)
                if (analysis.isImageOnly) {
                    // Collect consecutive image-only paragraphs for gallery
                    val (imageParagraphs, endIndex) = collectConsecutiveImageParagraphs(paragraphs, paragraphIndex)
                    val allImageWords = imageParagraphs.flatMap { it.words }.toImmutableList()

                    if (allImageWords.size > 1) {
                        result.add(ImageGalleryParagraph(allImageWords, paragraph.isRTL))
                    } else {
                        // Single image - render with FlowRow wrapper
                        result.add(paragraph)
                    }

                    paragraphIndex = endIndex // Return next index to process
                } else if (analysis.hasMultipleImages) {
                    // Mixed paragraph with multiple images - break it down into many paragraphs
                    result.addAll(processWordsWithImageGrouping(paragraph))
                    paragraphIndex++
                } else {
                    result.add(paragraph)
                    paragraphIndex++
                }
            }
        }

        return result
    }

    fun processWordsWithImageGrouping(paragraph: ParagraphState): List<ParagraphState> {
        val resultingParagraphs = mutableListOf<ParagraphState>()
        var i = 0
        val n = paragraph.words.size

        var currentParagraphSegments = mutableListOf<Segment>()
        while (i < n) {
            val word = paragraph.words[i]

            if (word is ImageSegment || word is Base64Segment) {
                // Collect consecutive image/whitespace segments (but not videos)
                val imageSegments = mutableListOf<Segment>()
                var j = i
                var hasVideo = false

                while (j < n) {
                    val seg = paragraph.words[j]
                    when {
                        seg is VideoSegment -> {
                            hasVideo = true
                            break
                        }

                        seg is ImageSegment || seg is Base64Segment -> {
                            imageSegments.add(seg)
                        }

                        seg is RegularTextSegment && seg.segmentText.isBlank() -> { /* skip whitespace */ }

                        else -> {
                            break
                        }
                    }
                    j++
                }

                // If we found a video, don't create a gallery - render images individually
                if (hasVideo || imageSegments.size <= 1) {
                    currentParagraphSegments.addAll(imageSegments)
                } else {
                    if (currentParagraphSegments.isNotEmpty()) {
                        resultingParagraphs.add(ParagraphState(currentParagraphSegments.toImmutableList(), paragraph.isRTL))
                        currentParagraphSegments = mutableListOf<Segment>()
                    }

                    resultingParagraphs.add(ImageGalleryParagraph(imageSegments.toImmutableList(), paragraph.isRTL))
                }

                i = j // jump past processed run
            } else {
                currentParagraphSegments.add(word)
                i++
            }
        }

        if (currentParagraphSegments.isNotEmpty()) {
            resultingParagraphs.add(ParagraphState(currentParagraphSegments.toImmutableList(), paragraph.isRTL))
        }

        return resultingParagraphs
    }
}
