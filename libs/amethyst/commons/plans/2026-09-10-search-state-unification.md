# Search state unification

*Status: done, by order (b). Phases 1 and 2 both landed; §9's questions are answered
below from what shipped rather than left open.*

## 1. Why

Search is implemented twice. Neither implementation delegates to the other, and
they drift silently. Over one session of feature work, four separate bugs were
found, every one of them the same shape — *a control that looks live on one
platform and does nothing on the other*:

| | Android before | Desktop |
|---|---|---|
| `-term` exclusions, `kind:reply`/`kind:media` | never applied | applied |
| `query.kinds` reaching the REQ | dropped | honoured |
| `Relevance` sort | ranked by date | ranked by score |
| Multi-kind alias serialization | wrote the alias once per kind | same bug, both |
| Search history / saved searches | absent | present |
| Per-relay progress, real `isSearching` | absent | present |

The first three are fixed (`2711b365`, `4beff0d7`); the last two are not. The
point is not the individual bugs — it is that four appeared in one pass without
looking for them. The structure manufactures this class of defect, so fixing
them one at a time has no end.

Supporting measurements, taken 2026-09-10:

- `SearchBarViewModel` — 615 lines, 13 top-level flows.
- `AdvancedSearchBarState` — 314 lines, in `commons/`, used **only** by desktop.
- The same query string is parsed ~9× per keystroke (4 direct `QueryParser.parse`
  calls plus 5 `plainTerms(…)`, which parses again).
- 10 debounces across 4 windows (100 ms ×8, 300, 400); 8 collectors debounce the
  *same* `searchValueFlow` independently.
- Scope is applied as 7 separate `if (scope == …) return emptyList()` guards
  inside result flows.
- Four hand-maintained answers to "which kinds are searchable": the relay
  allowlist (34), the local denylist (13), `KindRegistry.aliases` (33), and
  quartz's `SearchableEvent` — 142 reachable kinds, **referenced 0 times by the
  client**. *(The first two are now one list; see Phase 1 step 3.)*

## 2. The real blocker (it is not laziness)

Desktop did not reuse Android's search because it **could not**:

| Type | Module |
|---|---|
| `LocalCache`, `CacheSearch`, `Account`, `HiddenUsersState` | `amethyst/` |
| `Note`, `User`, `SearchQuery`, `QueryParser`, `SearchFilterBuilder` | `commons/` |

Android's result flows are built on `LocalCache.search.*`, an `amethyst`-module
singleton. Nothing in `commons/` can call it, so desktop wrote a parallel state
holder over relay callbacks alone. Every divergence follows from that one fact.

Note what this implies: the *pure* parts of search already live in `commons` and
are already shared-capable — `SearchQuery`, `QueryParser`, `QuerySerializer`,
`SearchFilterBuilder`, `SearchResultFilter`, `SearchResultSorter`,
`KindRegistry`. Three of the four bugs above were fixed this session by simply
*calling* code that was already there. The divergence in that layer is not
structural; it is a wiring failure that nothing prevents from recurring.

## 3. Relationship to the commons migration sweep — read before scheduling

`commons/plans/2026-08-30-commons-migration-sweep.md` already plans the move
that dissolves this problem, as its step 3:

> **Move `LocalCache` (3,980 lines).** … **This deletes `DesktopLocalCache.kt`
> (1,173 lines) — the single largest duplication in the repo.**

and step 2 changes `IFeedTopNavFilter` to use "the existing commons
`ICacheProvider` port". That port already exists and already carries
`findUsersStartingWith`, `getEventStream()`, `getNoteIfExists`,
`allRelayGroupChannels`.

**Therefore: do not build a bespoke search-side cache port.** A `SearchIndex`
abstraction invented here would be scaffolding that step 3 deletes. The
sequencing question belongs to the maintainer, and the two coherent orders are:

- **(a) After the sweep's steps 2–3.** `LocalCache` is in `commons`; a single
  shared `SearchState` reads it directly; no new port at all. Smallest total
  work, but gated on a large migration that "needs maintainer input".
- **(b) Before it, via `ICacheProvider`.** Extend the *existing* port with the
  five methods `CacheSearch` already implements (§5, Phase 2). This is additive
  to the sweep rather than parallel to it — the same port step 2 already needs —
  and survives the move unchanged.

Either way, **Phase 1 below is independent of both** and should land first.

## 4. Target design

One pipeline, three layers, with the platform boundary at the bottom only:

```
        text ──► SearchQuery ──► List<Filter> ──► results ──► post-filter ──► rank
             parse           build            ask          exclusions        sort
                                             ▲   ▲          pseudo-kinds
                                             │   │
                                    relays ──┘   └── cache (the only platform seam)
```

- **`SearchPipeline`** (commons, CLI-safe) — pure functions, no state, no
  coroutines. Owns parse → build → post-filter → rank. Already ~all present as
  loose objects; the value is making the *sequence* a single call so a caller
  cannot skip a step.
- **`SearchState`** (commons) — one debounced `StateFlow<SearchQuery>`, one
  scope decision, one result set, one `isSearching`. Replaces both
  `AdvancedSearchBarState` and the bulk of `SearchBarViewModel`.
- **Platform shells** — `SearchBarViewModel` keeps `LazyListState`,
  `FocusRequester`, invite-link routing, Namecoin, NIP-05; desktop keeps its
  panel/spotlight state. Both become thin.

### What is genuinely not shareable

Do not attempt to unify these; they are real differences, not drift:

- **Result types.** Android surfaces 7 (notes, users, hashtags, relays, public
  chat / ephemeral / live-activity channels); desktop surfaces 2. `SearchState`
  should expose a *map* of result kinds, and each front end renders what it has.
- **Sort orders.** Desktop has separate event and people orders; Android has
  one. Take desktop's shape; Android binds both to one control.
- **Scope.** An Android-only control today. Model it as a filter *over* the
  shared result set, applied once — not as 7 early returns.

## 5. Phases

### Phase 1 — one pipeline, no state moves *(migration-independent, do first)*

1. `SearchPipeline` in `commons/search/`: `parse`, `filters`, `keep`, `rank` as
   one object, with the ordering documented and tested.
2. Repoint both `SearchBarViewModel` and `AdvancedSearchBarState` at it. No
   behaviour change intended — this is the regression-test bed for Phase 2.
3. **One kind-set source.** *(Done — `RenderableKinds` in `commons/search/`,
   checked against `SearchableKinds` in quartz.)* The relay allowlist, desktop's
   copy of it, and the local scan's absent window are now one list. Quartz gained
   `SearchableKinds.ALL`, the 142 kinds `EventFactory` builds a `SearchableEvent`
   for, verified by sweeping the whole 16-bit kind space in a test rather than
   kept by hand; `RenderableKinds` is the 46 of those Amethyst has a card
   for, plus three that match on `content` alone, and a test pins the rest so a new
   searchable kind in quartz arrives as a decision.

   The audit that produced it found fifteen searchable, renderable kinds the
   search never asked for — pictures, all four video kinds, workouts, git repos,
   sites, napplets, meetings, calendar events, software apps. It also found that
   quartz's own golden test was pinning 126 kinds when 142 were searchable, and
   that twelve event classes (`FeedDefinitionEvent` among them) are never
   registered in `EventFactory`, so their events parse as plain `Event` and
   nothing ever indexes them. That last one is a quartz bug, filed here rather
   than fixed: it is not search's to make.

4. **One debounce policy, stated once.** *(Done — two windows on `SearchState`,
   named for what they protect: 100 ms before a cache scan, 300 ms before a REQ.
   Eight collectors had been declaring the 100 separately.)*

*Size: ~400 lines moved/added, ~200 deleted. No module boundaries crossed.*
*Risk: low. Both callers keep their current shape.*

### Phase 2 — one state holder *(done, by order (b))*

5. **The five search entry points on `ICacheProvider`.** *(Done.)* They default
   to returning nothing rather than being abstract: Desktop's cache holds notes
   and live channels but no public-chat or ephemeral store, and a port that
   forced it to implement those would be asking it to lie. `CacheSearch` now
   takes `LiveHiddenUsers` rather than the `HiddenUsersState` holder, which
   also fixed `findNotesStartingWith` re-reading `.flow.value` five times down
   one scan.
6. **`SearchState` in commons.** *(Done.)* `SearchBarViewModel` 615 → 508 lines;
   `AdvancedSearchBarState` delegates its text, parse, debounce and sort orders.
   `SearchInput` carries the text and its parse as one value, so a collector
   cannot pair one keystroke's characters with another's parse — and carries
   `nameTerms`, the most-repeated parse of all.
7. **One scope table.** *(Done — `SearchScope.shows(SearchResultKind)`.)* The
   seven guards disagreed: the note flow let hashtags through under Notes, the
   channel flows did not, and nothing said which was intended. The pinned test
   is written from the old guards, not the new enum.
8. **History and saved searches shared.** *(Done, and Android has both now.)*
   `SearchHistory` + a two-method `SearchHistoryStorage`; Desktop keeps its
   `Preferences` node, Android gets a DataStore file and a recent-searches list
   in what used to be a blank screen.

Two bugs surfaced only once the behaviour was under test: a saved search whose
label contained a tab lost the query it named, and re-running a query typed in a
different token order made a second history entry.

*Risk realised: none observed. Both front ends compile and the parity test below
passes; the behaviour changes are the ones named above.*

## 6. Non-goals

- Pagination past the 100/200 cap. Independent; do it separately.
- Changing the token language, the chip rendering, or `Route.Search` seeding.
- Touching `CacheSearch`'s matching semantics. It is correct and tested; it just
  is not reachable from `commons`.
- Unifying the two search *screens*. Android's list and desktop's spotlight are
  legitimately different UIs over the same state.

## 7. Risks

| Risk | Mitigation |
|---|---|
| Phase 2 silently changes what a query returns | Phase 1 first, so a shared pipeline is under test before state moves |
| Conflicts with the migration sweep | §3 — extend the sweep's own `ICacheProvider`, never a parallel port |
| Android's 7 result types bloat a shared API | Expose a result *map*; front ends render what they recognise |
| Scope semantics shift when guards collapse | Pin current behaviour per scope × result-type as a table test before touching it |

## 8. Test strategy

The pure layer is already well covered (`SearchFilterBuilderTest`,
`QueryParserTest`, `QuerySerializerTest`, `SearchResultFilterTest`,
`SearchResultSorterTest`, `SearchSeedTest`, `SearchTokenizerTest`). Add before
refactoring, not after:

- **A parity table**: one query × both front ends → same filters. *(Done:
  `desktopApp`'s `SearchFilterParityTest`, 16 query shapes. It lives there
  because that is the only module that can see both paths.)* This is the test
  that would have caught all four bugs.
- **Scope × result-type**: the 7 guards, pinned. *(Done: `SearchScopeTest`.)*
- **Kind-set parity**: relay allowlist == local allowlist, and every kind in it
  is one `SearchableEvent` covers. *(Done: `RenderableKindsTest`.)*
- Also added: `SearchPipelineTest` (one case per shipped bug), `SearchStateTest`,
  `SearchHistoryTest`, and quartz's `SearchableKindsTest`.

## 9. Questions, and what shipped

1. **Sequencing — (a) or (b)?** (b). The five entry points are additive to the
   sweep rather than parallel to it, and they survive the `LocalCache` move
   unchanged: when step 3 lands, `DesktopLocalCache` starts answering the same
   methods instead of inheriting the empty defaults, and nothing above the port
   changes.
2. **Delete `AdvancedSearchBarState` or keep it?** Kept, as Desktop's shell. What
   is left in it is genuinely Desktop's — relay callbacks, raw `Event` results,
   per-relay sync status, the form panel — so deleting it would only move that
   code, not remove it. It is still in `commons/` while being desktop-only,
   which remains a naming problem worth fixing on its own.
3. **Is Android's 7-result-type surface wanted on Desktop?** Left open — it is a
   product question, not a structural one. `SearchResultKind` now names the seven
   in commons, so the answer can be acted on without another refactor.

## 10. What is left

- Pagination past the 100/200 cap (§6, still a non-goal here).
- `FeedDefinitionEvent` and eleven other event classes are missing from
  `EventFactory`, so their events parse as a plain `Event` and nothing indexes
  them. A quartz bug, found by the kind sweep, not fixed here.
- `.claude/skills/searchable-events/references/searchable-kinds.md` is stale
  (133 kinds recorded, 142 reachable) and now has a machine-checked list to be
  reconciled against.
