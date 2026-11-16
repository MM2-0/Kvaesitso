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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * FHIR resources are polymorphic on the `resourceType` string. We only model the
 * handful of types Amethyst renders; anything else (and any resource with a
 * missing/unrecognized type) decodes into [UnknownResource] so a mixed [Bundle]
 * never fails to parse just because it carries a type we don't know about.
 */
object ResourceSerializer : JsonContentPolymorphicSerializer<Resource>(Resource::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Resource> =
        when (element.jsonObject["resourceType"]?.jsonPrimitive?.content) {
            "Practitioner" -> Practitioner.serializer()
            "Patient" -> Patient.serializer()
            "Bundle" -> Bundle.serializer()
            "VisionPrescription" -> VisionPrescription.serializer()
            else -> UnknownResource.serializer()
        }
}

@Serializable(with = ResourceSerializer::class)
@Stable
abstract class Resource {
    abstract val resourceType: String?
    abstract val id: String
}

/** Fallback for any FHIR resourceType we don't model. */
@Serializable
@Stable
class UnknownResource(
    override val resourceType: String? = null,
    override val id: String = "",
) : Resource()

@Serializable
@Stable
class Practitioner(
    override val resourceType: String? = null,
    override val id: String = "",
    var active: Boolean? = null,
    var name: ArrayList<HumanName> = arrayListOf(),
    var gender: String? = null,
) : Resource()

@Serializable
@Stable
class Patient(
    override val resourceType: String? = null,
    override val id: String = "",
    var active: Boolean? = null,
    var name: ArrayList<HumanName> = arrayListOf(),
    var gender: String? = null,
) : Resource()

@Serializable
@Stable
class HumanName(
    var use: String? = null,
    var family: String? = null,
    var given: ArrayList<String> = arrayListOf(),
) {
    fun assembleName(): String = given.joinToString(" ") + " " + family
}

@Serializable
@Stable
class Bundle(
    override val resourceType: String? = null,
    override val id: String = "",
    var type: String? = null,
    var created: String? = null,
    var entry: List<Resource> = arrayListOf(),
) : Resource()

@Serializable
@Stable
class VisionPrescription(
    override val resourceType: String? = null,
    override val id: String = "",
    var status: String? = null,
    var created: String? = null,
    var patient: Reference? = Reference(),
    var encounter: Reference? = Reference(),
    var dateWritten: String? = null,
    var prescriber: Reference? = Reference(),
    var lensSpecification: List<LensSpecification> = arrayListOf(),
) : Resource() {
    fun glasses() = lensSpecification.filter { it.product == "lens" }

    fun contacts() = lensSpecification.filter { it.product == "contacts" }

    fun glassesRightEyes() = lensSpecification.filter { it.product == "lens" && it.eye == "right" }

    fun glassesLeftEyes() = lensSpecification.filter { it.product == "lens" && it.eye == "left" }

    fun contactsRightEyes() = lensSpecification.filter { it.product == "contacts" && it.eye == "right" }

    fun contactsLeftEyes() = lensSpecification.filter { it.product == "contacts" && it.eye == "left" }
}

@Serializable
@Stable
class LensSpecification(
    var product: String? = null,
    var eye: String? = null,
    var sphere: Double? = null,
    var cylinder: Double? = null,
    var axis: Double? = null,
    var pd: Double? = null,
    var interAdd: Double? = null,
    var add: Double? = null,
    var prism: Prism? = null,
    // contact lenses
    var power: Double? = null,
    var backCurve: Double? = null,
    var diameter: Double? = null,
    var color: String? = null,
    var brand: String? = null,
    var note: String? = null,
)

@Serializable
@Stable
class Prism(
    var amount: Double? = null,
    var base: String? = null,
)

@Serializable
class Reference(
    var reference: String? = null,
)

data class FhirElementDatabase(
    var baseResource: Resource? = null,
    var localDb: ImmutableMap<String, Resource> = persistentMapOf(),
)

fun findReferenceInDb(
    it: String,
    db: Map<String, Resource>,
): Resource? {
    val parts = it.split("/")
    return if (parts.size == 2) {
        db.get(parts[1].removePrefix("#"))
    } else {
        db.get(it.removePrefix("#"))
    }
}

/**
 * Lenient FHIR JSON reader: unknown keys are ignored (implementations routinely add
 * their own fields) and missing keys fall back to the property defaults, so we parse
 * as much of a resource as we can rather than rejecting the whole document.
 */
val FhirJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

fun parseResourceBundleOrNull(json: String): FhirElementDatabase? =
    try {
        val resource = FhirJson.decodeFromString(ResourceSerializer, json)

        val db =
            when (resource) {
                is Bundle -> {
                    resource.entry.associateBy { it.id }.toImmutableMap()
                }

                else -> {
                    persistentMapOf(resource.id to resource)
                }
            }

        FhirElementDatabase(
            localDb = db,
            baseResource = resource,
        )
    } catch (e: Exception) {
        Log.e("RenderEyeGlassesPrescription", "Parser error", e)
        null
    }
