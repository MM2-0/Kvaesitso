# EGG-12: Catalog track (`catalog.json`)

`status: draft`
`requires: EGG-03`
`category: optional`

## Summary

A speaker MAY publish a sibling `catalog.json` track on the same moq-lite
broadcast as their `audio/data` track. The catalog carries codec
metadata: format, sample rate, channel count, bitrate, etc. It exists so
clients can render a "speaker is broadcasting Opus 48 kHz mono" tooltip
without parsing audio frames, and so future codec migrations can be
negotiated client-side.

## Wire format

### Track convention

A speaker MUST publish the catalog on the SAME broadcast as their audio
track:

```
SUBSCRIBE  broadcast=<speaker pubkey hex>  track="catalog.json"
```

### Payload

Each `Group` carries a single JSON document encoded as UTF-8. The
publisher emits a fresh group whenever the catalog changes (e.g. codec
swap on mute / unmute). Receivers SHOULD read only the most recent
group.

Document shape:

```json
{
  "version": 1,
  "audio": [
    {
      "track":         "audio/data",
      "codec":         "opus",
      "sample_rate":   48000,
      "channel_count": 1,
      "bitrate":       32000
    }
  ]
}
```

Field reference:

| field           | type   | required | meaning                              |
|-----------------|--------|----------|--------------------------------------|
| `version`       | int    | required | Schema version. Currently `1`.       |
| `audio`         | array  | required | One entry per audio track. May be empty. |
| `audio[].track` | string | required | Track name; matches the moq-lite track this catalog describes (typically `"audio/data"`). |
| `audio[].codec` | string | optional | Lower-case codec id (`opus`, `aac`, …). |
| `audio[].sample_rate` | int | optional | Sample rate in Hz.            |
| `audio[].channel_count` | int | optional | 1 = mono, 2 = stereo.       |
| `audio[].bitrate` | int  | optional | Target bitrate in bits per second. |

Unknown fields MUST be tolerated: parsers MUST ignore keys they do not
recognise rather than rejecting the document. New keys are added without
incrementing `version` unless they change the meaning of an existing key.

## Behavior

1. Publishing a catalog is OPTIONAL. A speaker that omits the
   `catalog.json` track MUST still be playable per EGG-03 (which already
   pins the audio parameters).
2. A subscriber MUST tolerate the absence of a catalog. A `Subscribe`
   response of `Drop`/error on the `catalog.json` track is benign — the
   subscriber falls back to the EGG-03 default parameters.
3. A subscriber MUST tolerate malformed JSON, missing `version`, and
   unknown fields. Failure to parse means "no catalog metadata" — render
   the default tooltip.
4. A publisher MUST NOT use the catalog track to deliver authoritative
   stream parameters that contradict EGG-03. The catalog is INFORMATIVE,
   not normative: a listener MUST be able to decode `audio/data` without
   reading the catalog.
5. Subscribers SHOULD limit how often they re-render the catalog tooltip:
   at most once per second, even if catalogs are published more
   frequently.
6. Hosts MAY use the catalog to detect non-conformant publishers (e.g. a
   broadcaster claiming `aac` when EGG-03 mandates `opus`) for
   moderation purposes. The actual moderation flow is out of scope for
   this EGG.

## Example

A speaker comes onstage with default Opus parameters:

```
SUBSCRIBE broadcast="speakerA" track="catalog.json"
←  GROUP   sequence=0
←  FRAME   {"version":1,"audio":[{"track":"audio/data","codec":"opus","sample_rate":48000,"channel_count":1,"bitrate":32000}]}
```

The same speaker bumps to 64 kbit/s:

```
←  GROUP   sequence=1
←  FRAME   {"version":1,"audio":[{"track":"audio/data","codec":"opus","sample_rate":48000,"channel_count":1,"bitrate":64000}]}
```

## Compatibility

EGG-12 is purely additive on top of EGG-03. A receiver that does not
support EGG-12 MUST still subscribe to `audio/data` directly (EGG-03)
and decode at the parameters EGG-03 mandates.

A future EGG MAY widen the catalog schema to describe multiple audio
quality tiers, video tracks, or speaker-side hints (e.g. push-to-talk
state). Those additions MUST keep `version: 1` working for existing
parsers; breaking changes ship as `version: 2` with a separate EGG.
