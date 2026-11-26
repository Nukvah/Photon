//! Photon core: all image work for the Android app lives here.
//!
//! So far: bridge smoke tests (Stage 0) and the jpegli encoder spike (Stage 1).
//! The full pipeline (decode, resize, size search) arrives in Stage 2.

pub mod encode;

uniffi::setup_scaffolding!();

/// Errors returned to Kotlin.
#[derive(Debug, uniffi::Error)]
pub enum PhotonError {
    /// Field is `reason`, not `message`: Kotlin exceptions already have `message`.
    Encode { reason: String },
}

impl std::fmt::Display for PhotonError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PhotonError::Encode { reason } => write!(f, "{reason}"),
        }
    }
}

impl std::error::Error for PhotonError {}

impl From<encode::EncodeError> for PhotonError {
    fn from(e: encode::EncodeError) -> Self {
        PhotonError::Encode {
            reason: e.to_string(),
        }
    }
}

/// Result of encoding the synthetic test image with jpegli.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct EncodeBenchmark {
    pub width: u32,
    pub height: u32,
    pub distance: f32,
    /// Size of one encoded JPEG.
    pub bytes: u64,
    /// Average encode time per run, in milliseconds.
    pub encode_ms: f64,
    pub iterations: u32,
    /// Fingerprint of the output bytes; equal on every run and on every device of one CPU type.
    pub fingerprint: String,
}

/// Encodes a deterministic synthetic image `iterations` times and reports size and speed.
#[uniffi::export]
pub fn benchmark_encode(
    width: u32,
    height: u32,
    distance: f32,
    iterations: u32,
) -> Result<EncodeBenchmark, PhotonError> {
    let iterations = iterations.max(1);
    let rgb = encode::synthetic_image(width, height);
    let options = encode::EncodeOptions {
        distance,
        ..Default::default()
    };
    let start = std::time::Instant::now();
    let mut jpeg = Vec::new();
    for _ in 0..iterations {
        jpeg = encode::encode_rgb(&rgb, width, height, &options)?;
    }
    let encode_ms = start.elapsed().as_secs_f64() * 1000.0 / iterations as f64;
    Ok(EncodeBenchmark {
        width,
        height,
        distance,
        bytes: jpeg.len() as u64,
        encode_ms,
        iterations,
        fingerprint: encode::fingerprint(&jpeg),
    })
}

/// Basic facts about the running core library.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct CoreInfo {
    /// Crate version, for example "0.1.0".
    pub version: String,
    /// CPU architecture the library was built for, for example "aarch64".
    pub target_arch: String,
    /// Operating system the library was built for, for example "android".
    pub target_os: String,
}

/// Returns version and build target, so the app can show which library it loaded.
#[uniffi::export]
pub fn core_info() -> CoreInfo {
    CoreInfo {
        version: env!("CARGO_PKG_VERSION").to_string(),
        target_arch: std::env::consts::ARCH.to_string(),
        target_os: std::env::consts::OS.to_string(),
    }
}

/// Round-trips a string through Rust; used as a smoke test of the bridge.
#[uniffi::export]
pub fn ping(message: String) -> String {
    format!(
        "photon-core {} received: {message}",
        env!("CARGO_PKG_VERSION")
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn core_info_reports_crate_version() {
        let info = core_info();
        assert_eq!(info.version, env!("CARGO_PKG_VERSION"));
        assert!(!info.target_arch.is_empty());
        assert!(!info.target_os.is_empty());
    }

    #[test]
    fn benchmark_reports_stable_fingerprint() {
        let a = benchmark_encode(256, 192, 1.0, 1).unwrap();
        let b = benchmark_encode(256, 192, 1.0, 2).unwrap();
        assert_eq!(a.fingerprint, b.fingerprint);
        assert_eq!(a.bytes, b.bytes);
    }

    #[test]
    fn ping_echoes_message() {
        assert!(ping("hello".into()).ends_with("received: hello"));
    }
}
