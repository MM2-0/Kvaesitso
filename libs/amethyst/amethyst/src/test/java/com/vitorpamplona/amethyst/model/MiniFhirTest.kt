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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.Bundle
import com.vitorpamplona.amethyst.commons.model.Patient
import com.vitorpamplona.amethyst.commons.model.Practitioner
import com.vitorpamplona.amethyst.commons.model.UnknownResource
import com.vitorpamplona.amethyst.commons.model.VisionPrescription
import com.vitorpamplona.amethyst.commons.model.findReferenceInDb
import com.vitorpamplona.amethyst.commons.model.parseResourceBundleOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniFhirTest {
    @Test
    fun parsesVisionPrescriptionBundle() {
        val json =
            """
            {"resourceType":"Bundle","id":"bundle-vision-test","type":"document","entry":[
              {"resourceType":"Practitioner","id":"2","active":true,"name":[{"use":"official","family":"Careful","given":["Adam"]}],"gender":"male"},
              {"resourceType":"Patient","id":"1","active":true,"name":[{"use":"official","family":"Duck","given":["Donald"]}],"gender":"male"},
              {"resourceType":"VisionPrescription","id":"3","status":"active","created":"2014-06-15","patient":{"reference":"#1"},"dateWritten":"2014-06-15","prescriber":{"reference":"#2"},"lensSpecification":[
                {"product":"lens","eye":"right","sphere":-2,"prism":{"amount":0.5,"base":"down"},"add":2},
                {"product":"lens","eye":"left","sphere":-1,"cylinder":-0.5,"axis":180,"prism":{"amount":0.5,"base":"up"},"add":2}
              ]}
            ]}
            """.trimIndent()

        val result = parseResourceBundleOrNull(json)
        assertNotNull(result)

        val bundle = result!!.baseResource as Bundle
        assertEquals(3, bundle.entry.size)

        val patient = findReferenceInDb("#1", result.localDb) as Patient
        assertEquals("Donald Duck", patient.name.first().assembleName())

        val prescriber = findReferenceInDb("#2", result.localDb) as Practitioner
        assertEquals("Adam Careful", prescriber.name.first().assembleName())

        val vision = bundle.entry.filterIsInstance<VisionPrescription>().first()
        assertEquals(2, vision.glasses().size)
        assertEquals(-2.0, vision.glassesRightEyes().first().sphere!!, 0.001)
        assertEquals(
            "down",
            vision
                .glassesRightEyes()
                .first()
                .prism
                ?.base,
        )
    }

    @Test
    fun ignoresUnknownAndExtraFields() {
        // resourceType we don't model + extra top-level and nested fields that many
        // implementations add. Should still parse the known bits, not throw.
        val json =
            """
            {"resourceType":"VisionPrescription","id":"7","status":"active","meta":{"versionId":"1"},
             "extraField":"whatever","lensSpecification":[
               {"eye":"right","sphere":-1.25,"vendorSpecific":{"foo":"bar"},"tags":["a","b"]}
             ]}
            """.trimIndent()

        val result = parseResourceBundleOrNull(json)
        assertNotNull(result)

        val vision = result!!.baseResource as VisionPrescription
        assertEquals("active", vision.status)
        assertEquals(-1.25, vision.lensSpecification.first().sphere!!, 0.001)
    }

    @Test
    fun fallsBackForUnknownResourceType() {
        val json = """{"resourceType":"Observation","id":"obs-1","valueQuantity":{"value":9.5}}"""

        val result = parseResourceBundleOrNull(json)
        assertNotNull(result)

        val base = result!!.baseResource
        assertTrue(base is UnknownResource)
        assertEquals("obs-1", base!!.id)
    }

    @Test
    fun keepsUnknownEntriesInsideBundle() {
        // A Bundle mixing a known and an unknown resource must not fail wholesale.
        val json =
            """
            {"resourceType":"Bundle","id":"b","entry":[
              {"resourceType":"Observation","id":"obs","code":{"text":"bp"}},
              {"resourceType":"Patient","id":"p","name":[{"family":"Doe","given":["Jane"]}]}
            ]}
            """.trimIndent()

        val result = parseResourceBundleOrNull(json)
        assertNotNull(result)

        val bundle = result!!.baseResource as Bundle
        assertEquals(2, bundle.entry.size)
        assertTrue(bundle.entry.first() is UnknownResource)

        val patient = findReferenceInDb("p", result.localDb) as Patient
        assertEquals("Jane Doe", patient.name.first().assembleName())
    }

    @Test
    fun returnsNullOnGarbage() {
        assertNull(parseResourceBundleOrNull("not json at all"))
    }
}
