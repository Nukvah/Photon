//! Safe wrapper around the jpegli encoder.

use photon_jpegli_sys as sys;
use std::os::raw::c_char;

/// Encoder settings. Defaults are the email-safe choice from the tech spec.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct EncodeOptions {
    /// jpegli "distance": 1.0 is visually lossless, higher means smaller files.
    pub distance: f32,
    /// 4:2:0 chroma subsampling (smaller) instead of 4:4:4.
    pub chroma_420: bool,
    /// 0 = baseline sequential (best compatibility with old mail clients).
    pub progressive_level: i32,
}

impl Default for EncodeOptions {
    fn default() -> Self {
        Self {
            distance: 1.2,
            chroma_420: true,
            progressive_level: 0,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EncodeError(pub String);

impl std::fmt::Display for EncodeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "JPEG encode failed: {}", self.0)
    }
}

impl std::error::Error for EncodeError {}

/// Encodes tightly packed 8-bit sRGB pixels into a JPEG.
pub fn encode_rgb(
    rgb: &[u8],
    width: u32,
    height: u32,
    options: &EncodeOptions,
) -> Result<Vec<u8>, EncodeError> {
    let expected = width as usize * height as usize * 3;
    if width == 0 || height == 0 || rgb.len() != expected {
        return Err(EncodeError(format!(
            "expected {expected} bytes for {width}x{height} RGB, got {}",
            rgb.len()
        )));
    }
    let raw = sys::photon_jpegli_options {
        distance: options.distance,
        chroma_420: options.chroma_420 as i32,
        progressive_level: options.progressive_level,
    };
    let mut out: *mut u8 = std::ptr::null_mut();
    let mut out_len: usize = 0;
    let mut err = [0 as c_char; 256];
    // SAFETY: the buffer length was checked above; out/out_len/err are valid for writes.
    let status = unsafe {
        sys::photon_jpegli_encode_rgb(
            rgb.as_ptr(),
            width,
            height,
            &raw,
            &mut out,
            &mut out_len,
            err.as_mut_ptr(),
            err.len(),
        )
    };
    if status != 0 || out.is_null() {
        // SAFETY: the shim always NUL-terminates err.
        let msg = unsafe { std::ffi::CStr::from_ptr(err.as_ptr()) }
            .to_string_lossy()
            .into_owned();
        return Err(EncodeError(msg));
    }
    // SAFETY: on success the shim returns a malloc'd buffer of out_len bytes.
    let bytes = unsafe { std::slice::from_raw_parts(out, out_len) }.to_vec();
    unsafe { sys::photon_jpegli_free(out) };
    Ok(bytes)
}

/// A deterministic, photo-like test image: smooth gradients, hard edges and fine noise.
/// Used for benchmarks and golden tests until real photo decoding lands in Stage 2.
pub fn synthetic_image(width: u32, height: u32) -> Vec<u8> {
    let mut rgb = Vec::with_capacity(width as usize * height as usize * 3);
    let mut seed: u32 = 0x9E37_79B9;
    for y in 0..height {
        for x in 0..width {
            seed ^= seed << 13;
            seed ^= seed >> 17;
            seed ^= seed << 5;
            let noise = (seed & 0x0F) as i32 - 8;
            let fx = x as f32 / width as f32;
            let fy = y as f32 / height as f32;
            let sky = fy < 0.45;
            let base = if sky {
                [90.0 + 80.0 * fy, 140.0 + 60.0 * fy, 220.0 - 40.0 * fy]
            } else {
                let stripe = if ((x / 24) + (y / 24)) % 2 == 0 {
                    30.0
                } else {
                    0.0
                };
                [70.0 + 90.0 * fx + stripe, 110.0 + 40.0 * fy, 50.0 + stripe]
            };
            for c in base {
                rgb.push((c as i32 + noise).clamp(0, 255) as u8);
            }
        }
    }
    rgb
}

/// FNV-1a 64-bit hash, hex encoded. Cheap fingerprint to compare outputs across runs and devices.
pub fn fingerprint(bytes: &[u8]) -> String {
    let mut hash: u64 = 0xcbf2_9ce4_8422_2325;
    for b in bytes {
        hash ^= *b as u64;
        hash = hash.wrapping_mul(0x0000_0100_0000_01b3);
    }
    format!("{hash:016x}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encodes_a_valid_jpeg() {
        let img = synthetic_image(64, 48);
        let jpeg = encode_rgb(&img, 64, 48, &EncodeOptions::default()).unwrap();
        assert_eq!(&jpeg[..2], &[0xFF, 0xD8], "SOI marker");
        assert_eq!(&jpeg[jpeg.len() - 2..], &[0xFF, 0xD9], "EOI marker");
    }

    #[test]
    fn same_input_gives_same_bytes() {
        let img = synthetic_image(320, 240);
        let opts = EncodeOptions::default();
        let a = encode_rgb(&img, 320, 240, &opts).unwrap();
        let b = encode_rgb(&img, 320, 240, &opts).unwrap();
        assert_eq!(a, b);
    }

    #[test]
    fn higher_distance_is_smaller() {
        let img = synthetic_image(320, 240);
        let fine = encode_rgb(
            &img,
            320,
            240,
            &EncodeOptions {
                distance: 1.0,
                ..Default::default()
            },
        )
        .unwrap();
        let coarse = encode_rgb(
            &img,
            320,
            240,
            &EncodeOptions {
                distance: 3.0,
                ..Default::default()
            },
        )
        .unwrap();
        assert!(
            coarse.len() < fine.len(),
            "{} !< {}",
            coarse.len(),
            fine.len()
        );
    }

    #[test]
    fn rejects_wrong_buffer_size() {
        assert!(encode_rgb(&[0; 10], 4, 4, &EncodeOptions::default()).is_err());
    }
}
