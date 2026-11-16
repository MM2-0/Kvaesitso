//! hang-publish — reference moq-lite / hang audio publisher.
//!
//! Opens a broadcast at `<relay>/<broadcast>`, publishes a hang
//! catalog with one Opus / Container::Legacy audio rendition, and
//! pumps Opus-encoded sine-wave frames in groups of 5 for
//! `--duration` seconds. Used by the cross-stack interop tests for
//! the Rust → Amethyst direction. See
//! `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`.

use std::io::Write;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::{Context, anyhow};
use bytes::Bytes;
use clap::Parser;
use hang::catalog::{Audio, AudioCodec, AudioConfig, Catalog, Container};

const SAMPLE_RATE_HZ: u32 = 48_000;
/// 20 ms at 48 kHz — same frame size Amethyst speakers send.
const FRAME_SIZE_SAMPLES: usize = 960;
/// Microseconds per frame: 20_000 = 1_000_000 * 960 / 48_000.
const FRAME_DURATION_US: u64 = 20_000;
/// 5 frames per group → 100 ms group cadence, matching nests speaker.
const FRAMES_PER_GROUP: usize = 5;
/// Default audio rendition track name in the catalog. Amethyst's
/// listener subscribes to `audio/data` per `MoqLiteNestsListener.AUDIO_TRACK`,
/// so that's what we ship by default. Override via `--track-name`.
const DEFAULT_TRACK_NAME: &str = "audio/data";

#[derive(Parser, Debug)]
#[command(
    name = "hang-publish",
    about = "Reference moq-lite / hang audio publisher for cross-stack interop"
)]
struct Args {
    /// HTTPS URL of the relay, e.g. `https://127.0.0.1:34721`.
    #[arg(long)]
    relay_url: String,

    /// Optional JWT for the `?jwt=` query string.
    #[arg(long)]
    jwt: Option<String>,

    /// Broadcast namespace (path under the relay root).
    #[arg(long)]
    broadcast: String,

    /// Sine-wave frequency in Hz. Used as the default for every
    /// channel; override per-channel via `--freq-hz-l` / `--freq-hz-r`.
    #[arg(long, default_value_t = 440)]
    freq_hz: u32,

    /// Per-channel frequency override for the LEFT channel. Falls
    /// back to `--freq-hz` when unset.
    #[arg(long)]
    freq_hz_l: Option<u32>,

    /// Per-channel frequency override for the RIGHT channel.
    /// Ignored when `--channels 1`. Falls back to `--freq-hz` when
    /// unset.
    #[arg(long)]
    freq_hz_r: Option<u32>,

    /// Maximum runtime in seconds.
    #[arg(long, default_value_t = 5)]
    duration: u64,

    /// Channel count: 1 (mono) or 2 (stereo). With `2` and
    /// `--freq-hz-l` / `--freq-hz-r` set, the L/R channels carry
    /// independent tones — useful for the I4 stereo cross-stack
    /// scenario.
    #[arg(long, default_value_t = 1)]
    channels: u32,

    /// Audio rendition track name. Default `audio/data` matches
    /// Amethyst's `MoqLiteNestsListener.AUDIO_TRACK`. Override for
    /// custom interop scenarios.
    #[arg(long, default_value_t = DEFAULT_TRACK_NAME.to_string())]
    track_name: String,

    /// If non-zero, drop the active session at this many ms into the
    /// broadcast and re-announce on a fresh session. Mirrors the
    /// behaviour the Amethyst reconnecting speaker exhibits during
    /// JWT refresh: the publisher unannounces, opens a new
    /// transport, and re-announces the same broadcast path so any
    /// listener with a re-issuance pump can pick up where it left
    /// off. Used by the I7 cross-stack interop scenario to
    /// exercise the Kotlin listener's publisher-cycle handling.
    #[arg(long, default_value_t = 0)]
    reconnect_after_ms: u64,

    /// If set, emit one line per frame to stdout, immediately before
    /// the frame is handed to the moq-lite group producer:
    ///
    ///   `SEND frame=<N> send_t_us=<UNIX_EPOCH_MICROS>`
    ///
    /// Used by `AudioLatencyComparisonTest` to pair publisher send
    /// time with the listener's arrival time and compute one-way
    /// latency. `send_t_us` is the wall-clock microseconds since
    /// `UNIX_EPOCH` (CLOCK_REALTIME on linux / macOS), which matches
    /// what the JVM's `Instant.now()` reads on the listener side —
    /// the two clocks share the same source when both processes run
    /// on the same host.
    ///
    /// Disabled by default so existing interop scenarios (I7, etc.)
    /// keep their plain stderr-only RUST_LOG output and don't have
    /// to filter through 50 frame-tag lines per second on stdout.
    #[arg(long, default_value_t = false)]
    log_send_times: bool,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // rustls 0.23 requires an explicit crypto-provider install.
    let _ = rustls::crypto::aws_lc_rs::default_provider().install_default();

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
    .context("hang-publish wallclock timeout")?;

    result
}

async fn run(args: Args) -> anyhow::Result<()> {
    let url = build_url(&args.relay_url, args.jwt.as_deref())?;

    let cfg = moq_native::ClientConfig::parse_from([
        "hang-publish",
        "--client-bind",
        "127.0.0.1:0",
        "--client-version",
        "moq-lite-03",
        "--tls-disable-verify=true",
    ]);
    let client = cfg.init().context("init moq client")?;

    let total_frames = (args.duration * 1_000_000 / FRAME_DURATION_US) as usize;
    let channels = match args.channels {
        1 => opus::Channels::Mono,
        2 => opus::Channels::Stereo,
        n => anyhow::bail!("unsupported channel count: {n}"),
    };
    let mut encoder = opus::Encoder::new(SAMPLE_RATE_HZ, channels, opus::Application::Audio)
        .context("init opus encoder")?;
    encoder
        .set_bitrate(opus::Bitrate::Bits(32_000))
        .context("set opus bitrate")?;

    // Per-channel phase step: each channel may have its own
    // frequency (I4 stereo). Defaults to args.freq_hz on every
    // channel.
    let mut phase_steps: Vec<f64> = Vec::with_capacity(args.channels as usize);
    for ch in 0..(args.channels as usize) {
        let f = match (ch, args.freq_hz_l, args.freq_hz_r) {
            (0, Some(l), _) => l,
            (1, _, Some(r)) => r,
            _ => args.freq_hz,
        };
        phase_steps
            .push(2.0_f64 * std::f64::consts::PI * (f as f64) / (SAMPLE_RATE_HZ as f64));
    }

    // Cross-cycle pump state. `frame_no` is the absolute frame index
    // since broadcast start (used as the legacy timestamp), so on a
    // mid-broadcast reconnect the new session continues monotonically
    // from where the old one left off. `sample_idx` likewise advances
    // across cycles so the per-channel sine wave keeps its phase
    // continuous — a regression that resets the phase manifests as
    // an audible click at the reconnect point on the listener side.
    let mut frame_no: usize = 0;
    let mut sample_idx: u64 = 0;
    // Group sequences must restart at 0 on each fresh broadcast.
    // moq-lite treats group sequences as broadcast-scoped, and the
    // re-announced broadcast is a brand-new producer-side instance
    // — so reset to 0 in each cycle below.

    let mut next_send = tokio::time::Instant::now();

    let reconnect_at_frame = if args.reconnect_after_ms > 0 {
        Some((args.reconnect_after_ms * 1_000 / FRAME_DURATION_US) as usize)
    } else {
        None
    };

    let mut cycle_idx: usize = 0;
    while frame_no < total_frames {
        cycle_idx += 1;
        // Set up a fresh origin → consumer pair for this cycle.
        // Dropping the previous Reconnect handle aborts its background
        // tokio task; dropping the prior origin causes the previous
        // session's broadcast to unannounce. On the listener side this
        // surfaces as Announce::Ended, the audio frames flow
        // completes, and the consumer's re-issuance pump fires a
        // fresh subscribe against the next-announced broadcast.
        let origin = moq_lite::Origin::produce();
        let publish_consumer = origin.consume();
        let session_url = url.clone();
        let session_client = client.clone();
        let _reconnect = session_client
            .with_publish(publish_consumer)
            .reconnect(session_url);

        // Stop the cycle either at total_frames or the reconnect
        // boundary, whichever comes first.
        let cycle_end = match reconnect_at_frame {
            Some(reconnect_frame) if cycle_idx == 1 && reconnect_frame < total_frames => {
                reconnect_frame
            }
            _ => total_frames,
        };

        tracing::info!(
            cycle = cycle_idx,
            from_frame = frame_no,
            until_frame = cycle_end,
            "publish cycle starting"
        );

        let outcome = publish_cycle(
            &origin,
            &args,
            &mut encoder,
            &phase_steps,
            &mut frame_no,
            &mut sample_idx,
            &mut next_send,
            cycle_end,
        )
        .await;
        // Drop the reconnect handle + origin BEFORE bubbling the
        // result so the relay sees the unannounce promptly. _reconnect
        // is dropped at scope-end which aborts its task; origin is
        // dropped a moment later when this iteration's stack frame
        // unwinds. Without explicitly ordering the drops, the next
        // cycle's `with_publish` call would race the previous
        // session's tear-down and the listener could see a stale
        // Active before the Ended.
        drop(_reconnect);
        drop(origin);
        outcome?;

        // Brief settling delay so the listener observes a clean
        // Ended → Active transition rather than two overlapping
        // Actives. ~50 ms is plenty for moq-relay 0.10.x's
        // announce-watch fan-out without being audibly long.
        if frame_no < total_frames {
            tokio::time::sleep(Duration::from_millis(50)).await;
            // The fresh cycle's pacing anchor must restart from
            // "now" — otherwise the publisher would try to catch up
            // by sending a burst of frames at full speed, which
            // confuses the listener's group-cadence assumptions.
            next_send = tokio::time::Instant::now();
        }
    }

    tracing::info!(
        frames = total_frames,
        cycles = cycle_idx,
        "hang-publish done"
    );
    Ok(())
}

/// Publish one cycle's worth of audio frames into the relay through
/// `origin`, advancing `frame_no` / `sample_idx` / `next_send` in
/// place. Stops at `cycle_end` (exclusive). Catalog + audio_track
/// are created fresh per cycle since they're owned by the cycle's
/// origin and would unannounce on origin drop anyway.
#[allow(clippy::too_many_arguments)]
async fn publish_cycle(
    origin: &moq_lite::OriginProducer,
    args: &Args,
    encoder: &mut opus::Encoder,
    phase_steps: &[f64],
    frame_no: &mut usize,
    sample_idx: &mut u64,
    next_send: &mut tokio::time::Instant,
    cycle_end: usize,
) -> anyhow::Result<()> {
    let mut broadcast = origin
        .create_broadcast(args.broadcast.as_str())
        .ok_or_else(|| anyhow!("broadcast '{}' not allowed by origin", args.broadcast))?;

    // 1. Catalog track. We declare one Opus rendition; the JSON
    //    payload mirrors what Amethyst's MoqLiteHangCatalog produces.
    let mut catalog_track = broadcast
        .create_track(hang::Catalog::default_track())
        .context("create catalog track")?;

    let mut renditions = std::collections::BTreeMap::new();
    renditions.insert(
        args.track_name.clone(),
        AudioConfig {
            codec: AudioCodec::Opus,
            sample_rate: SAMPLE_RATE_HZ,
            channel_count: args.channels,
            bitrate: Some(32_000),
            description: None,
            container: Container::Legacy,
            jitter: None,
        },
    );
    let catalog = Catalog {
        audio: Audio { renditions },
        ..Default::default()
    };
    let catalog_json =
        serde_json::to_vec(&catalog).context("serialize catalog json")?;

    let mut catalog_group = catalog_track
        .create_group(moq_lite::Group { sequence: 0 })
        .context("create catalog group")?;
    catalog_group
        .write_frame(catalog_json)
        .context("publish catalog frame")?;
    catalog_group.finish().ok();

    // 2. Audio track.
    let mut audio_track = broadcast
        .create_track(moq_lite::Track {
            name: args.track_name.clone(),
            priority: 1,
        })
        .context("create audio track")?;

    let mut opus_buf = vec![0u8; 4_000];
    let mut group_idx: u64 = 0;
    let mut frames_in_group = 0usize;
    let mut group: Option<moq_lite::GroupProducer> = None;

    while *frame_no < cycle_end {
        // Generate one PCM frame, possibly with a different sine
        // tone on each channel.
        let mut pcm = vec![0i16; FRAME_SIZE_SAMPLES * args.channels as usize];
        for i in 0..FRAME_SIZE_SAMPLES {
            let t = (*sample_idx + i as u64) as f64;
            for ch in 0..(args.channels as usize) {
                let v = (t * phase_steps[ch]).sin();
                let s = (v * 16_383.0) as i16;
                pcm[i * args.channels as usize + ch] = s;
            }
        }
        *sample_idx += FRAME_SIZE_SAMPLES as u64;

        let n = encoder
            .encode(&pcm, &mut opus_buf)
            .context("encode opus packet")?;
        let opus_packet = Bytes::copy_from_slice(&opus_buf[..n]);

        // Wrap the Opus packet in a hang Legacy frame: VarInt
        // timestamp prefix + raw codec payload. timestamp continues
        // across cycles so the listener sees a monotonic stream.
        let frame = hang::container::Frame {
            timestamp: hang::container::Timestamp::from_micros(
                (*frame_no as u64) * FRAME_DURATION_US,
            )
            .context("frame timestamp out of range")?,
            payload: opus_packet.into(),
        };

        // Start a new group every FRAMES_PER_GROUP frames. The 5-frame
        // group cadence matches Amethyst's NestMoqLiteBroadcaster default
        // and produces ~100 ms groups.
        if frames_in_group == 0 {
            if let Some(mut g) = group.take() {
                g.finish().ok();
            }
            group = Some(
                audio_track
                    .create_group(moq_lite::Group { sequence: group_idx })
                    .context("create audio group")?,
            );
            group_idx += 1;
        }

        let g = group.as_mut().expect("group always Some after init");
        if args.log_send_times {
            // Read SystemTime AS LATE AS POSSIBLE before the moq-lite
            // group write so the captured `send_t_us` reflects "moment
            // the frame entered the publisher's outbound buffer". The
            // group write is non-blocking (the per-stream send queue
            // does the actual QUIC work asynchronously) so this is the
            // closest deterministic anchor we have to "send time" from
            // application code. Unwrap on the duration_since: the
            // system clock running before UNIX_EPOCH would be a
            // catastrophic environment failure, not a test condition.
            let send_t_us = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .expect("system clock predates UNIX_EPOCH")
                .as_micros();
            // Bypass tracing — RUST_LOG goes to stderr by config and
            // the test parses stdout. Lock + flush per line because
            // Rust's `Stdout` is fully-buffered when not a TTY (which
            // is exactly our case here — `redirectErrorStream(true)`
            // on the JVM side makes stdout a pipe), so a passive
            // `writeln!` would batch frames into ~8 KB chunks and the
            // parser would see them in bursts instead of as a live
            // stream. Per-line flush is cheap (≤ 50 lines/s/publisher).
            let stdout = std::io::stdout();
            let mut handle = stdout.lock();
            let _ = writeln!(
                handle,
                "SEND frame={} send_t_us={}",
                *frame_no, send_t_us
            );
            let _ = handle.flush();
        }
        frame.encode(g).context("encode hang frame into group")?;
        frames_in_group += 1;
        if frames_in_group == FRAMES_PER_GROUP {
            frames_in_group = 0;
        }

        *frame_no += 1;

        let frame_period = Duration::from_micros(FRAME_DURATION_US);
        *next_send += frame_period;
        tokio::time::sleep_until(*next_send).await;
    }

    if let Some(mut g) = group.take() {
        g.finish().ok();
    }
    audio_track.finish().ok();
    catalog_track.finish().ok();
    Ok(())
}

/// Build the WebTransport URL the publisher connects to.
///
/// `relay_url` is taken as the *full* URL the publisher connects to
/// (scheme + authority + optional path). `broadcast` is the relative
/// announce-suffix passed to `Origin::create_broadcast`, NOT appended
/// to the URL. Callers that want the publisher's URL path to also be
/// `broadcast` should pass `--relay-url=<host>/<broadcast>` and
/// `--broadcast=<broadcast>` (the simple Rust↔Rust shape).
fn build_url(relay_url: &str, jwt: Option<&str>) -> anyhow::Result<url::Url> {
    let trimmed = relay_url.trim_end_matches('/');
    let raw = if let Some(jwt) = jwt {
        format!("{trimmed}?jwt={jwt}")
    } else {
        trimmed.to_string()
    };
    url::Url::parse(&raw).with_context(|| format!("malformed relay url: {raw}"))
}
