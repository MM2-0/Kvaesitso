# Git dashboard: what is left after the audit

_Status: done 2026-11-05, except where a section says otherwise. Quartz's share (history, blame and
search downloads) is `quartz/plans/2026-11-05-git-fetch-costs.md` in the quartz repo._

## Outcome

| Item | Result |
|---|---|
| 1. End-to-end run | Done: `amy serve --git-root` is a dev GRASP server (geode `GraspGitHost`); `amy git pr --source`, `pr-update --source` and `edit` run `GitProposals` (now on a `GitProposalHost`); `cli/tests/git/git-grasp-e2e.sh` passes (23/23 after the final audit), private repository included. The desktop app, as the maintainer on the same servers with `run-headless.sh --local-only`: inline comment, resolve, reopen, review verdict, label, merge, edit and propose, private repository browse and settings save, each looked at and checked on the server or relay. Not yet run in the app: New pull request and Update branch from a second account; an Android device. |
| 2. Editor | Done: a windowed editor (a lazy list of lines, one block of lines in a `TextFieldState` at a time). 15-29 ms per keystroke at 20, 256 and 430 KB, against 149 and 274 ms for the whole-file field; round trips keep CRLF and a missing final newline. The 64 KB stopgap was skipped. |
| 3. Row colours | Done, with an offscreen render test. |
| 4. Unknown announcement | Done in the planner: such an event goes only where the repository's events were seen, until the announcement shows it public. Composers do not yet fetch the announcement first (the planner rule covers the leak). |
| 5. Inline threads | Done: rendered under their line in the desktop app on a real thread. |
| 6. Blame and search waits | Done in quartz (`quartz/plans/archive/2026-11-05-git-fetch-costs.md`): blame on the amethyst repository answered in under 6 s in the desktop app (was 30 to 60 s); search skips binary files and streams packs. The app picks it up at the next quartz release. |

Found on the way: `AMETHYST_LOCAL_RELAYS` alone let a fresh test account publish its Marmot key
package to the default public relays at login (one throwaway key's, during this testing).
`run-headless.sh --local-only` now closes the app's proxy so only `127.0.0.1` answers, and
`tools/desktop-run/README.md` says so.

The in-app git features (review, PRs, edits, triage, settings, private repositories, checkout
helpers) shipped with an audit pass. Five items stayed open, and testing them found a sixth. Each
section says what was tested, what was found and the fix.

## Final audit (2026-11-05)

A last pass over everything this work added, for bugs, reuse, and whether each screen follows the
cache and the relays. Fixed:

- **Privacy.** The planner rule for an unknown announcement could be steered: any note with an `a`
  tag naming a repository moved its replies to that repository's relays. It now applies to git
  kinds and what acts on them (comments, reactions, labels, deletions), walks parents up to four
  deep, and prefers relays the parents were seen on. A private repository's deletions stay on its
  relays. Bookmarking a private repository puts it in the list's encrypted section.
- **Reactivity.** A deletion names the event it takes back, so deleting a label, a resolution or a
  review on another device never reached the screen: each such note is now watched
  (`WatchGitDeletions`). A deleted label also leaves its item's `labels` (`CachePruner`). The new
  pull request form loads the repository's announcements itself. Repository settings warn when the
  announcement changed while open. A newer signed state no longer blanks the file being read.
- **Editor.** Opening a line below an edited block that changed size crashed (`subList`); fixed
  with a test. A draft is kept only for the commit it was written on.
- **amy / GRASP.** `amy git merge` signs its git requests for a private repository and keeps the new
  state off the outbox; one loader (`GitSupport.loadRepository`) serves merge and the proposal
  verbs. The dev server checks the owner is an npub, caps bodies, refuses unknown encodings and
  pushes it cannot read, and makes `refs/nostr/<id>` create-only. `amy git edit` refuses paths
  with `.`, `..` or `.git` segments.
- **UI.** The diff of a pull request survives leaving the thread; labels show once in a thread
  (the triage bar, for members); replies to an inline comment show under it; Review and line
  comments need a signing account; folded files no longer use up the highlighting budget.
- **Tests.** The e2e script failed open: it never set its exit code, and a missing id made later
  asserts compare "null". It now stops on a missing id, checks refusals by their message, starts
  both relays or stops, and merges the private repository too: 23/23.

Checked in the desktop app (`--local-only`, maintainer key, local GRASP): labels in the triage bar
only; the diff shown again at once after leaving the thread; an inline comment, then a reply from a
second account arriving from the relay under it; Resolve, then the resolution and the labels
deleted through `amy delete`, each gone from the screen within seconds.

Then finished (same day):
- The diff highlights each opened file in one background pass and draws it plain until then;
  only the file viewer did that before.
- The form's branch field is `GitBranchField` beside the code browser's chip in `GitBrowseUi.kt`,
  and both menus draw the same `GitRefMenuItem` rows.
- "push negotiation failed" was the container's global `push.negotiate=true`: plain git 2.43
  prints it against a local bare repository too. The e2e's git helper turns it off.
- quartz comes from a JitPack build of quartz `main` after vitorpamplona/quartz#94 merged the NIP-34
  work onto 2.16.0 (`quartzJitpack` in the catalog, substituted for every quartz dependency, the
  bindings' included). Once a release carries it, empty `quartzJitpack` and bump `quartzKmp`.
  JitPack needed two more things: quartz's `jitpack.yml` now also builds the iOS variants (the KMP
  metadata compiles resolve them), and the root build points the sources and metadata variants
  back at their classified files, because JitPack's metadata rewrite drops the classifier. Quartz's
  `jitpack.yml` now publishes the Android variant, which JitPack builds lacked.
- A read-only login (an npub) no longer sees Close, Reopen or Merge: the status buttons now need a
  signing account, like Review, comments and triage.

- Reviews (kind 7321) have a feed card ("reviewed Approve (ACK)", the summary, the item) and reach
  the reviewed item's author in notifications. `NoteComposeKindCoverageTest` caught the missing
  card once it read the JitPack quartz's `EventFactory`.

Looked at in the desktop app on the JitPack quartz: as the maintainer's npub, a PR thread with no
Review, triage or status buttons and a Kotlin diff coloured in both the unified and split views; as
the maintainer, the New pull request form's branch fields and their menus; a review left by the
maintainer, in the contributor's notifications.

## 1. Signing flows have never run end to end

**Tested:** the pieces have unit and git-fixture tests: pushing to `refs/nostr/<id>`, commits made
from edits, the pusher against real `git http-backend`, and event building. Nothing has run the
whole thing: sign → push → re-sign → publish → read back in the app.

Those flows are opening a PR, updating one, proposing an edit, resolving and reopening a thread, a
review verdict, labels, a rename, and settings (announcement plus 30618 HEAD). Private repositories
add NIP-98 auth to all of them.

**Why not yet:** a GRASP target needs its relay and its git endpoint on the same host
(`GraspServer.pairedRelay`). Locally, `amy serve` (geode) and `git http-backend` run on different
ports, so the app treats the repository as non-GRASP and skips the push path. Testing against
relay.ngit.dev would publish to public relays.

**Plan:**

1. **A dev GRASP server in geode.** An option (`amy serve --git-root DIR`) that serves smart HTTP
   for `/<npub>/<repo>.git` on the relay's own port.
   - Requests go to `git http-backend` as a CGI subprocess. The git binary is run, not linked, so
     there is no license impact on shipped artifacts. It is test and dev tooling only, never
     bundled.
   - It enforces GRASP's rules:
     - anyone may push `refs/nostr/<event-id>`;
     - a branch push must match the newest 30618 of a maintainer;
     - a repository whose announcement says `private` wants a valid kind-27235 header on every
       request.
   - Quartz already has the relay-side pieces it needs: event store, `HTTPAuthorizationEvent`
     verification.
2. **`amy` verbs on the same code as the app.**
   - `amy git pr open` / `pr-update` / `edit` call `GitProposals` instead of only publishing the
     event. That is CLI parity, and it keeps the rule that amy is thin assembly.
   - Today `GitPrCommands` publishes without pushing, which is also wrong for GRASP.
3. **`cli/tests/git/git-grasp-e2e.sh`**, against that server only:
   1. announce a repository;
   2. open a PR from a branch, and check `refs/nostr/<id>` is on the server and the event names it;
   3. update the PR (1619);
   4. propose an edit (a commit made from file text);
   5. make one server refuse, to exercise the re-sign path;
   6. change the default branch;
   7. repeat steps 1 to 6 for a private repository, with auth required and nothing reaching any
      relay but the repository's.
4. **The desktop app against the same server.**
   - Run it with a throwaway key (`--login-file`) and `AMETHYST_LOCAL_RELAYS`.
   - Click through New pull request, Update branch, Edit and Propose, Resolve and Reopen, the
     review verdict, labels, rename and settings, and look at each result.
   - Inline comment threads get their first real rendering here (see 5).
5. **A unit test** for `GitProposals.publishWithCommits` with a fake pusher: all servers accept,
   one refuses, and every server refuses on each round.

## 2. Editing large files is slow

**Tested:** an offscreen render on the desktop JVM, timing one edit plus its frame in a field
holding the whole file:

| File | `OutlinedTextField(String)` | `BasicTextField(TextFieldState)` |
|---|---|---|
| 20 KB | 19 ms | |
| 51 KB | 42 ms | |
| 105 KB | 72 ms | |
| 256 KB (today's limit) | 149 ms | |
| 430 KB | 274 ms | 265 ms |

The cost is laying out the whole text on every keystroke, not copying a `String`, so
`TextFieldState` alone does not fix it. Phones will be several times slower than this.

**Plan:**

1. **Stopgap:** cap in-app editing at 64 KB. That is about 40 ms on desktop. Larger files show why
   and offer Check out locally instead.
2. **Real fix: a windowed editor.**
   - The file is shown as a `LazyColumn` of lines; one block (the lines around the cursor, or a
     tapped range) is a `TextFieldState`.
   - Committing a block splices it back into the file's line list.
   - Cost then tracks the block, not the file, and the 256 KB limit can return.
   - Line endings and the final newline are kept as read. The current editor already keeps the
     final newline; CRLF is unchecked.
3. **Test:** the same render timing as a jvmTest, asserting the edit cost is flat across file
   sizes, plus a round-trip test that an untouched file comes back byte for byte (CRLF, no
   trailing newline, tabs).

## 3. Diff row colours stop where the line's text ends (new)

**Found** while rendering the inline-comment test offscreen: in the unified view, an added or
removed line's background ends at its text instead of spanning the card. The desktop screenshots
show the same thing from before the audit. Each row sizes to its own content inside the
horizontally scrolled block.

**Plan:**
- Each scrolled run is at least the card's width (`BoxWithConstraints` outside the scroll,
  `widthIn(min = maxWidth)` plus `IntrinsicSize.Max` on the run), and each row fills it.
- **Test:** extend `GitDiffViewAnnotationTest` to assert that an added row's background width
  equals the card width when the line is short, and the widest line's width when it is long.

## 4. A private repository's event can leak when its announcement is not loaded

**Tested in the code:** `BroadcastRelayPlanner.privateGitRelays` returns null when none of the
repositories an event names is in the cache, so the event takes the normal outbox route.

This happens when the user replies, reacts or comments on a private repository's item that
arrived before its announcement, for example from a notification or a link.

**Plan:**
1. In the planner, an event that names a `30617:` coordinate whose announcement the cache lacks is
   treated as possibly private. It goes only to that tag's relay hint and the relays the item was
   seen on, never the outbox.
2. Composers of git events (comment, status, label, review) also load the announcement first,
   with `fetchFirst` on those relays, and send normally once it is known to be public.
3. **Test:** `BroadcastRelayPlannerTest` gets an issue under a repository that is not cached.
   Its comment goes to the hint relays only; once a public announcement arrives, the next one goes
   to the outbox.

## 5. Inline comment threads: layout verified, real thread not yet

**Tested:** `GitDiffViewAnnotationTest` (committed) renders a unified diff offscreen with a long
code line and a long comment under it.
- The comment is laid out at the card's 400 px and wraps.
- The code line scrolls on its own.
- Only the commented line draws anything.

That covers the layout fix from the audit. Real threads are `NoteCompose` cards with
Resolve/Reopen, and no public PR has inline comments yet. They are rendered in step 1.4.

## 6. Blame and search wait times in the app

These follow the quartz plan. On the app's side:
- Once quartz is released with it, bump the version. A release needs the maintainer's go-ahead.
- Show what blame is doing ("reading history", "reading N versions") instead of a bare bar, and
  let leaving the file cancel it. `produceState` already cancels; the quartz fetch must honour it
  mid-pack.

## Order

1. **Items 3 and 4:** small, contained, and they close a privacy gap.
2. **The editor stopgap (2.1).**
3. **The dev GRASP server and `amy` verbs (1.1, 1.2):** the largest piece, and the one that makes
   every later change in this area testable without public relays.
4. **The end-to-end script and the desktop pass (1.3, 1.4)**, fixing what they find.
5. **The windowed editor (2.2)**, and the quartz fetch work as its release lands (6).
