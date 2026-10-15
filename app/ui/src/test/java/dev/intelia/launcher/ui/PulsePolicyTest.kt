package dev.intelia.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class PulsePolicyTest {
    @Test fun capabilityGatesTakePriorityOverInteractions() {
        assertEquals(PulsePresentation.DISABLED, pulsePresentation(false,true,true,true,true))
        assertEquals(PulsePresentation.RESTRICTED, pulsePresentation(true,false,true,true,true))
        assertEquals(PulsePresentation.HIDDEN, pulsePresentation(true,true,false,false,false))
        assertEquals(PulsePresentation.COMPACT, pulsePresentation(true,true,true,false,false))
        assertEquals(PulsePresentation.EXPANDED, pulsePresentation(true,true,false,true,false))
        assertEquals(PulsePresentation.ARC, pulsePresentation(true,true,true,true,true))
    }
    @Test fun cameraAndTouchAndCompactGeometryAreIndependent() {
        val profile = PulseCameraProfile(x=.5f,y=.03f,widthDp=16f,heightDp=16f,paddingDp=4f)
        val touch = PulseGeometry.bounds(profile,1080,2400,2.625f)!!
        val compact = PulseGeometry.bounds(profile,1080,2400,2.625f,40f,6f)!!
        assertEquals(63,touch.width)
        assertEquals(147,compact.width)
        assertEquals(540,touch.left+touch.width/2)
        assertEquals(72,touch.top+touch.height/2)
        assertEquals(16f,profile.widthDp)
    }
    @Test fun restoredConfigCannotEnablePulseOrConfirmForeignCamera() {
        val profile=PulseCameraProfile(confirmed=true)
        val state=InteliaUiState(island=true,pulseProfiles=mapOf("v1:0:0:451" to profile))
        assertEquals(state,InteliaUiCodec.decode(InteliaUiCodec.encode(state)))
        val restored=InteliaUiCodec.forRestore(state)
        assertFalse(restored.island); assertFalse(restored.pulseProfiles.getValue("v1:0:0:451").confirmed)
    }
    @Test fun malformedProfilesAndUnboundedMapsFailBeforeRestore() {
        for (profile in listOf(PulseCameraProfile(x=2f),PulseCameraProfile(widthDp=1f),PulseCameraProfile(paddingDp=13f))) {
            try { InteliaUiCodec.decode(InteliaUiCodec.encode(InteliaUiState(pulseProfiles=mapOf("v1:0:0:451" to profile)))); fail() }
            catch(_:IllegalArgumentException) {}
        }
        assertNull(PulseGeometry.bounds(PulseCameraProfile(x=Float.NaN),1080,2400,2f))
        assertNull(PulseGeometry.bounds(PulseCameraProfile(),0,2400,2f))
        assertFalse(InteliaUiCodec.validExtensions(InteliaUiState(pulseProfiles=mapOf("../../other" to PulseCameraProfile()))))
    }
}
