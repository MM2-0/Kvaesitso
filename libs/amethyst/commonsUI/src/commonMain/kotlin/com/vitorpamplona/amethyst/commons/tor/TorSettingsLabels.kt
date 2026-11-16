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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.tor_custom
import com.vitorpamplona.amethyst.commons.resources.tor_custom_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_default
import com.vitorpamplona.amethyst.commons.resources.tor_default_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_external
import com.vitorpamplona.amethyst.commons.resources.tor_full_privacy
import com.vitorpamplona.amethyst.commons.resources.tor_full_privacy_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_internal
import com.vitorpamplona.amethyst.commons.resources.tor_off
import com.vitorpamplona.amethyst.commons.resources.tor_small_payloads
import com.vitorpamplona.amethyst.commons.resources.tor_small_payloads_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_when_needed
import com.vitorpamplona.amethyst.commons.resources.tor_when_needed_explainer
import org.jetbrains.compose.resources.StringResource

/**
 * The display labels for the Tor settings enums.
 *
 * Same split as UiSettingsLabels: the data ([TorType], [TorPresetType],
 * [com.vitorpamplona.amethyst.commons.tor.TorSettings]) is in `commons`, where
 * the CLI and any headless front end can read it, and the [StringResource]s are
 * here because they come from the generated `Res` class that only `commonsUI`
 * has.
 */
val TorType.resourceId: StringResource
    get() =
        when (this) {
            TorType.OFF -> Res.string.tor_off
            TorType.INTERNAL -> Res.string.tor_internal
            TorType.EXTERNAL -> Res.string.tor_external
        }

val TorPresetType.resourceId: StringResource
    get() =
        when (this) {
            TorPresetType.ONLY_WHEN_NEEDED -> Res.string.tor_when_needed
            TorPresetType.DEFAULT -> Res.string.tor_default
            TorPresetType.SMALL_PAYLOADS -> Res.string.tor_small_payloads
            TorPresetType.FULL_PRIVACY -> Res.string.tor_full_privacy
            TorPresetType.CUSTOM -> Res.string.tor_custom
        }

val TorPresetType.explainerId: StringResource
    get() =
        when (this) {
            TorPresetType.ONLY_WHEN_NEEDED -> Res.string.tor_when_needed_explainer
            TorPresetType.DEFAULT -> Res.string.tor_default_explainer
            TorPresetType.SMALL_PAYLOADS -> Res.string.tor_small_payloads_explainer
            TorPresetType.FULL_PRIVACY -> Res.string.tor_full_privacy_explainer
            TorPresetType.CUSTOM -> Res.string.tor_custom_explainer
        }
