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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe

import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash

fun compute50kmLine(geoHash: GeoHash): List<String> {
    val hashes = mutableListOf<String>()

    hashes.add(geoHash.toString())

    var currentGeoHash = geoHash
    repeat(5) {
        currentGeoHash = currentGeoHash.westernNeighbour
        hashes.add(currentGeoHash.toString())
    }

    currentGeoHash = geoHash
    repeat(5) {
        currentGeoHash = currentGeoHash.easternNeighbour
        hashes.add(currentGeoHash.toString())
    }

    return hashes
}

fun compute50kmRange(geoHash: GeoHash): List<String> {
    val hashes = mutableListOf<String>()

    hashes.addAll(compute50kmLine(geoHash))

    var currentGeoHash = geoHash
    repeat(5) {
        currentGeoHash = currentGeoHash.northernNeighbour
        hashes.addAll(compute50kmLine(currentGeoHash))
    }

    currentGeoHash = geoHash
    repeat(5) {
        currentGeoHash = currentGeoHash.southernNeighbour
        hashes.addAll(compute50kmLine(currentGeoHash))
    }

    return hashes
}
