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
package com.vitorpamplona.amethyst.commons.appupdate

/**
 * The fields of a zsp `zapstore.yaml` (https://github.com/zapstore/zsp) the release events use:
 * the listing text, its icon and screenshots (local paths or URLs), the supported NIPs every asset
 * carries, and the Zapstore communities (`h` tags). The YAML is read by the caller (amy) into the
 * plain map [fromMap] takes.
 */
data class ZapstoreConfig(
    val name: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val license: String? = null,
    val website: String? = null,
    val repository: String? = null,
    val icon: String? = null,
    val images: List<String> = emptyList(),
    val supportedNips: List<String> = emptyList(),
    val communities: List<String> = emptyList(),
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): ZapstoreConfig =
            ZapstoreConfig(
                name = map.string("name"),
                summary = map.string("summary"),
                description = map.string("description"),
                tags = map.strings("tags"),
                license = map.string("license"),
                website = map.string("website"),
                repository = map.string("repository"),
                icon = map.string("icon"),
                images = map.strings("images"),
                // YAML reads an unquoted 01 as the number 1: keep NIP ids two characters wide.
                supportedNips = map.strings("supported_nips").map { it.padStart(2, '0').uppercase() },
                communities = map.strings("communities"),
            )

        private fun Map<String, Any?>.string(key: String) = this[key]?.toString()?.takeIf { it.isNotBlank() }

        private fun Map<String, Any?>.strings(key: String): List<String> = (this[key] as? List<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.orEmpty()
    }
}
