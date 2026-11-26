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
    /// The progress callback asked to stop.
    Cancelled,
}

impl std::fmt::Display for PipelineError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PipelineError::Decode(e) => write!(f, "cannot read photo: {e}"),
            PipelineError::Encode(e) => write!(f, "cannot write JPEG: {e}"),
            PipelineError::Cancelled => f.write_str("cancelled"),
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

/// Progress reports go to a callback that returns `false` to stop the work.
/// Fractions only rise; 1.0 is never reported (the caller knows when it is done).
pub type Progress<'a> = &'a mut dyn FnMut(f32) -> bool;

/// Most encodes the size search can need: the two end points plus the bisection.
pub const MAX_ENCODES: u32 = 2 + SEARCH_STEPS;

pub fn process(input: &[u8], preset: Preset) -> Result<Processed, PipelineError> {
    process_with_progress(input, preset, &mut |_| true)
}

pub fn process_with_progress(
    input: &[u8],
    preset: Preset,
    progress: Progress,
) -> Result<Processed, PipelineError> {
    let (image, decode_info, orientation) = decode::decode_unoriented(input)?;
    finish(image, decode_info, orientation, preset, progress)
}

/// Same pipeline for pixels another decoder produced (Android decodes AVIF and HEIC).
/// `rgba` must already be upright sRGB, 4 bytes per pixel, not premultiplied.
pub fn process_rgba_with_progress(
    rgba: &[u8],
    width: u32,
    height: u32,
    preset: Preset,
    progress: Progress,
) -> Result<Processed, PipelineError> {
    let image = decode::from_rgba(rgba, width, height)?;
    let info = DecodeInfo {
        format: "pixels",
        exif_orientation: 1,
        icc_converted: false,
    };
    finish(
        image,
        info,
        image::metadata::Orientation::NoTransforms,
        preset,
        progress,
    )
}

/// Resize first, then rotate: same picture, far less memory for big photos.
fn finish(
    image: decode::RgbImage,
    decode_info: DecodeInfo,
    orientation: image::metadata::Orientation,
    preset: Preset,
    progress: Progress,
) -> Result<Processed, PipelineError> {
    // Rough shares of the time on a phone: decode 40 %, resize 15 %, encodes the rest.
    if !progress(0.4) {
        return Err(PipelineError::Cancelled);
    }
    let spec = preset.spec();
    let small = resize::downscale(&image, spec.target_pixels);
    drop(image);
    let small = decode::orient(small, orientation);
    if !progress(0.55) {
        return Err(PipelineError::Cancelled);
    }
    let mut on_encode = |done: u32| progress(0.55 + 0.45 * done as f32 / (MAX_ENCODES + 1) as f32);
    let (jpeg, distance, encodes) = size_search(&small, &spec, &mut on_encode)?;
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
    on_encode: &mut dyn FnMut(u32) -> bool,
) -> Result<(Vec<u8>, f32, u32), PipelineError> {
    let mut count = 0;
    let mut encode_at = |distance: f32| -> Result<Vec<u8>, PipelineError> {
        let options = EncodeOptions {
            distance,
            ..EncodeOptions::default()
        };
        let jpeg = encode::encode_rgb(&img.pixels, img.width, img.height, &options)?;
        count += 1;
        if !on_encode(count) {
            return Err(PipelineError::Cancelled);
        }
        Ok(jpeg)
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
        let (jpeg, distance, encodes) = size_search(&img, &spec, &mut |_| true).unwrap();
        assert!(jpeg.len() <= spec.max_bytes);
        assert!(distance > spec.min_distance && distance < spec.max_distance);
        assert_eq!(encodes, 2 + SEARCH_STEPS);
    }

    #[test]
    fn progress_rises_and_cancel_stops() {
        let input = testimg::jpeg_with_segments(640, 480, None, None);
        let mut seen = Vec::new();
        let out = process_with_progress(&input, Preset::Small, &mut |f| {
            seen.push(f);
            true
        })
        .unwrap();
        assert!(seen.windows(2).all(|w| w[0] < w[1]), "{seen:?}");
        assert!(seen.iter().all(|f| *f > 0.0 && *f < 1.0), "{seen:?}");
        assert_eq!(seen.len() as u32, 2 + out.encodes);

        let mut calls = 0;
        let stopped = process_with_progress(&input, Preset::Small, &mut |_| {
            calls += 1;
            calls < 2
        });
        assert_eq!(stopped, Err(PipelineError::Cancelled));
        assert_eq!(calls, 2);
    }

    #[test]
    fn pixels_from_another_decoder_match_the_file_path() {
        let png = testimg::png_fading(300, 200);
        let (image, _) = decode::decode(&png).unwrap();
        let rgba = image::load_from_memory(&png).unwrap().into_rgba8();
        let from_file = process(&png, Preset::Standard).unwrap();
        let from_pixels =
            process_rgba_with_progress(rgba.as_raw(), 300, 200, Preset::Standard, &mut |_| true)
                .unwrap();
        assert_eq!(from_file.jpeg, from_pixels.jpeg);
        assert_eq!(
            (image.width, image.height),
            (from_pixels.width, from_pixels.height)
        );
        assert!(process_rgba_with_progress(&[0; 7], 2, 2, Preset::Small, &mut |_| true).is_err());
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
