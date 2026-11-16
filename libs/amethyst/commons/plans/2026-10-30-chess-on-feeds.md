# Chess on the app's feeds

Status: implemented 2026-10-30 (desktop checked headless, read-only account; a full game between
two signing accounts and Android are not yet run).

## Why

The chess screens loaded games differently from the rest of the app. A `ChessLobbyLogic` polled a
hard-coded list of three relays (`ChessConfig`) every 2 to 15 seconds with one-shot REQs
(`ChessRelayFetchHelper`). It ran a second, callback-based subscription beside that, and kept its
own lobby state (`ChessLobbyState`, `LiveChessGameState`) that had to be reconciled with both.
It drew relay progress, sync and broadcast banners and a relay settings sheet. Moves went to the
same three relays through a custom broadcaster.

## What it is now

The game is the data: a Jester start note in `LocalCache`. Every move is linked to it as a reply
(`computeReplyTo`), so a game is `startNote` plus `startNote.replies`.

- **Lobby** (`ChessLobbyScreen`): one feed (`ChessLobbyFeedFilter`, a `FeedContentState` over
  start notes, newest activity first) for the top-bar list (`defaultChessFollowList`,
  `FollowListSlot.CHESS`, `liveChessFollowListsPerRelay`). "Mine", the default, is the user's games
  (`isChessGameOf`: started, challenged to by `p` or private ref, or moved in), the way Relay
  Groups reads "Mine" as the groups one is in; any other list is a lobby of its people's games,
  Global every open challenge. An empty "Mine" offers Browse open games (switches to Global).
  - `ChessFilterAssembler` (a `TopNavFeedFilterAssembler`) asks chosen authors for their kind 30,
    and Global or one relay for Jester starts only (`#e` START_REF), since kind 30 is shared with
    NKBIP-03;
  - `ChessLobbyGamesFilterAssembler` asks for the user's own games (outbox `authors`; inbox `#p`
    and jesterui's private challenge ref), streams the moves of the listed games (`#e`), and fetches
    by id the challenges of the user's games that never arrived (`chessGamesMissingTheirStart`: a
    game joined from Global keeps its challenge's author off every other list).
- **Card**: a start note renders as `ChessGameCard` in any feed: players, status (your turn, whose
  turn, result), board, and Accept on a seat the user can take. Its click goes to `Route.ChessGame`.
- **Board** (`ChessGameScreen`): `ChessGameViewModel` rebuilds a `ChessGameSnapshot` from the
  start note and its replies whenever one arrives. `ChessGameFilterAssembler` keeps one REQ open
  per open board, on the relays the game was seen on plus the players' and the user's, so those
  relays stay connected and moves stream in. There is no polling.
- **Publishing**: `account.signAndComputeBroadcast`, consumed into LocalCache like any event.
  `BroadcastRelayPlanner` adds the relays the game's start was seen on, where a jesterui opponent
  listens.
- **Rules**: quartz's `JesterGameEvents.mainLine()` decides which moves count (the player to
  move, extending the line by one), who holds each seat, and which move-less ending stands.
  `ChessStateReconstructor` replays that line with the engine, stops at an illegal move, and only
  believes a claimed mate or stalemate the board shows.
- **Draws and abandonment**: an offer rides on the offerer's move (`draw_offered`, the ½ toggle);
  the other player accepts with a draw-agreement end or declines by moving. The waiting player may
  claim a game after `CHESS_ABANDONMENT_SECONDS` (three days) of silence from the player to move.

No connection, sync or broadcast status is shown: chess behaves like every other feed.

## Next

What remains of the protocol gaps (notifications, chat, the `#l` end label, retiring 30064–30068)
is in quartz's `quartz/plans/2026-10-30-jester-chess-gaps.md`. `mainLine()` and
`buildChallenge()` shipped in quartz 2.11.0.
