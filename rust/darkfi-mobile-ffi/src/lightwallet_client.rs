//! gRPC client for the DarkFi lightwallet server.
//!
//! This module provides a Rust-native tonic gRPC client that connects to
//! `darkfi-lightwalletd` and retrieves compact blocks, OMR digests, and
//! server capabilities for the mobile sync engine.
//!
//! The tonic client runs on tokio, which is bridged to the smol runtime
//! via `async_compat::Compat`. All public async methods can be called
//! from smol executors.
//!
//! Used by `sync.rs` when the lightwallet path is active.
//!
//! ## Design lessons from zcash/lightwalletd
//!
//! - **Semantic gRPC error codes** (PR #various): classify errors by
//!   retryability so the sync loop can make smart retry decisions.
//! - **Startup health-check** (PR #490): probe the server before
//!   accepting wallet sync requests.
//! - **Debug logging at gRPC entrypoints** (logging commit): every RPC
//!   call is logged at DEBUG level with method name and timing.

use std::time::{Duration, Instant};

/// UnifOMR GenDetKey wire size (~19MB for n=512); raise tonic's 4MB default.
// Param2 UnifOMR detection keys are ~120 MiB on the wire.
const MAX_GRPC_MESSAGE_BYTES: usize = 160 * 1024 * 1024;

fn lwd_client(
    channel: tonic::transport::Channel,
) -> lightwallet_proto::dark_fi_light_wallet_client::DarkFiLightWalletClient<
    tonic::transport::Channel,
> {
    lightwallet_proto::dark_fi_light_wallet_client::DarkFiLightWalletClient::new(channel)
        .max_decoding_message_size(MAX_GRPC_MESSAGE_BYTES)
        .max_encoding_message_size(MAX_GRPC_MESSAGE_BYTES)
}

/// Semantic classification of gRPC errors.
///
/// Adopted from zcash/lightwalletd's systematic error code audit: the
/// server should return `codes.Unavailable` for transient failures vs.
/// `codes.InvalidArgument` for permanent ones, and the client should
/// use this to decide whether to retry.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GrpcErrorKind {
    /// Server or backend node is temporarily unreachable.
    /// Safe to retry with backoff.
    Unavailable,
    /// Request is malformed or uses an unsupported feature.
    /// Do NOT retry — fix the request first.
    InvalidArgument,
    /// Requested resource (block, tx) was not found.
    /// May be transient (block not yet mined) or permanent.
    NotFound,
    /// Server-side bug or unexpected internal error.
    /// Retry with longer backoff.
    Internal,
    /// Client cancelled the request (e.g. wallet closed).
    Cancelled,
    /// Any other error code not specifically handled.
    Other,
}

impl GrpcErrorKind {
    /// Classify a tonic Status code into a semantic error kind.
    pub fn from_tonic_code(code: tonic::Code) -> Self {
        match code {
            tonic::Code::Unavailable | tonic::Code::ResourceExhausted => Self::Unavailable,
            tonic::Code::InvalidArgument | tonic::Code::FailedPrecondition => Self::InvalidArgument,
            tonic::Code::NotFound => Self::NotFound,
            tonic::Code::Internal | tonic::Code::DataLoss | tonic::Code::Unknown => Self::Internal,
            tonic::Code::Cancelled | tonic::Code::DeadlineExceeded => Self::Cancelled,
            tonic::Code::Unimplemented => Self::InvalidArgument,
            _ => Self::Other,
        }
    }

    /// Whether this error kind is safe to retry.
    pub fn is_retryable(self) -> bool {
        matches!(self, Self::Unavailable | Self::Internal | Self::Other)
    }

    /// Whether the sync loop should immediately stop retrying.
    pub fn is_permanent(self) -> bool {
        matches!(self, Self::InvalidArgument | Self::Cancelled)
    }
}

/// An error from a lightwallet gRPC call, with semantic classification.
#[derive(Debug, Clone)]
pub struct LightwalletError {
    pub kind: GrpcErrorKind,
    pub method: String,
    pub message: String,
}

impl std::fmt::Display for LightwalletError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "{} ({}): {}",
            self.method,
            self.kind_label(),
            self.message
        )
    }
}

impl LightwalletError {
    fn kind_label(&self) -> &'static str {
        match self.kind {
            GrpcErrorKind::Unavailable => "unavailable",
            GrpcErrorKind::InvalidArgument => "invalid-argument",
            GrpcErrorKind::NotFound => "not-found",
            GrpcErrorKind::Internal => "internal",
            GrpcErrorKind::Cancelled => "cancelled",
            GrpcErrorKind::Other => "other",
        }
    }

    /// Build from a tonic Status and the method name.
    pub fn from_tonic(method: &str, status: tonic::Status) -> Self {
        Self {
            kind: GrpcErrorKind::from_tonic_code(status.code()),
            method: method.to_string(),
            message: status.message().to_string(),
        }
    }
}

/// Generated protobuf types + gRPC client stubs from `lightwallet.proto`.
pub mod lightwallet_proto {
    include!("proto_gen/darkfi.lightwallet.rs");
}

/// Compact block representation received from the lightwallet server.
/// Mirrors the protobuf `CompactBlock` message in `lightwallet.proto`.
#[derive(Debug, Clone)]
pub struct LightCompactBlock {
    pub height: u32,
    /// 32-byte blake3 header hash
    pub hash: Vec<u8>,
    /// 32-byte blake3 previous header hash
    pub prev_hash: Vec<u8>,
    pub timestamp: u64,
    /// Compact transactions in this block.
    pub txs: Vec<LightCompactTx>,
}

/// A compact transaction within a compact block.
/// Mirrors the protobuf `CompactTx` message.
#[derive(Debug, Clone)]
pub struct LightCompactTx {
    /// 32-byte blake3 transaction hash
    pub tx_hash: Vec<u8>,
    /// Money contract outputs (coins + encrypted notes)
    pub outputs: Vec<LightCompactOutput>,
    /// Revealed nullifiers (each 32 bytes)
    pub nullifiers: Vec<Vec<u8>>,
    /// Fee paid by this transaction
    pub fee: u64,
}

/// A compact output within a compact transaction.
/// Mirrors the protobuf `CompactOutput` message in `lightwallet.proto`.
///
/// Field names match the proto exactly to avoid serialization confusion:
/// - `coin`: Poseidon hash of CoinAttributes (32 bytes, pallas::Base)
/// - `encrypted_note`: Serialized AeadEncryptedNote (ephem_public + ciphertext)
/// - `value_commit`: Pedersen commitment for value (33 bytes, compressed pallas::Point)
/// - `token_commit`: Commitment for token ID (32 bytes, pallas::Base)
///
/// Clients trial-decrypt `encrypted_note` using DH (with `ephem_public` inside
/// the encrypted note) + ChaCha20Poly1305.
#[derive(Debug, Clone)]
pub struct LightCompactOutput {
    /// Coin commitment (32 bytes)
    pub coin: Vec<u8>,
    /// AeadEncryptedNote: serialized (ephem_public + ciphertext).
    /// Clients trial-decrypt this with their secret key.
    pub encrypted_note: Vec<u8>,
    /// Pedersen commitment for value (33 bytes, compressed point)
    pub value_commit: Vec<u8>,
    /// Commitment for token ID (32 bytes)
    pub token_commit: Vec<u8>,
}

/// Server info returned by GetLightInfo.
#[derive(Debug, Clone)]
pub struct LightServerInfo {
    pub server_version: String,
    pub chain_name: String,
    pub chain_tip_height: u32,
    pub omr_supported: bool,
    /// Best block hash at chain_tip_height (finding 5.3).
    /// May be empty if the server hasn't been updated with field 7.
    pub best_block_hash: Vec<u8>,
    /// Backend node (darkfid) version (finding 5.9).
    /// Empty string if server doesn't report it.
    pub backend_version: String,
}

/// OMR capabilities returned by GetOmrCapabilities.
/// Mirrors the protobuf `OmrCapabilities` message in `lightwallet.proto`.
#[derive(Debug, Clone)]
pub struct OmrServerCapabilities {
    /// Whether OMR is currently enabled and operational
    pub enabled: bool,
    /// Supported OMR scheme name (e.g. "fmd" for Fuzzy Message Detection)
    pub scheme: String,
    /// Server-side false positive rate (informational)
    pub false_positive_rate: f64,
    /// Maximum block range per digest request
    pub max_range_per_request: u32,
}

/// Client for the DarkFi lightwallet gRPC server.
///
/// This is designed to be used from the mobile sync engine. Each method
/// performs a single RPC call and returns the result.
///
/// ## Privacy features
///
/// - **Block range padding** (leak #4): All block range requests are rounded
///   up to power-of-2 bucket sizes so the server cannot infer the wallet's
///   exact birthday or age from the range endpoints.
///
/// - **TLS certificate pinning** (leak #6): When connecting to a known
///   lightwalletd server, the client can pin the server's TLS certificate
///   to prevent MITM attacks.
#[derive(Debug)]
struct PinnedVerifier {
    pinned_sha256: [u8; 32],
}

fn cert_hostname_matches(cert: &x509_parser::certificate::X509Certificate<'_>, host: &str) -> bool {
    use x509_parser::extensions::GeneralName;
    if let Ok(Some(san)) = cert.subject_alternative_name() {
        for name in &san.value.general_names {
            if let GeneralName::DNSName(dns) = name {
                if dns.eq_ignore_ascii_case(host) {
                    return true;
                }
            }
        }
    }
    cert.subject().iter_common_name().any(|cn| {
        cn.as_str()
            .map(|s| s.eq_ignore_ascii_case(host))
            .unwrap_or(false)
    })
}

impl rustls::client::danger::ServerCertVerifier for PinnedVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &rustls::pki_types::CertificateDer<'_>,
        _intermediates: &[rustls::pki_types::CertificateDer<'_>],
        server_name: &rustls::pki_types::ServerName<'_>,
        _ocsp_response: &[u8],
        now: rustls::pki_types::UnixTime,
    ) -> Result<rustls::client::danger::ServerCertVerified, rustls::Error> {
        use sha2::{Digest, Sha256};
        let mut hasher = Sha256::new();
        hasher.update(end_entity.as_ref());
        let hash = hasher.finalize();
        if hash.as_slice() != self.pinned_sha256 {
            return Err(rustls::Error::InvalidCertificate(
                rustls::CertificateError::UnknownIssuer,
            ));
        }

        // Pin matched — also enforce validity window + hostname (SAN/CN).
        let (_, cert) = x509_parser::parse_x509_certificate(end_entity.as_ref()).map_err(|_| {
            rustls::Error::InvalidCertificate(rustls::CertificateError::BadEncoding)
        })?;
        let now_secs = now.as_secs() as i64;
        let not_before = cert.validity().not_before.timestamp();
        let not_after = cert.validity().not_after.timestamp();
        if now_secs < not_before {
            return Err(rustls::Error::InvalidCertificate(
                rustls::CertificateError::NotValidYet,
            ));
        }
        if now_secs > not_after {
            return Err(rustls::Error::InvalidCertificate(
                rustls::CertificateError::Expired,
            ));
        }
        let host = match server_name {
            rustls::pki_types::ServerName::DnsName(d) => d.as_ref(),
            _ => {
                return Err(rustls::Error::InvalidCertificate(
                    rustls::CertificateError::NotValidForName,
                ));
            }
        };
        if !cert_hostname_matches(&cert, host) {
            return Err(rustls::Error::InvalidCertificate(
                rustls::CertificateError::NotValidForName,
            ));
        }

        Ok(rustls::client::danger::ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &rustls::pki_types::CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls12_signature(
            message,
            cert,
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &rustls::pki_types::CertificateDer<'_>,
        dss: &rustls::DigitallySignedStruct,
    ) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls13_signature(
            message,
            cert,
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        rustls::crypto::ring::default_provider()
            .signature_verification_algorithms
            .supported_schemes()
    }
}

/// Process-wide default SOCKS5 proxy for **remote** lightwalletd traffic.
///
/// Set at wallet bootstrap when `DrkBootstrapConfig.use_tor` is enabled (the
/// in-process arti proxy on `127.0.0.1:tor_socks_port`). Every
/// [`LightwalletClient`] constructed afterwards routes remote endpoints
/// through it — sync, broadcast, clue-directory lookups and bootstrap probes
/// all take the same Tor path, so no call site can accidentally leak the
/// device IP with a direct connection. Loopback endpoints stay direct.
static DEFAULT_SOCKS5_PROXY: std::sync::RwLock<Option<(String, u16)>> =
    std::sync::RwLock::new(None);

/// Install (or clear) the process-wide SOCKS5 route for remote lightwalletd
/// traffic. Explicit `socks5://` endpoint URLs take precedence.
pub fn set_default_socks5_proxy(proxy: Option<(String, u16)>) {
    *DEFAULT_SOCKS5_PROXY.write().unwrap() = proxy;
}

fn default_socks5_proxy_for(grpc_url: &str) -> Option<(String, u16)> {
    let rest = grpc_url
        .trim_start_matches("https://")
        .trim_start_matches("http://");
    let host = if let Some(v6) = rest.strip_prefix('[') {
        // IPv6 literal, e.g. `[::1]:9067`
        format!("[{}]", v6.split(']').next()?)
    } else {
        rest.split(['/', ':']).next().unwrap_or("").to_string()
    };
    if is_loopback_host(&host) {
        return None;
    }
    DEFAULT_SOCKS5_PROXY.read().unwrap().clone()
}

pub struct LightwalletClient {
    endpoint: String,
    /// Connection timeout for gRPC channel establishment.
    connect_timeout: Duration,
    /// Per-request timeout.
    request_timeout: Duration,
    /// Optional SHA-256 hash of the server's TLS certificate for pinning.
    /// When set, connections to servers with non-matching certificates
    /// are rejected.
    tls_pin_sha256: Option<[u8; 32]>,
    /// Optional SOCKS5 proxy `(host, port)` for Tor / privacy path.
    /// Set when the endpoint was a `socks5://proxy:port/dest:port` URL.
    socks5_proxy: Option<(String, u16)>,
    /// S13: when true (default), refuse cleartext `http://` to remote hosts
    /// even when dialling via SOCKS5/Tor. Prefer `https://` + TLS pin.
    require_https_over_socks: bool,
    /// Persistent gRPC channel (P4) — reused across RPCs in a sync session.
    channel: tokio::sync::Mutex<Option<tonic::transport::Channel>>,
}

impl LightwalletClient {
    /// Create a new client targeting the given gRPC endpoint.
    ///
    /// The endpoint should be a URL like `http://127.0.0.1:9067`,
    /// `https://lightwallet.example.com:9067`, or Android Tor form
    /// `socks5://proxy_host:proxy_port/dest_host:dest_port`.
    pub fn new(endpoint: &str) -> Self {
        let parsed = parse_lightwallet_endpoint(endpoint);
        let socks5_proxy = parsed
            .socks5_proxy
            .or_else(|| default_socks5_proxy_for(&parsed.grpc_url));
        Self {
            endpoint: parsed.grpc_url,
            connect_timeout: Duration::from_secs(10),
            request_timeout: Duration::from_secs(30),
            tls_pin_sha256: None,
            socks5_proxy,
            require_https_over_socks: true,
            channel: tokio::sync::Mutex::new(None),
        }
    }

    /// Create a new client with TLS certificate pinning.
    ///
    /// PRIVACY: Certificate pinning prevents MITM attacks where an attacker
    /// could intercept the lightwalletd connection and observe block range
    /// requests, detection keys, or transaction submissions.
    ///
    /// The `pin_sha256` is the SHA-256 of the server's **leaf certificate DER**
    /// (not SPKI). Production hosts must use `new_with_tls_pin`.
    pub fn new_with_tls_pin(endpoint: &str, pin_sha256: [u8; 32]) -> Self {
        let parsed = parse_lightwallet_endpoint(endpoint);
        let socks5_proxy = parsed
            .socks5_proxy
            .or_else(|| default_socks5_proxy_for(&parsed.grpc_url));
        Self {
            endpoint: parsed.grpc_url,
            connect_timeout: Duration::from_secs(10),
            request_timeout: Duration::from_secs(30),
            tls_pin_sha256: Some(pin_sha256),
            socks5_proxy,
            require_https_over_socks: true,
            channel: tokio::sync::Mutex::new(None),
        }
    }

    /// Allow cleartext remote endpoints over SOCKS5 (dev-only).
    ///
    /// Production should keep the default (`require_https_over_socks = true`).
    pub fn allow_cleartext_over_socks(mut self) -> Self {
        self.require_https_over_socks = false;
        self
    }

    /// Construct from endpoint + optional pin (S8). Remote HTTPS requires a pin
    /// at connect time via `enforce_transport_policy`.
    pub fn from_endpoint_and_pin(endpoint: &str, pin: Option<[u8; 32]>) -> Self {
        match pin {
            Some(p) => Self::new_with_tls_pin(endpoint, p),
            None => Self::new(endpoint),
        }
    }

    /// Check if TLS certificate pinning is configured.
    pub fn has_tls_pin(&self) -> bool {
        self.tls_pin_sha256.is_some()
    }

    /// Get the pinned certificate hash, if configured.
    pub fn tls_pin(&self) -> Option<&[u8; 32]> {
        self.tls_pin_sha256.as_ref()
    }

    /// Whether this client dials through a SOCKS5 proxy (Tor privacy path).
    pub fn has_socks5_proxy(&self) -> bool {
        self.socks5_proxy.is_some()
    }

    /// Convert from the tcp:// scheme used in DarkFi configs to http:// for tonic.
    fn grpc_endpoint(&self) -> String {
        // Endpoint is already normalized at construction (including socks5 rewrite).
        normalize_lightwallet_url(&self.endpoint)
    }

    /// S6/S8/S13 transport policy:
    /// - `http://` / cleartext: loopback only by default
    /// - remote cleartext via SOCKS5: refused when `require_https_over_socks`
    ///   (default true); opt out via [`Self::allow_cleartext_over_socks`]
    /// - `https://`: requires a TLS pin for remote hosts (even through SOCKS)
    fn enforce_transport_policy(
        endpoint: &str,
        has_pin: bool,
        via_socks: bool,
        require_https_over_socks: bool,
    ) -> Result<(), String> {
        let is_https = endpoint.starts_with("https://");
        let is_http = endpoint.starts_with("http://");
        if !is_http && !is_https {
            return Err(format!(
                "Unsupported lightwallet endpoint scheme: {endpoint}"
            ));
        }
        let host = endpoint
            .trim_start_matches("https://")
            .trim_start_matches("http://")
            .split(['/', ':'])
            .next()
            .unwrap_or("");
        let loopback = is_loopback_host(host);
        if is_http && !loopback {
            if via_socks && require_https_over_socks {
                return Err(
                    "Cleartext lightwallet over SOCKS5 is disabled (require_https_over_socks). \
                     Use https:// (with TLS pin) even through Tor, or call allow_cleartext_over_socks() for dev."
                        .into(),
                );
            }
            if !via_socks {
                return Err(
                    "Cleartext lightwallet URLs are only allowed for loopback (127.0.0.1 / ::1 / localhost) or via SOCKS5 (Tor). Use https:// or tcp+tls:// with a TLS pin."
                        .into(),
                );
            }
            tracing::warn!(
                target: "lightwallet-client",
                "SECURITY: cleartext lightwallet over SOCKS5 allowed (require_https_over_socks=false). Prefer https:// + TLS pin."
            );
        }
        if is_https && !has_pin && !loopback {
            return Err(
                "Remote HTTPS lightwallet requires TLS certificate pinning (new_with_tls_pin). Refusing system-roots-only connect."
                    .into(),
            );
        }
        Ok(())
    }

    async fn dial_tcp(
        socks5_proxy: &Option<(String, u16)>,
        host: &str,
        port: u16,
        connect_timeout: Duration,
    ) -> Result<tokio::net::TcpStream, std::io::Error> {
        if let Some((proxy_host, proxy_port)) = socks5_proxy {
            let proxy = (proxy_host.as_str(), *proxy_port);
            let dest = (host, port);
            let socks = tokio::time::timeout(
                connect_timeout,
                tokio_socks::tcp::Socks5Stream::connect(proxy, dest),
            )
            .await
            .map_err(|_| {
                std::io::Error::new(std::io::ErrorKind::TimedOut, "SOCKS5 connect timeout")
            })?
            .map_err(|e| {
                std::io::Error::new(
                    std::io::ErrorKind::ConnectionRefused,
                    format!("SOCKS5: {e}"),
                )
            })?;
            Ok(socks.into_inner())
        } else {
            let addr = format!("{host}:{port}");
            tokio::time::timeout(connect_timeout, tokio::net::TcpStream::connect(addr))
                .await
                .map_err(|_| {
                    std::io::Error::new(std::io::ErrorKind::TimedOut, "TCP connect timeout")
                })?
        }
    }

    async fn connect_channel(&self) -> Result<tonic::transport::Channel, String> {
        {
            let guard = self.channel.lock().await;
            if let Some(ch) = guard.as_ref() {
                return Ok(ch.clone());
            }
        }

        let endpoint = self.grpc_endpoint();
        let via_socks = self.socks5_proxy.is_some();
        Self::enforce_transport_policy(
            &endpoint,
            self.tls_pin_sha256.is_some(),
            via_socks,
            self.require_https_over_socks,
        )?;

        let socks5_proxy = self.socks5_proxy.clone();
        let connect_timeout = self.connect_timeout;
        let use_tls = endpoint.starts_with("https://");

        let fresh = if use_tls {
            let pin_hash = self
                .tls_pin_sha256
                .ok_or_else(|| "HTTPS lightwallet requires TLS certificate pinning".to_string())?;
            let connector = tower::service_fn(move |uri: tonic::codegen::http::Uri| {
                let socks5_proxy = socks5_proxy.clone();
                async move {
                    let host = uri
                        .host()
                        .ok_or_else(|| {
                            std::io::Error::new(std::io::ErrorKind::InvalidInput, "missing host")
                        })?
                        .to_string();
                    let port = uri.port_u16().unwrap_or(443);
                    let tcp_stream =
                        Self::dial_tcp(&socks5_proxy, &host, port, connect_timeout).await?;

                    let verifier = std::sync::Arc::new(PinnedVerifier {
                        pinned_sha256: pin_hash,
                    });
                    let rustls_config = rustls::ClientConfig::builder_with_provider(
                        std::sync::Arc::new(rustls::crypto::ring::default_provider()),
                    )
                    .with_safe_default_protocol_versions()
                    .unwrap()
                    .dangerous()
                    .with_custom_certificate_verifier(verifier)
                    .with_no_client_auth();
                    let tls_connector =
                        tokio_rustls::TlsConnector::from(std::sync::Arc::new(rustls_config));
                    let server_name = host.to_string().try_into().map_err(|e| {
                        std::io::Error::new(
                            std::io::ErrorKind::InvalidInput,
                            format!("Invalid domain name: {e}"),
                        )
                    })?;
                    let tls_stream = tls_connector
                        .connect(server_name, tcp_stream)
                        .await
                        .map_err(|e| {
                            std::io::Error::new(
                                std::io::ErrorKind::PermissionDenied,
                                format!("TLS handshake failed: {e}"),
                            )
                        })?;
                    Ok::<_, std::io::Error>(hyper_util::rt::TokioIo::new(tls_stream))
                }
            });

            tonic::transport::Endpoint::from_shared(endpoint.clone())
                .map_err(|e| format!("Invalid endpoint: {e}"))?
                .timeout(self.request_timeout)
                .connect_with_connector(connector)
                .await
                .map_err(|e| format!("gRPC connect (TLS): {e}"))?
        } else if via_socks {
            // Cleartext gRPC through local Tor SOCKS (privacy path).
            let connector = tower::service_fn(move |uri: tonic::codegen::http::Uri| {
                let socks5_proxy = socks5_proxy.clone();
                async move {
                    let host = uri
                        .host()
                        .ok_or_else(|| {
                            std::io::Error::new(std::io::ErrorKind::InvalidInput, "missing host")
                        })?
                        .to_string();
                    let port = uri.port_u16().unwrap_or(80);
                    let tcp_stream =
                        Self::dial_tcp(&socks5_proxy, &host, port, connect_timeout).await?;
                    Ok::<_, std::io::Error>(hyper_util::rt::TokioIo::new(tcp_stream))
                }
            });

            tonic::transport::Endpoint::from_shared(endpoint.clone())
                .map_err(|e| format!("Invalid endpoint: {e}"))?
                .timeout(self.request_timeout)
                .connect_with_connector(connector)
                .await
                .map_err(|e| format!("gRPC connect (SOCKS5): {e}"))?
        } else {
            // Loopback cleartext only (policy already enforced above).
            tonic::transport::Channel::from_shared(endpoint.clone())
                .map_err(|e| format!("Invalid endpoint: {e}"))?
                .connect_timeout(self.connect_timeout)
                .timeout(self.request_timeout)
                .connect()
                .await
                .map_err(|e| format!("gRPC connect: {e}"))?
        };

        let mut guard = self.channel.lock().await;
        *guard = Some(fresh.clone());
        Ok(fresh)
    }

    /// Startup health-check: verify the server is reachable and the
    /// backend node (darkfid) has synced past genesis.
    ///
    /// Adopted from zcash/lightwalletd's `FirstRPC()` pattern (finding 1.1/1.3):
    /// probe the server up to `max_retries` times with backoff before
    /// accepting wallet sync requests. Returns the server info on success.
    ///
    /// This prevents the wallet from entering a sync loop against a
    /// lightwalletd whose backend darkfid hasn't synced yet.
    pub async fn probe_health(
        &self,
        max_retries: u32,
        retry_interval: Duration,
    ) -> Result<LightServerInfo, String> {
        let mut attempt = 0u32;
        loop {
            match self.get_light_info().await {
                Ok(info) => {
                    if info.chain_tip_height == 0 {
                        attempt += 1;
                        if attempt >= max_retries {
                            return Err(format!(
                                "Server reachable but chain tip is 0 after {attempt} probes. \
                                 Is darkfid synced?"
                            ));
                        }
                        tracing::warn!(
                            target: "lightwallet-client",
                            "Health probe {attempt}/{max_retries}: chain tip is 0, \
                             darkfid may still be syncing. Retrying in {:?}…",
                            retry_interval,
                        );
                        smol::Timer::after(retry_interval).await;
                        continue;
                    }
                    if attempt > 0 {
                        tracing::info!(
                            target: "lightwallet-client",
                            "Health probe succeeded after {attempt} retries: tip={}",
                            info.chain_tip_height,
                        );
                    }
                    return Ok(info);
                }
                Err(e) => {
                    attempt += 1;
                    if attempt >= max_retries {
                        return Err(format!(
                            "Lightwalletd unreachable after {attempt} probes: {e}"
                        ));
                    }
                    tracing::warn!(
                        target: "lightwallet-client",
                        "Health probe {attempt}/{max_retries} failed: {e}. \
                         Retrying in {:?}…",
                        retry_interval,
                    );
                    smol::Timer::after(retry_interval).await;
                }
            }
        }
    }

    /// Probe the server for basic info (version, chain tip, OMR support).
    ///
    /// This is the first call the sync engine makes to determine which
    /// sync path to use.
    pub async fn get_light_info(&self) -> Result<LightServerInfo, String> {
        let start = Instant::now();
        let result = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;

            let mut client = lwd_client(channel);

            let resp = client
                .get_light_info(lightwallet_proto::Empty {})
                .await
                .map_err(|e| format!("GetLightInfo RPC: {e}"))?;

            let info = resp.into_inner();
            Ok(LightServerInfo {
                server_version: info.version,
                chain_name: info.chain_name,
                chain_tip_height: info.block_height,
                omr_supported: info.omr_supported,
                best_block_hash: info.best_block_hash,
                backend_version: info.backend_version,
            })
        })
        .await;
        tracing::debug!(
            target: "lightwallet-client",
            "gRPC GetLightInfo: ok={}, tip={} ({:?})",
            result.is_ok(),
            result.as_ref().map(|i| i.chain_tip_height).unwrap_or(0),
            start.elapsed(),
        );
        result
    }

    /// Fetch compact blocks in the given height range [start, end] inclusive.
    ///
    /// PRIVACY: The requested range is automatically padded to a power-of-2
    /// bucket size via `pad_block_range()`. The server receives the padded
    /// range, so it cannot infer the wallet's exact birthday or scan position
    /// from the range endpoints. The client discards blocks outside the
    /// originally requested range after receiving them.
    ///
    /// Returns blocks in ascending height order.
    pub async fn get_compact_block_range(
        &self,
        start_height: u32,
        end_height: u32,
    ) -> Result<Vec<LightCompactBlock>, String> {
        // Finding 5.4: validate range before sending to server
        validate_block_range(start_height, end_height)?;

        let start = Instant::now();
        // PRIVACY: Pad the range to hide exact wallet birthday
        let (padded_start, padded_end) = pad_block_range(start_height, end_height);

        tracing::debug!(
            target: "lightwallet-client",
            "gRPC GetBlockRange({start_height}..={end_height}) padded to \
             ({padded_start}..={padded_end})"
        );

        let blocks: Vec<LightCompactBlock> = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;

            let mut client = lwd_client(channel);

            let request = lightwallet_proto::BlockRange {
                start_height: padded_start,
                end_height: padded_end,
            };

            let mut stream = client
                .get_block_range(request)
                .await
                .map_err(|e| format!("GetBlockRange RPC: {e}"))?
                .into_inner();

            // Pre-allocate based on expected range size to avoid
            // per-block allocations (lesson from zcash memory fix).
            let expected_count = (padded_end.saturating_sub(padded_start) + 1) as usize;
            let mut blocks = Vec::with_capacity(expected_count.min(10_000));
            while let Some(proto_block) = stream
                .message()
                .await
                .map_err(|e| format!("GetBlockRange stream: {e}"))?
            {
                blocks.push(proto_compact_to_light(proto_block));
            }

            Ok::<Vec<LightCompactBlock>, String>(blocks)
        })
        .await?;

        // PRIVACY: Filter to only the originally requested range.
        // The server received the padded range; discard the padding blocks.
        let filtered: Vec<LightCompactBlock> = blocks
            .into_iter()
            .filter(|b| b.height >= start_height && b.height <= end_height)
            .collect();

        tracing::debug!(
            target: "lightwallet-client",
            "gRPC GetBlockRange: {} blocks received, {} after filter ({:?})",
            filtered.len() + (end_height.saturating_sub(start_height) + 1) as usize - filtered.len(),
            filtered.len(),
            start.elapsed(),
        );

        Ok(filtered)
    }

    /// Fetch a single compact block by height.
    pub async fn get_block(&self, height: u32) -> Result<LightCompactBlock, String> {
        async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client = lwd_client(channel);
            let resp = client
                .get_block(lightwallet_proto::BlockHeight { height })
                .await
                .map_err(|e| format!("GetBlock RPC: {e}"))?;
            Ok(proto_compact_to_light(resp.into_inner()))
        })
        .await
    }

    /// Sparse fetch: compact blocks at specific heights only.
    ///
    /// Prefers `GetCompactBlocksAtHeights`; falls back to N× `GetBlock` if the
    /// RPC is unimplemented / unavailable. Server cap is 512 heights per call.
    pub async fn get_compact_blocks_at_heights(
        &self,
        heights: &[u32],
    ) -> Result<Vec<LightCompactBlock>, String> {
        if heights.is_empty() {
            return Ok(Vec::new());
        }

        let mut unique: Vec<u32> = heights.to_vec();
        unique.sort_unstable();
        unique.dedup();

        let start = Instant::now();
        let result = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client =
                lwd_client(channel);

            match client
                .get_compact_blocks_at_heights(lightwallet_proto::HeightList {
                    heights: unique.clone(),
                })
                .await
            {
                Ok(response) => {
                    let mut stream = response.into_inner();
                    let mut blocks = Vec::with_capacity(unique.len());
                    while let Some(proto_block) = stream
                        .message()
                        .await
                        .map_err(|e| format!("GetCompactBlocksAtHeights stream: {e}"))?
                    {
                        blocks.push(proto_compact_to_light(proto_block));
                    }
                    Ok(blocks)
                }
                Err(status) => {
                    let code = status.code();
                    let msg = status.message().to_lowercase();
                    let fallback = code == tonic::Code::Unimplemented
                        || code == tonic::Code::NotFound
                        || msg.contains("unknown")
                        || msg.contains("unimplemented");
                    if !fallback {
                        return Err(format!("GetCompactBlocksAtHeights RPC: {status}"));
                    }
                    tracing::debug!(
                        target: "lightwallet-client",
                        "GetCompactBlocksAtHeights unavailable ({status}); falling back to N× GetBlock"
                    );
                    let mut blocks = Vec::with_capacity(unique.len());
                    for h in &unique {
                        let resp = client
                            .get_block(lightwallet_proto::BlockHeight { height: *h })
                            .await
                            .map_err(|e| format!("GetBlock({h}) fallback RPC: {e}"))?;
                        blocks.push(proto_compact_to_light(resp.into_inner()));
                    }
                    Ok(blocks)
                }
            }
        })
        .await;

        tracing::debug!(
            target: "lightwallet-client",
            "gRPC sparse compact blocks ({} heights): ok={} ({:?})",
            unique.len(),
            result.is_ok(),
            start.elapsed(),
        );
        result
    }

    /// Collect note commitments (coins) for `[start, end]` inclusive.
    pub async fn get_note_commitments(
        &self,
        start_height: u32,
        end_height: u32,
    ) -> Result<Vec<(u32, Vec<Vec<u8>>)>, String> {
        validate_block_range(start_height, end_height)?;
        let start = Instant::now();
        let updates = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client = lwd_client(channel);
            let mut stream = client
                .get_note_commitments(lightwallet_proto::BlockRange {
                    start_height,
                    end_height,
                })
                .await
                .map_err(|e| format!("GetNoteCommitments RPC: {e}"))?
                .into_inner();

            let mut out = Vec::new();
            while let Some(update) = stream
                .message()
                .await
                .map_err(|e| format!("GetNoteCommitments stream: {e}"))?
            {
                out.push((update.height, update.coins));
            }
            Ok::<Vec<(u32, Vec<Vec<u8>>)>, String>(out)
        })
        .await?;

        tracing::debug!(
            target: "lightwallet-client",
            "gRPC GetNoteCommitments({start_height}..={end_height}): {} updates ({:?})",
            updates.len(),
            start.elapsed(),
        );
        Ok(updates)
    }

    /// Collect nullifiers revealed in `[start, end]` inclusive.
    pub async fn get_nullifiers(
        &self,
        start_height: u32,
        end_height: u32,
    ) -> Result<Vec<(u32, Vec<Vec<u8>>)>, String> {
        validate_block_range(start_height, end_height)?;
        let start = Instant::now();
        let updates = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client = lwd_client(channel);
            let mut stream = client
                .get_nullifiers(lightwallet_proto::BlockRange {
                    start_height,
                    end_height,
                })
                .await
                .map_err(|e| format!("GetNullifiers RPC: {e}"))?
                .into_inner();

            let mut out = Vec::new();
            while let Some(update) = stream
                .message()
                .await
                .map_err(|e| format!("GetNullifiers stream: {e}"))?
            {
                out.push((update.height, update.nullifiers));
            }
            Ok::<Vec<(u32, Vec<Vec<u8>>)>, String>(out)
        })
        .await?;

        tracing::debug!(
            target: "lightwallet-client",
            "gRPC GetNullifiers({start_height}..={end_height}): {} updates ({:?})",
            updates.len(),
            start.elapsed(),
        );
        Ok(updates)
    }

    /// Query the server's OMR capabilities.
    pub async fn get_omr_capabilities(&self) -> Result<OmrServerCapabilities, String> {
        async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;

            let mut client = lwd_client(channel);

            let resp = client
                .get_omr_capabilities(lightwallet_proto::Empty {})
                .await
                .map_err(|e| format!("GetOmrCapabilities RPC: {e}"))?;

            let caps = resp.into_inner();
            Ok(OmrServerCapabilities {
                enabled: caps.enabled,
                scheme: caps.scheme,
                false_positive_rate: caps.false_positive_rate,
                max_range_per_request: caps.max_range_per_request,
            })
        })
        .await
    }

    pub async fn register_omr_clue(
        &self,
        tx_hash: Vec<u8>,
        omr_clue: Vec<u8>,
    ) -> Result<(), String> {
        if tx_hash.len() != 32 {
            return Err(format!("tx_hash must be 32 bytes, got {}", tx_hash.len()));
        }
        if omr_clue.is_empty() {
            return Err("omr_clue cannot be empty".to_string());
        }

        async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client = lwd_client(channel);
            client
                .register_omr_clue(lightwallet_proto::OmrClueRegistration {
                    tx_hash,
                    omr_clue,
                    omr_clue_output_index: 0, // payment output
                    // No recipient-encrypted metadata on the standalone
                    // registration path; SendTransaction carries it instead.
                    omr_metadata_enc: Vec::new(),
                })
                .await
                .map_err(|e| format!("RegisterOmrClue RPC: {e}"))?;
            Ok(())
        })
        .await
    }

    /// Broadcast a raw DarkFi transaction (optionally with OMR clue) via lightwalletd.
    ///
    /// Prefer this over darkfid broadcast + standalone `RegisterOmrClue` so the
    /// clue is bound to the same peer/session as the send (S12).
    pub async fn send_transaction(
        &self,
        tx_data: Vec<u8>,
        omr_clue: Vec<u8>,
        omr_metadata_enc: Vec<u8>,
    ) -> Result<Vec<u8>, String> {
        let start = Instant::now();
        if tx_data.is_empty() {
            return Err("transaction data cannot be empty".into());
        }
        let result = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client = lwd_client(channel);
            let had_clue = !omr_clue.is_empty();
            let resp = client
                .send_transaction(lightwallet_proto::RawTransaction {
                    data: tx_data,
                    omr_clue,
                    omr_clue_output_index: 0, // payment output (not change)
                    omr_metadata_enc,
                })
                .await
                .map_err(|e| format!("SendTransaction RPC: {e}"))?;
            let inner = resp.into_inner();
            // Finding 5.5: validate server error format — empty means success
            if !inner.error.is_empty() {
                return Err(format!("Server rejected transaction: {}", inner.error));
            }
            // Fail closed if a UnifOMR clue was supplied but not accepted.
            if had_clue && !inner.clue_accepted {
                return Err(
                    "SendTransaction succeeded but clue_accepted=false — UnifOMR hint not stored"
                        .into(),
                );
            }
            // Finding 5.5: validate tx_hash encoding consistency.
            // DarkFi returns raw 32-byte hash; reject if server sends
            // hex-encoded (64 bytes) or empty response.
            if inner.tx_hash.len() != 32 {
                return Err(format!(
                    "SendTransaction returned tx_hash of {} bytes (expected 32 raw bytes)",
                    inner.tx_hash.len()
                ));
            }
            Ok(inner.tx_hash)
        })
        .await;
        tracing::debug!(
            target: "lightwallet-client",
            "gRPC SendTransaction: ok={} ({:?})",
            result.is_ok(),
            start.elapsed(),
        );
        result
    }

    pub async fn get_unif_omr_digest(
        &self,
        detection_keys: Vec<Vec<u8>>,
        start_height: u32,
        end_height: u32,
    ) -> Result<Vec<u8>, String> {
        validate_block_range(start_height, end_height)?;
        if detection_keys.is_empty() {
            return Err("detection_keys required".into());
        }
        if detection_keys.iter().any(|k| k.is_empty()) {
            return Err("empty detection key not allowed".into());
        }
        let result = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client = lwd_client(channel);
            let chunk_size = 1024 * 1024; // 1 MiB
            let num_keys = detection_keys.len() as u32;
            let stream = async_stream::stream! {
                yield lightwallet_proto::DetectionKeyChunk {
                    start_height,
                    end_height,
                    num_keys,
                    data: vec![],
                    key_done: false,
                };
                for key in detection_keys {
                    let mut offset = 0;
                    while offset < key.len() {
                        let end_offset = (offset + chunk_size).min(key.len());
                        let chunk_data = key[offset..end_offset].to_vec();
                        offset = end_offset;
                        yield lightwallet_proto::DetectionKeyChunk {
                            start_height: 0,
                            end_height: 0,
                            num_keys: 0,
                            data: chunk_data,
                            key_done: offset == key.len(),
                        };
                    }
                }
            };
            let resp = client
                .get_unif_omr_digest(tonic::Request::new(stream))
                .await
                .map_err(|e| format!("GetUnifOmrDigest RPC: {e}"))?;
            Ok(resp.into_inner().encrypted_digest)
        })
        .await;
        result
    }

    pub async fn register_clue_public_key(
        &self,
        payment_pubkey: Vec<u8>,
        clue_public_key: Vec<u8>,
        ownership_proof: Vec<u8>,
        key_version: u64,
    ) -> Result<(), String> {
        let result = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client = lwd_client(channel);
            client
                .register_clue_public_key(lightwallet_proto::CluePublicKeyRegistration {
                    payment_pubkey,
                    clue_public_key,
                    ownership_proof,
                    key_version,
                })
                .await
                .map_err(|e| format!("RegisterCluePublicKey RPC: {e}"))?;
            Ok(())
        })
        .await;
        result
    }

    pub async fn get_clue_public_key(
        &self,
        payment_pubkey: Vec<u8>,
    ) -> Result<(bool, Vec<u8>, Vec<u8>, u64), String> {
        let result = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client = lwd_client(channel);
            let resp = client
                .get_clue_public_key(lightwallet_proto::PaymentPubkey { payment_pubkey })
                .await
                .map_err(|e| format!("GetCluePublicKey RPC: {e}"))?;
            let inner = resp.into_inner();
            Ok((
                inner.found,
                inner.clue_public_key,
                inner.ownership_proof,
                inner.key_version,
            ))
        })
        .await;
        result
    }

    pub async fn fetch_pir_batch(
        &self,
        query_ciphertexts: Vec<Vec<u8>>,
        start_height: u32,
        end_height: u32,
        limb_index: u32,
    ) -> Result<Vec<Vec<u8>>, String> {
        let result = async_compat::Compat::new(async {
            let channel = self.connect_channel().await?;
            let mut client = lwd_client(channel);

            let resp = client
                .fetch_pir_batch(lightwallet_proto::BatchPirRequest {
                    query_ciphertexts,
                    start_height,
                    end_height,
                    limb_index,
                })
                .await
                .map_err(|e| format!("FetchPirBatch RPC: {e}"))?;
            Ok(resp.into_inner().payload_ciphertexts)
        })
        .await;
        result
    }
}

/// Convert a protobuf CompactBlock to the local LightCompactBlock type.
pub(crate) fn proto_compact_to_light(pb: lightwallet_proto::CompactBlock) -> LightCompactBlock {
    LightCompactBlock {
        height: pb.height,
        hash: pb.hash,
        prev_hash: pb.prev_hash,
        timestamp: pb.timestamp,
        txs: pb
            .txs
            .into_iter()
            .map(|tx| LightCompactTx {
                tx_hash: tx.tx_hash,
                outputs: tx
                    .outputs
                    .into_iter()
                    .map(|o| LightCompactOutput {
                        coin: o.coin,
                        encrypted_note: o.encrypted_note,
                        value_commit: o.value_commit,
                        token_commit: o.token_commit,
                    })
                    .collect(),
                nullifiers: tx.nullifiers,
                fee: tx.fee,
            })
            .collect(),
    }
}

// =============================================================================
// Block & Range Validation (Findings 5.1, 5.4)
// =============================================================================

/// Minimum expected byte length for an AeadEncryptedNote.
///
/// An encrypted note contains at least:
/// - 32 bytes ephemeral public key
/// - 16 bytes Poly1305 tag
///
/// = 48 bytes minimum. Actual notes are longer (contain ciphertext).
const MIN_ENCRYPTED_NOTE_LEN: usize = 48;

/// Validate a compact block's structural integrity (finding 5.1).
///
/// Adopted from zcash/lightwalletd PR #278: deserialize and validate blocks
/// at ingest time instead of discovering corruption when a wallet requests
/// them.
///
/// Returns `Ok(())` if the block passes all checks, `Err(description)` if
/// any field has an invalid length or value.
pub fn validate_compact_block(block: &LightCompactBlock) -> Result<(), String> {
    if block.height == 0 {
        return Err("Block height is 0 (genesis blocks should not appear in sync stream)".into());
    }
    if block.hash.len() != 32 {
        return Err(format!(
            "Block {}: hash is {} bytes (expected 32)",
            block.height,
            block.hash.len()
        ));
    }
    if block.prev_hash.len() != 32 {
        return Err(format!(
            "Block {}: prev_hash is {} bytes (expected 32)",
            block.height,
            block.prev_hash.len()
        ));
    }

    for (tx_idx, tx) in block.txs.iter().enumerate() {
        if tx.tx_hash.len() != 32 {
            return Err(format!(
                "Block {} tx[{}]: tx_hash is {} bytes (expected 32)",
                block.height,
                tx_idx,
                tx.tx_hash.len()
            ));
        }
        for (out_idx, output) in tx.outputs.iter().enumerate() {
            if output.coin.len() != 32 {
                return Err(format!(
                    "Block {} tx[{}] output[{}]: coin is {} bytes (expected 32)",
                    block.height,
                    tx_idx,
                    out_idx,
                    output.coin.len()
                ));
            }
            if !output.encrypted_note.is_empty()
                && output.encrypted_note.len() < MIN_ENCRYPTED_NOTE_LEN
            {
                return Err(format!(
                    "Block {} tx[{}] output[{}]: encrypted_note is {} bytes (minimum {})",
                    block.height,
                    tx_idx,
                    out_idx,
                    output.encrypted_note.len(),
                    MIN_ENCRYPTED_NOTE_LEN,
                ));
            }
        }
        for (nf_idx, nf) in tx.nullifiers.iter().enumerate() {
            if nf.len() != 32 {
                return Err(format!(
                    "Block {} tx[{}] nullifier[{}]: is {} bytes (expected 32)",
                    block.height,
                    tx_idx,
                    nf_idx,
                    nf.len()
                ));
            }
        }
    }
    Ok(())
}

/// Validate a block range request before sending to the server (finding 5.4).
///
/// Adopted from zcash/lightwalletd's null-argument segfault fix: check
/// inputs on the client side to avoid sending invalid requests.
pub fn validate_block_range(start: u32, end: u32) -> Result<(), String> {
    if start > end {
        return Err(format!(
            "Invalid block range: start ({start}) > end ({end})"
        ));
    }
    if start == 0 && end == 0 {
        return Err("Block range start and end are both 0".into());
    }
    Ok(())
}

// =============================================================================
// Block Range Padding (Privacy Leak #4)
// =============================================================================

/// Minimum padding bucket size (blocks).
const MIN_BUCKET_SIZE: u32 = 1024;

/// Pad a block range to a power-of-2 bucket boundary.
///
/// PRIVACY: Without padding, the exact block range requested reveals:
/// - The wallet's birthday height (start of first request)
/// - How long the wallet has been offline (gap between scanned and tip)
/// - Whether the wallet is a new or old wallet
///
/// This function rounds the range to the next power-of-2 bucket size
/// (minimum 1024 blocks) and aligns the start to a bucket boundary.
///
/// ## Examples
///
/// ```text
/// pad_block_range(42000, 42500) → (41984, 43007)  // 1024-block bucket
/// pad_block_range(0, 50000)     → (0, 65535)       // 65536-block bucket
/// pad_block_range(100, 100)     → (0, 1023)        // minimum 1024 bucket
/// ```
///
/// The server sees only the padded range. The client silently discards
/// blocks outside the originally requested range.
pub fn pad_block_range(start: u32, end: u32) -> (u32, u32) {
    let range_size = end.saturating_sub(start).saturating_add(1);

    // Round up to next power of 2, with minimum bucket size
    let bucket = range_size.max(MIN_BUCKET_SIZE).next_power_of_two();

    // Align start down to bucket boundary
    let aligned_start = (start / bucket) * bucket;
    let mut aligned_end = aligned_start.saturating_add(bucket).saturating_sub(1);

    // If the aligned range doesn't cover the original end, extend by one more bucket.
    // This can happen when `end` crosses a bucket boundary relative to `aligned_start`.
    while aligned_end < end {
        aligned_end = aligned_end.saturating_add(bucket);
    }

    (aligned_start, aligned_end)
}

// =============================================================================
// URL Normalization / SOCKS5
// =============================================================================

/// Parsed lightwallet endpoint after scheme normalization.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParsedLightwalletEndpoint {
    /// gRPC URL (`http://` / `https://`) for tonic.
    pub grpc_url: String,
    /// Optional SOCKS5 proxy when the input was `socks5://proxy/dest`.
    pub socks5_proxy: Option<(String, u16)>,
}

/// Parse `socks5://proxy_host:proxy_port/dest_host:dest_port` (Android TorDarkfidEndpoint).
///
/// Returns `None` if the URL is not a socks5 DarkFi transport URI.
pub fn parse_socks5_lightwallet_url(url: &str) -> Option<ParsedLightwalletEndpoint> {
    let url = url.trim();
    if !url.starts_with("socks5://") {
        return None;
    }
    let rest = &url["socks5://".len()..];
    let (authority, dest) = rest.split_once('/')?;
    if dest.is_empty() {
        return None;
    }
    let (proxy_host, proxy_port_s) = authority.rsplit_once(':')?;
    let proxy_port: u16 = proxy_port_s.parse().ok()?;
    if proxy_host.is_empty() {
        return None;
    }
    // Destination may be host:port (IPv6 not supported in this Android URI form).
    let dest = dest.trim_start_matches('/');
    let (dest_host, dest_port_s) = dest.rsplit_once(':')?;
    let dest_port: u16 = dest_port_s.parse().ok()?;
    if dest_host.is_empty() {
        return None;
    }
    Some(ParsedLightwalletEndpoint {
        grpc_url: format!("http://{dest_host}:{dest_port}"),
        socks5_proxy: Some((proxy_host.to_string(), proxy_port)),
    })
}

/// Parse any supported lightwallet endpoint into a gRPC URL + optional SOCKS5 proxy.
pub fn parse_lightwallet_endpoint(url: &str) -> ParsedLightwalletEndpoint {
    if let Some(parsed) = parse_socks5_lightwallet_url(url) {
        return parsed;
    }
    ParsedLightwalletEndpoint {
        grpc_url: normalize_lightwallet_url(url),
        socks5_proxy: None,
    }
}

/// Convert a `tcp://host:port` / `tcp+tls://host:port` endpoint URL to a form
/// usable by the lightwallet gRPC client.
///
/// DarkFi configs use `tcp://` for JSON-RPC and `http://` / `https://` /
/// `tcp+tls://` for gRPC. This helper normalizes all of them.
/// SOCKS5 URLs should be handled via [`parse_socks5_lightwallet_url`] /
/// [`parse_lightwallet_endpoint`] so the proxy is retained.
pub fn normalize_lightwallet_url(url: &str) -> String {
    let url = url.trim();
    if let Some(parsed) = parse_socks5_lightwallet_url(url) {
        return parsed.grpc_url;
    }
    if let Some(rest) = url.strip_prefix("tcp+tls://") {
        format!("https://{rest}")
    } else if let Some(rest) = url.strip_prefix("tcp://") {
        format!("http://{rest}")
    } else if url.starts_with("http://") || url.starts_with("https://") {
        url.to_string()
    } else {
        format!("http://{}", url)
    }
}

pub fn is_loopback_host(host: &str) -> bool {
    matches!(host, "127.0.0.1" | "localhost" | "::1" | "[::1]")
}

#[cfg(test)]
mod tests {
    use super::*;

    // =========================================================================
    // SOCKS5 URL parsing
    // =========================================================================

    #[test]
    fn parse_socks5_tor_url() {
        let p = parse_socks5_lightwallet_url("socks5://127.0.0.1:9050/node.dark.fi:9067").unwrap();
        assert_eq!(p.grpc_url, "http://node.dark.fi:9067");
        assert_eq!(p.socks5_proxy, Some(("127.0.0.1".into(), 9050)));
    }

    #[test]
    fn parse_socks5_custom_proxy() {
        let p = parse_lightwallet_endpoint("socks5://10.0.0.5:9150/lw.example:9067");
        assert_eq!(p.grpc_url, "http://lw.example:9067");
        assert_eq!(p.socks5_proxy, Some(("10.0.0.5".into(), 9150)));
        let client = LightwalletClient::new("socks5://10.0.0.5:9150/lw.example:9067");
        assert!(client.has_socks5_proxy());
        assert_eq!(client.grpc_endpoint(), "http://lw.example:9067");
    }

    #[test]
    fn parse_socks5_rejects_malformed() {
        assert!(parse_socks5_lightwallet_url("socks5://127.0.0.1:9050").is_none());
        assert!(parse_socks5_lightwallet_url("socks5://127.0.0.1/host:9067").is_none());
        assert!(parse_socks5_lightwallet_url("http://127.0.0.1:9067").is_none());
    }

    #[test]
    fn normalize_socks5_to_http_dest() {
        assert_eq!(
            normalize_lightwallet_url("socks5://127.0.0.1:9050/node.dark.fi:9067"),
            "http://node.dark.fi:9067"
        );
    }

    #[test]
    fn enforce_rejects_remote_http_via_socks_by_default() {
        // S13: require_https_over_socks=true refuses cleartext remote via SOCKS.
        assert!(LightwalletClient::enforce_transport_policy(
            "http://node.dark.fi:9067",
            false,
            true,
            true,
        )
        .is_err());
        assert!(LightwalletClient::enforce_transport_policy(
            "http://node.dark.fi:9067",
            false,
            false,
            true,
        )
        .is_err());
        // Dev opt-out still allows cleartext over SOCKS.
        assert!(LightwalletClient::enforce_transport_policy(
            "http://node.dark.fi:9067",
            false,
            true,
            false,
        )
        .is_ok());
    }

    #[test]
    fn enforce_still_requires_pin_for_remote_https() {
        assert!(LightwalletClient::enforce_transport_policy(
            "https://lw.darkfi.xyz:9067",
            false,
            true,
            true,
        )
        .is_err());
        assert!(LightwalletClient::enforce_transport_policy(
            "https://lw.darkfi.xyz:9067",
            true,
            true,
            true,
        )
        .is_ok());
    }

    // =========================================================================
    // URL normalization
    // =========================================================================

    #[test]
    fn normalize_tcp_url() {
        assert_eq!(
            normalize_lightwallet_url("tcp://127.0.0.1:9067"),
            "http://127.0.0.1:9067"
        );
    }

    #[test]
    fn normalize_tcp_preserves_port() {
        assert_eq!(
            normalize_lightwallet_url("tcp://127.0.0.1:9067"),
            "http://127.0.0.1:9067"
        );
        assert_eq!(
            normalize_lightwallet_url("tcp://127.0.0.1:18345"),
            "http://127.0.0.1:18345"
        );
    }

    #[test]
    fn normalize_https_passthrough() {
        assert_eq!(
            normalize_lightwallet_url("https://lw.darkfi.xyz:9067"),
            "https://lw.darkfi.xyz:9067"
        );
    }

    #[test]
    fn normalize_bare_host() {
        assert_eq!(
            normalize_lightwallet_url("192.168.1.1:9067"),
            "http://192.168.1.1:9067"
        );
    }

    #[test]
    fn client_grpc_endpoint_from_tcp() {
        let client = LightwalletClient::new("tcp://127.0.0.1:9067");
        assert_eq!(client.grpc_endpoint(), "http://127.0.0.1:9067");
    }

    #[test]
    fn client_grpc_endpoint_from_tls() {
        let client = LightwalletClient::new("tcp+tls://lw.darkfi.xyz:9067");
        assert_eq!(client.grpc_endpoint(), "https://lw.darkfi.xyz:9067");
    }

    #[test]
    fn client_grpc_endpoint_passthrough() {
        let client = LightwalletClient::new("http://localhost:9067");
        assert_eq!(client.grpc_endpoint(), "http://localhost:9067");
    }

    // =========================================================================
    // TLS certificate pinning
    // =========================================================================

    #[test]
    fn client_no_pin_by_default() {
        let client = LightwalletClient::new("http://localhost:9067");
        assert!(!client.has_tls_pin());
        assert!(client.tls_pin().is_none());
    }

    #[test]
    fn client_with_tls_pin() {
        let pin = [0xAA; 32];
        let client = LightwalletClient::new_with_tls_pin("https://lw.darkfi.xyz:9067", pin);
        assert!(client.has_tls_pin());
        assert_eq!(client.tls_pin(), Some(&pin));
    }

    // =========================================================================
    // Block range padding (Privacy leak #4)
    // =========================================================================

    #[test]
    fn pad_single_block_to_min_bucket() {
        let (start, end) = pad_block_range(500, 500);
        // Single block should pad to MIN_BUCKET_SIZE (1024)
        assert_eq!(start, 0);
        assert_eq!(end, 1023);
        assert_eq!(end - start + 1, 1024);
    }

    #[test]
    fn pad_small_range_to_min_bucket() {
        let (start, end) = pad_block_range(42000, 42100);
        // 101 blocks → pad to 1024 bucket
        let bucket_size = end - start + 1;
        assert_eq!(bucket_size, 1024);
        // Start should be aligned to 1024 boundary
        assert_eq!(start % 1024, 0);
        // Original range should be within padded range
        assert!(start <= 42000);
        assert!(end >= 42100);
    }

    #[test]
    fn pad_medium_range_to_power_of_2() {
        let (start, end) = pad_block_range(10000, 12000);
        // 2001 blocks → next power of 2 = 2048
        // Start aligned to 2048 boundary: (10000/2048)*2048 = 8192
        // First bucket ends at 10239, which < 12000, so extends
        assert_eq!(start % 2048, 0, "Start must be aligned to bucket boundary");
        assert!(start <= 10000, "Padded start must be ≤ original start");
        assert!(end >= 12000, "Padded end must be ≥ original end");
    }

    #[test]
    fn pad_large_range() {
        let (start, end) = pad_block_range(0, 50000);
        // 50001 blocks → next power of 2 = 65536
        let bucket_size = end - start + 1;
        assert_eq!(bucket_size, 65536);
        assert_eq!(start, 0);
        assert_eq!(end, 65535);
    }

    #[test]
    fn pad_aligned_range_stays_same_size() {
        let (start, end) = pad_block_range(0, 1023);
        // Already 1024 blocks = MIN_BUCKET_SIZE
        assert_eq!(start, 0);
        assert_eq!(end, 1023);
    }

    #[test]
    fn pad_range_near_boundary() {
        let (start, end) = pad_block_range(1024, 2047);
        // 1024 blocks starting at boundary
        assert_eq!(start, 1024);
        assert_eq!(end, 2047);
    }

    #[test]
    fn pad_range_hides_birthday() {
        // Wallet born at block 42,000 syncing to tip 42,500
        let (start, end) = pad_block_range(42000, 42500);
        // Server should NOT see exact birthday (42000)
        assert!(start < 42000, "Padding must hide exact birthday");
        assert!(end > 42500, "Padding must extend past exact scan point");
    }

    #[test]
    fn pad_range_is_deterministic() {
        let r1 = pad_block_range(42000, 42500);
        let r2 = pad_block_range(42000, 42500);
        assert_eq!(r1, r2, "Padding must be deterministic for same input");
    }

    #[test]
    fn pad_range_overflow_safety() {
        // Near u32::MAX — should not overflow
        let (start, end) = pad_block_range(u32::MAX - 100, u32::MAX);
        assert!(start <= u32::MAX - 100);
        assert!(end >= u32::MAX - 100);
    }

    // =========================================================================
    // GrpcErrorKind classification (finding 2.1)
    // =========================================================================

    #[test]
    fn grpc_error_kind_unavailable_is_retryable() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::Unavailable);
        assert_eq!(kind, GrpcErrorKind::Unavailable);
        assert!(kind.is_retryable());
        assert!(!kind.is_permanent());
    }

    #[test]
    fn grpc_error_kind_resource_exhausted_is_retryable() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::ResourceExhausted);
        assert_eq!(kind, GrpcErrorKind::Unavailable);
        assert!(kind.is_retryable());
    }

    #[test]
    fn grpc_error_kind_invalid_argument_is_permanent() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::InvalidArgument);
        assert_eq!(kind, GrpcErrorKind::InvalidArgument);
        assert!(kind.is_permanent());
        assert!(!kind.is_retryable());
    }

    #[test]
    fn grpc_error_kind_not_found() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::NotFound);
        assert_eq!(kind, GrpcErrorKind::NotFound);
        assert!(!kind.is_retryable());
        assert!(!kind.is_permanent());
    }

    #[test]
    fn grpc_error_kind_internal_is_retryable() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::Internal);
        assert_eq!(kind, GrpcErrorKind::Internal);
        assert!(kind.is_retryable());
    }

    #[test]
    fn grpc_error_kind_cancelled_is_permanent() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::Cancelled);
        assert_eq!(kind, GrpcErrorKind::Cancelled);
        assert!(kind.is_permanent());
    }

    #[test]
    fn grpc_error_kind_deadline_exceeded_is_cancelled() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::DeadlineExceeded);
        assert_eq!(kind, GrpcErrorKind::Cancelled);
    }

    #[test]
    fn grpc_error_kind_unimplemented_is_invalid_argument() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::Unimplemented);
        assert_eq!(kind, GrpcErrorKind::InvalidArgument);
        assert!(kind.is_permanent());
    }

    #[test]
    fn grpc_error_kind_unknown_is_internal() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::Unknown);
        assert_eq!(kind, GrpcErrorKind::Internal);
        assert!(kind.is_retryable());
    }

    #[test]
    fn grpc_error_kind_data_loss_is_internal() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::DataLoss);
        assert_eq!(kind, GrpcErrorKind::Internal);
    }

    #[test]
    fn grpc_error_kind_ok_is_other() {
        let kind = GrpcErrorKind::from_tonic_code(tonic::Code::Ok);
        assert_eq!(kind, GrpcErrorKind::Other);
    }

    // =========================================================================
    // LightwalletError
    // =========================================================================

    #[test]
    fn lightwallet_error_display() {
        let err = LightwalletError {
            kind: GrpcErrorKind::Unavailable,
            method: "GetLightInfo".to_string(),
            message: "connection refused".to_string(),
        };
        let display = format!("{err}");
        assert!(display.contains("GetLightInfo"));
        assert!(display.contains("unavailable"));
        assert!(display.contains("connection refused"));
    }

    #[test]
    fn lightwallet_error_from_tonic_status() {
        let status = tonic::Status::unavailable("server overloaded");
        let err = LightwalletError::from_tonic("GetBlockRange", status);
        assert_eq!(err.kind, GrpcErrorKind::Unavailable);
        assert_eq!(err.method, "GetBlockRange");
        assert!(err.message.contains("overloaded"));
    }

    // =========================================================================
    // Block validation (finding 5.1)
    // =========================================================================

    fn make_valid_block() -> LightCompactBlock {
        LightCompactBlock {
            height: 42000,
            hash: vec![0u8; 32],
            prev_hash: vec![1u8; 32],
            timestamp: 1700000000,
            txs: vec![LightCompactTx {
                tx_hash: vec![2u8; 32],
                outputs: vec![LightCompactOutput {
                    coin: vec![3u8; 32],
                    encrypted_note: vec![4u8; 128],
                    value_commit: vec![5u8; 33],
                    token_commit: vec![6u8; 32],
                }],
                nullifiers: vec![vec![7u8; 32]],
                fee: 1000,
            }],
        }
    }

    #[test]
    fn validate_block_valid() {
        assert!(validate_compact_block(&make_valid_block()).is_ok());
    }

    #[test]
    fn validate_block_empty_txs() {
        let mut block = make_valid_block();
        block.txs.clear();
        assert!(validate_compact_block(&block).is_ok());
    }

    #[test]
    fn validate_block_bad_hash_length() {
        let mut block = make_valid_block();
        block.hash = vec![0u8; 16];
        let err = validate_compact_block(&block).unwrap_err();
        assert!(err.contains("hash is 16 bytes"), "Got: {err}");
    }

    #[test]
    fn validate_block_bad_prev_hash_length() {
        let mut block = make_valid_block();
        block.prev_hash = vec![0u8; 64];
        let err = validate_compact_block(&block).unwrap_err();
        assert!(err.contains("prev_hash is 64 bytes"), "Got: {err}");
    }

    #[test]
    fn validate_block_bad_tx_hash_length() {
        let mut block = make_valid_block();
        block.txs[0].tx_hash = vec![0u8; 20];
        let err = validate_compact_block(&block).unwrap_err();
        assert!(err.contains("tx_hash is 20 bytes"), "Got: {err}");
    }

    #[test]
    fn validate_block_bad_coin_length() {
        let mut block = make_valid_block();
        block.txs[0].outputs[0].coin = vec![0u8; 10];
        let err = validate_compact_block(&block).unwrap_err();
        assert!(err.contains("coin is 10 bytes"), "Got: {err}");
    }

    #[test]
    fn validate_block_too_short_encrypted_note() {
        let mut block = make_valid_block();
        block.txs[0].outputs[0].encrypted_note = vec![0u8; 30];
        let err = validate_compact_block(&block).unwrap_err();
        assert!(err.contains("encrypted_note is 30 bytes"), "Got: {err}");
    }

    #[test]
    fn validate_block_empty_encrypted_note_is_ok() {
        let mut block = make_valid_block();
        block.txs[0].outputs[0].encrypted_note = vec![];
        assert!(validate_compact_block(&block).is_ok());
    }

    #[test]
    fn validate_block_bad_nullifier_length() {
        let mut block = make_valid_block();
        block.txs[0].nullifiers[0] = vec![0u8; 16];
        let err = validate_compact_block(&block).unwrap_err();
        assert!(err.contains("nullifier[0]: is 16 bytes"), "Got: {err}");
    }

    // =========================================================================
    // Range validation (finding 5.4)
    // =========================================================================

    #[test]
    fn validate_range_valid() {
        assert!(validate_block_range(1, 100).is_ok());
        assert!(validate_block_range(100, 100).is_ok());
    }

    #[test]
    fn validate_range_start_greater_than_end() {
        let err = validate_block_range(200, 100).unwrap_err();
        assert!(err.contains("start (200) > end (100)"), "Got: {err}");
    }

    #[test]
    fn validate_range_both_zero() {
        let err = validate_block_range(0, 0).unwrap_err();
        assert!(err.contains("both 0"), "Got: {err}");
    }

    #[test]
    fn validate_range_start_zero_end_nonzero() {
        assert!(validate_block_range(0, 100).is_ok());
    }

    // =========================================================================
    // Block height zero (finding 5.1 gap fix)
    // =========================================================================

    #[test]
    fn validate_block_height_zero_rejected() {
        let mut block = make_valid_block();
        block.height = 0;
        let err = validate_compact_block(&block).unwrap_err();
        assert!(err.contains("height is 0"), "Got: {err}");
    }

    // =========================================================================
    // LightServerInfo new fields (findings 5.3, 5.9)
    // =========================================================================

    #[test]
    fn light_server_info_default_fields() {
        let info = LightServerInfo {
            server_version: "0.1.0".into(),
            chain_name: "testnet".into(),
            chain_tip_height: 42000,
            omr_supported: true,
            best_block_hash: vec![0xAA; 32],
            backend_version: "darkfid 0.5.0".into(),
        };
        assert_eq!(info.best_block_hash.len(), 32);
        assert_eq!(info.backend_version, "darkfid 0.5.0");
    }

    #[test]
    fn light_server_info_empty_optional_fields() {
        let info = LightServerInfo {
            server_version: "0.1.0".into(),
            chain_name: "testnet".into(),
            chain_tip_height: 42000,
            omr_supported: false,
            best_block_hash: vec![],
            backend_version: String::new(),
        };
        assert!(info.best_block_hash.is_empty());
        assert!(info.backend_version.is_empty());
    }

    // =========================================================================
    // Constants sanity checks
    // =========================================================================

    #[test]
    fn min_encrypted_note_len_covers_ephem_key_and_tag() {
        // 32 bytes ephemeral public key + 16 bytes Poly1305 tag = 48 minimum
        assert_eq!(MIN_ENCRYPTED_NOTE_LEN, 48);
    }
}
