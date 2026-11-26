//! The whole pipeline: decode → sRGB → upright → resize → jpegli with a size search.
//!
//! Same input bytes + same preset = same output bytes on every device of one CPU type.
//! Nothing here reads the clock, the locale, threads or random numbers.

use crate::decode::{self, DecodeError, DecodeInfo};
use crate::encode::{self, EncodeError, EncodeOptions};
use crate::resize;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Preset {
    Small,
    Standard,
    Sharp,
}

/// Numbers behind one preset.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct PresetSpec {
    /// Output pixel budget (width × height).
    pub target_pixels: u64,
    /// Size cap per photo. Quality drops (up to `max_distance`) only to stay under it.
    pub max_bytes: usize,
    /// Best quality tried first. 1.0 is jpegli's "visually lossless".
    pub min_distance: f32,
    /// Lowest quality allowed; a photo still over the cap here is kept at this quality.
    pub max_distance: f32,
}

impl Preset {
    pub const ALL: [Preset; 3] = [Preset::Small, Preset::Standard, Preset::Sharp];

    /// Checked on 17 real 12 MP phone photos (Display P3): median output was
    /// 80 KB (Small), 157 KB (Standard) and 258 KB (Sharp); about 1 in 5 busy
    /// scenes hit the cap and needed at most distance 1.8.
    pub fn spec(self) -> PresetSpec {
        match self {
            Preset::Small => PresetSpec {
                target_pixels: 500_000,
                max_bytes: 150 * 1024,
                min_distance: 1.0,
                max_distance: 3.0,
            },
            Preset::Standard => PresetSpec {
                target_pixels: 1_000_000,
                max_bytes: 250 * 1024,
                min_distance: 1.0,
                max_distance: 3.0,
            },
            Preset::Sharp => PresetSpec {
                target_pixels: 2_000_000,
                max_bytes: 500 * 1024,
                min_distance: 1.0,
                max_distance: 3.0,
            },
        }
    }

    pub fn name(self) -> &'static str {
        match self {
            Preset::Small => "small",
            Preset::Standard => "standard",
            Preset::Sharp => "sharp",
        }
    }

    pub fn from_name(name: &str) -> Option<Preset> {
        Preset::ALL
            .into_iter()
            .find(|p| p.name().eq_ignore_ascii_case(name))
    }
}

/// Bisection steps after the two end points; fixed so the work and result never vary.
const SEARCH_STEPS: u32 = 5;

#[derive(Debug, Clone, PartialEq)]
pub struct Processed {
    pub jpeg: Vec<u8>,
    pub width: u32,
    pub height: u32,
    /// jpegli distance used; lower is better quality.
    pub distance: f32,
    /// Number of encodes the size search needed.
    pub encodes: u32,
    pub decode_info: DecodeInfo,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PipelineError {
    Decode(DecodeError),
    Encode(EncodeError),
}

impl std::fmt::Display for PipelineError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PipelineError::Decode(e) => write!(f, "cannot read photo: {e}"),
            PipelineError::Encode(e) => write!(f, "cannot write JPEG: {e}"),
        }
    }
}

impl std::error::Error for PipelineError {}

impl From<DecodeError> for PipelineError {
    fn from(e: DecodeError) -> Self {
        PipelineError::Decode(e)
    }
}

impl From<EncodeError> for PipelineError {
    fn from(e: EncodeError) -> Self {
        PipelineError::Encode(e)
    }
}

pub fn process(input: &[u8], preset: Preset) -> Result<Processed, PipelineError> {
    let spec = preset.spec();
    let (image, decode_info) = decode::decode(input)?;
    let small = resize::downscale(&image, spec.target_pixels);
    drop(image);
    let (jpeg, distance, encodes) = size_search(&small, &spec)?;
    Ok(Processed {
        jpeg,
        width: small.width,
        height: small.height,
        distance,
        encodes,
        decode_info,
    })
}

/// Best quality that fits `max_bytes`:
/// 1. `min_distance` fits → done (most photos, one encode).
/// 2. `max_distance` does not fit → keep it (rare: huge noisy photos).
/// 3. Otherwise bisect a fixed number of steps and keep the best fitting result.
fn size_search(
    img: &decode::RgbImage,
    spec: &PresetSpec,
) -> Result<(Vec<u8>, f32, u32), EncodeError> {
    let encode_at = |distance: f32| {
        let options = EncodeOptions {
            distance,
            ..EncodeOptions::default()
        };
        encode::encode_rgb(&img.pixels, img.width, img.height, &options)
    };

    let best = encode_at(spec.min_distance)?;
    if best.len() <= spec.max_bytes {
        return Ok((best, spec.min_distance, 1));
    }
    let worst = encode_at(spec.max_distance)?;
    if worst.len() > spec.max_bytes {
        return Ok((worst, spec.max_distance, 2));
    }

    let (mut lo, mut hi) = (spec.min_distance, spec.max_distance);
    let mut fit = (worst, spec.max_distance);
    let mut encodes = 2;
    for _ in 0..SEARCH_STEPS {
        // Round to 1/1000 so the distances tried are short, readable numbers.
        let mid = ((lo + hi) * 500.0).round() / 1000.0;
        let jpeg = encode_at(mid)?;
        encodes += 1;
        if jpeg.len() <= spec.max_bytes {
            hi = mid;
            fit = (jpeg, mid);
        } else {
            lo = mid;
        }
    }
    Ok((fit.0, fit.1, encodes))
}

/// File names for a batch: the original name, made email safe, as `.jpg`.
///
/// `IMG_2041.HEIC` → `IMG_2041.jpg`; `Léto 2024 (1).png` → `L_to_2024_1.jpg`;
/// unknown → `photo.jpg`. Duplicates get `-2`, `-3`, … in input order.
pub fn output_file_names(inputs: &[String]) -> Vec<String> {
    let mut used = std::collections::HashSet::new();
    inputs
        .iter()
        .map(|input| {
            let stem = safe_stem(input);
            let mut n = 1;
            loop {
                let name = if n == 1 {
                    format!("{stem}.jpg")
                } else {
                    format!("{stem}-{n}.jpg")
                };
                if used.insert(name.to_ascii_lowercase()) {
                    return name;
                }
                n += 1;
            }
        })
        .collect()
}

fn safe_stem(input: &str) -> String {
    let file = input.rsplit(['/', '\\']).next().unwrap_or("");
    let stem = match file.rfind('.') {
        Some(i) if i > 0 => &file[..i],
        _ => file,
    };
    let mut out = String::new();
    for c in stem.chars() {
        if c.is_ascii_alphanumeric() || c == '-' {
            out.push(c);
        } else if !out.ends_with('_') {
            out.push('_');
        }
    }
    let out: String = out.trim_matches(['_', '-']).chars().take(60).collect();
    if out.is_empty() {
        "photo".to_string()
    } else {
        out
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testimg;

    #[test]
    fn output_is_jpeg_without_metadata() {
        let p3 = moxcms::ColorProfile::new_display_p3().encode().unwrap();
        let input = testimg::jpeg_with_segments(64, 48, Some(6), Some(&p3));
        let out = process(&input, Preset::Standard).unwrap();
        assert_eq!((out.width, out.height), (48, 64));
        assert!(!contains(&out.jpeg, b"Exif\0"));
        assert!(!contains(&out.jpeg, b"ICC_PROFILE"));
        assert_eq!(&out.jpeg[..2], &[0xFF, 0xD8]);
    }

    #[test]
    fn same_input_same_bytes() {
        let input = testimg::jpeg_with_segments(1600, 1200, Some(3), None);
        let a = process(&input, Preset::Small).unwrap();
        let b = process(&input, Preset::Small).unwrap();
        assert_eq!(a, b);
        assert!(u64::from(a.width) * u64::from(a.height) <= 500_000);
    }

    #[test]
    fn size_search_meets_a_tight_cap() {
        let img = decode::RgbImage {
            width: 800,
            height: 600,
            pixels: encode::synthetic_image(800, 600),
        };
        let mut spec = Preset::Standard.spec();
        let size_at = |distance: f32| {
            let options = EncodeOptions {
                distance,
                ..EncodeOptions::default()
            };
            encode::encode_rgb(&img.pixels, 800, 600, &options)
                .unwrap()
                .len()
        };
        // A cap halfway between the best and worst quality sizes forces a bisection.
        spec.max_bytes = (size_at(spec.min_distance) + size_at(spec.max_distance)) / 2;
        let (jpeg, distance, encodes) = size_search(&img, &spec).unwrap();
        assert!(jpeg.len() <= spec.max_bytes);
        assert!(distance > spec.min_distance && distance < spec.max_distance);
        assert_eq!(encodes, 2 + SEARCH_STEPS);
    }

    #[test]
    fn file_names_are_email_safe_and_unique() {
        let names: Vec<String> = [
            "content://media/external/images/IMG_2041.HEIC",
            "Léto 2024 (1).png",
            "IMG_2041.jpg",
            "",
            ".hidden",
            "img_2041.JPG",
        ]
        .iter()
        .map(|s| s.to_string())
        .collect();
        assert_eq!(
            output_file_names(&names),
            [
                "IMG_2041.jpg",
                "L_to_2024_1.jpg",
                "IMG_2041-2.jpg",
                "photo.jpg",
                "hidden.jpg",
                "img_2041-3.jpg"
            ]
        );
    }

    #[test]
    fn preset_names_round_trip() {
        for p in Preset::ALL {
            assert_eq!(Preset::from_name(p.name()), Some(p));
        }
        assert_eq!(Preset::from_name("STANDARD"), Some(Preset::Standard));
        assert_eq!(Preset::from_name("huge"), None);
    }

    fn contains(hay: &[u8], needle: &[u8]) -> bool {
        hay.windows(needle.len()).any(|w| w == needle)
    }
}
