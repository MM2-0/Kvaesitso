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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.napplet.protocol.IntentAvailability
import com.vitorpamplona.amethyst.commons.napplet.protocol.IntentCandidate
import com.vitorpamplona.amethyst.commons.napplet.protocol.IntentContract
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.snapshot.NappletSnapshotEvent

/** An installed napplet that can handle intents: its catalog [id], [manifest] and the [contracts] it accepts. */
class NappletIntentHandler(
    val id: String,
    val manifest: NappletManifest,
    val contracts: List<IntentContract>,
) {
    val title: String? get() = manifest.title()

    fun accepts(convention: String) = contracts.any { it.convention == convention }

    fun archetypes(): Set<String> = contracts.mapTo(LinkedHashSet()) { NappletIntentCatalog.archetypeOf(it.convention) }
}

/**
 * NAP-INTENT's catalog: the handlers among the napplets the user installed, which in Amethyst are the
 * napplets pinned to the favorites, in the user's own order. That order is the user's default: the
 * first installed handler of an archetype handles it, and the user changes it by reordering their
 * favorites. A napplet can't add itself, reorder, or set a default.
 *
 * A napplet is a handler only when its manifest is launchable here, declares `intent` (without it the
 * napplet has no `onDelivery` to receive with), and advertises a role with `z` plus an `i` contract
 * `napplet:<that role>/<action>`. Advertisements are routing hints and grant nothing.
 */
class NappletIntentCatalog(
    val handlers: List<NappletIntentHandler>,
) {
    /** The handlers of [convention]'s archetype that accept it, the default first. */
    fun candidatesFor(convention: String): List<NappletIntentHandler> = handlers.filter { it.accepts(convention) }

    fun availability(archetype: String): IntentAvailability {
        val ofRole = handlers.filter { archetype in it.archetypes() }
        return IntentAvailability(
            archetype,
            ofRole.mapIndexed { index, handler ->
                IntentCandidate(handler.id, handler.title, handler.contracts.filter { archetypeOf(it.convention) == archetype }, isDefault = index == 0)
            },
        )
    }

    fun allAvailability(): List<IntentAvailability> = handlers.flatMap { it.archetypes() }.distinct().map(::availability)

    companion object {
        private val CONVENTION = Regex("^napplet:([^/?#]+)/([^/?#]+)$")

        fun archetypeOf(convention: String): String = convention.removePrefix("napplet:").substringBefore('/')

        /** Whether [convention] is a stable, queryless, fragment-free `napplet:<archetype>/<action>`. */
        fun isConvention(convention: String): Boolean = CONVENTION.matches(convention)

        /**
         * NAP-INTENT's catalog identifier: named napplets by publisher, kind and `d`; root napplets by
         * publisher and kind; snapshots by their event id. Never a bare `d`.
         */
        fun catalogId(manifest: Event): String =
            when (manifest) {
                is NamedNappletEvent -> "${manifest.kind}:${manifest.pubKey}:${manifest.identifier()}"
                is NappletSnapshotEvent -> "${manifest.kind}:${manifest.id}"
                else -> "${manifest.kind}:${manifest.pubKey}"
            }

        /** The catalog identifier of a running napplet, from its verified launch identity: the `sender` of what it invokes. */
        fun catalogId(identity: NappletIdentity): String =
            when (identity.manifestKind) {
                NamedNappletEvent.KIND -> "${NamedNappletEvent.KIND}:${identity.authorPubKey}:${identity.identifier}"
                NappletSnapshotEvent.KIND -> "${NappletSnapshotEvent.KIND}:${identity.manifestEventId.orEmpty()}"
                RootNappletEvent.KIND -> "${RootNappletEvent.KIND}:${identity.authorPubKey}"
                else -> "napplet:${identity.coordinate}"
            }

        /** The handler [manifest] makes, or null when it isn't one (see the class). */
        fun handlerOf(manifest: NappletManifest): NappletIntentHandler? {
            if (!manifest.isLaunchable()) return null
            if ("intent" !in manifest.required() && "intent" !in manifest.optional()) return null
            val roles = manifest.archetypes().toSet()
            val contracts =
                manifest
                    .intents()
                    .filter { isConvention(it.intent) && archetypeOf(it.intent) in roles }
                    .map { IntentContract(it.intent, it.params) }
                    .distinctBy { it.convention }
            if (contracts.isEmpty()) return null
            return NappletIntentHandler(catalogId(manifest as Event), manifest, contracts)
        }

        /**
         * The manifest a favorite [coordinate] (`kind:pubkey:d`) names: the newest one in the cache, or
         * the copy the favorites keep, once its signature checks out.
         */
        fun favoriteManifest(
            registry: FavoriteAppsRegistry,
            coordinate: String,
        ): NappletManifest? =
            LocalCache.getAddressableNoteIfExists(coordinate)?.event as? NappletManifest
                ?: registry.cachedManifest(coordinate)?.let(Event::fromJsonOrNull)?.takeIf { it.verify() } as? NappletManifest

        /** The catalog of the napplets pinned to [registry], in the user's order. */
        fun of(registry: FavoriteAppsRegistry): NappletIntentCatalog = of(registry.favorites.value) { favoriteManifest(registry, it) }

        /**
         * The catalog of [favorites], in their order, each napplet's manifest read by [manifestOf]
         * (its favorite coordinate `kind:pubkey:d` in, the manifest or null out).
         */
        fun of(
            favorites: List<FavoriteApp>,
            manifestOf: (coordinate: String) -> NappletManifest?,
        ): NappletIntentCatalog =
            NappletIntentCatalog(
                favorites
                    .filterIsInstance<FavoriteApp.NostrApp>()
                    .mapNotNull { manifestOf(it.coordinate)?.let(::handlerOf) }
                    .distinctBy { it.id },
            )
    }
}
