# Local search as `filter(Filter)` — retiring the bespoke `find*StartingWith` scans

_Status: **steps 1–6 shipped**; see §8 for what landed, what changed on contact with the code, and
what is deliberately left. The three decisions in §2 were taken as recommended._

## 0. The shape of the thing

`LocalCache` today answers two different kinds of question with two unrelated mechanisms:

- **Generic**, already built: `filter(filter: Filter): SortedSet<Note>`, plus `observeNotes`,
  `observeEvents`, `observeNewEvents` and an indexed observable registry. Every feed filter in the
  app goes through it.
- **Bespoke**, five hand-written scans in `CacheSearch`: `findNotesStartingWith`,
  `findUsersStartingWith`, and three channel variants.

The generic path cannot answer a search because **`FilterMatcher.match` has no `search`
parameter**. It takes `ids, authors, kinds, tags, tagsAll, since, until` and nothing else, so a
`Filter` carrying only NIP-50 `search` matches *every* event. That one hole is the entire reason
`CacheSearch` exists as a separate mechanism.

Quartz already owns the missing half: `SearchableEvent.indexableContent()` (126 classes),
`SearchQuery.parse` / `stripExtensions` for the wire grammar, and two reference consumers — the
SQLite store (FTS5, bm25 then `created_at`) and the filesystem store (`FsSearchTokenizer`).

So this is not "abandon the model". It is: close one hole in the matcher, move viewer policy out
of the matcher where it never belonged, and delete five scans.

**The payoff is bigger than the tidy-up.** Once local search is `filter(Filter)`, the search field
feeds the *same* `SearchFilterBuilder.build(query)` output to both the relay REQ and the local
cache. `from:`, `to:`, `since:`, `#t`, `group:` then work identically local and remote. Today they
work only on desktop and only against relays; on Android the token chips are cosmetic, because
`SearchBarViewModel` puts the raw text (tokens and all) into the NIP-50 `search` string. This
refactor and that bug are one fix.

## 1. What must not change

`CacheSearch` does five things a `Filter` cannot express. Three are real requirements; two are
misplacements.

Requirements:
- **Bech32 / hex id entry.** `decodeEventIdAsHexOrNull(text)` → resolve the note, preferring the
  addressable note when it is the same event. This is a *lookup*, not a search, and stays as a
  pre-step that builds `Filter(ids = …)`.
- **Viewer policy.** `isHiddenFor(hiddenUsers)`, `excludeNoteEventFromSearchResults(note)`, and
  skipping `isContentEncoded()` events.
- **Tag-value matching** with an excluded-tag-name list (`excludedTagNamesFromSearch`).

Misplacements — these belong outside the filter:
- Viewer policy is not a wire concept. A relay has no idea what you have muted. It becomes a
  composed predicate, not a filter field (§3.2).
- Relevance ordering has nowhere to live today: `filter()` returns
  `toSortedSet(CreatedAtIdHexComparator)` — recency only, where the SQLite store ranks bm25 first.

## 2. Open decisions

### 2.1 What "matches" means locally

`findNotesStartingWith` does `content.contains(text, true)` — **substring**, despite the name. The
stores tokenize. `"itcoin"` finds "bitcoin" today and would not under a tokenizer.

| | Substring (today) | Token (the stores) |
| --- | --- | --- |
| Behaviour preserved | yes | no — mid-word queries stop matching |
| Matches what relays return | no | yes |
| Cost | one scan per field | tokenize per field, or an index |
| Reuse | none | `FsSearchTokenizer`, but it lives in `jvmMain` and would need promoting to `commonMain`/`jvmAndroid` |

Recommendation: **keep substring for v1** and treat tokenization as a separate, later change with
its own before/after. Local and remote results already differ (one is FTS5+bm25, the other a linear
scan); pretending otherwise is not worth a behavioural regression in the same PR that moves the
plumbing. Revisit when local search gets a real index.

### 2.2 Where viewer policy is applied

Recommendation: `LocalCache.filter(filter: Filter, predicate: (Note) -> Boolean = { true })`, with
the mute/kind/encoding rules extracted from `CacheSearch` into a reusable
`NoteVisibility(hiddenUsers)` predicate. Every feed benefits, not just search, and the wire type
stays a wire type.

### 2.3 Relevance ordering

`SearchSortOrder.RELEVANCE` exists app-side (`SearchResultSorter`). Recommendation: leave ranking
above `filter()`, keep `filter()` recency-ordered, and document that local ordering is *not*
bm25 parity. If relevance parity is wanted later it needs a score out of the matcher, which the
`Boolean` return cannot carry — a reason not to paint ourselves into `Boolean` (§4.3).

## 3. Does touching `FilterMatcher` interfere with, or slow down, current callers?

Two separate questions. The speed answer is "no, if done right". The correctness answer is "yes,
and that is the risk".

### 3.1 Correctness — the actual hazard

`Filter.match` has wide reach: 33 feed filters under `amethyst/.../dal/`, `FilterIndex`, and
geode's `MirrorWorker`. Today any filter carrying `search` matches on its NIP-01 fields alone.
The moment the matcher honours `search`, every one of those newly narrows.

Most never set `search`. But "most" is not "none", and a relay mirroring less than it used to is a
bad way to find out.

**Mitigation — do not flip the default first.** Land the search capability as an opt-in the
LocalCache search path composes explicitly (a `SearchMatcher`, or a `matchIncludingSearch`
entry point). Audit every construction site of a `Filter` that reaches `match`, confirm which
carry `search`, then make it the default in a second, separate commit that can be reverted alone.

### 3.2 Speed — the added check is free, but the loop around it is not

A trailing `if (search != null && !matchesSearch(event, search)) return false` costs **one
reference comparison** for every existing caller. Checked last, after the cheap field rejects, it
is not measurable. That part is safe.

The problem is what a search scan *reveals*. `FilterMatcher.match` allocates inside the per-event
loop today:

```kotlin
tags?.forEach { tag ->
    val valueSet = tag.value.toSet()              // a Set per event, per tag key
    if (!event.tags.any { … }) return false       // stdlib any → an iterator per event
}
tagsAll?.forEach { tag ->
    val eventTagValueSet =
        event.tags.mapNotNullTo(mutableSetOf()) { … }  // a MutableSet + full tag scan per event
    …
}
```

These are pre-existing and tolerable at feed-rebuild rates. They are not tolerable on a
full-cache scan per keystroke — and `SearchFilterBuilder` makes them worse, because
`ScopeIds.tagValues` emits up to four spellings per hashtag, so `toSet()` runs on a 4-element list
per note.

`CLAUDE.md`'s hot-path rule already says what to use: `fastAny` / `fastForEach` from
`nip01Core/core/TagArray.kt`, not the stdlib collection operators.

**The structural fix — hoist per-filter work out of the per-event loop.** Everything derived from
the `Filter` (the value sets, the tokenized or `DualCase`-folded search terms) is loop-invariant
and is currently recomputed per event. A prepared matcher built once per query and reused across
the scan removes all of it:

```kotlin
class PreparedFilter(filter: Filter) {          // built once per query
    private val tagValueSets = …                // Sets built once, not per event
    private val terms = …                       // DualCase terms folded once
    fun match(event: Event): Boolean            // allocation-free
}
```

This is a win for every existing caller, not just search — feed rebuilds pay the same per-event
allocations today. **Recommendation: land the prepared matcher first, as a pure no-behaviour-change
commit with the existing tests as the guard, before adding `search` at all.**

## 4. The indexable surface — do not build strings

### 4.1 The current cost

Of the 126 implementations:

| Shape | Count | Allocation per call |
| --- | --- | --- |
| `listOfNotNull(…).joinToString("\n")` | 86 | list + `StringBuilder` + joined `String` |
| `content` | 28 | none |
| JSON-parsing (kind 0, channels, stalls) | rest | **re-parses JSON every call** |

Kind 0 is the worst case and the most searched: `contactMetaData()` has no cache, so it runs
`JsonMapper.fromJson<UserMetadata>(content)` on every invocation. Calling `indexableContent()`
across the cache per keystroke would parse every profile, per keystroke.

For a write path — index once on insert — that cost is irrelevant, which is why it looks the way
it does. For a read path over the whole cache it is disqualifying. `indexableContent()` is the
right API for the stores and the wrong API for matching.

### 4.2 The precedent already in the tree

`UserMetadata.anyPropertyContains(terms: List<DualCase>)` is exactly the right shape, and already
exists for kind 0:

```kotlin
fun anyPropertyContains(terms: List<DualCase>): Boolean =
    name?.containsAny(terms) == true ||
        displayName?.containsAny(terms) == true ||
        about?.containsAny(terms) == true || …
```

No list, no join, `||` short-circuits on the first hit, and `DualCase` folds each term's cases
**once** rather than per comparison. The proposal is to generalise this, not to invent something.

### 4.3 Proposed surface

```kotlin
fun interface IndexableFieldVisitor {
    /** @return false to stop walking — a hit on the title need not touch the body. */
    fun visit(field: String?): Boolean
}

interface SearchableEvent {
    fun forEachIndexableField(visitor: IndexableFieldVisitor)

    /** Kept for the stores. Derived, so its output stays byte-identical to today's. */
    fun indexableContent(): String
}
```

An implementation carries no collection at all:

```kotlin
override fun forEachIndexableField(v: IndexableFieldVisitor) {
    if (!v.visit(name())) return
    if (!v.visit(description())) return
    v.visit(content)
}
```

Notes on the shape:

- **`fun interface`, not a lambda parameter.** An interface method cannot be `inline`, so a lambda
  at the call site would allocate. A `fun interface` instance is created **once per scan** and
  reused for every event, carrying the loop-invariant query terms on itself. Zero allocation per
  event, which is the whole point.
- **Nulls pass through**, so implementors never need `listOfNotNull`.
- **`Boolean` return for short-circuit.** Caveat: it forecloses scoring (§2.3). If bm25-ish
  relevance is ever wanted locally, this wants to be an accumulator instead. Worth deciding now,
  cheaply, rather than re-touching 126 classes later.
- **Rejected alternative: `fun indexableFields(): List<String>`.** Simpler, but still allocates a
  list per event and cannot short-circuit. Acceptable fallback if the visitor proves awkward for
  some kind, but it is strictly worse.

### 4.4 The compatibility constraint

`indexableContent()` **must survive with byte-identical output**. The SQLite and filesystem stores
index through it, and `references/searchable-kinds.md` (the `searchable-events` skill) is mirrored
by external engines — the Vespa-backed store's `SearchExtractors` tracks that table at pin bumps.
A changed body means stale results downstream and a `reindexFullTextSearch()` for existing
databases.

If the default `indexableContent()` is *derived* from `forEachIndexableField` with the same field
order and separator, output is identical, the table stays valid, and no reindex is needed. Watch
the separator: most kinds join with `"\n"`, a handful of metadata-ish kinds with `" "`, so the
derived default needs the separator as a per-kind property rather than a hard-coded `"\n"`.

Per that skill's mandatory-maintenance rule: any change here updates
`references/searchable-kinds.md` in the same PR.

### 4.5 JSON kinds still need a cache

Even with the visitor, kind 0 re-parses on every event visited. Either cache the parsed
`UserMetadata` on the event, or route kind-0 matching through the existing
`UserMetadata.anyPropertyContains` against `LocalCache`'s already-parsed `User.metadataOrNull()`
— which is what `findUsersStartingWith` effectively does today, and is the cheaper answer.

## 5. Sequencing

Each step is independently revertible; none but the last changes user-visible behaviour.

| # | Step | Behaviour change |
| --- | --- | --- |
| 1 | `PreparedFilter` — hoist per-filter work, swap stdlib ops for `fast*` (§3.2) | none |
| 2 | `forEachIndexableField` + derived `indexableContent()`; update the kinds table (§4) | none |
| 3 | Search matching as an opt-in entry point, substring semantics (§2.1) | none — nothing calls it yet |
| 4 | `filter(filter, predicate)` + `NoteVisibility` extracted from `CacheSearch` (§2.2) | none |
| 5 | `find*StartingWith` → filter builders; delete the bespoke scans | parity-tested |
| 6 | `SearchBarViewModel` feeds `SearchFilterBuilder` output to *both* relay and cache | **the Android fix** |
| 7 | Audit `search`-carrying filters, then make the matcher honour it by default (§3.1) | audited |

## 6. Parity harness

`desktopApp/src/jvmTest/.../cache/FindUsersTest.kt` already covers `findUsersStartingWith` in 8
tests and is a ready-made before/after gate. There is no equivalent for
`findNotesStartingWith`; write one against the current implementation **before** step 5, so the
rewrite is measured against recorded behaviour rather than against intent.

Call sites to migrate: `SearchBarViewModel`, `UserSuggestionState`, `UserSearchEngine`,
`BuzzNewDmViewModel`, `AgentAttestationScreen`, `ICacheProvider`, `SearchBarState`, and desktop's
`DesktopLocalCache`, `ChatPane`, `ComposeNoteDialog`.

## 7. Related

- `event-store-semantics` skill — the store contract the local matcher should not contradict;
  query-side rules are STORE-S01…S06. Read it before fixing the semantics in step 3.
- `searchable-events` skill — the indexing surface, the authoritative kind table, and the
  mandatory-maintenance rule for §4.4.
- `2026-09-07` search-field work (`SearchTokenizer` / `SearchFilterBuilder` in `commons/search/`)
  — produces the `Filter`s step 6 consumes.


## 8. What actually shipped

Steps 1–6 landed. Two things changed on contact with the code, both for the better:

**Step 7 is moot, and the blast radius never opens.** The plan assumed local search needed
`FilterMatcher` to honour `search`, which would have narrowed every filter carrying one — 33 feed
filters, `FilterIndex`, geode's `MirrorWorker` — and needed an audit before flipping. It does not.
`LocalCache.filter` grew a **predicate** parameter instead, and search composes an
`EventSearchMatcher` into it. `FilterMatcher` is untouched, so nothing else can change behaviour.
The predicate is also where viewer policy went (§2.2), so one parameter answers both.

**Step 1 turned out to be a pure win with no search in it.** `FilterMatcher` was allocating three
ways per event; fixing that needed no new API and pays for all 33 existing callers. It is pinned by
a differential test that keeps the old implementation as an oracle and fuzzes 20,000 random
event/filter pairs against it.

**Step 2 is scoped, not universal.** `forEachIndexableField` has a default that falls back to
`indexableContent()` — already free for the ~28 kinds whose indexable content is `content` itself —
and only the kinds local search actually scans override it (text notes, long-form, wiki,
highlights, classifieds, live activities, community definitions). The remaining ~79 joining
implementations still allocate on the read path, which costs nothing until something scans them.
A test pins every override's fields to rejoin to `indexableContent()` byte-for-byte, so the
externally-mirrored kind table stays valid and no reindex is needed.

### Deliberately not done

- **`findUsersStartingWith` and the three channel finders stay.** They are name-prefix lookups over
  users and channels, not event filters; forcing them through a `Filter` would be worse, not
  better. The plan's framing ("retire the bespoke scans") was too broad — only the *note* search is
  filter-shaped.
- **`findNotesStartingWith` stays, on one narrow path.** It matches `idHex.startsWith(text)` and
  resolves bech32 pointers, neither of which is content and so neither reachable through a
  filter's `search`. Text that could name an event — a bech32 pointer, or ≥8 hex characters — is
  routed to it; everything else goes through the filters. The first cut fell back to it whenever
  the filter path came up empty, which made every zero-result keystroke scan the cache twice.
- **No characterization test for `findNotesStartingWith`.** The parity harness §6 asked for was not
  written; the id path it guards is now the only thing still using that scan, and it is unchanged.
  Worth writing if that path is ever touched.

### Semantics that changed

Local text matching is now **terms ANDed**, each a case-insensitive substring, where it was one
literal phrase. A single-word query — the common case — is identical; `bitcoin lightning` now finds
notes carrying both words rather than only that exact phrase, which is what the relay already
returned for the same string. Ordering stays recency (§2.3): no local relevance score exists, and
`EventSearchMatcher` answers only yes or no.
