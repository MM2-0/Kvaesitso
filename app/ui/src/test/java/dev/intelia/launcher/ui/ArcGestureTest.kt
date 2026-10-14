package dev.intelia.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class ArcGestureTest {
    private fun gesture() = ArcGesture(listOf(-100f to -62f, 0f to -90f, 100f to -62f))
    @Test fun holdAndReleaseWithoutMoveNeverLaunch() { assertNull(gesture().finish()) }
    @Test fun requiresUpwardThresholdThenValidTargetAndOnlyOneRelease() {
        val g = gesture(); assertNull(g.move(0f, -12f)); assertEquals(1, g.move(0f, -90f))
        assertEquals(1, g.finish()); assertNull(g.finish()); assertNull(g.move(100f, -62f))
    }
    @Test fun returnToLensCancelsSelection() {
        val g = gesture(); g.move(0f, -90f); assertNull(g.move(0f, -5f)); assertNull(g.finish())
    }
    @Test fun sidewaysAndOutsideArcAreSafe() {
        val g = gesture(); assertNull(g.move(100f, 0f)); assertNull(g.move(350f, -90f)); assertNull(g.finish())
    }
    @Test fun cancelledAndMultiTouchSessionsCannotLaunchLater() {
        for (multi in listOf(false, true)) {
            val g = gesture(); g.move(0f, -90f)
            if (multi) g.move(0f, -90f, 2) else g.cancel()
            assertNull(g.move(0f, -90f)); assertNull(g.finish())
        }
    }
    @Test fun missingSlotNeverMovesNeighborIntoItsPlace() {
        val g = ArcGesture(listOf(null, 0f to -90f, 100f to -62f))
        assertNull(g.move(-100f, -62f)); assertEquals(1, g.move(0f, -90f))
    }
    @Test fun quickMotionCanSelectButOvershootClearsSelection() {
        val g = gesture(); assertEquals(2, g.move(100f, -62f)); assertNull(g.move(100f, -200f)); assertNull(g.finish())
    }
}
