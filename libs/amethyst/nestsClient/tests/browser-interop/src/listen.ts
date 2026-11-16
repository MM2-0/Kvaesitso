// Phase 4 (T16) browser-listener harness. Connects to the
// `NativeMoqRelayHarness` moq-relay subprocess via WebTransport, subscribes
// to the `<broadcast>/audio/data` track produced by the Amethyst Kotlin
// speaker, decodes each Opus packet via WebCodecs AudioDecoder, and posts
// the resulting Float32 PCM samples back to the bun WS server (`ws://`),
// which appends them to a file on disk for the Kotlin test to read.
//
// Reads its parameters from `location.search`:
//
//   relay     — the relay's WebTransport URL,
//               e.g. `https://127.0.0.1:43219/nests/<kind>:<host>:<room>?jwt=`.
//               Pass the FULL connection target (path + query) — Amethyst's
//               nests namespace is part of the relay path per `NestsConnect.kt`.
//   broadcast — the publisher's moq-lite broadcast path
//               (= `speakerPubkeyHex` per `MoqLiteNestsSpeaker.kt`).
//   track     — the audio track name. Defaults to `audio/data`.
//   wsPort    — the bun WS back-channel port; we POST PCM here.
//   duration  — broadcast capture window in seconds.
//
// Mirrors the data path of `kixelated/moq` `js/watch/src/audio/decoder.ts`
// (`#runLegacyDecoder`) with the `@moq/hang` `Container.Legacy.Format` consumer
// and the WebCodecs AudioDecoder warmup-skip semantics. Verbatim matching
// the watcher's per-frame behaviour is what catches a Chromium-side
// regression that wire-byte tests can't see.

import * as Moq from "@moq/lite";
import * as Container from "@moq/hang/container";
import { PRIORITY as CATALOG_PRIORITY } from "@moq/hang/catalog";

const params = new URLSearchParams(location.search);
const relayParam = params.get("relay");
const broadcastParam = params.get("broadcast");
const trackParam = params.get("track") ?? "audio/data";
const wsPort = Number(params.get("wsPort") ?? "0");
const durationSec = Number(params.get("duration") ?? "5");
const certSha256B64 = params.get("certSha256"); // Base64 SHA-256 of leaf DER cert.

function required(v: string | null, name: string): string {
    if (!v) throw new Error(`listen.html: missing ?${name}=`);
    return v;
}
const relayUrlString = required(relayParam, "relay");
const broadcastName = required(broadcastParam, "broadcast");
if (!wsPort) throw new Error("listen.html: missing ?wsPort=");

const status = (msg: string) => {
    const el = document.getElementById("status");
    if (el) el.textContent = msg;
    console.log("[listen]", msg);
};

const fail = (msg: string) => {
    status(`ERROR: ${msg}`);
    document.body.dataset.state = "error";
    throw new Error(msg);
};

async function main() {
    // -- WS back-channel ------------------------------------------------
    // The bun server appends every binary message we send to a PCM file
    // on disk. We open it BEFORE the WebTransport so the very first frame
    // (which decodes via WebCodecs after Container.Legacy strips the 2-byte
    // timestamp) is captured even if it arrives before the page reaches
    // its `done` state.
    const ws = new WebSocket(`ws://127.0.0.1:${wsPort}/pcm`);
    ws.binaryType = "arraybuffer";
    await new Promise<void>((resolve, reject) => {
        ws.addEventListener("open", () => resolve(), { once: true });
        ws.addEventListener("error", () => reject(new Error("ws connect failed")), { once: true });
    });
    status("ws connected");

    const sendPcm = (chunk: Float32Array) => {
        // Float32 LE matches the format hang-listen writes; the Kotlin
        // test reads it via `readFloat32Pcm`.
        if (ws.readyState === WebSocket.OPEN) ws.send(chunk.buffer);
    };
    const sendDone = () => {
        if (ws.readyState === WebSocket.OPEN) ws.send("done");
    };

    // -- Connect to the relay ------------------------------------------
    // `relayParam` already includes the namespace path + ?jwt=… query
    // (built by `buildRelayConnectTarget` in NestsConnect.kt). We pass it
    // straight to `Connection.connect` which feeds it to `new WebTransport(url)`
    // verbatim. Self-signed cert pinning is via Chromium's
    // `--ignore-certificate-errors` flag — we do NOT compute a SHA-256 hash
    // since the relay's auto-generated cert isn't deterministic.
    const relayUrl = new URL(relayUrlString);
    status(`connecting to ${relayUrl.toString()}`);
    // If the test driver passed a leaf-cert SHA-256, pin it via
    // `serverCertificateHashes`. Chromium's `--ignore-certificate-errors`
    // does NOT bypass QUIC cert validation (crbug.com/1190655), so this
    // is the supported path for self-signed test certs over WebTransport.
    // The hash is base64 — convert to a Uint8Array. Fail loudly if the
    // hash is malformed; falling back to no-pin would just produce a
    // QUIC_TLS_CERTIFICATE_UNKNOWN error one round-trip later.
    const webtransportOpts: WebTransportOptions = {};
    if (certSha256B64) {
        const raw = Uint8Array.from(atob(certSha256B64), (c) => c.charCodeAt(0));
        webtransportOpts.serverCertificateHashes = [
            { algorithm: "sha-256", value: raw },
        ];
    }
    const conn = await Moq.Connection.connect(relayUrl, {
        // Disable the WebSocket fallback — the harness relay only speaks QUIC.
        websocket: { enabled: false },
        webtransport: webtransportOpts,
    });
    status(`connected, alpn=${conn.version}`);

    // Expose for Playwright to read post-hoc.
    (window as any).__moqVersion = conn.version;

    // -- Subscribe to the audio track ----------------------------------
    const broadcastPath = Moq.Path.from(broadcastName);
    const broadcast = conn.consume(broadcastPath);
    const track = broadcast.subscribe(trackParam, CATALOG_PRIORITY.audio);
    status(`subscribed broadcast=${broadcastName} track=${trackParam}`);

    // The hang Container.Legacy.Consumer strips the Varint-encoded
    // timestamp prefix (per `kixelated/moq/js/hang/src/container/legacy.ts`)
    // and yields an Opus packet per `next()`. Mirrors the data path the
    // @moq/watch decoder uses internally for `container.kind = "legacy"`
    // catalogs (the kind Amethyst publishes via `MoqLiteHangCatalog.opus48k`).
    const consumer = new Container.Legacy.Consumer(track, {
        // Tight latency — the harness runs over loopback, no jitter.
        // Pass a literal Time.Milli (number); the consumer accepts it directly.
        latency: 100 as any,
    });

    // -- WebCodecs AudioDecoder ----------------------------------------
    const sampleRate = 48_000;
    // Channel count from `?channels=N` URL param; defaults to mono.
    // The hang-tier I4 uses 2 (440 Hz L / 660 Hz R) — Chromium's
    // WebCodecs AudioDecoder must be configured with the correct
    // channel count up front; reconfiguring after frames arrive
    // discards decoder state.
    const numberOfChannels = Number(params.get("channels") ?? "1");
    let warmed = 0;
    // I14 instrumentation. `decoderOutputs` counts every successful
    // `output()` callback (warmup frames included), `decoderErrors`
    // counts every WebCodecs `error()` callback. A T8 regression that
    // leaks `OpusHead` into a normal audio frame surfaces as either a
    // non-zero error count (decoder rejects the bytes) or — if Chromium
    // tolerates it — as the warmup window absorbing the stray frame
    // and the FFT peak shifting. The error counter catches case 1
    // deterministically; the FFT peak in I1 catches case 2.
    let decoderOutputs = 0;
    let decoderErrors = 0;

    const decoder = new AudioDecoder({
        output: (data: AudioData) => {
            warmed++;
            decoderOutputs++;
            if (warmed <= 3) {
                // Mirror @moq/watch's 3-frame WebCodecs warmup skip.
                data.close();
                return;
            }
            const channels = data.numberOfChannels;
            const frames = data.numberOfFrames;
            // Interleave channels into a single Float32 buffer (Float32 LE,
            // matching hang-listen's output format). Mono → just one plane.
            if (channels === 1) {
                const buf = new Float32Array(frames);
                data.copyTo(buf, { format: "f32-planar", planeIndex: 0 });
                sendPcm(buf);
            } else {
                const planes: Float32Array[] = [];
                for (let c = 0; c < channels; c++) {
                    const p = new Float32Array(frames);
                    data.copyTo(p, { format: "f32-planar", planeIndex: c });
                    planes.push(p);
                }
                const interleaved = new Float32Array(frames * channels);
                for (let f = 0; f < frames; f++) {
                    for (let c = 0; c < channels; c++) {
                        interleaved[f * channels + c] = planes[c][f];
                    }
                }
                sendPcm(interleaved);
            }
            data.close();
        },
        error: (err) => {
            decoderErrors++;
            console.error("[listen] AudioDecoder", err);
        },
    });

    decoder.configure({
        codec: "opus",
        sampleRate,
        numberOfChannels,
        // No description for Opus per @moq/watch decoder.ts comment:
        // "Opus in CMAF uses raw packets; dOps is not a valid OGG header".
    });

    // -- Frame pump -----------------------------------------------------
    const deadline = performance.now() + durationSec * 1000;
    let framesDecoded = 0;
    document.body.dataset.state = "playing";

    while (performance.now() < deadline) {
        const next = await Promise.race([
            consumer.next(),
            new Promise<undefined>((r) =>
                setTimeout(() => r(undefined), Math.max(50, deadline - performance.now())),
            ),
        ]);
        if (!next) break;
        const { frame } = next;
        if (!frame) continue;

        framesDecoded++;
        if (decoder.state === "closed") break;
        decoder.decode(
            new EncodedAudioChunk({
                type: frame.keyframe ? "key" : "delta",
                data: frame.data,
                timestamp: frame.timestamp,
            }),
        );
    }

    status(`done, frames=${framesDecoded}`);
    (window as any).__framesDecoded = framesDecoded;
    (window as any).__decoderOutputs = decoderOutputs;
    (window as any).__decoderErrors = decoderErrors;

    // Flush any pending decoder output, then signal the WS server we're done.
    try {
        await decoder.flush();
    } catch (e) {
        console.warn("[listen] flush:", e);
    }
    if (decoder.state !== "closed") decoder.close();
    consumer.close();
    sendDone();

    document.body.dataset.state = "done";
    status(`done. frames=${framesDecoded}`);
}

main().catch((e) => {
    console.error("[listen] fatal:", e);
    fail(String(e?.stack ?? e));
});
