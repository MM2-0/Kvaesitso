# Retiring EncryptedStorage

Status: **migrated, not yet deleted.** Every key has a home in the new stores.
The legacy files are still written, so they are still there — and the reader
can never go.

## The constraint

Every migration in the preference layer is *lazy*: it reads the legacy store
when it runs, not when the app is installed. Ten come through
`EncryptedStorage` — the eight `LegacyKeyTable` copies on the per-account
DataStore plus the key, secret and roster stores. Only the Cashu counters and
calendar reminders read a plain (non-encrypted) source and would survive its
removal.

So deleting `EncryptedStorage` does not merely affect installs that have not
upgraded yet. It strands anyone who **skips** the release introducing the new
stores: a pre-migration build upgrading straight to a post-deletion build runs
its migration against a reader that no longer exists. Keys, accounts, wallets
and settings stay encrypted on disk with nothing able to read them, and the app
opens as a fresh install. Auto-update off, the F-Droid cadence and restoring
from a backup all skip releases.

**The reader is permanent.** What a later release can retire is the legacy
*write*, which stops new data landing there while old data stays readable.
`androidx.security.crypto` has to stay for as long as the reader does. That is
an unmaintained-library risk, not an active vulnerability, and a much smaller
cost than stranding users.

## Where each key went

`LegacyKeyCoverageTest` holds this to being exhaustive: every constant in
`PrefKeys` is either claimed by a migration table, one of the secrets, on the
accepted-loss list, or a key of the global file. A key added to `PrefKeys` and
to none of those fails that test at the commit that adds it.

| group | destination | legacy write |
|---|---|---|
| follow lists, cached events, upload, dialogs, relay auth, feed visibility, notifications | the account's plain DataStore | already retired |
| identity — pubkey, signer, local relays, backup conflicts, backup flag | the account's plain DataStore | **kept** |
| private key | `SecureKeyStorage` | **kept** |
| NIP-46 material, wallets, payment source | the account's encrypted DataStore | **kept** |
| current account, saved accounts | the encrypted roster store | **kept** |
| UI settings (`shared_settings`) | `UiSharedPreferences`' own DataStore | none left |
| location-chat identity — seed, nickname | the account's encrypted DataStore, as its own `GeohashIdentitySecrets` group | **kept** |

The stores that still mirror are the ones whose loss is not an annoyance: an
account that cannot be listed, signed with, or paid from. They keep the
rollback window open until the device pass below has happened.

The location-chat identity is the odd one, in two ways worth knowing before
step 4. It is its **own** group rather than fields on `AccountSecrets`: every
account save mirrors a whole `AccountSecrets` built from `AccountSettings`,
which does not hold these, and that group save removes keys whose value is
null — folded in, the seed would be deleted by the next unrelated save and
every geohash identity the user has would silently change. And its legacy home
is a **different file**, `secret_keeper_<pubkey hex>`, because its writer
passed `signer.pubKey` where every other caller passes an npub.

The UI settings copy is **guarded** where the others are not. That store has
been the real home of these settings for a while, so most installs already
have a populated one and copying the old blob over it would undo every UI
change since. The copy only runs into a store that has never been saved
(`ui.theme` absent, which `save` always writes).

## Deliberately not migrated

| key | what is lost |
|---|---|
| `PENDING_ATTESTATIONS` | queued OTS attestations are not published |
| `NOTIF_GLOBAL_TO_CURATED_MIGRATED` | the one-shot notification filter migration runs once more |
| `LAST_READ_PER_ROUTE` | every feed reads as unread once |
| `USE_PROXY`, `PROXY_PORT` | nothing — only ever removed, never read |
| `TOR_SETTINGS` | nothing — no reader left anywhere |

These are listed in `LegacyAccountKeys.accepted`, which is what lets the
cleanup treat any *other* unclaimed key as a reason to keep the file.

## Deleting a legacy file

`LegacyPreferenceCleanup` runs after every successful account load and deletes
that account's files — `secret_keeper_<npub>` **and** the location-chat
identity's `secret_keeper_<pubkey hex>` — only when it can prove nothing would
be lost. Both, because nothing else would ever remove the second one: the
cleanup enumerates npub-keyed files, so left out of this it would sit on disk
holding a seed forever.

1. **Every key in the file is accounted for** — claimed by a table, one of the
   secrets, or on the accepted list. Driven from the file's own keys, not from
   a checklist, because a checklist fails silently in the one direction that
   matters.
2. **Every copy that had something to copy has run.** Marker-based, not a value
   comparison: those groups stopped being legacy-written when they moved, so
   the file is a frozen snapshot and the two are *expected* to diverge as soon
   as the user changes a setting. `CopyOnceMigration` commits the values and
   its marker as one `Preferences`, so the marker cannot be set without them.
3. **The secrets and the private key read back identical** from the current
   stores. Those *are* still dual-written, so the stronger question is
   available and is asked.
4. **The location-chat identity has been copied**, when its file holds one.
   Read from the hex-keyed file, not the npub one — these two keys were never
   in that one, so a check pointed at it would never fire. An account that
   never opened a location chat holds neither key, which is a real answer and
   must not hold the file hostage.
5. A store that cannot be read is a reason, never a pass.

It refuses today, and says so, because of the last condition:
`LEGACY_WRITES_RETIRED` is false. While the app still mirrors into the file,
deleting it achieves nothing — the next save recreates it — and would look
like it had worked.

## Order of work

1. ~~Migrate the remaining keys.~~ Done.
2. ~~Gate deletion on a per-account read-back.~~ Done.
3. Do the device pass below.
4. Flip `LEGACY_WRITES_RETIRED` and drop the legacy writes for the identity,
   key, secret and roster stores. This ends the rollback window, so it is a
   release of its own. The flag is `internal`, not private, so the
   location-chat mirror in `GeohashChatIdentityState` reads the same switch —
   flipping it stops that write too, and the cleanup then removes both of the
   account's legacy files. One flip, nothing left behind.
5. Keep the reader, and `androidx.security.crypto`, indefinitely.

## Verification this needs and has not had

None of the AndroidKeyStore paths have executed: this environment has no device
or emulator, and `commons` has no Robolectric. What is tested is the decision
logic against fakes — which is why step 3 is not optional, and why deletion is
the one irreversible step in the whole series.

On a real device, before step 4 ships: upgrade an install holding accounts and
confirm they all list; open one and sign; force-stop and relaunch; add and
remove an account; pair a NIP-46 signer; pay from a wallet; check the
key-backup nudge stays dismissed; confirm UI settings survive the upgrade;
open a location chat under a bunker or external signer and confirm the
throwaway identity and nickname are the same ones as before the upgrade — the
seed is the one migrated value whose loss is silent rather than visible.
Then let the cleanup run with the flag flipped, and confirm the files are gone
and everything above still holds on the next cold start.
