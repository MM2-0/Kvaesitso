package dev.intelia.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class PulseAnchorGeometryTest {
    private val display = PulseAnchorGeometry.Bounds(0, 0, 1080, 2400)

    @Test fun cutoutAndSafeAnchorAreDistinct() {
        val hole = PulseAnchorGeometry.Bounds(510, 20, 570, 80)
        assertEquals(PulseAnchorGeometry.Bounds(468, 20, 612, 164), PulseAnchorGeometry.anchor(display, hole, 100, 144, false))
        assertEquals(PulseAnchorGeometry.Bounds(468, 100, 612, 244), PulseAnchorGeometry.anchor(display, hole, 100, 144, true))
    }

    @Test fun edgeCutoutStaysInsideDisplay() {
        val hole = PulseAnchorGeometry.Bounds(0, 0, 40, 40)
        assertEquals(0, PulseAnchorGeometry.anchor(display, hole, 100, 144, false)!!.left)
    }

    @Test fun missingCutoutUsesExplicitFallback() {
        assertEquals(468, PulseAnchorGeometry.anchor(display, null, 100, 144, true)!!.left)
    }

    @Test fun unavailableSpaceFailsClosed() {
        assertNull(PulseAnchorGeometry.anchor(display, null, 2350, 144, true))
        assertNull(PulseAnchorGeometry.anchor(display, null, 0, -1, false))
    }

    @Test fun nonzeroOriginAndOversizedTargetAreBounded() {
        val small = PulseAnchorGeometry.Bounds(50, 80, 150, 400)
        assertEquals(PulseAnchorGeometry.Bounds(50, 80, 150, 224), PulseAnchorGeometry.anchor(small, null, 0, 144, false))
    }

    @Test fun manualCameraCenterOverridesIncorrectOemRectangle() {
        assertEquals(PulseAnchorGeometry.Bounds(477, 5, 603, 131),
            PulseAnchorGeometry.calibrated(display, .5f, 68f / 2400, 48f, 48f, 2.625f))
    }

    @Test fun normalizedCenterSurvivesResolutionChange() {
        val larger = PulseAnchorGeometry.Bounds(0, 0, 2160, 4800)
        val result = PulseAnchorGeometry.calibrated(larger, .5f, .1f, 48f, 48f, 3f)!!
        assertEquals(1080, result.left + result.width / 2)
        assertEquals(480, result.top + result.height / 2)
    }

    @Test fun onePixelNudgeSurvivesFloatRoundTrip() {
        val result = PulseAnchorGeometry.calibrated(display, .5f + 1f / 1080, .5f, 48f, 48f, 2.625f)!!
        assertEquals(478, result.left)
    }

    @Test fun manualValuesAreBoundedAndMalformedDataRejected() {
        val result = PulseAnchorGeometry.calibrated(display, 0f, 1f, 10000f, 10000f, 2.625f)!!
        assertEquals(0, result.left)
        assertEquals(2400, result.bottom)
        assertEquals(800, result.height)
        assertNull(PulseAnchorGeometry.calibrated(display, Float.NaN, .5f, 48f, 48f, 2f))
        assertNull(PulseAnchorGeometry.calibrated(display, 2f, .5f, 48f, 48f, 2f))
        assertNull(PulseAnchorGeometry.calibrated(display, .5f, .5f, 20f, 48f, 2f))
    }
}
