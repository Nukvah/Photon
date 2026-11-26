//! Photon core: all image work for the Android app lives here.
//!
//! Stage 0 only exposes a couple of test functions so the Kotlin app can
//! prove it can load and call the library. The pipeline arrives in Stage 2.

uniffi::setup_scaffolding!();

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
    fn ping_echoes_message() {
        assert!(ping("hello".into()).ends_with("received: hello"));
    }
}
