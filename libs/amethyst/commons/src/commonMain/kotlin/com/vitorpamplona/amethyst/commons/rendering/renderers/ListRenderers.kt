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
package com.vitorpamplona.amethyst.commons.rendering.renderers

import com.vitorpamplona.amethyst.commons.rendering.EventRenderer
import com.vitorpamplona.amethyst.commons.rendering.RelayEntry
import com.vitorpamplona.amethyst.commons.rendering.RenderContext
import com.vitorpamplona.amethyst.commons.rendering.RenderSupport
import com.vitorpamplona.amethyst.commons.rendering.RenderedDetails
import com.vitorpamplona.amethyst.commons.rendering.RenderedEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType

/** kind:0 — profile. The content is JSON, not prose; the parsed fields go in [RenderedDetails.Profile]. */
object MetadataRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val metadata = (event as? MetadataEvent)?.contactMetaData() ?: return RenderSupport.build(event, ctx, text = "")
        return RenderSupport.build(
            event,
            ctx,
            text = "",
            title = metadata.bestName(),
            summary = metadata.about,
            details =
                RenderedDetails.Profile(
                    name = metadata.name,
                    displayName = metadata.displayName,
                    about = metadata.about,
                    picture = metadata.picture,
                    banner = metadata.banner,
                    website = metadata.website,
                    nip05 = metadata.nip05,
                    lud16 = metadata.lud16,
                ),
        )
    }
}

/** kind:3 — NIP-02 follow list. Only well-formed keys are listed. */
object ContactListRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val contacts = event as? ContactListEvent ?: return RenderSupport.build(event, ctx, text = "")
        return RenderSupport
            .build(
                event,
                ctx,
                // Legacy clients stored a relay JSON blob in the content; it is not text.
                text = "",
                details = RenderedDetails.ContactList(contacts.verifiedFollowKeySet().toList()),
                // The follows are the details; listing every `p` again as a mention doubles the output.
            ).copy(mentions = emptyList())
    }
}

/** kind:10002 — NIP-65 read/write relays. */
object NIP65RelayListRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val list = event as? AdvertisedRelayListEvent ?: return RenderSupport.build(event, ctx, text = "")
        val relays =
            list.relays().map {
                RelayEntry(
                    url = it.relayUrl.url,
                    read = it.type != AdvertisedRelayType.WRITE,
                    write = it.type != AdvertisedRelayType.READ,
                )
            }
        return RenderSupport.build(event, ctx, text = "", details = RenderedDetails.RelayList(relays))
    }
}

/** kind:10050 — NIP-17 DM inbox relays. Every entry is a read (inbox) relay. */
object DmRelayListRenderer : EventRenderer {
    override fun render(
        event: Event,
        ctx: RenderContext,
    ): RenderedEvent {
        val list = event as? DmRelayListEvent ?: return RenderSupport.build(event, ctx, text = "")
        // Shows the list as published, like the kind:10002 renderer above: rendering dials nothing.
        val relays = list.allRelays().map { RelayEntry(url = it.url, read = true, write = false) }
        return RenderSupport.build(event, ctx, text = "", details = RenderedDetails.RelayList(relays))
    }
}
