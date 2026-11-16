//! hang-listen — reference moq-lite / hang audio listener.
//!
//! Used by the Amethyst cross-stack interop test harness to verify
//! that an Amethyst Kotlin speaker is intelligible to the canonical
//! `kixelated/moq` listener stack. See
//! `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`.
//!
//! Wire path:
//!   1. Connect via `web-transport-quinn` over QUIC.
//!   2. Open a `moq-lite-03` session (via moq_native::ClientConfig).
//!   3. Subscribe to the hang Catalog track at `<broadcast>/catalog.json`.
//!   4. Pick the first audio rendition with `codec="opus"` and
//!      `container.kind="legacy"`.
//!   5. Subscribe to that rendition's track via
//!      `moq_mux::container::Consumer<Hang::Legacy>`.
//!   6. For each frame: decode Opus → Float32 PCM, write to stdout
//!      or `--output-pcm` as raw little-endian f32s.

use std::io::Write;
use std::path::PathBuf;
use std::time::Duration;

use anyhow::{Context, anyhow};
use clap::Parser;
use hang::catalog::{AudioCodec, Container};

const SAMPLE_RATE_HZ: u32 = 48_000;
/// 120 ms at 48 kHz — Opus's worst-case frame size; pre-allocate
/// once and let `Decoder::decode` write what it actually decoded.
const MAX_PCM_PER_PACKET: usize = (SAMPLE_RATE_HZ as usize) / 1000 * 120;

#[derive(Parser, Debug)]
#[command(
    name = "hang-listen",
    about = "Reference moq-lite / hang audio listener for cross-stack interop"
)]
struct Args {
    /// HTTPS URL of the relay, e.g. `https://127.0.0.1:34721`.
    #[arg(long)]
    relay_url: String,

    /// Optional JWT for the `?jwt=` query string. The Amethyst test
    /// harness configures the relay with `--auth-public ""`, in which
    /// case this can be omitted.
    #[arg(long)]
    jwt: Option<String>,

    /// Broadcast namespace. The full path is `<broadcast>/<track>`.
    #[arg(long)]
    broadcast: String,

    /// Maximum runtime in seconds.
    #[arg(long, default_value_t = 5)]
    duration: u64,

    /// Output Float32 little-endian PCM here. Use `-` for stdout.
    /// If absent, the binary discards PCM (used as a smoke test).
    #[arg(long)]
    output_pcm: Option<String>,

    /// Dump the first audio frame's raw bytes (the post-Hang::Legacy
    /// payload — already stripped of the moq-lite frame size prefix
    /// but NOT the hang VarInt timestamp prefix) to this path. Used
    /// by I11 to assert the publisher isn't shipping
    /// `OpusHead\\1\\1...` Codec-Specific-Data as the first audio
    /// frame (the T8 regression in the audit branch).
    #[arg(long)]
    dump_first_frame: Option<String>,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // rustls 0.23 requires an explicit crypto-provider install.
    // Mirror moq-relay's main.rs choice (aws-lc-rs).
    let _ = rustls::crypto::aws_lc_rs::default_provider().install_default();

    // Init logger early so config / handshake errors surface.
    let _ = tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .with_writer(std::io::stderr)
        .try_init();

    let args = Args::parse();

    let result = tokio::time::timeout(
        Duration::from_secs(args.duration + 5),
        run(args),
    )
    .await
    .context("hang-listen wallclock timeout")?;

    result
}

async fn run(args: Args) -> anyhow::Result<()> {
    let url = build_url(&args.relay_url, &args.broadcast, args.jwt.as_deref())?;

    // moq-lite-03 ALPN, IPv4 client bind (sandbox friendly), and TLS
    // verification disabled so the test harness's --tls-generate
    // cert chain works without a custom truststore.
    let cfg = moq_native::ClientConfig::parse_from([
        "hang-listen",
        "--client-bind",
        "127.0.0.1:0",
        "--client-version",
        "moq-lite-03",
        "--tls-disable-verify=true",
    ]);
    let client = cfg.init().context("init moq client")?;

    // Set up an Origin so the session can publish incoming
    // broadcasts to us, then drive both the session and the
    // subscribe loop in parallel.
    let origin = moq_lite::Origin::produce();
    let consumer = origin.consume();

    let session_url = url.clone();
    let session = tokio::spawn(async move {
        // Use reconnect() with a tight timeout so the test exits
        // quickly when the relay drops us. closed() returns when the
        // backoff loop finally gives up.
        let reconnect = client.with_consume(origin).reconnect(session_url);
        if let Err(err) = reconnect.closed().await {
            tracing::warn!(%err, "reconnect loop exited");
        }
    });

    let listen_result = listen(
        consumer,
        args.output_pcm.as_deref(),
        args.dump_first_frame.as_deref(),
        args.duration,
    )
    .await;

    // The session task will exit on its own when the URL closes; we
    // don't need to abort it for a clean shutdown.
    drop(session);

    listen_result
}

async fn listen(
    mut origin: moq_lite::OriginConsumer,
    output_pcm: Option<&str>,
    output_dump_first_frame: Option<&str>,
    duration_sec: u64,
) -> anyhow::Result<()> {
    // Open the PCM sink up front so we fail fast on a bad path.
    let mut pcm: Box<dyn Write + Send> = match output_pcm {
        Some("-") => Box::new(std::io::stdout()),
        Some(path) => Box::new(
            std::fs::File::create(PathBuf::from(path))
                .with_context(|| format!("create output-pcm file '{path}'"))?,
        ),
        None => Box::new(std::io::sink()),
    };

    // Wait for the broadcast to be announced. The relay forwards
    // any matching broadcast across the configured namespace.
    let (path, broadcast) = origin
        .announced()
        .await
        .ok_or_else(|| anyhow!("origin closed before any broadcast announced"))?;
    let broadcast = broadcast.ok_or_else(|| anyhow!("broadcast unannounced: {path}"))?;
    tracing::info!(%path, "broadcast announced");

    // Subscribe to the catalog and read the first published
    // version. The naïve "subscribe → next() with timeout → on
    // timeout resubscribe" pattern is broken on moq-rs 0.10.x:
    //
    //   - When the `TrackConsumer` is dropped, moq-rs's track
    //     producer side observes `track.unused()` and aborts the
    //     wire subscribe with `Error::Cancel`, which maps to
    //     stream-reset code **0** (per `moq-lite/src/error.rs`).
    //   - Code 0 is broadcast to any consumer that calls
    //     `subscribe_track` for the SAME track name within the
    //     race window — the new consumer's `.next()` resolves
    //     immediately with `cancelled`, producing the cascade
    //     `subscribe cancelled id=0 → subscribe error id=1 code=0
    //     → subscribe_track failed: cancelled` we observed.
    //
    // Fix: hold ONE subscription open for the full retry budget.
    // Inner timeouts on `.next()` poll for the first published
    // group; an outer timeout caps the total wait. The track
    // consumer stays alive across inner iterations, so moq-rs
    // never sees `track.unused()` and never propagates `Cancel`.
    //
    // The Amethyst speaker's `onNewSubscriber` hook fires once
    // per inbound SUBSCRIBE (see `MoqLiteNestsSpeaker.kt`'s
    // `setOnNewSubscriber`). With one long-lived subscribe, we
    // get one hook fire, and however long the speaker takes to
    // respond — under accumulated relay state, packet-loss
    // shim-induced re-transmits, or other test-side timing
    // pressure — we still see the first published group as long
    // as it arrives within the budget.
    //
    // Total budget: 10 s. Within every scenario's broadcast
    // window (the shortest is `subscribe_drop_for_unknown_track`
    // at 5 s, but that test asserts a SubscribeDrop and never
    // reaches this path).
    let catalog_track = broadcast
        .subscribe_track(&hang::Catalog::default_track())
        .context("subscribe catalog")?;
    let mut catalog = hang::CatalogConsumer::new(catalog_track);
    let info = match tokio::time::timeout(Duration::from_secs(10), async {
        loop {
            match catalog.next().await {
                Ok(Some(c)) => return Ok::<hang::Catalog, anyhow::Error>(c),
                Ok(None) => {
                    return Err(anyhow!("catalog ended before first publish"));
                }
                Err(e) => return Err(anyhow::Error::new(e).context("read catalog")),
            }
        }
    })
    .await
    {
        Ok(Ok(c)) => c,
        Ok(Err(e)) => return Err(e.context("catalog read")),
        Err(_) => return Err(anyhow!("catalog read timed out after 10 s")),
    };

    // Pick the first Opus / Container::Legacy audio rendition.
    let (track_name, audio_cfg) = info
        .audio
        .renditions
        .iter()
        .find(|(_, cfg)| matches!(cfg.codec, AudioCodec::Opus) && cfg.container == Container::Legacy)
        .ok_or_else(|| {
            anyhow!(
                "no audio rendition with codec=opus container.kind=legacy in catalog: {:?}",
                info.audio.renditions.keys().collect::<Vec<_>>()
            )
        })?;

    // Audio renditions advertise `numberOfChannels` (1 for mono, 2 for
    // stereo). nests speakers send mono; stereo is exercised by I4.
    let channels = match audio_cfg.channel_count {
        1 => opus::Channels::Mono,
        2 => opus::Channels::Stereo,
        n => anyhow::bail!("unsupported channel count: {n}"),
    };
    tracing::info!(
        track = %track_name,
        sample_rate = audio_cfg.sample_rate,
        channels = audio_cfg.channel_count,
        "subscribing to audio rendition"
    );

    let track = moq_lite::Track {
        name: track_name.clone(),
        priority: 1,
    };
    let track_consumer = broadcast.subscribe_track(&track).context("subscribe audio")?;
    let mut frames = moq_mux::hang::Consumer::new(track_consumer, moq_mux::hang::Legacy)
        // Zero latency = aggressive skip. We prefer a more forgiving
        // budget so jitter doesn't drop frames in tests.
        .with_latency(Duration::from_millis(500));

    let mut decoder =
        opus::Decoder::new(audio_cfg.sample_rate, channels).context("init opus decoder")?;
    let mut pcm_buf = vec![0i16; MAX_PCM_PER_PACKET * audio_cfg.channel_count as usize];

    let dump_first_frame_path = output_dump_first_frame.map(PathBuf::from);
    let deadline = tokio::time::Instant::now() + Duration::from_secs(duration_sec);
    let mut total_samples: u64 = 0;
    let mut frame_count: u64 = 0;
    loop {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        if remaining.is_zero() {
            break;
        }
        let frame = match tokio::time::timeout(remaining, frames.read()).await {
            Ok(Ok(Some(f))) => f,
            Ok(Ok(None)) => {
                tracing::info!("track ended");
                break;
            }
            Ok(Err(e)) => {
                // A "cancelled" tail-error after we've already
                // collected frames is just the publisher closing
                // its side of the broadcast — treat it as a
                // normal end-of-stream rather than failing the
                // whole run. Test scripts assert against the PCM
                // file size + content, not the exit code's
                // distinction between graceful-end and
                // publisher-cancel.
                if frame_count > 0 {
                    tracing::info!(error = %e, "track cancelled after {frame_count} frames; treating as EOF");
                    break;
                }
                return Err(anyhow::Error::new(e).context("read audio frame"));
            }
            Err(_) => {
                tracing::info!("duration elapsed");
                break;
            }
        };

        // First-frame capture for I11. payload is the post-
        // Container::Legacy-strip codec payload (i.e. the raw
        // Opus packet, no timestamp prefix). If the publisher
        // accidentally ships `OpusHead\1\1...` Codec-Specific-Data
        // as the first audio frame, this is where it shows up.
        if frame_count == 0 {
            if let Some(path) = dump_first_frame_path.as_ref() {
                std::fs::write(path, frame.payload.as_ref())
                    .with_context(|| format!("write dump-first-frame to '{}'", path.display()))?;
            }
        }

        // payload is the raw Opus packet — the timestamp varint has
        // already been stripped by `Hang::Legacy` decoding.
        let n = decoder
            .decode(&frame.payload, &mut pcm_buf, false)
            .with_context(|| format!("decode opus packet ({} bytes)", frame.payload.len()))?;

        // n is the number of samples per channel; total interleaved
        // samples in pcm_buf is n * channels.
        let interleaved = n * audio_cfg.channel_count as usize;
        for s in &pcm_buf[..interleaved] {
            // i16 → f32 in [-1, 1].
            let f = (*s as f32) / 32_768.0;
            pcm.write_all(&f.to_le_bytes())
                .context("write pcm sample")?;
        }
        total_samples += interleaved as u64;
        frame_count += 1;
    }

    pcm.flush().ok();
    tracing::info!(frames = frame_count, samples = total_samples, "hang-listen done");
    Ok(())
}

fn build_url(relay_url: &str, broadcast: &str, jwt: Option<&str>) -> anyhow::Result<url::Url> {
    let trimmed = relay_url.trim_end_matches('/');
    let raw = if let Some(jwt) = jwt {
        format!("{trimmed}/{broadcast}?jwt={jwt}")
    } else {
        format!("{trimmed}/{broadcast}")
    };
    url::Url::parse(&raw).with_context(|| format!("malformed relay/broadcast url: {raw}"))
}
