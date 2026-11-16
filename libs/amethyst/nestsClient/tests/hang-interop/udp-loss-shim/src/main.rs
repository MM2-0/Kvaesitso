//! udp-loss-shim — UDP loopback that drops a configurable fraction
//! of datagrams. Used by the I9 packet-loss cross-stack interop
//! scenario. See
//! `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`.
//!
//! Topology:
//!
//!   client → `--listen <addr>` (this binary) → `--upstream <addr>` (moq-relay)
//!
//! The shim picks one client (the first peer that sends to its
//! listen socket), forwards datagrams in both directions
//! 1:1 modulo the loss roll, and exits when the parent test
//! kills it. moq-lite is on QUIC which is connection-multiplexed
//! by the client's source port, so single-tenant forwarding is
//! enough for the test scenarios.

use std::net::SocketAddr;
use std::sync::Arc;

use anyhow::{Context, Result};
use clap::Parser;
use tokio::net::UdpSocket;
use tokio::sync::Mutex;

#[derive(Parser, Debug)]
#[command(name = "udp-loss-shim", about = "UDP loopback with configurable packet loss")]
struct Args {
    /// Address to listen on (the client connects here).
    #[arg(long)]
    listen: String,

    /// Upstream address to forward to (moq-relay's UDP port).
    #[arg(long)]
    upstream: String,

    /// Fraction of datagrams to drop, 0.0–1.0. Applied independently
    /// to each direction.
    #[arg(long, default_value_t = 0.0)]
    loss_rate: f32,
}

#[tokio::main]
async fn main() -> Result<()> {
    let _ = tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .with_writer(std::io::stderr)
        .try_init();

    let args = Args::parse();
    anyhow::ensure!(
        (0.0..=1.0).contains(&args.loss_rate),
        "loss-rate must be in 0.0..=1.0, got {}",
        args.loss_rate
    );

    let listen_addr: SocketAddr = args.listen.parse().context("parse --listen")?;
    let upstream_addr: SocketAddr = args.upstream.parse().context("parse --upstream")?;

    // Listen socket — accepts datagrams from the client.
    let listen_sock = Arc::new(
        UdpSocket::bind(listen_addr)
            .await
            .with_context(|| format!("bind --listen {listen_addr}"))?,
    );
    // Upstream socket — talks to moq-relay. Bound to an ephemeral
    // port; relay's outbound path back replies to whatever source
    // port we picked.
    let upstream_sock = Arc::new(
        UdpSocket::bind(SocketAddr::from(([127, 0, 0, 1], 0)))
            .await
            .context("bind upstream socket")?,
    );
    upstream_sock
        .connect(upstream_addr)
        .await
        .with_context(|| format!("connect upstream {upstream_addr}"))?;

    tracing::info!(
        listen = %listen_addr,
        upstream = %upstream_addr,
        loss_rate = args.loss_rate,
        "udp-loss-shim ready"
    );

    // Track the client's source address. moq-lite's QUIC client
    // doesn't use connection migration in our test setup, so the
    // first peer that sends to us is the only client we care about.
    let client_addr: Arc<Mutex<Option<SocketAddr>>> = Arc::new(Mutex::new(None));

    // Direction 1: client → upstream (with loss).
    let loss = args.loss_rate;
    let listen_clone = listen_sock.clone();
    let upstream_clone = upstream_sock.clone();
    let client_clone = client_addr.clone();
    tokio::spawn(async move {
        let mut buf = [0u8; 65_535];
        loop {
            let (n, src) = match listen_clone.recv_from(&mut buf).await {
                Ok(v) => v,
                Err(e) => {
                    tracing::warn!(%e, "listen recv error; exiting");
                    return;
                }
            };
            // Latch the client address on first packet.
            {
                let mut c = client_clone.lock().await;
                if c.is_none() {
                    tracing::info!(%src, "client latched");
                    *c = Some(src);
                }
            }
            if rand::random::<f32>() < loss {
                tracing::trace!(bytes = n, %src, "drop client→upstream");
                continue;
            }
            if let Err(e) = upstream_clone.send(&buf[..n]).await {
                tracing::warn!(%e, "upstream send failed");
            }
        }
    });

    // Direction 2: upstream → client (with loss).
    let loss = args.loss_rate;
    let upstream_clone = upstream_sock.clone();
    let listen_clone = listen_sock.clone();
    let client_clone = client_addr.clone();
    let mut buf = [0u8; 65_535];
    loop {
        let n = match upstream_clone.recv(&mut buf).await {
            Ok(v) => v,
            Err(e) => {
                tracing::warn!(%e, "upstream recv error; exiting");
                return Ok(());
            }
        };
        if rand::random::<f32>() < loss {
            tracing::trace!(bytes = n, "drop upstream→client");
            continue;
        }
        let dst = match *client_clone.lock().await {
            Some(addr) => addr,
            None => {
                tracing::trace!(bytes = n, "upstream sent before client latched; ignoring");
                continue;
            }
        };
        if let Err(e) = listen_clone.send_to(&buf[..n], dst).await {
            tracing::warn!(%e, %dst, "listen send_to failed");
        }
    }
}
