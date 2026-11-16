# Notification end-to-end test (Layer 3)

Drives the whole push-notification pipeline on a real device/emulator: a second
identity publishes each notification kind through `amy` to the account logged
into the phone, and the script reads the system notification shade back over
`adb` to assert the right notification landed on the right channel — then tests
the two behaviors unit tests can't reach: **cold-push metadata enrichment** and
**dismiss-on-read**.

It's written to be run unattended by an agent: every check prints
`PASS` / `WARN` / `FAIL`, and the script exits non-zero if any hard assertion
fails.

## What it covers

| Check | How it's triggered | Asserted from the shade |
| --- | --- | --- |
| Mention (+ liveness) | `amy event --kind 1` p-tagging A | `MentionsID` + "mentioned you" |
| Reply | kind 1 with `e`+`p` tags to A's note | `RepliesID` + "replied" |
| Reaction | kind 7 to A's note | `ReactionsID` + "reacted" |
| Buzz bare reaction | kind 7 with `e` tag, **no `p` tag** | `ReactionsID` (routed via `tagsAnEventByUser`) |
| Repost *(new kind)* | kind 6 to A's note | `RepostsID` + "reposted" |
| Picture *(new kind)* | kind 20 with `imeta` + `p` | `MediaID` + "shared a photo" |
| DM | `amy dm send` | `PrivateMessagesID` + the body |
| **Enrichment** | cold-start, then publish sender kind:0 | title flips from raw pubkey → display name in place |
| **Dismiss-on-read** | deep-link the note, then re-check | notification gone |

Zaps (need a Lightning wallet), git/badge/nutzap/onchain (need repo / badge
definition / cashu proofs) aren't automated here — they follow the same
`amy event --kind …` pattern with the appropriate tags if you want to extend it.

### Buzz DM (manual)

Buzz DM notifications (`StreamMessageV2Event` k40002 / `ChatEvent` k9 in a `t=dm`
relay-group channel) are participant-routed off the channel's kind-39000
metadata, so they can't be triggered in one `amy event` call. To verify by hand:

1. Create a Buzz DM channel whose kind-39000 metadata has `["t","dm"]` and a
   `["p", <A_hex>]` participant tag (use the Buzz UI, or `amy relaygroup` /
   `amy event --kind 39000` as the relay/group admin), on a relay A is connected
   to. Let A load the channel (open it once) so its 39000 metadata is cached —
   push only fires when the participant list is known.
2. As B, post a message into it: `amy event --kind 9 --content "buzz dm hi"
   --tags '[["h","<groupId>"]]' --relay <groupRelay>` (or kind 40002).
3. Background the app; expect a `PrivateMessagesID` notification titled with the
   channel name and body `B: buzz dm hi`, tapping it opens the chatroom.

The gate is intentionally strict (participant metadata must be loaded), matching
the in-app feed — a cold push with no cached channel won't notify.

## Prerequisites

- A device/emulator on `adb` with the **debug** app installed and an account
  logged in — this is identity **A**, the receiver.
- `amy` built: `./gradlew :cli:installDist` (binary at
  `cli/build/install/amy/bin/amy`).
- `jq` on `PATH`.
- **A relay both sides use.** It must be in A's inbox/read relays **and** A's
  always-on notification service must be enabled, so events arrive while the app
  is backgrounded. For an emulator with a local relay:

  ```bash
  amy serve --port 7777 &                 # embeds the geode relay
  adb reverse tcp:7777 tcp:7777           # phone's 127.0.0.1:7777 → host
  # then add ws://127.0.0.1:7777 to the account's relays in the app
  ```

## Run

```bash
A_NPUB=npub1…  RELAY=wss://relay.example  ./tools/notification-e2e/run.sh
```

Reply/reaction/repost need a note **authored by A** on the relay; if A has none
the script warns and skips those three — post one from the phone first for full
coverage.

## Knobs

| Env | Default | |
| --- | --- | --- |
| `A_NPUB` | *(required)* | receiver npub (the phone's account) |
| `RELAY` | *(required)* | relay both sides use |
| `APP_PKG` | `com.vitorpamplona.amethyst.debug` | app id |
| `ADB_SERIAL` | first device | `adb -s` target |
| `AMY` | `cli/build/install/amy/bin/amy` | amy binary |
| `TIMEOUT` | `40` | seconds to wait for a notification |
| `IMG_URL` | a small public jpg | image for the media test |

## Notes / limitations

- Verification greps `dumpsys notification --noredact` for the channel id **and**
  a content string; it doesn't bind them to the same record, so run it against a
  reasonably quiet device.
- The dismiss-on-read step depends on the note deep-link format; if it can't
  confirm the clear it `WARN`s rather than failing, so eyeball it once.
- It exercises the **always-on relay** delivery path (no FCM/UnifiedPush server
  needed). To test the real push transport, send through your push server and
  keep the rest of the flow identical.
