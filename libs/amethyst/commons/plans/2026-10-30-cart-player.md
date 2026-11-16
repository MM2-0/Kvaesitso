# Playing game cartridges in notes — 2026-10-30

Status: shipped on Android and Desktop. Checked on Desktop with a real note; not yet on an Android
device.

Notes can carry 128x128 fantasy-console cartridges (`.p8.png`, for example as a
`blossom:<sha256>.png` URI). Amethyst detects them and plays them natively. The runtime itself, a
from-scratch Lua interpreter, graphics, sound and cart formats, is the
[kiko-kmp](https://github.com/vitorpamplona/kiko-kmp) library, in three artifacts: `kiko` runs a
cart, `kiko-player` plays it in real time without UI (game thread, frame clock, hang watchdog,
saves, sound) and `kiko-compose` shows it (`KikoGame`: screen, touch controls, keys, pausing with
the app). `commons` uses `libs.kiko.player`, `commonsUI` uses `libs.kiko.compose`; `-PkikoKmpDir`
builds a local checkout. Their design notes live in that repository. This note covers what
Amethyst adds around them.

## In the app


- **Detection** (`commons/.../cartridges/CartDetector.kt`): every image a note shows passes the
  shared `ZoomableContentView` wrapper (`commonsUI/.../note/platform/NotePlatformCalls.kt`). A PNG
  whose size is 160x205 or unknown gets its first 24 bytes read with a Range request through the
  image HTTP client (Tor-aware), and only when the user lets images load. The one cart post found
  on relays is a bare `image.nostr.build/<hash>.png` with no `imeta`, so tags alone would miss it.
  Answers are cached per URL.
- **Chats**: a cart linked in a chat message's text goes through the same rich-text renderer as a
  note. A cart sent as an encrypted attachment (NIP-17 file message, Marmot or Cordn media) plays
  too: the chat view caches its key in `EncryptionKeyCache`, and `EncryptedBlobInterceptor` on the
  image client decrypts the download, so the probe and the player read plaintext and check the
  plaintext hash the message records (`EncryptedCartDownloadTest`). The interceptor drops the
  probe's Range header, so an encrypted PNG of unknown size is downloaded whole to answer it. An
  encrypted `blossom:` attachment works too: `EncryptionKeyCache` files a key registered under a
  `blossom:` URI by the blob's sha256 as well, and finds it again for any server URL whose last
  path segment is `<sha256>[.ext]` (`EncryptionKeyCacheTest`). The player then checks the
  plaintext hash, not the URI's, which names the ciphertext. An encrypted attachment carries one
  file and no cart links, so it plays as a single cart.
- **Player** (`commonsUI/.../ui/cartridges/CartPlayer.kt`, `CartPlayerViewModel` in commons): the
  app holds one `KikoPlayer` per cart in `CartPlayerViewModel` (so a rotation keeps the game) and
  shows it with `KikoGame` in a full-screen dialog, adding the pause and close buttons, its
  strings, Back and the Android window mode. The library gives it the screen scaled by whole
  numbers, a direction pad and O/X buttons (multi-touch, slide between directions), keyboard and
  gamepad keys, and sound through `AudioTrack` (Android) or `javax.sound` (Desktop). A pause
  button opens the game's pause menu on touch screens. The pad and the O/X area each read every
  finger as it slides, so a thumb moves between directions, diagonals and off the pad's edge
  without lifting, and rolls from O to X through a strip that presses both (kiko-compose's
  `ControlsTouchTest`). Each new press gives a haptic tick. Both areas are excluded from Android's
  edge gestures, and Back opens the pause menu (Back again closes the player), so a stray swipe
  never ends a game. Landscape hides the system bars; the screen stays on while the player is
  open. The pointer over the screen and typed keys reach games that read them. Input is handed to
  the game's own thread between frames, so a tap shorter than a frame still counts. The game runs
  on its own thread, paced by the clock; it pauses when the dialog closes and resumes where it
  was. `blossom:` URIs are resolved through the BUD-10 finder, and a declared sha256 is checked
  before the cart loads; a failed download and a file that is not a cart get different
  messages.
- **Saves** (kiko-player's save buffer over `CartSaveStore`, a `KikoSaveStore`): `cartdata` lands
  in the app's `cart_saves` DataStore as soon as the game saves (at most once a second for a game
  that saves every frame, plus once more when the player stops). Kept per install, by the game's
  own save id. `CartSaveKillTest` SIGKILLs a JVM right after a save and reads it back in the next
  one; on Desktop a high score survived `kill -9` and showed on the title after a restart. The
  game in progress is not saved: the Lua state (coroutines included) lives only in memory, so a
  killed app restarts the game from its title, like the original console.
- **Lifecycle**: the game runs only while the app is started. Home, the app switcher or a
  minimized window pauses it and its sound and writes its saves.
- **Multi-cart games**: a note lists a game's carts in its `imeta` tags. The entry cart is the one
  whose URL is in the text, and its `cart` field is optional: a game needs it to come back to its
  first cart. An extra cart is an `imeta` with a `cart` field whose URL isn't that entry, and it needs
  `x` (64 hex digits). `cart` is the exact string the cart passes to `load` or `reload`, `#` and
  version included, and it repeats for a game that uses two spellings. At most 16 extra carts of
  1 MiB each, and 3 URLs per cart counting `fallback`s; past that, entries are ignored. List the entry
  cart first, because `firstContentImage` takes the note's first image for the share card.

  `CartLinks` reads these. The player fetches the extras when Play is pressed, and each must match
  its `x`. Only `http(s)` URLs are fetched, so an extra cart can't be a `blossom:` URI. `loadCart` is a lookup in memory, because it runs on the game thread inside a frame, so a
  game can only reach what its note lists; any other name shows "this game continues in another
  cartridge", as does an extra that failed to download. Saves are kept under a hash of the entry cart
  and every declared `name x` pair, so a stranger's note can't list a hostile cart beside a game's
  front cart to reach its saves. Fetching from the original's site would add a third-party server,
  so `#id` is never resolved there. Posting `cart` fields from the composer isn't supported yet.

  A note with no `cart` field at all and exactly two carts works without one, so uploading both carts
  of a game is enough. A cart here is an `imeta` with a 160x205 `dim`, a valid `x` and no `m` other
  than `image/png`. Each cart is the other's
  partner: the first name the game asks for gets the other cart, and the second gets the entry cart,
  because a game starts on the entry and the other cart's first ask is the way back. A third name has
  no cart. The two share one save key, a hash of both hashes, whichever one was started. Notes of
  three or more carts can't be told apart, so they need `cart` fields. A game that loads a cart from
  outside its note, posted beside an unrelated cart, starts that cart instead of showing the message.
  kiko doesn't say which cart is running, so a game that first asks for its own name, or for a cart
  it can do without, gets the two swapped; `cart` fields avoid that. A game's second cart may also
  need what the first leaves in memory, as Into Ruins' does, and then can't be started directly.
- **Uploads**: Android's `MediaCompressor` uploads a PNG that loads as a cart unchanged instead of
  re-encoding it to JPEG. Desktop never re-encoded PNGs.

## Next steps

1. Try it on an Android phone (touch controls, sound latency, rotation keeping the game, frame
   time of the heaviest carts on a low-end device).
2. iOS: an HTTP client for `CartBytes`, and an audio sink in kiko-player, both stubs there.
