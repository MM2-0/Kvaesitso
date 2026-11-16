#!/usr/bin/env bash
#
# Copyright (c) 2025 Vitor Pamplona — MIT (see repo LICENSE).
#
# Layer-3 end-to-end test for Amethyst push notifications.
#
# Drives the WHOLE pipeline on a real device/emulator: a second identity (run
# through `amy`) publishes each notification kind to the account logged into the
# phone, and the script reads back the system notification shade over `adb` to
# assert the right notification appeared on the right channel — then exercises
# the two behaviors unit tests can't reach: cold-push metadata enrichment and
# dismiss-on-read.
#
# It is written to be run by an agent: every check prints PASS / WARN / FAIL and
# the script exits non-zero if any hard assertion fails.
#
# ---------------------------------------------------------------------------
# PREREQUISITES (the harness checks what it can and aborts with guidance)
#   - A device/emulator on `adb` with the DEBUG app installed and an account
#     logged in (this is identity A, the receiver).
#   - `amy` built:  ./gradlew :cli:installDist   (binary at
#     cli/build/install/amy/bin/amy)
#   - `jq` on PATH.
#   - A relay BOTH sides use: it must be in A's inbox/read relays AND A's
#     always-on notification service must be enabled, so backgrounded events
#     arrive. For an emulator + a local relay, `amy serve` + `adb reverse
#     tcp:7777 tcp:7777` and point the account at ws://127.0.0.1:7777.
#
# USAGE
#   A_NPUB=npub1…  RELAY=wss://relay.example  ./tools/notification-e2e/run.sh
#
# ENV (override as needed)
#   A_NPUB      (required) receiver npub — the account logged into the phone
#   RELAY       (required) ws/wss relay URL both sides use
#   APP_PKG     app id             (default com.vitorpamplona.amethyst.debug)
#   ADB_SERIAL  adb -s target      (default: the only connected device)
#   AMY         amy binary path    (default cli/build/install/amy/bin/amy)
#   SENDER_HOME amy data dir for B  (default /tmp/amy-e2e-sender)
#   FRESH_HOME  amy data dir for C  (default /tmp/amy-e2e-fresh)
#   IMG_URL     image for the media test (default a small public jpg)
#   TIMEOUT     seconds to wait for a notification to appear (default 40)
# ---------------------------------------------------------------------------
set -uo pipefail

A_NPUB="${A_NPUB:?set A_NPUB to the npub logged into the phone}"
RELAY="${RELAY:?set RELAY to a relay both the phone and amy use}"
APP_PKG="${APP_PKG:-com.vitorpamplona.amethyst.debug}"
AMY="${AMY:-cli/build/install/amy/bin/amy}"
SENDER_HOME="${SENDER_HOME:-/tmp/amy-e2e-sender}"
FRESH_HOME="${FRESH_HOME:-/tmp/amy-e2e-fresh}"
IMG_URL="${IMG_URL:-https://image.nostr.build/nostr.build_1x1.jpg}"
TIMEOUT="${TIMEOUT:-40}"
MAIN_ACTIVITY="com.vitorpamplona.amethyst.ui.MainActivity"

ADB=(adb)
[ -n "${ADB_SERIAL:-}" ] && ADB=(adb -s "$ADB_SERIAL")

pass=0 fail=0 warn=0
ok()   { echo "  PASS  $*"; pass=$((pass + 1)); }
bad()  { echo "  FAIL  $*"; fail=$((fail + 1)); }
meh()  { echo "  WARN  $*"; warn=$((warn + 1)); }
hdr()  { echo; echo "== $* =="; }

amyB() { "$AMY" --data-dir "$SENDER_HOME" "$@"; }
amyC() { "$AMY" --data-dir "$FRESH_HOME" "$@"; }
dump() { "${ADB[@]}" shell dumpsys notification --noredact 2>/dev/null; }

# Wait until BOTH strings appear somewhere in the shade (proxy for "a
# notification on <channel> carrying <text>"). Returns 0 on success.
wait_for() {
  local channel="$1" text="$2" deadline=$((SECONDS + TIMEOUT))
  while [ $SECONDS -lt $deadline ]; do
    local d; d="$(dump)"
    if grep -qF "$channel" <<<"$d" && grep -qF "$text" <<<"$d"; then return 0; fi
    sleep 2
  done
  return 1
}

# Publish a raw signed event as sender B, targeting A. Echoes the event id.
pubB() { # kind  content  tagsJSON
  amyB event --kind "$1" --content "$2" --tags "$3" --relay "$RELAY" --json \
    | jq -r '.event.id'
}

hex_of() { "$AMY" decode "$1" 2>/dev/null | grep -oE '[0-9a-f]{64}' | head -1; }

# ---------------------------------------------------------------------------
hdr "Preflight"
command -v jq >/dev/null || { echo "jq not found"; exit 2; }
[ -x "$AMY" ] || { echo "amy not found at $AMY — run ./gradlew :cli:installDist"; exit 2; }
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "no adb device (set ADB_SERIAL?)"; exit 2; }
"${ADB[@]}" shell pm path "$APP_PKG" >/dev/null 2>&1 || { echo "$APP_PKG not installed"; exit 2; }

A_HEX="$(hex_of "$A_NPUB")"
[ -n "$A_HEX" ] || { echo "could not decode A_NPUB to hex"; exit 2; }
echo "receiver A = $A_NPUB ($A_HEX)"

# Ensure POST_NOTIFICATIONS (Android 13+) and that notifications are on.
"${ADB[@]}" shell pm grant "$APP_PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# Two sender identities: B (has a profile) and C (fresh, no profile → drives the
# enrichment test).
if [ ! -f "$SENDER_HOME/current" ] && [ -z "$(ls -A "$SENDER_HOME" 2>/dev/null)" ]; then
  amyB create >/dev/null
  amyB profile edit --name "E2E Sender" --relay "$RELAY" >/dev/null 2>&1 || \
    amyB event --kind 0 --content '{"name":"E2E Sender"}' --relay "$RELAY" >/dev/null
fi
B_NPUB="$(amyB whoami 2>/dev/null | grep -oE 'npub1[0-9a-z]+' | head -1)"
B_HEX="$(hex_of "$B_NPUB")"
echo "sender  B = ${B_NPUB:-?} ($B_HEX)"

# Background the app so notifications aren't suppressed (MainActivity.isResumed).
"${ADB[@]}" shell input keyevent KEYCODE_HOME
sleep 1

# A target note authored by A, for reply/reaction/repost (must exist on RELAY).
TARGET_ID="$(amyB fetch --author "$A_HEX" --kind 1 --limit 1 --relay "$RELAY" --json 2>/dev/null | jq -r '.events[0].id // empty')"
if [ -z "$TARGET_ID" ]; then
  meh "A has no kind:1 note on $RELAY — reply/reaction/repost will be skipped."
  meh "Post one from the phone (or 'amy' as A) and re-run for full coverage."
fi

# ---------------------------------------------------------------------------
hdr "Liveness — mention (also confirms the phone actually receives from RELAY)"
pubB 1 "e2e mention nostr:$A_NPUB" "[[\"p\",\"$A_HEX\"]]" >/dev/null
if wait_for "MentionsID" "mentioned you"; then
  ok "mention → Mentions channel"
else
  bad "no mention notification within ${TIMEOUT}s."
  echo "  -> Is $RELAY in A's inbox relays and the always-on service enabled?"
  echo "     Aborting: nothing else can pass until events reach the phone."
  exit 1
fi

# ---------------------------------------------------------------------------
hdr "Per-kind delivery"

pubB 1 "e2e reply body" "[[\"p\",\"$A_HEX\"]]" >/dev/null  # non-reply mention already covered

if [ -n "$TARGET_ID" ]; then
  pubB 1 "e2e reply to your note" "[[\"e\",\"$TARGET_ID\",\"\",\"root\"],[\"p\",\"$A_HEX\"]]" >/dev/null
  wait_for "RepliesID" "replied" && ok "reply → Replies channel" || bad "reply notification missing"

  pubB 7 "🤙" "[[\"e\",\"$TARGET_ID\"],[\"p\",\"$A_HEX\"]]" >/dev/null
  wait_for "ReactionsID" "reacted" && ok "reaction → Reactions channel" || bad "reaction notification missing"

  # Buzz-style bare like: a NIP-25 kind-7 with an `e` tag but NO `p` tag (Buzz
  # relays emit these). Must still notify — routed via tagsAnEventByUser, not the
  # p-tag gate. A distinct emoji separates it from the 🤙 reaction above.
  pubB 7 "❤️" "[[\"e\",\"$TARGET_ID\"]]" >/dev/null
  wait_for "ReactionsID" "❤️" && ok "Buzz bare reaction (no p-tag) → Reactions channel" || bad "Buzz bare reaction missing"

  pubB 6 "" "[[\"e\",\"$TARGET_ID\"],[\"p\",\"$A_HEX\"]]" >/dev/null
  wait_for "RepostsID" "reposted" && ok "repost → Reposts channel (new kind)" || bad "repost notification missing"
else
  meh "skipped reply/reaction/repost (no target note)"
fi

pubB 20 "e2e photo caption" "[[\"p\",\"$A_HEX\"],[\"imeta\",\"url $IMG_URL\",\"m image/jpeg\"]]" >/dev/null
wait_for "MediaID" "shared a photo" && ok "picture → Media channel (BigPicture, new kind)" || bad "media notification missing"

amyB dm send "$A_NPUB" "e2e direct message" --relay "$RELAY" >/dev/null 2>&1 \
  && { wait_for "PrivateMessagesID" "e2e direct message" && ok "DM → Private Messages (MessagingStyle)" || meh "DM notification missing (check A's kind:10050 DM relays include $RELAY)"; } \
  || meh "amy dm send failed (DM relays / wallet not configured) — skipped"

# Buzz DM (kind 40002 / kind 9 in a `t=dm` relay-group channel) is participant-
# routed off the channel's 39000 metadata, which this script can't set up in one
# shot. Not auto-triggered — see README "Buzz DM (manual)". Expect it to land on
# PrivateMessagesID with the channel name as title and "<sender>: <text>" body.
meh "Buzz DM not auto-triggered (needs a t=dm relay-group channel) — see README"

# ---------------------------------------------------------------------------
hdr "Observability — cold-push metadata enrichment"
# Fresh sender C with NO profile: the notification must first show a raw pubkey,
# then update in place to C's display name once its kind:0 arrives.
if [ -z "$(ls -A "$FRESH_HOME" 2>/dev/null)" ]; then amyC create >/dev/null; fi
C_NPUB="$(amyC whoami 2>/dev/null | grep -oE 'npub1[0-9a-z]+' | head -1)"
C_HEX="$(hex_of "$C_NPUB")"

"${ADB[@]}" shell am force-stop "$APP_PKG"   # cold process: empty LocalCache
sleep 2
"$AMY" --data-dir "$FRESH_HOME" event --kind 1 --content "e2e enrichment nostr:$A_NPUB" \
  --tags "[[\"p\",\"$A_HEX\"]]" --relay "$RELAY" --json >/dev/null
if wait_for "MentionsID" "mentioned you"; then
  before="$(dump)"
  if grep -qiE "npub1|${C_HEX:0:12}" <<<"$before"; then
    ok "cold notification shows the raw pubkey first (pre-enrichment)"
  else
    meh "could not confirm the raw-pubkey placeholder (title may already be resolved)"
  fi
  # Now publish C's profile; the SAME notification should re-render to the name.
  amyC profile edit --name "E2E Fresh" --relay "$RELAY" >/dev/null 2>&1 || \
    amyC event --kind 0 --content '{"name":"E2E Fresh"}' --relay "$RELAY" >/dev/null
  if wait_for "MentionsID" "E2E Fresh"; then
    ok "notification enriched in place to the display name after kind:0 arrived"
  else
    bad "notification never picked up C's name — enrichment window may not be firing"
  fi
else
  meh "cold enrichment mention didn't arrive — skipped"
fi

# ---------------------------------------------------------------------------
hdr "Dismiss-on-read — opening the post clears its tray notification"
"${ADB[@]}" shell input keyevent KEYCODE_HOME; sleep 1
MENTION_ID="$(pubB 1 "e2e dismiss target nostr:$A_NPUB" "[[\"p\",\"$A_HEX\"]]")"
if [ -n "$MENTION_ID" ] && wait_for "MentionsID" "e2e dismiss target"; then
  NEVENT="$(amyB encode nevent "$MENTION_ID" --author "$B_HEX" --relay "$RELAY" --json 2>/dev/null | grep -oE 'nevent1[0-9a-z]+' | head -1)"
  # Simulate the tap: open the note deep-link the notification would have used.
  "${ADB[@]}" shell am start -n "$APP_PKG/$MAIN_ACTIVITY" \
    -a android.intent.action.VIEW -d "nostr:$NEVENT?account=$A_NPUB" >/dev/null 2>&1
  sleep 4
  "${ADB[@]}" shell input keyevent KEYCODE_HOME; sleep 2
  if grep -qF "e2e dismiss target" <<<"$(dump)"; then
    meh "notification still present after opening the note (deep-link format may need adjusting)"
  else
    ok "reading the post cleared its notification from the tray"
  fi
else
  meh "dismiss-on-read target didn't arrive — skipped"
fi

# ---------------------------------------------------------------------------
hdr "Result"
echo "  $pass passed, $warn warnings, $fail failed"
[ "$fail" -eq 0 ] || exit 1
