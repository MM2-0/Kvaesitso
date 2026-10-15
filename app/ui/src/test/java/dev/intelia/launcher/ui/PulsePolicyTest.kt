package dev.intelia.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class PulsePolicyTest {
    @Test fun unlockRestoresOrdinaryAppButPreservesSensitiveAppRestriction() {
        val policy=PulseRestrictions()
        policy.onUnlock(); assertTrue(policy.restricted)
        policy.onHomeVisible(); assertFalse(policy.restricted)
        policy.onWindow("com.android.systemui",null); assertTrue(policy.restricted)
        policy.onUnlock(); assertFalse(policy.restricted)
        for(pkg in listOf("com.android.settings","org.example.phone")) {
            policy.onWindow(pkg,"org.example.phone"); assertTrue(policy.restricted)
            policy.onWindow("com.android.systemui",null)
            policy.onUnlock(); assertTrue(policy.restricted)
        }
        policy.onWindow("org.example.music",null); assertFalse(policy.restricted)
        policy.interrupt(); policy.onUnlock(); assertTrue(policy.restricted)
        policy.onHomeVisible(); assertFalse(policy.restricted)
    }
    @Test fun systemPermissionAndDefaultPhoneScreensSuppressPulse() {
        for (pkg in listOf("com.android.systemui","com.android.settings","com.google.android.permissioncontroller",
            "com.samsung.android.incallui","com.android.server.telecom","com.google.android.dialer","org.example.phone")) {
            assertTrue(pkg,pulseRestrictedPackage(pkg,"org.example.phone"))
        }
        assertFalse(pulseRestrictedPackage("dev.intelia.launcher.debug",null))
        assertFalse(pulseRestrictedPackage("org.example.music","org.example.phone"))
    }
    @Test fun cameraBoundsStayInsideAllSupportedScreenShapes() {
        for ((width,height) in listOf(720 to 1600,1080 to 2392,2392 to 1080,1440 to 3200,2208 to 1840)) {
            for (density in listOf(1f,2.625f,4f)) for (x in listOf(0f,.05f,.5f,.95f,1f)) for (y in listOf(0f,.03f,.5f,1f)) {
                val profile=PulseCameraProfile(x=x,y=y,widthDp=96f,heightDp=96f,paddingDp=12f,confirmed=true)
                for ((extraW,extraH) in listOf(24f to 24f,40f to 6f)) {
                    val b=PulseGeometry.bounds(profile,width,height,density,extraW,extraH)!!
                    assertTrue(b.left>=0 && b.top>=0 && b.right<=width && b.bottom<=height)
                    assertTrue(kotlin.math.abs(b.width-(96f+extraW)*density)<=.5f)
                }
            }
        }
    }
    @Test fun calibrationKeysCannotCrossDisplayRotationOrAspect() {
        val keys=(0..3).flatMap { rotation -> (0..1).map { display -> PulseGeometry.key(display,rotation,1080,2392) } }
        assertEquals(8,keys.toSet().size)
        assertNotEquals(PulseGeometry.key(0,0,1080,2392),PulseGeometry.key(0,0,2392,1080))
        assertEquals(PulseGeometry.key(0,0,1080,2392),PulseGeometry.key(0,0,2160,4784))
    }
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
