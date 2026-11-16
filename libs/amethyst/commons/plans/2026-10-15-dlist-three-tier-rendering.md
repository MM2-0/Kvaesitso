# Decentralized List items: three tiers of rendering

Status: implemented 2026-10-15. Port of Brainstorm-UI's ADR 0004 ("renderers read the governing
concept") and ADR 0007 ("three tiers of item rendering").

## The tiers

1. **Raw.** No definition could be read, or it declares no fields: the item's tags as they are
   (`DListItemView.defined == false`, `undeclared`). A feed card still guesses a title from
   `name` / `title` / `t` and a Tapestry tag element's JSON content.
2. **Dynamic.** Every concept gets this from its governing definition: field declarations,
   `field-type`, display hints (`title`, `summary`, `image`, `link`, `media`, `location`, `fact`)
   and URL templates (`["link", <template id>, <relay>, <placeholder>, <field>, …]`).
   Fields nothing shows fold into "More fields", with undeclared tags apart.
3. **Custom.** A view made by hand for one community concept, keyed by the community concept's
   coordinate (`DListConceptView`): GitHub accounts, food and drink places, books. It reads values
   only through the governing definition and says whether it `applies`; when it doesn't, the item
   is drawn the tier-two way. Among many (feeds, a header's item rows) a view only changes how the
   item reads (`present`: a GitHub avatar, a book's portrait cover). Its full view is drawn on the
   item's own page, the thread view (`RenderDListItem(isPage = true)`).

Tier two names no concept. Remove an entry from `DListConceptView` and its items read the
tier-two way everywhere.

## Where it lives

| What | Where |
|---|---|
| Header/item reading, hints, media, geohash | `commons/…/decentralizedLists/DListPresentation.kt` |
| URL templates and their safety rules | `commons/…/decentralizedLists/LinkTemplates.kt` |
| Governing definition and agreement | `commons/…/decentralizedLists/ConceptResolution.kt` |
| Tier-three registry and readers | `commons/…/decentralizedLists/tiers/` |
| Shared Compose parts, resolution in composition | `commonsUI/…/ui/note/types/dlists/DListParts.kt` |
| The card (tiers one and two) and dispatch | `commonsUI/…/ui/note/types/dlists/DList.kt` |
| Tier-three views | `commonsUI/…/ui/note/types/dlists/tiers/` |

## Which definition governs

The item's `z` names a header. That header's `b` names its community concept (a header that
points at itself, or at nothing, is its own community). The first of these governs: the reader's
copy (`39998:<reader>:<community d>`), their Tapestry Assistant's copy (the assistant from
their kind-10040), the community header, then the header the item named. A candidate counts only
when its `b` points at the community concept. Never by timestamp. Brainstorm's "house" tier is
Brainstorm's own account and has no counterpart here.

## Staying current

Every header the card reads (listed, community, the reader's and Assistant's copies) and every
URL template goes through `observeNoteEvent`, so a version that reaches `LocalCache` redraws the
card. EventFinder's watcher also asks, while an addressable is on screen, for newer versions of
it (`filterNewVersionsOfAddresses`, the loader's own grouping: per kind and author, `#d` chunked,
plain replaceables by author, `since` the watcher's last EOSE), on the author's write relays and
where it was seen. This applies to every addressable rendered, not only D-lists. The reader's
Assistant comes from their kind 10040's flow, so it changes in place too.

## Left out, and why

Brainstorm's tier-three pages also fetch third-party data (GitHub's account facts and repositories,
Open Library's description and rating, a website's preview picture) through a same-origin proxy,
so readers never contact those sites. Amethyst has no such proxy, so none of it is fetched: the
views show what the item says. Reviews, recommendations and web-of-trust endorsements on places
and books are also not ported.

Maps use the platform's `LocationPreviewMap`: osmdroid with OpenStreetMap tiles on Android, and on
desktop `DesktopLocationPreviewMap`, OSM tiles laid out in Compose and loaded through the app's Coil
loader (its User-Agent, disk cache and Tor routing), with the same tile filters and pin as Android.
A place's own page shows it outright, as Brainstorm's does; a tier-two item with a `location`
shows it only when the reader asks, so a card contacts no tile server by itself.
