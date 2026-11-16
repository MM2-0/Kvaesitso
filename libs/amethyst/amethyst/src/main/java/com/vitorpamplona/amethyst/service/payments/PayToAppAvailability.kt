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
package com.vitorpamplona.amethyst.service.payments

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.model.payments.PaymentTargetTypes
import com.vitorpamplona.amethyst.commons.ui.payments.PayToAppInfo
import com.vitorpamplona.amethyst.commons.ui.payments.PayToAppProbe
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/** A host no registrar can delegate (RFC 2606), so only catch-all browsers match it. */
private const val CONTROL_URL = "https://probe.invalid/"

/**
 * Answers "can anything on this phone open this payment target, and what does it
 * look like?" for the zap picker's hand-off chip.
 *
 * **Package visibility.** `targetSdk` is 37, so on Android 11+ every query here
 * returns nothing unless `AndroidManifest.xml`'s `<queries>` block declares the
 * scheme. The declarations are deliberately `<intent>` filters rather than
 * `QUERY_ALL_PACKAGES`, which is policy-restricted on Play.
 *
 * **Why this is not a per-post lookup.** [warm] probes the targets of the one
 * author whose zap picker is open — a handful of entries — and merges the answers
 * into a cache keyed by scheme+host, so a type already probed for someone else is
 * simply refreshed. Feed rendering never triggers a probe; it only reads [peek].
 *
 * The result is a [StateFlow] rather than a plain map because a bare map write
 * is invisible to Compose: the chip would stay missing until some unrelated
 * recomposition happened to run.
 */
class PayToAppAvailability(
    private val context: Context,
) : PayToAppProbe {
    private val state = MutableStateFlow<Map<String, PayToAppInfo>>(emptyMap())
    override val flow: StateFlow<Map<String, PayToAppInfo>> = state.asStateFlow()

    /**
     * Decoded icons, kept across warms and keyed by package and size.
     *
     * [warm] runs every time the picker opens, so that resolution stays fresh when
     * the user installs an app and comes back. Re-reading the APK's resources and
     * re-rasterising the icon each of those times is the expensive half and answers
     * the same thing, so only the cheap half repeats.
     */
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    /**
     * Probes every distinct type in [targets] and merges the answers into the cache.
     *
     * Merging rather than replacing: the probe set is one author's target list, so
     * replacing would evict what was learned about every other author the moment a
     * second picker opened. Re-probing an already-known type is the point — that is
     * how a newly installed app becomes visible — and the merge just overwrites it.
     *
     * Blocking: `loadIcon` reads the target APK's resources. Call from `Dispatchers.IO`.
     * [iconPx] is the size the chip draws at — decoding once here is what keeps
     * the icon out of the composition path.
     */
    override fun warm(
        targets: List<PaymentTarget>,
        iconPx: Int,
    ) {
        val pm = context.packageManager
        val keys =
            targets
                .asSequence()
                .map { it.type }
                .filterNot { PaymentTargetTypes.isWalletCovered(it) }
                .distinctBy { PaymentTargetTypes.probeKeyFor(it) }
                .toList()

        if (keys.isEmpty()) return

        // Only https targets need the control probe; skip the extra query otherwise.
        val browsers = if (keys.any(PaymentTargetTypes::isWebTarget)) browserPackages(pm) else emptySet()
        val probed =
            keys.associate { type ->
                PaymentTargetTypes.probeKeyFor(type) to probe(pm, type, browsers, iconPx)
            }
        state.update { it + probed }
    }

    private fun probe(
        pm: PackageManager,
        rawType: String,
        browsers: Set<String>,
        iconPx: Int,
    ): PayToAppInfo {
        val intent = viewIntent(PaymentTargetTypes.probeKeyFor(rawType))
        val handlers = queryActivities(pm, intent)
        val isWeb = PaymentTargetTypes.isWebTarget(rawType)

        // A browser resolves any https:// URI, so for web targets "something
        // resolves" is trivially true and tells us nothing. Gate them open, but
        // only claim an app — and therefore an icon — when a handler exists that
        // is not merely a browser. Chrome's icon on a Venmo chip is worse than none.
        val appHandlers = if (isWeb) handlers.filterNot { it.packageName() in browsers } else handlers
        val resolves = isWeb || handlers.isNotEmpty()
        if (appHandlers.isEmpty()) return PayToAppInfo(resolves = resolves)

        val chosen = defaultActivity(pm, intent, appHandlers) ?: return PayToAppInfo(resolves = resolves)

        return PayToAppInfo(
            resolves = resolves,
            label = runCatching { chosen.loadLabel(pm).toString() }.getOrNull(),
            icon = loadIcon(pm, chosen, iconPx),
        )
    }

    /**
     * The single app the hand-off would open, or null when the system would show
     * a chooser instead. With several handlers and no user default, Android hands
     * back its own `ResolverActivity` — there is no app to name there, so the chip
     * falls back to the brand-coloured glyph.
     */
    private fun defaultActivity(
        pm: PackageManager,
        intent: Intent,
        handlers: List<ResolveInfo>,
    ): ResolveInfo? {
        if (handlers.size == 1) return handlers.first()
        val preferred =
            runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
                ?: return null
        val pkg = preferred.packageName()
        if (pkg == "android" || preferred.activityInfo?.name?.contains("ResolverActivity") == true) return null
        return handlers.firstOrNull { it.packageName() == pkg }
    }

    /**
     * minSdk is 26, so any icon may be an `AdaptiveIconDrawable`: a 108x108 canvas
     * whose outer margin the launcher masks away. Rasterising it at the chip's own
     * size — rather than at its intrinsic size — is what stops the logo from
     * arriving as a speck floating in that bleed. The circular mask is applied by
     * the composable, matching how a launcher presents the same icon.
     */
    private fun loadIcon(
        pm: PackageManager,
        info: ResolveInfo,
        px: Int,
    ): ImageBitmap? {
        val pkg = info.packageName() ?: return null
        icons["$pkg@$px"]?.let { return it }

        return runCatching {
            info.loadIcon(pm).toBitmap(px, px).asImageBitmap()
        }.onSuccess {
            icons["$pkg@$px"] = it
        }.onFailure {
            Log.w("PayToAppAvailability", "Could not load icon for $pkg", it)
        }.getOrNull()
    }

    @Suppress("QueryPermissionsNeeded")
    private fun queryActivities(
        pm: PackageManager,
        intent: Intent,
    ): List<ResolveInfo> =
        runCatching {
            // MATCH_DEFAULT_ONLY mirrors startActivity, which implies CATEGORY_DEFAULT.
            // Without it we would list activities the hand-off could never launch.
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())

    /** Packages that answer a URL nobody can own — i.e. general-purpose browsers. */
    @Suppress("QueryPermissionsNeeded")
    private fun browserPackages(pm: PackageManager): Set<String> = queryActivities(pm, viewIntent(CONTROL_URL)).mapNotNull { it.packageName() }.toSet()

    /**
     * Deliberately carries **no** category. `IntentFilter.matchCategories` returns
     * the first category on the *intent* that the filter lacks, so every category
     * added here narrows the match — a probe carrying `BROWSABLE` would miss any
     * app whose filter declares only `DEFAULT`, and hide a chip that would have
     * opened fine. Paired with `MATCH_DEFAULT_ONLY` in [queryActivities], this
     * resolves exactly the set `startActivity` would.
     */
    private fun viewIntent(uri: String) = Intent(Intent.ACTION_VIEW, uri.toUri())

    private fun ResolveInfo.packageName(): String? = activityInfo?.packageName
}
