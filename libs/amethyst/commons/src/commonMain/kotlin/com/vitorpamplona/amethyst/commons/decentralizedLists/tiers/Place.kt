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
package com.vitorpamplona.amethyst.commons.decentralizedLists.tiers

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListHeaderView
import com.vitorpamplona.amethyst.commons.decentralizedLists.degreesText
import com.vitorpamplona.amethyst.commons.decentralizedLists.fieldHref
import com.vitorpamplona.amethyst.commons.decentralizedLists.httpUrl
import com.vitorpamplona.amethyst.commons.decentralizedLists.telHref
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.FieldTypeTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A food and drink place (Brainstorm `components/items/PlaceView`), read through the governing
 * definition's fields, by the community concept's names for them. A copy that drops `name` leaves
 * the item to the tier-two card.
 */
@Immutable
data class Place(
    val name: String,
    val category: String?,
    val cuisine: String?,
    val description: String?,
    val address: String?,
    val locality: String?,
    val region: String?,
    val postalCode: String?,
    val country: String?,
    val lat: Double?,
    val lon: Double?,
    val website: String?,
    val phone: String?,
    /** What a call dials: the definition's `tel:` when it types `phone` so, else what the value reads as. */
    val call: String?,
    val hours: String?,
    val bitcoin: String?,
    val priceRange: Int?,
    val menu: String?,
    val reservations: String?,
    val source: String?,
)

object PlaceFields {
    const val NAME = "name"
    const val CATEGORY = "category"
    const val CUISINE = "cuisine"
    const val DESCRIPTION = "description"
    const val ADDRESS = "address"
    const val LOCALITY = "locality"
    const val REGION = "region"
    const val POSTAL_CODE = "postal-code"
    const val COUNTRY = "country"
    const val LAT = "lat"
    const val LON = "lon"
    const val WEBSITE = "website"
    const val PHONE = "phone"
    const val HOURS = "opening-hours"
    const val BITCOIN = "accepts-bitcoin"
    const val PRICE_RANGE = "price-range"
    const val MENU = "menu"
    const val RESERVATIONS = "reservations"
    const val IMAGE = "image"
    const val LICENSE = "license"
    const val SOURCE = "source"

    val ALL =
        setOf(
            NAME,
            CATEGORY,
            CUISINE,
            DESCRIPTION,
            ADDRESS,
            LOCALITY,
            REGION,
            POSTAL_CODE,
            COUNTRY,
            LAT,
            LON,
            WEBSITE,
            PHONE,
            HOURS,
            BITCOIN,
            PRICE_RANGE,
            MENU,
            RESERVATIONS,
            IMAGE,
            LICENSE,
            SOURCE,
        )
}

/** The place the item names: null without a declared, filled name. */
fun placeOf(
    item: TagArray,
    definition: DListHeaderView,
): Place? {
    fun read(name: String) = item.declaredValue(definition, name)

    val name = read(PlaceFields.NAME) ?: return null
    val phone = read(PlaceFields.PHONE)
    val phoneType = definition.field(PlaceFields.PHONE)?.type
    return Place(
        name = name,
        category = read(PlaceFields.CATEGORY),
        cuisine = read(PlaceFields.CUISINE),
        description = read(PlaceFields.DESCRIPTION),
        address = read(PlaceFields.ADDRESS),
        locality = read(PlaceFields.LOCALITY),
        region = read(PlaceFields.REGION),
        postalCode = read(PlaceFields.POSTAL_CODE),
        country = read(PlaceFields.COUNTRY),
        lat = read(PlaceFields.LAT)?.toDoubleOrNull()?.takeIf { it in -90.0..90.0 },
        lon = read(PlaceFields.LON)?.toDoubleOrNull()?.takeIf { it in -180.0..180.0 },
        website = httpUrl(read(PlaceFields.WEBSITE)),
        phone = phone,
        // Both tiers agree when the header types `phone` as `tel`; when it doesn't, this view still
        // knows the field is a phone number and dials what the value reads as.
        call = phone?.let { if (phoneType == FieldTypeTag.TEL) fieldHref(phoneType, it) else telHref(it) },
        hours = read(PlaceFields.HOURS),
        bitcoin = read(PlaceFields.BITCOIN),
        priceRange = read(PlaceFields.PRICE_RANGE)?.toIntOrNull()?.takeIf { it in 1..4 },
        menu = httpUrl(read(PlaceFields.MENU)),
        reservations = httpUrl(read(PlaceFields.RESERVATIONS)),
        source = read(PlaceFields.SOURCE),
    )
}

/** "fast_food" -> "Fast food". */
fun categoryWords(value: String?): String? {
    val s = value?.trim()?.replace('_', ' ') ?: return null
    return s.ifEmpty { null }?.replaceFirstChar { it.uppercase() }
}

/** What it is as words: "Fast food · Pizza". */
fun Place.kindWords(): String? = listOfNotNull(categoryWords(category), categoryWords(cuisine)).joinToString(" · ").ifEmpty { null }

/** Directions to the place by its coordinates. On Android the maps app takes the link over. */
fun Place.directionsUrl(): String? {
    val lat = degreesText(lat ?: return null)
    val lon = degreesText(lon ?: return null)
    return "https://www.google.com/maps/dir/?api=1&destination=$lat%2C$lon"
}

/** OpenStreetMap, pinned at the place. */
fun Place.mapUrl(): String? {
    val lat = degreesText(lat ?: return null)
    val lon = degreesText(lon ?: return null)
    return "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=18/$lat/$lon"
}

/** How a place takes Bitcoin, from its `accepts-bitcoin` value. */
enum class BitcoinPayment { LIGHTNING, ONCHAIN, BOTH, UNSPECIFIED }

/** `lightning`, `onchain`, `both`, anything else that isn't a no (yes, the method unknown); null for a no or nothing. */
fun bitcoinPaymentOf(value: String?): BitcoinPayment? =
    when (value?.trim()?.lowercase()) {
        null, "", "no", "false", "none", "0" -> null
        "lightning" -> BitcoinPayment.LIGHTNING
        "onchain", "on-chain" -> BitcoinPayment.ONCHAIN
        "both" -> BitcoinPayment.BOTH
        else -> BitcoinPayment.UNSPECIFIED
    }

private val WHITESPACE = Regex("\\s+")
private val ZIP_PLUS_FOUR = Regex("^(\\d{5})-\\d{4}$")

/**
 * The address as a reader expects it: the street, then the city, region and postcode. The list's
 * one-line `address` often already ends with the city and postcode (OpenStreetMap's habit), so the
 * trailing words that repeat them are peeled off the street first.
 */
fun Place.addressLines(): List<String> {
    val locality = locality?.trim()?.ifEmpty { null }
    val region = region?.trim()?.ifEmpty { null }
    val postal = postalCode?.trim()?.ifEmpty { null }
    var street = address?.trim()?.ifEmpty { null }
    var postalSeen: String? = null
    if (street != null) {
        val words = street.split(WHITESPACE).toMutableList()

        val localityWords = locality?.split(WHITESPACE)

        fun peel(): Boolean {
            val last = words.lastOrNull() ?: return false
            if (postal != null && (last == postal || last.startsWith("$postal-"))) {
                postalSeen = words.removeAt(words.lastIndex)
                return true
            }
            if (locality != null && localityWords != null) {
                val lw = localityWords
                if (words.size > lw.size && words.takeLast(lw.size).joinToString(" ").equals(locality, ignoreCase = true)) {
                    repeat(lw.size) { words.removeAt(words.lastIndex) }
                    return true
                }
            }
            return false
        }
        while (words.size > 1 && peel()) {
            // until nothing more repeats
        }
        street =
            words
                .joinToString(" ")
                .removeSuffix(",")
                .trimEnd(',', ' ')
                .ifEmpty { null }
    }
    val code = (postalSeen ?: postal)?.replace(ZIP_PLUS_FOUR, "$1")
    val regionLine = listOfNotNull(region, code).joinToString(" ").ifEmpty { null }
    val cityLine = listOfNotNull(locality, regionLine).joinToString(", ").ifEmpty { null }
    return listOfNotNull(street, cityLine)
}

/** The days of the week, Monday first, as OpenStreetMap's `opening_hours` names them. */
enum class Weekday(
    val code: String,
) {
    MO("mo"),
    TU("tu"),
    WE("we"),
    TH("th"),
    FR("fr"),
    SA("sa"),
    SU("su"),
    ;

    fun plus(days: Int) = entries[(ordinal + days).mod(7)]

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code.lowercase() }
    }
}

/** Minutes since local midnight; [close] runs past 1440 when the span ends after midnight. */
@Immutable
data class Span(
    val open: Int,
    val close: Int,
)

typealias Week = Map<Weekday, List<Span>>

sealed interface OpeningStatus {
    /** Open until [until] (minutes from today's midnight, past 1440 for tomorrow); null when it never closes. */
    data class Open(
        val until: Int?,
    ) : OpeningStatus

    data class Closed(
        val opensAt: Int?,
        val opensDay: Weekday?,
    ) : OpeningStatus
}

private val TIME = Regex("^(\\d{1,2}):(\\d{2})$")

// OpenStreetMap's `,` between rules ("Mo-Fr 09:00-17:00, Sa 10:00-14:00"): after a time, before days.
private val ADDITIONAL_RULE = Regex("(?<=\\d)\\s*,\\s*(?=[A-Za-z]{2}\\b)")
private val HOLIDAYS = setOf("ph", "sh")
private val RULE = Regex("^([A-Za-z]{2}(?:-[A-Za-z]{2})?(?:,[A-Za-z]{2}(?:-[A-Za-z]{2})?)*)\\s+(.+)$")

private fun minutesOf(text: String): Int? {
    val m = TIME.find(text.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (h > 24 || min > 59) return null
    return h * 60 + min
}

private fun daysOf(text: String): List<Weekday>? {
    val out = ArrayList<Weekday>()
    for (part in text.split(',')) {
        if (part.trim().lowercase() in HOLIDAYS) continue
        val range = part.trim().split('-')
        when (range.size) {
            1 -> out.add(Weekday.of(range[0]) ?: return null)
            2 -> {
                val from = Weekday.of(range[0]) ?: return null
                val to = Weekday.of(range[1]) ?: return null
                var d = from
                while (true) {
                    out.add(d)
                    if (d == to) break
                    d = d.plus(1)
                }
            }
            else -> return null
        }
    }
    return out
}

private fun spansOf(text: String): List<Span>? =
    text.split(',').map { part ->
        val bounds = part.trim().split('-')
        if (bounds.size != 2) return null
        val open = minutesOf(bounds[0]) ?: return null
        var close = minutesOf(bounds[1]) ?: return null
        if (close <= open) close += 1440 // past midnight
        Span(open, close)
    }

/**
 * Opening hours as OpenStreetMap writes them, the subset the Food and Drink list carries (Brainstorm
 * `lib/openingHours.ts`): `;`-separated rules, each a day or day range (`Mo`, `Tu-Sa`, `Sa-Mo`
 * wrapping the week) with `HH:MM-HH:MM` spans separated by `,`, or `off`; and `24/7`. A later rule
 * replaces an earlier one for its days. Beyond Brainstorm's subset, a rule with no days is every
 * day, rules may also be joined by `,`, and public and school holiday rules (`PH off`) are skipped:
 * there is no holiday calendar to apply them by. Anything else is null: no week is better than a
 * wrong one.
 */
fun parseOpeningHours(text: String?): Week? {
    val s = text?.trim()?.ifEmpty { null } ?: return null
    if (s == "24/7") return Weekday.entries.associateWith { listOf(Span(0, 1440)) }
    val week = Weekday.entries.associateWith { emptyList<Span>() }.toMutableMap()
    for (rule in s.split(';').flatMap { it.split(ADDITIONAL_RULE) }) {
        val r = rule.trim()
        if (r.isEmpty()) continue
        if (r.take(2).lowercase() in HOLIDAYS && (r.length == 2 || !r[2].isLetter())) continue
        val m = RULE.find(r)
        val days = if (m != null) daysOf(m.groupValues[1]) ?: return null else Weekday.entries
        if (days.isEmpty()) continue
        val rest = (if (m != null) m.groupValues[2] else r).trim().lowercase()
        val spans = if (rest == "off" || rest == "closed") emptyList() else spansOf(rest) ?: return null
        days.forEach { week[it] = spans }
    }
    return week
}

/** Whether the place is open at a local moment, and when that changes. */
fun openingStatus(
    week: Week,
    day: Weekday,
    minutes: Int,
): OpeningStatus {
    week[day.plus(-1)]?.forEach { s -> if (s.close > 1440 && minutes < s.close - 1440) return openUntil(week, day, s.close - 1440) }
    val today = week[day].orEmpty()
    today.forEach { s -> if (minutes >= s.open && minutes < s.close) return openUntil(week, day, s.close) }
    today.filter { it.open > minutes }.minByOrNull { it.open }?.let { return OpeningStatus.Closed(it.open, day) }
    for (k in 1..7) {
        val d = day.plus(k)
        week[d].orEmpty().minByOrNull { it.open }?.let { return OpeningStatus.Closed(it.open, d) }
    }
    return OpeningStatus.Closed(null, null)
}

/**
 * Open until [close], minutes from [day]'s midnight, carried on while the next day opens at the
 * midnight this one closes at: a `24/7` place, or `Fr 18:00-24:00; Sa 00:00-02:00`, never reads
 * "until 00:00". A week of that never closes.
 */
private fun openUntil(
    week: Week,
    day: Weekday,
    close: Int,
): OpeningStatus.Open {
    var end = close
    repeat(7) {
        if (end % 1440 != 0) return OpeningStatus.Open(end)
        val next = week[day.plus(end / 1440)]?.firstOrNull { it.open == 0 } ?: return OpeningStatus.Open(end)
        end = end + next.close
    }
    return OpeningStatus.Open(null)
}

/**
 * The place's local day and minutes since midnight at [epochSeconds], estimated from its longitude,
 * 15 degrees an hour, as Brainstorm does: there is no time-zone map here. It is an estimate: off by
 * an hour where daylight saving is in force, and by more where a zone is far from its meridian
 * (Spain, at UTC+1 on Greenwich's longitude; western China, on Beijing's clock).
 */
fun placeLocalTime(
    lon: Double,
    epochSeconds: Long,
): Pair<Weekday, Int> {
    val offsetMinutes = (lon / 15).roundToInt() * 60
    val localMinutes = epochSeconds / 60 + offsetMinutes
    val days = localMinutes.floorDiv(1440)
    // 1970-01-01 was a Thursday.
    val day = Weekday.TH.plus(days.mod(7))
    return day to localMinutes.mod(1440)
}

/** 1020 -> "17:00". Past-midnight closes wrap. */
fun clockWords(minutes: Int): String {
    val t = minutes.mod(1440)
    return "${(t / 60).toString().padStart(2, '0')}:${(t % 60).toString().padStart(2, '0')}"
}

/** A day's spans as words, "11:00–14:00, 17:00–22:00"; empty for a closed day. */
fun spansWords(spans: List<Span>): String = spans.joinToString(", ") { "${clockWords(it.open)}–${clockWords(it.close)}" }

/** Great-circle distance in kilometres. */
fun distanceKm(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val toRad = PI / 180
    val sinLat = sin((lat2 - lat1) * toRad / 2)
    val sinLon = sin((lon2 - lon1) * toRad / 2)
    val a = sinLat * sinLat + cos(lat1 * toRad) * cos(lat2 * toRad) * sinLon * sinLon
    return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
}
