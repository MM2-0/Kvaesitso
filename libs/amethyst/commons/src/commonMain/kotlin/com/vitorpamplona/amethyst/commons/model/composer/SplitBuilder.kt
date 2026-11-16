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
package com.vitorpamplona.amethyst.commons.model.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

class SplitBuilder<T> {
    var items: List<SplitItem<T>> by mutableStateOf(emptyList())

    fun addItem(
        key: T,
        percentage: Float,
    ) {
        val newItem = SplitItem(key)
        newItem.percentage = percentage
        this.items = items.plus(newItem)
    }

    fun addItem(key: T): Int {
        val wasEqualSplit = isEqualSplit()
        val newItem = SplitItem(key)
        items = items.plus(newItem)

        if (wasEqualSplit) {
            forceEqualSplit()
        } else {
            updatePercentage(items.lastIndex, equalSplit())
        }

        return items.lastIndex
    }

    fun equalSplit() = 1f / items.size

    fun isEqualSplit(): Boolean {
        val expectedPercentage = equalSplit()
        return items.all { (it.percentage - expectedPercentage) < 0.01 }
    }

    fun forceEqualSplit() {
        val correctPercentage = equalSplit()
        items.forEach { it.percentage = correctPercentage }
    }

    fun updatePercentage(
        index: Int,
        percentage: Float,
    ) {
        if (items.isEmpty()) return

        val splitItem = items.getOrNull(index) ?: return

        if (items.size == 1) {
            splitItem.percentage = 1f
        } else {
            splitItem.percentage = percentage

            val othersMustShare = 1.0f - splitItem.percentage

            val othersHave =
                items.sumOf { if (it == splitItem) 0.0 else it.percentage.toDouble() }.toFloat()

            if (abs(othersHave - othersMustShare) < 0.01) return // nothing to do

            bottomUpAdjustment(othersMustShare, othersHave, index)
        }
    }

    private fun bottomUpAdjustment(
        othersMustShare: Float,
        othersHave: Float,
        exceptForIndex: Int,
    ) {
        var needToRemove = othersHave - othersMustShare
        if (needToRemove > 0) {
            for (i in items.indices.reversed()) {
                if (i == exceptForIndex) continue // do not update the current item

                if (needToRemove < items[i].percentage) {
                    val oldValue = items[i].percentage
                    items[i].percentage -= needToRemove
                    needToRemove = 0f
                } else {
                    val oldValue = items[i].percentage
                    needToRemove -= items[i].percentage
                    items[i].percentage = 0f
                }

                if (needToRemove < 0.01) {
                    break
                }
            }
        } else if (needToRemove < 0) {
            if (items.lastIndex == exceptForIndex) {
                items[items.lastIndex - 1].percentage += -needToRemove
            } else {
                items.last().percentage += -needToRemove
            }
        }
    }
}

class SplitItem<T>(
    val key: T,
) {
    // 0 to 1
    var percentage by mutableFloatStateOf(0f)
}
