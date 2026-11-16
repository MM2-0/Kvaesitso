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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.health

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.health.HealthConnectRationaleScreen
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

/**
 * The Health Connect permissions rationale — the screen Health Connect itself opens when the user
 * taps "privacy policy" / "read more" next to Amethyst, on both the pre-request dialog and the
 * later data-management screens.
 *
 * Declaring the intent filters is not optional: without a target for
 * `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 13 and lower) or
 * `ACTION_VIEW_PERMISSION_USAGE` + `CATEGORY_HEALTH_PERMISSIONS` (Android 14+) the permission
 * request fails silently and no dialog appears at all.
 *
 * Deliberately its own activity rather than a route inside MainActivity: Health Connect launches it
 * cold, from outside the app, with no account loaded and no guarantee the user is even logged in.
 * It shows static, read-only copy and touches nothing account-scoped.
 */
class HealthConnectRationaleActivity : AppCompatActivity() {
    companion object {
        const val PRIVACY_POLICY_URL = "https://github.com/vitorpamplona/amethyst/blob/main/PRIVACY.md"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AmethystTheme {
                HealthConnectRationaleScreen(
                    onOpenPrivacyPolicy = {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())) }
                    },
                    onClose = { finish() },
                )
            }
        }
    }
}
