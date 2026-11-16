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
package com.vitorpamplona.amethyst.commons.nip99Classifieds

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.tags.VisibilityTag

/**
 * What a listing's badge says. [gone] is a listing no longer for sale; [UNLISTED] and
 * [PRE_ORDER] are still for sale and say how.
 */
enum class ListingStatus(
    val gone: Boolean,
) {
    SOLD(true),
    ENDED(true),
    INACTIVE(true),
    DELETED(true),
    SOLD_OUT(true),

    /**
     * Open Markets' `visibility: hidden`: out of the catalog, not off sale. Conduit sells its
     * event-only products through the event's collection, every one of them hidden.
     */
    UNLISTED(false),

    /** Open Markets' `visibility: pre-order`: for sale now, delivered later. */
    PRE_ORDER(false),
}

private val GONE_STATUSES =
    mapOf(
        "sold" to ListingStatus.SOLD,
        "ended" to ListingStatus.ENDED,
        "inactive" to ListingStatus.INACTIVE,
        "deleted" to ListingStatus.DELETED,
    )

/**
 * The listing's badge, or null for a listed product on sale. A seller's own `status` wins, then
 * a stock of zero (except on a `variable` parent, whose variations carry their own), then the
 * Open Markets visibility.
 */
fun ClassifiedsEvent.listingStatus(): ListingStatus? {
    status()
        ?.trim()
        ?.lowercase()
        ?.let { GONE_STATUSES[it] }
        ?.let { return it }
    if (stock() == 0L && productType()?.isVariable() != true) return ListingStatus.SOLD_OUT
    if (isHidden()) return ListingStatus.UNLISTED
    if (visibility() == VisibilityTag.VISIBILITY.PRE_ORDER) return ListingStatus.PRE_ORDER
    return null
}

/**
 * Whether a catalog (the marketplace feeds) shows the listing: anything but `visibility: hidden`.
 * A hidden product is reached by its own link or by a collection that names it, never by browsing.
 */
fun ClassifiedsEvent.isInCatalog() = !isHidden()

/** One spelling of an address: tags in the wild carry upper-case pubkeys, the cache keys lower. */
fun Address.normalized(): Address = if (pubKeyHex.any { it.isUpperCase() }) Address(kind, pubKeyHex.lowercase(), dTag) else this

/**
 * Whether [product] shows on [collection]'s page. The collection's own seller's products always
 * do. Another seller's shows only when both sides say so: the collection names it AND it files
 * itself under the collection with its own `a` tag, the way an event market gathers its vendors.
 * A collection cannot claim a stranger's listing.
 */
fun ProductCollectionEvent.shows(product: ClassifiedsEvent): Boolean {
    if (product.pubKey == pubKey) return true
    val self = address()
    return product.collections().any { it.normalized() == self }
}

/**
 * Whether [product] may say it is "in" this collection. Its own `a` tag is a claim anyone can
 * write, so the collection has to agree: it is the product seller's own, or it names the product.
 */
fun ProductCollectionEvent.acknowledges(product: ClassifiedsEvent): Boolean {
    if (product.pubKey == pubKey) return true
    val address = product.address()
    return products().any { it.normalized() == address }
}
