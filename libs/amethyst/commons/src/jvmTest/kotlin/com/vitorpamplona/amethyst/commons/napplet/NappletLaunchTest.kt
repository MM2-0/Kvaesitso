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

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NappletLaunchTest {
    private val signer = NostrSignerSync()
    private val artifact = "ab".repeat(32)

    private fun napplet(
        identifier: String = "demo",
        description: String = "A napplet",
        required: List<String> = emptyList(),
    ): NamedNappletEvent = signer.sign(NamedNappletEvent.build(identifier, artifact, description, required = required))

    @Test
    fun aValidManifestLaunchesLocked() {
        val event = napplet(required = listOf("storage"))
        val launch = NappletLaunch.fromManifest(event, event.pubKey, "demo").getOrThrow()
        assertEquals(HostProfile.NAPPLET, launch.profile)
        assertEquals(setOf(NappletCapability.STORAGE), launch.declared)
        assertEquals(listOf("storage"), launch.declaredDomains)
        assertEquals(listOf("/index.html" to artifact), launch.paths.map { it.path to it.hash })
        assertEquals(artifact, launch.identity.aggregateHash)
        assertEquals(event.id, launch.identity.manifestEventId)
    }

    /** PhotoHop as published ("Fotohop"): it requires NAP-FS, which used to keep it from opening at all. */
    @Test
    fun thePublishedPhotoHopLaunchesWithFiles() {
        val event = Event.fromJson(PHOTOHOP) as RootNappletEvent
        val launch = NappletLaunch.fromManifest(event).getOrThrow()
        assertEquals("Fotohop", launch.title)
        assertEquals(setOf(NappletCapability.FS, NappletCapability.STORAGE, NappletCapability.THEME), launch.declared)
        assertEquals("16511c25535fe08d5fd1b39ea0da30f843ee2654f6838deab6108b1a9ecf6c45", launch.identity.aggregateHash)
    }

    @Test
    fun aForgedSignatureIsRefused() {
        val signed = napplet()
        val forged = NamedNappletEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, "A different description", signed.sig)
        assertTrue(NappletLaunch.fromManifest(forged).isFailure)
    }

    @Test
    fun anotherAuthorOrNameIsRefused() {
        val event = napplet()
        assertTrue(NappletLaunch.fromManifest(event, "aa".repeat(32)).isFailure)
        assertTrue(NappletLaunch.fromManifest(event, event.pubKey, "other").isFailure)
    }

    @Test
    fun anInvalidManifestOrAnUnofferedDomainIsRefused() {
        assertTrue(NappletLaunch.fromManifest(napplet(description = " ")).isFailure)
        assertTrue(NappletLaunch.fromManifest(napplet(required = listOf("relay", "mesh"))).isFailure)
    }

    @Test
    fun aSiteLaunchesInTheWebsitePosture() {
        val paths = listOf(PathTag("/index.html", "cd".repeat(32)), PathTag("/app.js", "ef".repeat(32)))
        val named = signer.sign(NamedSiteEvent.build("blog", paths, servers = listOf("https://blossom.example"), title = "Blog"))
        val launch = NappletLaunch.fromSite(named).getOrThrow()
        assertEquals(HostProfile.WEBSITE, launch.profile)
        assertEquals(NappletCapability.WEBSITE_CAPABILITIES, launch.declared)
        assertEquals("blog", launch.identity.identifier)
        assertEquals("Blog", launch.title)
        assertEquals(paths.map { it.path to it.hash }, launch.paths.map { it.path to it.hash })
        assertTrue("https://blossom.example" in launch.servers)

        val root = signer.sign(RootSiteEvent.build(paths))
        val rootLaunch = NappletLaunch.fromSite(root).getOrThrow()
        assertEquals("", rootLaunch.identity.identifier)
        assertEquals("nsite", rootLaunch.title)
    }

    @Test
    fun aSiteWithoutFilesOrANappletAsASiteIsRefused() {
        assertTrue(NappletLaunch.fromSite(signer.sign(RootSiteEvent.build(emptyList()))).isFailure)
        assertTrue(NappletLaunch.fromSite(napplet()).isFailure)
    }

    // A note can embed an older signed version of an app the user trusts. Grants are per app, so every
    // launch (inline or full screen, Android or desktop) must run the newest version the device has seen.
    @Test
    fun anOlderEmbeddedVersionLaunchesAsTheNewestKnown() {
        val old = signer.sign<NamedNappletEvent>(NamedNappletEvent.build("versioned", artifact, "Old version", createdAt = 1_000))
        val current = signer.sign<NamedNappletEvent>(NamedNappletEvent.build("versioned", "cd".repeat(32), "Current version", createdAt = 2_000))
        LocalCache.justConsume(current, null, true)

        val launch = NappletLaunch.fromManifest(old, old.pubKey, "versioned").getOrThrow()

        assertEquals(current.id, launch.identity.manifestEventId)
        assertEquals("cd".repeat(32), launch.identity.aggregateHash)
    }

    private companion object {
        const val PHOTOHOP =
            """{"id":"c583b5d73d4f90fff17ce335a1f75b70cbf199ecec9735cff25de96a0ef5b636","pubkey":"e771af0b05c8e95fcdf6feb3500544d2fb1ccd384788e9f490bb3ee28e8ed66f","created_at":1793437334,"kind":15129,""" +
                """"tags":[["x","16511c25535fe08d5fd1b39ea0da30f843ee2654f6838deab6108b1a9ecf6c45"],["R","fs"],["O","keys"],["O","storage"],["O","theme"],["title","Fotohop"],["server","https://blssm.us"],["server","https://cdn.hzrd149.com"],["server","https://nostr.download"],["server","https://blossom.primal.net"],["client","napplet.run"],["screenshot","x bf38b76f88c404281dd559f3e6f5f6c109146145b117208d1ce2653ae5f0969e","m image/png","dim 1200x750","size 74072"]],""" +
                """"content":"Layered image editor with drawing, text, cropping, adjustments, and PNG export","sig":"48051bb32076427b1527c6bfd79961225f6ccf266c52b5902775fa74e442d51b1895170d44e76697093bb60d83fd8eaba7339b56afb90bd9729820926bed524e"}"""
    }
}
