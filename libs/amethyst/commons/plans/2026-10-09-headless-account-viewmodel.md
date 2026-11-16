# Headless AccountViewModel and typed user notices

Status: shipped (steps 1-3); follow-ups below

## Problem

`AccountViewModel` (3.2k lines) lives in `commonsUI`, so every ViewModel that
takes it is stuck there too, out of reach of `cli`. It imports no Compose UI. What
pins it is that `commons` has no headless way to say "tell the user this": every
user-facing message is a compose-resources `StringResource`.

- 46 `Res` strings in `AccountViewModel` itself (PoW publish failures, Concord
  kicks and pin failures, signer errors, Bolt12, nutzap, Cashu redemption).
- The payment helpers it calls build their error text the same way
  (`loadStringRes(Res.string.x, args)` then a `(title, message)` callback):
  `ZapPaymentHandler`, `V4VPaymentHandler`, `LightningInvoiceResolver`,
  `MeltProcessor`, plus `HttpStatusMessages` and `NwcResponseMessages`.
- ~34 headless helpers sit in `commonsUI` only because their users did
  (relay filter assemblers, `AccountFeedContentStates`, `UriToRoute`, `CachedState`).

## Design

### Typed notices (`commons.notices`)

- `sealed interface UserNotice`: a typed fact about what happened
  (`SignerNotice.ReadOnly`, `InvoiceNotice.CallbackNotFound(user)`, …), never text.
  All subtypes live in `commons.notices` (a sealed hierarchy must share a
  package), which doubles as the catalog of everything the app tells the user
  from headless code.
- `fun interface UserNoticeSink { fun notify(notice: UserNotice) }`: where
  headless code reports a notice (a toast in the GUI apps).
- `fun interface UserNoticeResolver { suspend fun resolve(notice): ResolvedNotice }`:
  for headless code whose API already hands text to a caller (the payment
  helpers' `onError(title, message, user)` callbacks keep their signatures, so the
  zap buttons and dialogs don't change).

`commonsUI` implements both: one exhaustive `when` maps each notice to its
`Res` title/message (`commons.notices.ui`), `ToastManager` implements
`UserNoticeSink`, and `DisplayErrorMessages` renders a notice toast in
composition like any resource toast. Adding a notice without a rendering does
not compile. `cli` can supply its own resolver.

Rejected: a generic `MessageKey` that `commonsUI` maps to `Res`. It only moves
string keys around instead of naming what failed, and loses the exhaustiveness.

### Split the ViewModel

- `commons`: `open class BaseAccountViewModel` holds the whole headless body. It
  takes a `UserNoticeSink` and a `UserNoticeResolver` and never sees `Res`.
- `commonsUI`: `class AccountViewModel : BaseAccountViewModel` adds only what
  needs the UI module: its `ToastManager` (passed down as the sink), the Res
  resolver, and the `CallSessionBridge` registration (Android's call activity
  reads the UI type back out). Every member the UI calls is inherited, so the
  hundreds of `accountViewModel.x` call sites don't change.
- ViewModels that need `launchSigner`/notices take `BaseAccountViewModel`;
  ones that only need the account take `Account`.

## Steps

1. Move the ViewModels that only read `accountViewModel.account` (switch them to
   `Account`).
2. Add `commons.notices`; convert the payment helpers and move them to `commons`.
3. Extract `BaseAccountViewModel` with its headless helpers; move the ViewModels it
   unblocks.

## Outcome

- Step 1: 7 ViewModels switched to `Account` and moved.
- Step 2: `commons.notices` added; `LightningInvoiceResolver`, `ZapPaymentHandler`,
  `V4VPaymentHandler` and `MeltProcessor` report notices and moved to `commons`.
  `TopNavFilterState` labels became a `TopNavLabel` enum; `NotificationSummaryState`
  publishes plain chart data and `commonsUI` builds the Vico model.
- Step 3: `BaseAccountViewModel` in `commons` with 28 helpers (relay filter
  assemblers, feed states, URI routing, caches); 9 more ViewModels moved onto it.

One behavior change: an LNURL server that answers with an error status is now
reported as "service unavailable" directly, instead of being re-wrapped as "could
not resolve" with the first message inside.

## Follow-ups

Still in `commonsUI`, by blocker:

- `toastManager`/`Res` in the ViewModel itself: podcast and music composers.
- `CountFilter`/`RelayCountResult` carry `StringResource` labels: the relay-list
  ViewModels (`BasicRelaySetupInfoModel` and its 7 subclasses).
- UI-module helpers: `uploadToDefaultServer*` (emoji packs, new user metadata),
  `UserSuggestionState` (list display), `ChannelNewMessageViewModel`/`LevelFeedViewModel`
  (Compose UI state), `TransactionFilter`, `distanceUnit`, `EditNestSheet`, and the
  Blossom/Nests server lists.

`ToastManager`'s ~150 existing `Res` call sites stay: UI code may keep using `Res`.
