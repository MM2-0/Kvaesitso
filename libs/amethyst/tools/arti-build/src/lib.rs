use jni::{jni_sig, jni_str, EnvUnowned};
use jni::errors::{LogErrorAndDefault, Result as JniResult, ThrowRuntimeExAndDefault};
use jni::objects::{Global, JClass, JObject, JString};
use jni::sys::jint;
use jni::JavaVM;

use arti_client::{BootstrapBehavior, TorClient};
use arti_client::config::TorClientConfigBuilder;
// `kind()` is a trait method (tor_error::HasKind), not inherent, so the trait has to be in
// scope wherever we classify a connect failure. Both are re-exported by arti-client.
use arti_client::HasKind;
use tor_rtcompat::PreferredRuntime;

use std::sync::{Arc, Mutex, Once};
use std::path::PathBuf;
use anyhow::Result;

// ============================================================================
// Global State
// ============================================================================

static ARTI_CLIENT: Mutex<Option<Arc<TorClient<PreferredRuntime>>>> = Mutex::new(None);
static TOKIO_RUNTIME: Mutex<Option<tokio::runtime::Runtime>> = Mutex::new(None);
static JAVA_VM: Mutex<Option<JavaVM>> = Mutex::new(None);
static LOG_CALLBACK: Mutex<Option<Global<JObject<'static>>>> = Mutex::new(None);
static SOCKS_TASK: Mutex<Option<tokio::task::JoinHandle<()>>> = Mutex::new(None);
// The background directory download started by initialize(). It holds an Arc<TorClient>, so
// destroy() must abort it too — otherwise the client cannot drop, the state file lock is never
// released, and the next initialize() fails.
static BOOTSTRAP_TASK: Mutex<Option<tokio::task::JoinHandle<()>>> = Mutex::new(None);
// Per-connection handler tasks. Tracked so destroy() can abort in-flight handlers
// — otherwise their Arc<TorClient> clones keep the client alive and the
// state file lock would not be released for the next initialize().
static HANDLER_TASKS: Mutex<Vec<tokio::task::JoinHandle<()>>> = Mutex::new(Vec::new());
static INIT_ONCE: Once = Once::new();

// ============================================================================
// Logging
// ============================================================================

fn send_log_to_java(message: String) {
    let vm_opt = JAVA_VM.lock().unwrap();
    let callback_opt = LOG_CALLBACK.lock().unwrap();

    if let (Some(vm), Some(callback)) = (vm_opt.as_ref(), callback_opt.as_ref()) {
        // jni 0.22 only hands out an `Env` inside a closure, borrowed from an
        // attachment pinned to the stack; it also pushes a local-reference frame
        // per call, so `jmessage` is released when the closure returns instead of
        // accumulating on this long-lived logging thread.
        let _ = vm.attach_current_thread(|env| -> JniResult<()> {
            if let Ok(jmessage) = env.new_string(&message) {
                let _ = env.call_method(
                    &**callback,
                    jni_str!("onLogLine"),
                    jni_sig!("(Ljava/lang/String;)V"),
                    &[(&jmessage).into()],
                );
            }
            Ok(())
        });
    }
}

macro_rules! log_info {
    ($($arg:tt)*) => {{
        let msg = format!($($arg)*);
        send_log_to_java(msg);
    }};
}

macro_rules! log_error {
    ($($arg:tt)*) => {{
        let msg = format!("ERROR: {}", format!($($arg)*));
        send_log_to_java(msg);
    }};
}

// ============================================================================
// JNI Functions — package: com.vitorpamplona.amethyst.ui.tor
// ============================================================================

#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_getVersion<'caller>(
    mut env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
) -> JString<'caller> {
    // jni 0.22: the raw environment pointer is FFI-only (`EnvUnowned`); JNI calls
    // need the `Env` that `with_env` borrows for the closure. `resolve` maps an
    // `Err` to the policy — here a Java RuntimeException plus a null return —
    // rather than losing it.
    //
    // Only the `Err` half is live: the policy's panic half runs through
    // `catch_unwind`, which catches nothing under this crate's
    // `panic = "abort"` release profile, so a panic in here still takes the
    // process down exactly as it did before the migration.
    env.with_env(|env| -> JniResult<JString<'caller>> {
        if JAVA_VM.lock().unwrap().is_none() {
            if let Ok(vm) = env.get_java_vm() {
                *JAVA_VM.lock().unwrap() = Some(vm);
            }
        }

        let version = format!("Arti {} (custom build with rustls)", env!("CARGO_PKG_VERSION"));
        env.new_string(version)
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_setLogCallback<'caller>(
    mut env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    callback: JObject<'caller>,
) {
    env.with_env(|env| -> JniResult<()> {
        if JAVA_VM.lock().unwrap().is_none() {
            if let Ok(vm) = env.get_java_vm() {
                *JAVA_VM.lock().unwrap() = Some(vm);
            }
        }

        if let Ok(global_ref) = env.new_global_ref(&callback) {
            *LOG_CALLBACK.lock().unwrap() = Some(global_ref);
            log_info!("Log callback registered");
        }
        Ok(())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

/// Initialize Arti runtime and bootstrap the TorClient.
/// The TorClient is created once and reused for the app's lifetime.
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_initialize<'caller>(
    mut env: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    data_dir: JString<'caller>,
) -> jint {
    // Everything JNI-owned is read inside this closure; the rest of the function
    // is pure Rust that blocks on Tokio, which must not hold an `Env`.
    //
    // `None` carries a failed read, because it is `Option::default()` and so is
    // also what the policy yields for an `Err`. Both end at the same `-1` the
    // old `Err` arm returned. Resolving to `jint` directly would have defaulted
    // to `0`, the value this API reports as success.
    //
    // The already-initialized check deliberately stays *outside* the closure,
    // against the whole `Option`: threading it through as a sentinel value
    // would leave that sentinel to be re-tested after the closure, and a
    // concurrent destroy() landing in between would let it through as the data
    // directory.
    let data_dir_str: Option<String> = env
        .with_env(|env| -> JniResult<Option<String>> {
            if JAVA_VM.lock().unwrap().is_none() {
                if let Ok(vm) = env.get_java_vm() {
                    *JAVA_VM.lock().unwrap() = Some(vm);
                }
            }

            Ok(match data_dir.try_to_string(env) {
                Ok(s) => Some(s),
                Err(e) => {
                    log_error!("Failed to convert data_dir: {:?}", e);
                    None
                }
            })
        })
        .resolve::<LogErrorAndDefault>();

    // Already initialized — skip
    if ARTI_CLIENT.lock().unwrap().is_some() {
        log_info!("Arti already initialized, reusing existing client");
        return 0;
    }

    let data_dir_str: String = match data_dir_str {
        Some(s) => s,
        None => return -1,
    };

    log_info!("Initializing Arti with data directory: {}", data_dir_str);

    INIT_ONCE.call_once(|| {
        // arti-v2.3.0's tor-rtcompat no longer installs a rustls CryptoProvider
        // implicitly — without this, TorClient::create_bootstrapped panics on
        // first TLS handshake. install_default() returns Err if a provider is
        // already installed, which is fine; we just want at-least-one.
        let _ = rustls::crypto::ring::default_provider().install_default();

        match tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .build()
        {
            Ok(rt) => {
                log_info!("Tokio runtime created successfully");
                *TOKIO_RUNTIME.lock().unwrap() = Some(rt);
            }
            Err(e) => {
                log_error!("Failed to create Tokio runtime: {:?}", e);
            }
        }
    });

    let runtime_guard = TOKIO_RUNTIME.lock().unwrap();
    let runtime = match runtime_guard.as_ref() {
        Some(rt) => rt,
        None => {
            log_error!("Tokio runtime not initialized");
            return -2;
        }
    };

    let data_path = PathBuf::from(data_dir_str);
    let cache_dir = data_path.join("cache");
    let state_dir = data_path.join("state");

    std::fs::create_dir_all(&cache_dir).ok();
    std::fs::create_dir_all(&state_dir).ok();

    let outcome: jint = runtime.block_on(async {
        log_info!("Creating Arti client...");

        let mut builder = TorClientConfigBuilder::from_directories(state_dir, cache_dir);

        // Arti's fs-mistrust walks every parent of the state dir and rejects any
        // that has an "unsafe" owner. On Android the app's private filesDir is
        // sandboxed by the OS, so the default strict check is correct. On JVM
        // host runs (TorArtiNativeIntegrationTest) the data dir lives under
        // /tmp and the check trips on container-style ownership of `/` (UID 999
        // etc.). Disable it for non-Android targets — these are the test/dev
        // surface, not a user-facing binary.
        #[cfg(not(target_os = "android"))]
        {
            builder.storage().permissions().dangerously_trust_everyone();
        }

        let config = match builder.build() {
            Ok(c) => c,
            Err(e) => {
                log_error!("Failed to build Tor config: {:?}", e);
                return -3;
            }
        };

        // Create the client WITHOUT waiting for the directory.
        //
        // `create_bootstrapped` used to block this JNI call — and the Kotlin lifecycle lock it
        // holds — for the entire directory download: 12.6-33.8s measured on a cold install. The
        // app treated Tor as absent for all of it, because `activePortOrNull` stays null until
        // status flips to Active, so every relay dial fell back to 127.0.0.1:9050 (the Orbot
        // default) where nothing listens, and failed instantly into backoff.
        //
        // With `BootstrapBehavior::OnDemand` the client is usable the moment it exists and each
        // stream waits for the directory itself. The SOCKS proxy binds right away, so a dial
        // issued mid-download queues on its own circuit instead of failing. Readiness stops being
        // a global gate the app has to poll and becomes a property of individual connections.
        //
        // The `_async` variant is deliberate: it allows a short grace period for the state file
        // lock, which a destroy()/initialize() cycle needs, where the sync one waits not at all.
        let client = match TorClient::builder()
            .config(config)
            .bootstrap_behavior(BootstrapBehavior::OnDemand)
            .create_unbootstrapped_async()
            .await
        {
            // Arti 2.4.0 made every TorClient constructor return an Arc<TorClient>
            // (TorClient itself is no longer Clone), so there is nothing to wrap here.
            Ok(c) => c,
            Err(e) => {
                log_error!("Failed to create Tor client: {:?}", e);
                return -3;
            }
        };

        *ARTI_CLIENT.lock().unwrap() = Some(Arc::clone(&client));

        // Start the download now rather than leaving it for the first stream to trigger, so it
        // overlaps the login screen exactly as it used to, and publish the outcome so Kotlin can
        // move the status from Bootstrapping to Active at the right moment.
        let handle = tokio::spawn(async move {
            let started = std::time::Instant::now();
            match client.bootstrap().await {
                Ok(()) => log_info!(
                    "Arti directory bootstrap complete after {}ms",
                    started.elapsed().as_millis()
                ),
                // Not fatal and deliberately not latched anywhere: with OnDemand the next stream
                // retries the bootstrap on its own, and isBootstrapped() reports live readiness, so
                // a recovery after this point is picked up without us having to model it.
                Err(e) => log_error!(
                    "Arti directory bootstrap failed after {}ms (streams will retry): {:?}",
                    started.elapsed().as_millis(),
                    e
                ),
            }
        });
        if let Some(previous) = BOOTSTRAP_TASK.lock().unwrap().replace(handle) {
            previous.abort();
        }

        log_info!("Arti client created (unbootstrapped; directory downloading in background)");
        0
    });

    if outcome == 0 {
        log_info!("Arti initialized successfully");
    }
    outcome
}

/// Start the SOCKS5 proxy on the specified port.
/// Can be called multiple times — stops any existing listener first.
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_startSocksProxy(
    _env: EnvUnowned,
    _class: JClass,
    port: jint,
) -> jint {
    log_info!("Starting SOCKS proxy on port {}", port);

    // Stop any existing SOCKS server first
    if let Some(handle) = SOCKS_TASK.lock().unwrap().take() {
        log_info!("Aborting previous SOCKS server task");
        handle.abort();
    }

    let client_guard = ARTI_CLIENT.lock().unwrap();
    let client = match client_guard.as_ref() {
        Some(c) => Arc::clone(c),
        None => {
            log_error!("Arti client not initialized — call initialize() first");
            return -1;
        }
    };
    drop(client_guard);

    let runtime_guard = TOKIO_RUNTIME.lock().unwrap();
    let runtime = match runtime_guard.as_ref() {
        Some(rt) => rt,
        None => {
            log_error!("Tokio runtime not initialized");
            return -2;
        }
    };

    let addr = format!("127.0.0.1:{}", port);

    let bind_result = runtime.block_on(async {
        tokio::net::TcpListener::bind(&addr).await
    });

    let listener = match bind_result {
        Ok(l) => {
            log_info!("SOCKS proxy bound to {}", addr);
            l
        }
        Err(e) => {
            log_error!("Failed to bind SOCKS proxy to {}: {:?}", addr, e);
            return -3;
        }
    };

    let handle = runtime.spawn(async move {
        log_info!("Sufficiently bootstrapped; system SOCKS now functional");

        loop {
            match listener.accept().await {
                Ok((stream, _peer_addr)) => {
                    let client_clone = Arc::clone(&client);
                    let h = tokio::spawn(async move {
                        if let Err(e) = handle_socks_connection(stream, client_clone).await {
                            log_error!("SOCKS connection error: {:?}", e);
                        }
                    });
                    let mut handlers = HANDLER_TASKS.lock().unwrap();
                    handlers.retain(|h| !h.is_finished());
                    handlers.push(h);
                }
                Err(e) => {
                    log_error!("Failed to accept SOCKS connection: {:?}", e);
                    break;
                }
            }
        }
    });

    *SOCKS_TASK.lock().unwrap() = Some(handle);
    log_info!("SOCKS proxy started on port {}", port);
    0
}

/// Handle a single SOCKS5 connection through Tor.
/// Maps an Arti connect failure onto the SOCKS5 reply code that means the same thing.
///
/// Why this matters: every failure used to be reported as 0x05 (connection refused), so a
/// domain that no longer exists, an exit that timed out, and a genuinely refused port were
/// indistinguishable to the client. Java's SOCKS client renders 0x05 as
/// `SocketException("SOCKS: Connection refused")`, so the whole failure taxonomy collapsed
/// into one opaque string and the caller could only apply its most generic retry policy.
/// Measured on a cold start: 639 of ~768 relay failures arrived this way.
///
/// The codes below are the ones Java surfaces with distinct messages, which is what lets the
/// caller tell "this relay is gone" from "this circuit had a bad minute":
///   0x01 general failure     -> "SOCKS server general failure"
///   0x02 not allowed         -> "SOCKS: Connection not allowed by ruleset"
///   0x03 network unreachable -> "SOCKS: Network unreachable"
///   0x04 host unreachable    -> "SOCKS: Host unreachable"
///   0x05 connection refused  -> "SOCKS: Connection refused"
///   0x06 TTL expired         -> "SOCKS: TTL expired"
///
/// Note on name-resolution failures: Arti documents `RemoteHostResolutionFailed` as
/// retryable, because an exit's resolver failing is not proof the name is dead. We still map
/// it to 0x04 rather than 0x01, because the caller's response to 0x04 is a bounded backoff,
/// not permanent condemnation — which *is* a retry, just a slower one. Probing the relays
/// that produced this error found 17 of 21 to be NXDOMAIN from a normal resolver, so the
/// conservative reading costs far more than it saves. `RemoteHostNotFound` (the unambiguous
/// case) maps to 0x04 too.
fn socks_reply_for(e: &arti_client::Error) -> u8 {
    use arti_client::ErrorKind::*;

    match e.kind() {
        // The name does not resolve, or the exit could not resolve it.
        RemoteHostNotFound | RemoteHostResolutionFailed => 0x04,
        // The host answered and said no. A real, specific refusal.
        RemoteConnectionRefused => 0x05,
        // The exit refuses this destination by policy; another exit may allow it.
        ExitPolicyRejected => 0x02,
        // No route from the exit.
        RemoteNetworkFailed => 0x03,
        // Timed out — transient by nature, at the exit or against our own threshold.
        ExitTimeout | RemoteNetworkTimeout => 0x06,
        // Anything else stays deliberately vague rather than being mislabelled.
        _ => 0x01,
    }
}

async fn handle_socks_connection(
    mut stream: tokio::net::TcpStream,
    client: Arc<TorClient<PreferredRuntime>>,
) -> Result<()> {
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    let mut buf = [0u8; 512];

    // SOCKS5 handshake: read version + methods
    let n = stream.read(&mut buf).await?;
    if n < 2 {
        return Err(anyhow::anyhow!("Invalid SOCKS handshake"));
    }

    // No auth required
    stream.write_all(&[0x05, 0x00]).await?;

    // Read request
    let n = stream.read(&mut buf).await?;
    if n < 10 {
        return Err(anyhow::anyhow!("Invalid SOCKS request"));
    }

    let version = buf[0];
    let cmd = buf[1];
    let atyp = buf[3];

    if version != 0x05 {
        return Err(anyhow::anyhow!("Unsupported SOCKS version: {}", version));
    }

    if cmd != 0x01 {
        stream.write_all(&[0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
        return Err(anyhow::anyhow!("Unsupported SOCKS command: {}", cmd));
    }

    let (target_host, target_port) = match atyp {
        0x01 => {
            let ip = format!("{}.{}.{}.{}", buf[4], buf[5], buf[6], buf[7]);
            let port = u16::from_be_bytes([buf[8], buf[9]]);
            (ip, port)
        }
        0x03 => {
            let len = buf[4] as usize;
            if n < 5 + len + 2 {
                return Err(anyhow::anyhow!("Invalid domain name length"));
            }
            let domain = String::from_utf8_lossy(&buf[5..5 + len]).to_string();
            let port = u16::from_be_bytes([buf[5 + len], buf[5 + len + 1]]);
            (domain, port)
        }
        0x04 => {
            if n < 22 {
                stream.write_all(&[0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
                return Err(anyhow::anyhow!("Truncated IPv6 request"));
            }
            let ip = format!(
                "{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}:{:02x}{:02x}",
                buf[4], buf[5], buf[6], buf[7], buf[8], buf[9], buf[10], buf[11],
                buf[12], buf[13], buf[14], buf[15], buf[16], buf[17], buf[18], buf[19]
            );
            let port = u16::from_be_bytes([buf[20], buf[21]]);
            (ip, port)
        }
        _ => {
            stream.write_all(&[0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
            return Err(anyhow::anyhow!("Unsupported address type: {}", atyp));
        }
    };

    let tor_stream = match client.connect((target_host.as_str(), target_port)).await {
        Ok(s) => s,
        Err(e) => {
            let reply = socks_reply_for(&e);
            log_error!(
                "Failed to connect through Tor to {}:{}: kind={:?} socks_reply=0x{:02x} {:?}",
                target_host, target_port, e.kind(), reply, e
            );
            stream.write_all(&[0x05, reply, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
            return Err(e.into());
        }
    };

    // SOCKS5 success
    stream.write_all(&[0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;

    // Bidirectional forwarding
    let (mut client_read, mut client_write) = stream.split();
    let (mut tor_read, mut tor_write) = tor_stream.split();

    tokio::select! {
        r = tokio::io::copy(&mut client_read, &mut tor_write) => {
            if let Err(ref e) = r { log_error!("Client->Tor error: {:?}", e); }
        }
        r = tokio::io::copy(&mut tor_read, &mut client_write) => {
            if let Err(ref e) = r { log_error!("Tor->Client error: {:?}", e); }
        }
    };

    Ok(())
}

/// Stop the SOCKS proxy listener. The TorClient stays alive.
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_stopSocksProxy(
    _env: EnvUnowned,
    _class: JClass,
) -> jint {
    log_info!("Stopping SOCKS proxy...");

    if let Some(handle) = SOCKS_TASK.lock().unwrap().take() {
        handle.abort();
    }

    let rt_handle = TOKIO_RUNTIME
        .lock()
        .unwrap()
        .as_ref()
        .map(|rt| rt.handle().clone());
    if let Some(rh) = rt_handle {
        rh.block_on(async {
            tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
        });
    }

    // NOTE: TorClient is NOT destroyed — it persists for reuse.

    log_info!("SOCKS proxy stopped");
    0
}

/// Directory-download progress in permille (0..1000), or -1 when there is no client.
///
/// Lets Kotlin tell a slow download from a stalled one. A timeout cannot: measured cold downloads
/// ran 12.6-34.4s on the same hardware and network, so any fixed patience is either short enough to
/// kill healthy ones or long enough to sit on a dead one. Forward progress separates them exactly.
///
/// Deliberately does NOT surface `BootstrapStatus::blocked()`. Arti documents it as best-effort and
/// warns it "may declare that Arti is stuck for reasons that are incorrect; or it may declare that
/// the client is not stuck when in fact no progress is being made" — acting on that would trade a
/// measurable signal for a guess.
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_bootstrapProgressPermille(
    _env: EnvUnowned,
    _class: JClass,
) -> jint {
    match ARTI_CLIENT.lock().unwrap().as_ref() {
        Some(client) => (client.bootstrap_status().as_frac() * 1000.0).clamp(0.0, 1000.0) as jint,
        None => -1,
    }
}

/// Live readiness: 1 = ready for traffic, 0 = not yet, -1 = no client at all.
///
/// Asks Arti itself (`bootstrap_status().ready_for_traffic()`, a cheap borrow-and-clone of a small
/// struct) rather than latching the outcome of the one background `bootstrap()` call. That call can
/// fail while the client stays perfectly usable — with OnDemand the next stream just retries — so a
/// latched failure would report "not bootstrapped" forever against a Tor that actually works,
/// leaving the UI wrong and the exit-rotation self-heal disabled.
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_isBootstrapped(
    _env: EnvUnowned,
    _class: JClass,
) -> jint {
    match ARTI_CLIENT.lock().unwrap().as_ref() {
        Some(client) => {
            if client.bootstrap_status().ready_for_traffic() {
                1
            } else {
                0
            }
        }
        None => -1,
    }
}

/// Destroy the TorClient — used by self-heal paths in Kotlin when Tor is
/// stuck and the in-memory state (guards, circuits) needs to be rebuilt
/// from scratch. Aborts the SOCKS listener and all in-flight per-connection
/// handlers, then drops the static Arc so Arti's state file lock can be
/// released. The next call to [initialize] will create a fresh TorClient
/// (and re-bootstrap).
#[no_mangle]
pub extern "C" fn Java_com_vitorpamplona_amethyst_ui_tor_ArtiNative_destroy(
    _env: EnvUnowned,
    _class: JClass,
) -> jint {
    log_info!("Destroying Arti client");

    // Clone the runtime handle and release the TOKIO_RUNTIME mutex immediately —
    // we will hold it for ~500ms below, and other JNI calls that need the runtime
    // (e.g. a Kotlin start() racing with us) would otherwise block on this mutex.
    let rt_handle = TOKIO_RUNTIME
        .lock()
        .unwrap()
        .as_ref()
        .map(|rt| rt.handle().clone());

    // Abort the listener and wait for it to actually terminate before draining
    // HANDLER_TASKS. The accept loop has no .await between `accept` and
    // `HANDLER_TASKS.push(h)`, so abort() alone is racy: a handler can be spawned
    // and pushed AFTER our drain. Awaiting the JoinHandle (with timeout) closes
    // that window — no new handlers can be pushed once the listener task is gone.
    let socks_handle = SOCKS_TASK.lock().unwrap().take();
    if let (Some(h), Some(rh)) = (socks_handle, rt_handle.as_ref()) {
        h.abort();
        rh.block_on(async {
            let _ = tokio::time::timeout(tokio::time::Duration::from_secs(1), h).await;
        });
    }

    // The background directory download holds an Arc<TorClient> too, for as long as it runs —
    // which on a dead network is indefinitely. Abort it with the handlers or the state file lock
    // outlives this destroy() and the next initialize() cannot take it.
    if let Some(h) = BOOTSTRAP_TASK.lock().unwrap().take() {
        h.abort();
    }

    // Abort all in-flight handlers — each holds an Arc<TorClient> clone, and
    // the client cannot drop (state file lock cannot release) while any clone
    // is alive.
    let handlers = std::mem::take(&mut *HANDLER_TASKS.lock().unwrap());
    for h in &handlers {
        h.abort();
    }
    drop(handlers);

    // Give tokio a moment to actually cancel and drop the task frames so the
    // handler Arcs are released before we drop our static one.
    if let Some(rh) = rt_handle {
        rh.block_on(async {
            tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;
        });
    }

    // Drop the static Arc. If any handler is still holding a clone, the TorClient stays alive
    // until that handler finishes — in which case the next initialize() fails and Kotlin leaves
    // status Connecting for TorManager's self-heal watchdog to retry. (It no longer wipes all Arti
    // data inline: that turned a transient failure into a lost guard sample and a lost consensus.)
    let _ = ARTI_CLIENT.lock().unwrap().take();

    log_info!("Arti client destroyed");
    0
}