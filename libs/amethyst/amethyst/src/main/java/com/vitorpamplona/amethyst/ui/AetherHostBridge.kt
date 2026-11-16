/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Application-level factories only: never retain an Activity or its Context here. */
object AetherHostBridge {
    const val EXTRA_CLIENT_INTENT = "com.telos.aether.CLIENT_INTENT"

    var createHostIntent: ((Context) -> Intent)? = null

    fun redirect(context: Context, incoming: Intent): Intent? {
        val factory = createHostIntent ?: return null
        return factory(context.applicationContext).apply {
            putExtra(EXTRA_CLIENT_INTENT, Intent(incoming))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(incoming.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            clipData = incoming.clipData
            if (clipData == null) {
                val uris = if (incoming.action == Intent.ACTION_SEND_MULTIPLE) {
                    IntentCompat.getParcelableArrayListExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else {
                    listOfNotNull(IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java))
                }
                uris.take(100).forEach { uri ->
                    val current = clipData
                    if (current == null) clipData = ClipData.newRawUri("Aether", uri)
                    else current.addItem(ClipData.Item(uri))
                }
            }
        }
    }

    fun clientIntent(intent: Intent): Intent {
        IntentCompat.getParcelableExtra(intent, EXTRA_CLIENT_INTENT, Intent::class.java)?.let { return it }
        if (createHostIntent == null || intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE || intent.data?.scheme == "nostr") return intent
        // Telos task identity URIs are not Nostr links.
        return Intent()
    }
}
