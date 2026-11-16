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
package com.vitorpamplona.amethyst.commons.util

import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import java.util.Locale

/**
 * Honours the Android 14+ "Regional preferences → Measurement system" override,
 * which the platform surfaces through the default locale's Unicode `-u-ms-`
 * extension (`metric` / `ussystem` / `uksystem`). When no explicit override is
 * set it falls back to ICU's locale-derived measurement system (US and UK both
 * use miles for distance), available since API 28; on older releases it falls
 * back to a country-code check.
 */
actual fun phonePrefersMiles(): Boolean {
    val locale = Locale.getDefault(Locale.Category.FORMAT)

    locale.getUnicodeLocaleType("ms")?.let { ms ->
        return ms == "ussystem" || ms == "uksystem"
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        when (LocaleData.getMeasurementSystem(ULocale.forLocale(locale))) {
            LocaleData.MeasurementSystem.US, LocaleData.MeasurementSystem.UK -> true
            else -> false
        }
    } else {
        locale.country.uppercase(Locale.ROOT) in MILES_COUNTRIES
    }
}
