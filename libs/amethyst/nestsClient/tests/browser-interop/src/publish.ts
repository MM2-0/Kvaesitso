// Phase 4 (T16) browser-publisher harness.
//
// Inverse of `listen.ts`: drives a sine `OscillatorNode` through the
// WebCodecs `AudioEncoder` (Opus mode) and pushes each encoded packet
// onto a moq-lite track via `Container.Legacy.Producer`, prefixed with
// a Varint-encoded timestamp the watcher (`Container.Legacy.Format`)
// will strip on decode. Also publishes a `catalog.json` track that
// matches `MoqLiteHangCatalog.opus48k` byte-for-byte so the Amethyst
// listener (and `hang-listen` for cross-validation) can discover the
// audio rendition.
//
// Optional `?reconnectAfterMs=N` URL param: cycles the moq session
// at N ms into the broadcast — drops the current `Connection`,
// builds a fresh one, re-publishes the same broadcast suffix. The
// relay sees `Announce::Ended → Active` on the same path. Used by
// the Browser I7 scenario (Chromium publisher reconnect → Kotlin
// listener recovers via `connectReconnectingNestsListener`'s
// re-issuance pump).

import * as Moq from "@moq/lite";
import * as Container from "@moq/hang/container";

const params = new URLSearchParams(location.search);
const relayParam = params.get("relay");
const broadcastParam = params.get("broadcast");
const trackParam = params.get("track") ?? "audio/data";
const catalogTrack = params.get("catalogTrack") ?? "catalog.json";
const freqHz = Number(params.get("freqHz") ?? "440");
const channels = Number(params.get("channels") ?? "1");
const durationSec = Number(params.get("duration") ?? "5");
const wsPort = Number(params.get("wsPort") ?? "0");
const reconnectAfterMs = Number(params.get("reconnectAfterMs") ?? "0");
const certSha256B64 = params.get("certSha256");

function required(v: string | null, name: string): string {
    if (!v) throw new Error(`publish.html: missing ?${name}=`);
    return v;
}
const relayUrlString = required(relayParam, "relay");
const broadcastName = required(broadcastParam, "broadcast");

const status = (msg: string) => {
    const el = document.getElementById("status");
    if (el) el.textContent = msg;
    console.log("[publish]", msg);
};

const catalogJson = JSON.stringify({
    audio: {
        renditions: {
            [trackParam]: {
                codec: "opus",
                container: { kind: "legacy" },
                sampleRate: 48000,
                numberOfChannels: channels,
                jitter: 20,
            },
        },
    },
});
const catalogBytes = new TextEncoder().encode(catalogJson);

/**
 * Open one moq-lite session + broadcast. Returns the bits the encoder
 * pump needs (Connection + audio Track) plus a `close` to tear it down
 * cleanly when the reconnect cycle fires.
 */
type Session = {
    audioMoqTrack: Moq.Track;
    closeAll: () => void;
};

async function openSession(): Promise<Session> {
    const relayUrl = new URL(relayUrlString);
    status(`connecting to ${relayUrl.toString()}`);
    // serverCertificateHashes pinning per the same comment in listen.ts
    // — Chromium's --ignore-certificate-errors does NOT bypass QUIC
    // cert validation. The test driver passes the SHA-256 of the
    // relay's leaf DER cert via ?certSha256=base64.
    const webtransportOpts: WebTransportOptions = {};
    if (certSha256B64) {
        const raw = Uint8Array.from(atob(certSha256B64), (c) => c.charCodeAt(0));
        webtransportOpts.serverCertificateHashes = [
            { algorithm: "sha-256", value: raw },
        ];
    }
    const conn = await Moq.Connection.connect(relayUrl, {
        websocket: { enabled: false },
        webtransport: webtransportOpts,
    });
    (window as any).__moqVersion = conn.version;
    status(`connected, alpn=${conn.version}`);

    const broadcast = new Moq.Broadcast();
    conn.publish(Moq.Path.from(broadcastName), broadcast);
    status(`announced ${broadcastName}`);

    let audioTrackResolved: Moq.Track | undefined;
    const audioTrackResolver = new Promise<Moq.Track>((resolve) => {
        const probe = setInterval(() => {
            if (audioTrackResolved) {
                clearInterval(probe);
                resolve(audioTrackResolved);
            }
        }, 20);
    });

    // Serve catalog + audio tracks as they're requested by the relay.
    const requestPump = (async () => {
        for (;;) {
            const req = await broadcast.requested();
            if (!req) return;
            if (req.track.name === catalogTrack) {
                const group = req.track.appendGroup();
                group.writeFrame(catalogBytes);
                group.close();
            } else if (req.track.name === trackParam) {
                audioTrackResolved = req.track;
            }
        }
    })().catch((e) => console.error("[publish] requests:", e));

    const audioMoqTrack = await audioTrackResolver;

    const closeAll = () => {
        try {
            broadcast.close();
        } catch (_) {
            // ignore
        }
        try {
            conn.close();
        } catch (_) {
            // ignore
        }
        // requestPump exits on its own once broadcast.requested()
        // returns null after broadcast.close().
        void requestPump;
    };

    return { audioMoqTrack, closeAll };
}

async function main() {
    let ws: WebSocket | undefined;
    if (wsPort) {
        ws = new WebSocket(`ws://127.0.0.1:${wsPort}/pcm`);
        await new Promise<void>((resolve) => {
            ws!.addEventListener("open", () => resolve(), { once: true });
            ws!.addEventListener("error", () => resolve(), { once: true });
        });
    }
    const sendDone = () => {
        if (ws?.readyState === WebSocket.OPEN) ws.send("done");
    };

    // Open the FIRST session.
    let session = await openSession();

    // -- Audio source pump (single source across reconnect cycles) -----
    // Sine osc → MediaStreamAudioDestinationNode → MediaStreamTrack →
    // MediaStreamTrackProcessor → AudioData. The osc + processor
    // SURVIVE a reconnect — only the moq-lite Producer (which writes
    // to the per-cycle session's track) is rebuilt.
    const ctx = new AudioContext({ sampleRate: 48_000, latencyHint: "interactive" });
    await ctx.resume();
    const osc = ctx.createOscillator();
    osc.frequency.value = freqHz;
    osc.type = "sine";
    const dst = ctx.createMediaStreamDestination();
    // The destination's channelCount defaults to 2 (stereo); pin it
    // to whatever the test configured so the AudioEncoder's
    // `numberOfChannels` matches what AudioData carries. Mismatch
    // surfaces as `EncodingError: Input audio buffer is incompatible
    // with codec parameters` and immediately closes the codec.
    dst.channelCount = channels;
    dst.channelCountMode = "explicit";
    dst.channelInterpretation = "speakers";
    osc.connect(dst);
    osc.start();

    const audioTrack = dst.stream.getAudioTracks()[0];
    // @ts-expect-error MediaStreamTrackProcessor is Chrome-only
    const processor = new MediaStreamTrackProcessor({ track: audioTrack });
    const reader = (processor.readable as ReadableStream<AudioData>).getReader();

    // Producer is rebuilt on each reconnect cycle.
    let producer = new Container.Legacy.Producer(session.audioMoqTrack);
    let producerStarted = false;
    let cycleId = 0;

    const encoder = new AudioEncoder({
        output: (chunk, _meta) => {
            const data = new Uint8Array(chunk.byteLength);
            chunk.copyTo(data);
            // Force a new group at each cycle's start so the first
            // post-reconnect frame is a keyframe — Container.Legacy
            // requires it. `producerStarted` tracks per-producer.
            const isKey = !producerStarted;
            producerStarted = true;
            try {
                producer.encode(data, chunk.timestamp as any, isKey);
            } catch (e) {
                // The producer can throw if the underlying session
                // closed mid-encode (we're between cycles). Swallow
                // — the next encoded chunk lands on the new producer.
                console.warn("[publish] encoder.output: producer.encode threw", e);
            }
        },
        error: (e) => console.error("[publish] AudioEncoder", e),
    });
    encoder.configure({
        codec: "opus",
        sampleRate: 48_000,
        numberOfChannels: channels,
        bitrate: 32_000,
    });

    document.body.dataset.state = "publishing";
    status("publishing");

    // -- Reconnect scheduler (optional) --------------------------------
    // If reconnectAfterMs > 0, fire ONCE at that mark to cycle the
    // session. We schedule one-shot — the test only needs to assert
    // the listener recovers across a single Announce::Ended → Active.
    let reconnectFired = false;
    const reconnectScheduler = (async () => {
        if (reconnectAfterMs <= 0) return;
        await new Promise((r) => setTimeout(r, reconnectAfterMs));
        if (reconnectFired) return;
        reconnectFired = true;
        cycleId += 1;
        status(`reconnect cycle ${cycleId}: closing session`);
        const oldSession = session;
        // Close the current session first so the relay sees
        // Announce::Ended cleanly. Then open a fresh one.
        oldSession.closeAll();
        try {
            session = await openSession();
        } catch (e) {
            console.error("[publish] reconnect openSession failed", e);
            return;
        }
        producer = new Container.Legacy.Producer(session.audioMoqTrack);
        producerStarted = false;
        status(`reconnect cycle ${cycleId}: published fresh session`);
        (window as any).__publishCycle = cycleId;
    })();

    // -- Encoder feed loop --------------------------------------------
    const deadline = performance.now() + durationSec * 1000;
    let framesIn = 0;
    while (performance.now() < deadline) {
        const { done, value } = await reader.read();
        if (done || !value) break;
        try {
            encoder.encode(value);
            framesIn++;
        } finally {
            value.close();
        }
    }
    status(`flushing, framesIn=${framesIn}, cycles=${cycleId}`);

    try {
        await encoder.flush();
    } catch (e) {
        console.warn("[publish] flush:", e);
    }
    encoder.close();
    osc.stop();
    audioTrack.stop();
    try {
        producer.close();
    } catch (_) {
        // ignore
    }
    session.closeAll();
    sendDone();
    void reconnectScheduler;

    document.body.dataset.state = "done";
    (window as any).__framesIn = framesIn;
    (window as any).__publishCycle = cycleId;
    status(`done. framesIn=${framesIn}, cycles=${cycleId}`);
}

main().catch((e) => {
    console.error("[publish] fatal:", e);
    document.body.dataset.state = "error";
    status(`ERROR: ${e?.stack ?? e}`);
});
