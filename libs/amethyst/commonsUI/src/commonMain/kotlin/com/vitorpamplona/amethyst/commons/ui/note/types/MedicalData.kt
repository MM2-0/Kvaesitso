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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Bundle
import com.vitorpamplona.amethyst.commons.model.FhirElementDatabase
import com.vitorpamplona.amethyst.commons.model.LensSpecification
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.Patient
import com.vitorpamplona.amethyst.commons.model.Practitioner
import com.vitorpamplona.amethyst.commons.model.Prism
import com.vitorpamplona.amethyst.commons.model.Reference
import com.vitorpamplona.amethyst.commons.model.Resource
import com.vitorpamplona.amethyst.commons.model.VisionPrescription
import com.vitorpamplona.amethyst.commons.model.findReferenceInDb
import com.vitorpamplona.amethyst.commons.model.parseResourceBundleOrNull
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kc_dev_ci_policy_unknown
import com.vitorpamplona.amethyst.commons.resources.medical_data_add
import com.vitorpamplona.amethyst.commons.resources.medical_data_axis
import com.vitorpamplona.amethyst.commons.resources.medical_data_curve
import com.vitorpamplona.amethyst.commons.resources.medical_data_cylinder
import com.vitorpamplona.amethyst.commons.resources.medical_data_diameter
import com.vitorpamplona.amethyst.commons.resources.medical_data_eye
import com.vitorpamplona.amethyst.commons.resources.medical_data_inter_add
import com.vitorpamplona.amethyst.commons.resources.medical_data_patient
import com.vitorpamplona.amethyst.commons.resources.medical_data_prism
import com.vitorpamplona.amethyst.commons.resources.medical_data_pupillary_distance
import com.vitorpamplona.amethyst.commons.resources.medical_data_signed_by
import com.vitorpamplona.amethyst.commons.resources.medical_data_sphere
import com.vitorpamplona.amethyst.commons.resources.medical_prescription_contacts
import com.vitorpamplona.amethyst.commons.resources.medical_prescription_empty
import com.vitorpamplona.amethyst.commons.resources.medical_prescription_glasses
import com.vitorpamplona.amethyst.commons.resources.medical_prescription_vision
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.util.DecimalPatternFormatter
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Preview
@Composable
fun RenderEyeGlassesPrescriptionPreview() {
    val prescriptionEvent =
        Event.fromJson(
            "{\"id\":\"0c15d2bc6f7dcc42fa4426d35d30d09840c9afa5b46d100415006e41d6471416\",\"pubkey\":\"bcd4715cc34f98dce7b52fddaf1d826e5ce0263479b7e110a5bd3c3789486ca8\",\"created_at\":1709074097,\"kind\":82,\"tags\":[],\"content\":\"{\\\"resourceType\\\":\\\"Bundle\\\",\\\"id\\\":\\\"bundle-vision-test\\\",\\\"type\\\":\\\"document\\\",\\\"entry\\\":[{\\\"resourceType\\\":\\\"Practitioner\\\",\\\"id\\\":\\\"2\\\",\\\"active\\\":true,\\\"name\\\":[{\\\"use\\\":\\\"official\\\",\\\"family\\\":\\\"Careful\\\",\\\"given\\\":[\\\"Adam\\\"]}],\\\"gender\\\":\\\"male\\\"},{\\\"resourceType\\\":\\\"Patient\\\",\\\"id\\\":\\\"1\\\",\\\"active\\\":true,\\\"name\\\":[{\\\"use\\\":\\\"official\\\",\\\"family\\\":\\\"Duck\\\",\\\"given\\\":[\\\"Donald\\\"]}],\\\"gender\\\":\\\"male\\\"},{\\\"resourceType\\\":\\\"VisionPrescription\\\",\\\"status\\\":\\\"active\\\",\\\"created\\\":\\\"2014-06-15\\\",\\\"patient\\\":{\\\"reference\\\":\\\"#1\\\"},\\\"dateWritten\\\":\\\"2014-06-15\\\",\\\"prescriber\\\":{\\\"reference\\\":\\\"#2\\\"},\\\"lensSpecification\\\":[{\\\"eye\\\":\\\"right\\\",\\\"sphere\\\":-2,\\\"prism\\\":[{\\\"amount\\\":0.5,\\\"base\\\":\\\"down\\\"}],\\\"add\\\":2},{\\\"eye\\\":\\\"left\\\",\\\"sphere\\\":-1,\\\"cylinder\\\":-0.5,\\\"axis\\\":180,\\\"prism\\\":[{\\\"amount\\\":0.5,\\\"base\\\":\\\"up\\\"}],\\\"add\\\":2}]}]}\",\"sig\":\"dc58f6109111ca06920c0c711aeaf8e2ee84975afa60d939828d4e01e2edea738f735fb5b1fcadf6d5496e36ac429abf7020a55fd1e4ed215738afc8d07cb950\"}",
        ) as FhirResourceEvent

    RenderFhirResource(prescriptionEvent)
}

@Preview
@Composable
fun RenderEyeGlassesPrescription2Preview() {
    val vision =
        VisionPrescription(
            id = "1",
            status = null,
            created = null,
            patient = Reference(),
            encounter = Reference(),
            dateWritten = null,
            prescriber = Reference(),
            lensSpecification =
                listOf(
                    LensSpecification(
                        product = "lens",
                        eye = "right",
                        sphere = -1.00,
                        cylinder = -2.00,
                        axis = 180.0,
                        pd = 31.0,
                        interAdd = 1.50,
                        add = 1.75,
                        prism = Prism(12.0, "down"),
                        power = null,
                        diameter = null,
                        color = null,
                        brand = null,
                        note = null,
                    ),
                    LensSpecification(
                        product = "lens",
                        eye = "left",
                        sphere = -1.00,
                        cylinder = -2.00,
                        axis = 180.0,
                        pd = 31.0,
                        interAdd = 1.50,
                        add = 1.75,
                        prism = Prism(12.0, "down"),
                        power = null,
                        diameter = null,
                        color = null,
                        brand = null,
                        note = null,
                    ),
                    LensSpecification(
                        product = "contacts",
                        eye = "right",
                        sphere = -1.00,
                        cylinder = -2.00,
                        axis = 180.0,
                        pd = null,
                        interAdd = null,
                        add = null,
                        prism = null,
                        backCurve = 12.0,
                        power = 1.2,
                        diameter = 1.2,
                        color = "blue",
                        brand = "Blue Glasses",
                        note = "note",
                    ),
                    LensSpecification(
                        product = "contacts",
                        eye = "left",
                        sphere = -1.00,
                        cylinder = -2.00,
                        axis = 180.0,
                        pd = null,
                        interAdd = null,
                        add = null,
                        prism = null,
                        backCurve = 12.0,
                        power = 1.2,
                        diameter = 1.2,
                        color = "blue",
                        brand = "Blue Glasses",
                        note = "note",
                    ),
                ),
        )

    val db =
        mapOf(
            "1" to vision,
        ).toImmutableMap()

    RenderEyeGlassesPrescription(vision, db)
}

@Composable
fun RenderFhirResource(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? FhirResourceEvent ?: return

    RenderFhirResource(event)
}

@Composable
fun RenderFhirResource(event: FhirResourceEvent) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val state by produceState(initialValue = FhirElementDatabase(), key1 = event) {
        withContext(Dispatchers.IO) {
            parseResourceBundleOrNull(event.content)?.let {
                value = it
            }
        }
    }

    state.baseResource?.let { resource ->
        when (resource) {
            is Bundle -> {
                val vision = resource.entry.filterIsInstance<VisionPrescription>()

                vision.firstOrNull()?.let {
                    RenderEyeGlassesPrescription(it, state.localDb)
                }
            }

            is VisionPrescription -> {
                RenderEyeGlassesPrescription(resource, state.localDb)
            }

            else -> {
            }
        }
    }
}

@Composable
fun RenderEyeGlassesPrescription(
    visionPrescription: VisionPrescription,
    db: ImmutableMap<String, Resource>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Size10dp),
    ) {
        val glassesRightEye = visionPrescription.glassesRightEyes().firstOrNull()
        val glassesLeftEye = visionPrescription.glassesLeftEyes().firstOrNull()

        val contactsRightEye = visionPrescription.contactsRightEyes().firstOrNull()
        val contactsLeftEye = visionPrescription.contactsLeftEyes().firstOrNull()

        val isGlasses = glassesRightEye != null || glassesLeftEye != null
        val isContacts = contactsRightEye != null || contactsLeftEye != null

        Text(
            when {
                isGlasses && isContacts -> stringRes(Res.string.medical_prescription_vision)
                isGlasses -> stringRes(Res.string.medical_prescription_glasses)
                isContacts -> stringRes(Res.string.medical_prescription_contacts)
                else -> stringRes(Res.string.medical_prescription_empty)
            },
            modifier = Modifier.padding(4.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(StdVertSpacer)

        visionPrescription.patient?.reference?.let {
            val patient = findReferenceInDb(it, db) as? Patient

            patient?.name?.firstOrNull()?.assembleName()?.let {
                Text(
                    text = stringRes(Res.string.medical_data_patient, it),
                    modifier = Modifier.padding(4.dp).fillMaxWidth(),
                )
            }
        }
        visionPrescription.status?.let {
            Text(
                text = "Status: ${it.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }}",
                modifier = Modifier.padding(4.dp).fillMaxWidth(),
            )
        }

        Spacer(DoubleVertSpacer)

        if (isGlasses) {
            RenderEyeGlassesPrescriptionHeaderRow()
            HorizontalDivider(thickness = DividerThickness)

            glassesRightEye?.let {
                RenderEyeGlassesPrescriptionRow(data = it)
                HorizontalDivider(thickness = DividerThickness)
            }

            glassesLeftEye?.let {
                RenderEyeGlassesPrescriptionRow(data = it)
                HorizontalDivider(thickness = DividerThickness)
            }
        }

        Spacer(DoubleVertSpacer)

        if (isContacts) {
            RenderEyeContactsPrescriptionHeaderRow()
            HorizontalDivider(thickness = DividerThickness)

            contactsRightEye?.let {
                RenderEyeContactsPrescriptionRow(data = it)
                HorizontalDivider(thickness = DividerThickness)
            }

            contactsLeftEye?.let {
                RenderEyeContactsPrescriptionRow(data = it)
                HorizontalDivider(thickness = DividerThickness)
            }
        }

        visionPrescription.prescriber?.reference?.let {
            val practitioner = findReferenceInDb(it, db) as? Practitioner

            practitioner?.name?.firstOrNull()?.assembleName()?.let {
                Spacer(DoubleVertSpacer)
                Text(
                    text = stringRes(Res.string.medical_data_signed_by, it),
                    modifier = Modifier.padding(4.dp).fillMaxWidth(),
                    textAlign = TextAlign.Right,
                )
            }
        }
    }
}

@Composable
fun RenderEyeGlassesPrescriptionHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringRes(Res.string.medical_data_eye),
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = stringRes(Res.string.medical_data_sphere),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = stringRes(Res.string.medical_data_cylinder),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = stringRes(Res.string.medical_data_axis),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = stringRes(Res.string.medical_data_pupillary_distance),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
    }
}

@Composable
fun RenderEyeGlassesPrescriptionRow(data: LensSpecification) {
    val numberFormat = DecimalPatternFormatter("##.00")
    val pdFormat = DecimalPatternFormatter("##.0")
    val integerFormat = DecimalPatternFormatter("###")

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                data.eye?.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                } ?: stringRes(Res.string.kc_dev_ci_policy_unknown),
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = formatOrBlank(data.sphere, numberFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = formatOrBlank(data.cylinder, numberFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = formatOrBlank(data.axis, integerFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = formatOrBlank(data.pd, pdFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
    }

    if (data.interAdd != null) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "",
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            VerticalDivider(thickness = DividerThickness)

            if (data.interAdd != null) {
                Text(
                    text = stringRes(Res.string.medical_data_inter_add),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(4.dp).weight(2f),
                )

                Text(
                    text = formatOrBlank(data.interAdd, numberFormat),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(4.dp).weight(2f),
                )
            }
        }
    }

    if (data.add != null) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "",
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            VerticalDivider(thickness = DividerThickness)

            if (data.add != null) {
                Text(
                    text = stringRes(Res.string.medical_data_add),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(4.dp).weight(2f),
                )

                Text(
                    text = formatOrBlank(data.add, numberFormat),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.padding(4.dp).weight(2f),
                )
            }
        }
    }

    if (data.prism?.amount != null || data.prism?.base != null) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "",
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            VerticalDivider(thickness = DividerThickness)

            Text(
                text = stringRes(Res.string.medical_data_prism),
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(2f),
            )

            Text(
                text = formatOrBlank(data.prism?.amount, numberFormat),
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )

            Text(
                text = data.prism?.base ?: "",
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
        }
    }
}

@Composable
fun RenderEyeContactsPrescriptionHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringRes(Res.string.medical_data_eye),
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = stringRes(Res.string.medical_data_sphere),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = stringRes(Res.string.medical_data_cylinder),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = stringRes(Res.string.medical_data_axis),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
    }
}

@Composable
fun RenderEyeContactsPrescriptionRow(data: LensSpecification) {
    val numberFormat = DecimalPatternFormatter("##.00")
    val integerFormat = DecimalPatternFormatter("###")

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                data.eye?.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                } ?: stringRes(Res.string.kc_dev_ci_policy_unknown),
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = formatOrBlank(data.sphere, numberFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = formatOrBlank(data.cylinder, numberFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = formatOrBlank(data.axis, integerFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
    }

    if (data.backCurve != null || data.diameter != null) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "",
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            VerticalDivider(thickness = DividerThickness)
            Text(
                text = "",
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            Text(
                text = stringRes(Res.string.medical_data_curve),
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            Text(
                text = formatOrBlank(data.backCurve, numberFormat),
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
        }
    }

    if (data.backCurve != null || data.diameter != null) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "",
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            VerticalDivider(thickness = DividerThickness)
            Text(
                text = "",
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            Text(
                text = stringRes(Res.string.medical_data_diameter),
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            Text(
                text = formatOrBlank(data.diameter, numberFormat),
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
        }
    }

    if (data.brand != null) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "",
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(1f),
            )
            VerticalDivider(thickness = DividerThickness)
            Text(
                text = data.brand ?: "",
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(4.dp).weight(3f),
            )
        }
    }
}

fun formatOrBlank(
    amount: Double?,
    numberFormat: DecimalPatternFormatter,
): String {
    if (amount == null) return ""
    if (abs(amount) < 0.01) return ""
    return numberFormat.format(amount)
}
