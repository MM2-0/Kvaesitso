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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.fitness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.FitnessGoals
import com.vitorpamplona.amethyst.commons.fitness.FitnessInsights
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the redesigned My Fitness dashboard headlessly, in both themes, over a synthetic
 * half-year of training: a runner who rides at weekends, lifts on Tuesdays, slumped for a few weeks
 * in August and is building back. Structure, not pixels: every string resolves (a missing one
 * throws here) and the screen follows the theme.
 *
 * Set MY_FITNESS_RENDER_DIR to also write the PNGs for a human to look at.
 */
class FitnessDashboardRenderTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val now = ZonedDateTime.of(2026, 10, 7, 18, 0, 0, 0, zone).toInstant()
    private val today = LocalDate.of(2026, 10, 7)

    private fun sampleLog(): List<DetectedWorkout> {
        val random = Random(42)
        val list = mutableListOf<DetectedWorkout>()
        var id = 0

        fun add(
            date: LocalDate,
            exercise: ExerciseType,
            minutes: Int,
            hour: Int,
            km: Double? = null,
            climb: Double? = null,
            hr: Int? = null,
            published: Boolean = true,
        ) {
            if (date.isAfter(today)) return
            list +=
                DetectedWorkout(
                    id = "s${id++}",
                    exercise = exercise,
                    title = null,
                    startTimeEpochSeconds = date.atTime(hour, random.nextInt(0, 50)).atZone(zone).toEpochSecond(),
                    durationSeconds = minutes * 60L,
                    distanceMeters = km?.let { it * 1000 },
                    calories = if (exercise == ExerciseType.FASTING) null else minutes * 9,
                    avgHeartRate = hr,
                    maxHeartRate = hr?.let { it + 25 },
                    steps = null,
                    elevationGainMeters = climb,
                    source = "Garmin",
                    alreadyPublished = published,
                )
        }

        val monday = today.minusDays(today.dayOfWeek.value - 1L)
        for (w in 25 downTo 0) {
            val start = monday.minusWeeks(w.toLong())
            // A slump in weeks 7..9 ago, then building back up.
            val slump = w in 7..9
            val build = if (w < 7) (7 - w) * 0.05 else 0.0
            if (!slump || random.nextBoolean()) add(start, ExerciseType.RUNNING, (35 * (1 + build)).toInt(), 6, km = 6.5 * (1 + build), hr = 148)
            if (!slump) add(start.plusDays(1), ExerciseType.STRENGTH, 45, 19, hr = 118)
            if (!slump && random.nextInt(10) < 7) add(start.plusDays(3), ExerciseType.RUNNING, 30 + random.nextInt(15), 7, km = 5.5 + random.nextDouble(2.0), hr = 152)
            if (!slump || random.nextInt(4) == 0) add(start.plusDays(5), ExerciseType.CYCLING, 75 + random.nextInt(60), 8, km = 30.0 + random.nextInt(30), climb = 300.0 + random.nextInt(500), hr = 132)
            if (random.nextInt(10) < 4) add(start.plusDays(6), ExerciseType.YOGA, 30, 9)
            if (random.nextInt(10) < 3) add(start.plusDays(4), ExerciseType.WALKING, 40, 13, km = 3.5)
        }
        // This week's long run, which sets a record, recorded by the watch and not yet shared.
        add(today.minusDays(1), ExerciseType.RUNNING, 82, 7, km = 15.2, hr = 155, published = false)
        add(today, ExerciseType.FASTING, 16 * 60, 0)
        return list
    }

    private val goals = FitnessGoals(weeklyActiveMinutes = 180, weeklyWorkouts = 4, weeklyActiveDays = 4, distance = FitnessGoals.DistanceGoal(ExerciseType.RUNNING, 25_000.0))

    private val scenarios: Map<String, @Composable () -> Unit> =
        mapOf(
            "dashboard" to {
                FitnessDashboard(
                    insights = FitnessInsights.build(sampleLog(), goals, now, zone),
                    onEditGoals = {},
                    canShare = { !it.alreadyPublished },
                    onShare = { _, _ -> },
                    showDeviceHorizon = true,
                    miles = false,
                )
            },
            "new-user" to {
                FitnessDashboard(
                    insights =
                        FitnessInsights.build(
                            listOf(
                                DetectedWorkout("n1", ExerciseType.WALKING, null, today.atTime(8, 0).atZone(zone).toEpochSecond(), 1800, 2400.0, null, null, null, 3200, null, "Pixel"),
                            ),
                            FitnessGoals.DEFAULT,
                            now,
                            zone,
                        ),
                    onEditGoals = {},
                    canShare = { true },
                    onShare = { _, _ -> },
                    miles = true,
                )
            },
            // The editor body rather than the AlertDialog: a dialog window is captured mid enter-animation.
            "goals-editor" to {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                    Column(Modifier.padding(24.dp)) { FitnessGoalsEditor(goals, {}, miles = false) }
                }
            },
        )

    private fun render(
        name: String,
        dark: Boolean,
        height: Int,
        content: @Composable () -> Unit,
    ): BufferedImage {
        val scene =
            ImageComposeScene(width = 820, height = height, density = Density(2f)) {
                AmethystPreviewTheme(dark = dark) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                    ) {
                        content()
                    }
                }
            }
        return try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            System.getenv("MY_FITNESS_RENDER_DIR")?.let { dir ->
                File(dir, "my-fitness-$name-${if (dark) "dark" else "light"}.png").writeBytes(png)
            }
            ImageIO.read(ByteArrayInputStream(png))
        } finally {
            scene.close()
        }
    }

    private fun BufferedImage.distinctColours(): Int {
        val seen = mutableSetOf<Int>()
        for (x in 0 until width step 3) {
            for (y in 0 until height step 3) seen += getRGB(x, y)
        }
        return seen.size
    }

    @Test
    fun everyStateRendersInBothThemes() {
        scenarios.forEach { (name, content) ->
            val height = if (name == "dashboard") 6400 else 3200
            val light = render(name, false, height, content)
            val dark = render(name, true, height, content)

            assertTrue(light.distinctColours() > 20, "$name: the light screen drew ${light.distinctColours()} colours")
            assertTrue(dark.distinctColours() > 20, "$name: the dark screen drew ${dark.distinctColours()} colours")
            assertTrue(light.getRGB(2, 2) != dark.getRGB(2, 2), "$name: the screen ignored the theme")
        }
    }
}
