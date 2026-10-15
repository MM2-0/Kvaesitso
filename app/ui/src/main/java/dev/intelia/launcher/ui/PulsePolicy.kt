package dev.intelia.launcher.ui

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class PulseCameraProfile(val x: Float = .5f, val y: Float = .03f,
    val widthDp: Float = 24f, val heightDp: Float = 24f, val paddingDp: Float = 4f,
    val confirmed: Boolean = false) {
    fun valid() = listOf(x, y, widthDp, heightDp, paddingDp).all { it.isFinite() } &&
        x in 0f..1f && y in 0f..1f && widthDp in 8f..96f && heightDp in 8f..96f && paddingDp in 0f..12f
}

internal enum class PulsePresentation { DISABLED, RESTRICTED, HIDDEN, COMPACT, EXPANDED, ARC }
internal fun pulseRestrictedPackage(pkg: String, defaultDialer: String?) =
    pkg == "com.android.systemui" || pkg == "com.android.settings" ||
        pkg.endsWith(".permissioncontroller") || pkg.contains("incallui") ||
        pkg == "com.android.server.telecom" || pkg == "com.android.dialer" ||
        pkg == "com.google.android.dialer" || (defaultDialer != null && pkg == defaultDialer)

/** SystemUI temporarily covers the last app; unlock must not clear its safety restriction. */
internal class PulseRestrictions {
    var restricted = true; private set
    private var appRestricted = true
    fun onWindow(pkg: String, defaultDialer: String?) {
        if (pkg != "com.android.systemui") appRestricted = pulseRestrictedPackage(pkg, defaultDialer)
        restricted = if (pkg == "com.android.systemui") true else appRestricted
    }
    fun onHomeVisible() { appRestricted = false; restricted = false }
    fun onUnlock() { restricted = appRestricted }
    fun interrupt() { appRestricted = true; restricted = true }
}

internal fun pulsePresentation(enabled: Boolean, available: Boolean, playing: Boolean,
    detail: Boolean, arc: Boolean): PulsePresentation = when {
    !enabled -> PulsePresentation.DISABLED
    !available -> PulsePresentation.RESTRICTED
    arc -> PulsePresentation.ARC
    detail -> PulsePresentation.EXPANDED
    playing -> PulsePresentation.COMPACT
    else -> PulsePresentation.HIDDEN
}

internal object PulseGeometry {
    fun key(displayId: Int, rotation: Int, width: Int, height: Int) = "v1:$displayId:$rotation:${width * 1000L / height.coerceAtLeast(1)}"
    fun bounds(profile: PulseCameraProfile, width: Int, height: Int, density: Float,
        extraWidthDp: Float = profile.paddingDp * 2, extraHeightDp: Float = profile.paddingDp * 2): PulseAnchorGeometry.Bounds? {
        if (!profile.valid() || width <= 0 || height <= 0 || !density.isFinite() || density <= 0) return null
        val w = ((profile.widthDp + extraWidthDp) * density).roundToInt()
        val h = ((profile.heightDp + extraHeightDp) * density).roundToInt()
        if (w !in 1..width || h !in 1..height) return null
        val left = (profile.x * width - w / 2f).roundToInt().coerceIn(0, width - w)
        val top = (profile.y * height - h / 2f).roundToInt().coerceIn(0, height - h)
        return PulseAnchorGeometry.Bounds(left, top, left + w, top + h)
    }
}
