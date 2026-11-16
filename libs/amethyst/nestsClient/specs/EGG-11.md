# EGG-11: Recording

`status: draft`
`requires: EGG-01`
`category: decorative`

## Summary

When a host captures a room out-of-band and wants to make the recording
available to audience members who missed the live session, they re-publish
the closed `kind:30312` with a `["recording", url]` tag.

The recording itself is not delivered through the audio plane (EGG-03) —
it is a static asset on the open web. Clients hand the URL to the system
media player rather than embedding playback inline.

## Wire format

A `["recording", url]` tag added to a `kind:30312` event:

```json
["recording", "<https URL of the recording>"]
```

The URL MUST point at a publicly-fetchable audio file (Opus, MP3, AAC, M4A,
or any format the local OS / app ecosystem supports for `audio/*` MIME
types).

A `kind:30312` carrying a `recording` tag MUST also carry `status="closed"`.
A live or planned room with a recording tag is non-conformant; receivers
SHOULD ignore the recording tag in that case.

## Behavior

1. The host MUST NOT publish a `recording` tag until the recording is
   uploaded and reachable. A recording tag pointing at a 404 erodes
   trust; receivers SHOULD silently hide the listen-back UI when the
   URL fails to resolve.
2. The URL MUST be `https://`. `http://` URLs MUST be rejected.
3. The host SHOULD include a `Content-Type` header on the asset's HTTP
   response so the system media player can pick the right handler.
4. Receivers SHOULD render the listen-back affordance as a single button
   ("Listen to recording") that hands the URL to the platform's media
   intent (Android: `ACTION_VIEW`; iOS: `UIApplication.open`; web:
   `<a target="_blank">`). Receivers MUST NOT auto-play.
5. Receivers MUST gracefully tolerate the absence of any registered
   media handler — show the user a "no app installed to play this"
   toast rather than crash or no-op silently.
6. Clients MUST NOT subscribe to the audio plane (EGG-03) for a closed
   room. The recording is the only audio path post-close.
7. Hosts MAY publish multiple recording tags (e.g. one MP3, one Opus).
   Receivers SHOULD prefer the FIRST entry and treat extras as
   alternatives.

## Example

```json
{
  "kind": 30312,
  "pubkey": "abc...host",
  "created_at": 1714010000,
  "tags": [
    ["d", "office-hours-2026-04"],
    ["room", "Office Hours"],
    ["status", "closed"],
    ["service", "https://moq.nostrnests.com"],
    ["endpoint", "https://moq.nostrnests.com"],
    ["p", "abc...host", "wss://relay", "host"],
    ["recording", "https://recordings.example.com/office-hours-2026-04.opus"]
  ],
  "content": "",
  "...": "..."
}
```

## Compatibility

Receivers without EGG-11 see a closed room without a listen-back button.
A receiver with EGG-11 but without a registered media app for the URL's
MIME type falls back to the toast described in rule 5.

Recording is not part of the moq-lite spec; it is a Nostr-side
augmentation. A nests deployment that does not capture rooms continues
to interop without changes.
