//! Decoding: any supported file (JPEG, PNG, WebP) to upright 8-bit sRGB pixels.
//!
//! Steps, always in this order so output is deterministic:
//! 1. Guess the format from the bytes (never from the file name).
//! 2. Decode to 8-bit RGB; transparent pixels are flattened onto white.
//! 3. Convert from the embedded ICC profile (e.g. Display P3) to sRGB.
//! 4. Bake in the EXIF orientation so the photo is upright without metadata.

use std::io::Cursor;

use image::{DynamicImage, ImageDecoder, ImageReader};
use moxcms::{ColorProfile, DataColorSpace, Layout, RenderingIntent, TransformOptions};

/// Largest input accepted, in pixels (about 200 MP); protects the phone's memory.
pub const MAX_INPUT_PIXELS: u64 = 200_000_000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DecodeError(pub String);

impl std::fmt::Display for DecodeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for DecodeError {}

impl From<image::ImageError> for DecodeError {
    fn from(e: image::ImageError) -> Self {
        DecodeError(e.to_string())
    }
}

/// Upright, 8-bit sRGB pixels, 3 bytes per pixel, rows top to bottom.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RgbImage {
    pub width: u32,
    pub height: u32,
    pub pixels: Vec<u8>,
}

/// What the decoder found besides the pixels; reported for the UI and tests.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DecodeInfo {
    pub format: &'static str,
    /// EXIF orientation 1 to 8; 1 means already upright.
    pub exif_orientation: u8,
    /// True when an embedded ICC profile was converted to sRGB.
    pub icc_converted: bool,
}

pub fn decode(bytes: &[u8]) -> Result<(RgbImage, DecodeInfo), DecodeError> {
    let reader = ImageReader::new(Cursor::new(bytes))
        .with_guessed_format()
        .map_err(|e| DecodeError(e.to_string()))?;
    let format = match reader.format() {
        Some(image::ImageFormat::Jpeg) => "jpeg",
        Some(image::ImageFormat::Png) => "png",
        Some(image::ImageFormat::WebP) => "webp",
        _ => return Err(DecodeError("unsupported image format".into())),
    };
    let mut decoder = reader.into_decoder()?;
    let (w, h) = decoder.dimensions();
    if w == 0 || h == 0 || u64::from(w) * u64::from(h) > MAX_INPUT_PIXELS {
        return Err(DecodeError(format!("image size {w}x{h} is not supported")));
    }
    // Broken metadata must never stop a photo from being processed.
    let orientation = decoder
        .orientation()
        .unwrap_or(image::metadata::Orientation::NoTransforms);
    let icc = decoder.icc_profile().ok().flatten();
    let image = DynamicImage::from_decoder(decoder)?;

    let mut rgb = flatten_to_rgb8(image);
    let icc_converted = match icc {
        Some(profile) => convert_to_srgb(&mut rgb, &profile),
        None => false,
    };
    let mut upright = DynamicImage::ImageRgb8(rgb);
    upright.apply_orientation(orientation);
    let upright = upright.into_rgb8();

    let info = DecodeInfo {
        format,
        exif_orientation: orientation_number(orientation),
        icc_converted,
    };
    let (width, height) = upright.dimensions();
    Ok((
        RgbImage {
            width,
            height,
            pixels: upright.into_raw(),
        },
        info,
    ))
}

/// Pixels from another decoder: RGBA (not premultiplied), flattened onto white like files.
pub fn from_rgba(rgba: &[u8], width: u32, height: u32) -> Result<RgbImage, DecodeError> {
    let pixels = u64::from(width) * u64::from(height);
    if width == 0 || height == 0 || pixels > MAX_INPUT_PIXELS {
        return Err(DecodeError(format!(
            "image size {width}x{height} is not supported"
        )));
    }
    if rgba.len() as u64 != pixels * 4 {
        return Err(DecodeError(format!(
            "expected {} bytes of RGBA for {width}x{height}, got {}",
            pixels * 4,
            rgba.len()
        )));
    }
    let buffer = image::RgbaImage::from_raw(width, height, rgba.to_vec())
        .ok_or_else(|| DecodeError("bad RGBA buffer".into()))?;
    let rgb = flatten_to_rgb8(DynamicImage::ImageRgba8(buffer));
    Ok(RgbImage {
        width,
        height,
        pixels: rgb.into_raw(),
    })
}

/// 8-bit RGB; alpha is composited onto white (email clients show white, not black).
fn flatten_to_rgb8(image: DynamicImage) -> image::RgbImage {
    if !image.color().has_alpha() {
        return image.into_rgb8();
    }
    let rgba = image.into_rgba8();
    let (w, h) = rgba.dimensions();
    let mut out = image::RgbImage::new(w, h);
    for (dst, src) in out.pixels_mut().zip(rgba.pixels()) {
        let a = u32::from(src[3]);
        for c in 0..3 {
            // Integer blend with rounding: (c*a + 255*(255-a)) / 255.
            let v = u32::from(src[c]) * a + 255 * (255 - a);
            dst[c] = ((v + 127) / 255) as u8;
        }
    }
    out
}

/// Converts in place; returns false (pixels untouched) when the profile is not an
/// RGB profile we can read, so a broken profile never fails the photo.
fn convert_to_srgb(rgb: &mut image::RgbImage, icc: &[u8]) -> bool {
    let Ok(src) = ColorProfile::new_from_slice(icc) else {
        return false;
    };
    if src.color_space != DataColorSpace::Rgb {
        return false;
    }
    let dst = ColorProfile::new_srgb();
    let options = TransformOptions {
        rendering_intent: RenderingIntent::Perceptual,
        // Integer maths: same result on every phone of one CPU type.
        prefer_fixed_point: true,
        ..TransformOptions::default()
    };
    let Ok(transform) = src.create_transform_8bit(Layout::Rgb, &dst, Layout::Rgb, options) else {
        return false;
    };
    let mut out = vec![0u8; rgb.as_raw().len()];
    if transform.transform(rgb.as_raw(), &mut out).is_err() {
        return false;
    }
    rgb.copy_from_slice(&out);
    true
}

fn orientation_number(o: image::metadata::Orientation) -> u8 {
    use image::metadata::Orientation::*;
    match o {
        NoTransforms => 1,
        FlipHorizontal => 2,
        Rotate180 => 3,
        FlipVertical => 4,
        Rotate90FlipH => 5,
        Rotate90 => 6,
        Rotate270FlipH => 7,
        Rotate270 => 8,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::testimg;

    #[test]
    fn decodes_png_and_flattens_alpha_on_white() {
        let png = testimg::png_rgba(4, 2, [0, 0, 0, 0]);
        let (img, info) = decode(&png).unwrap();
        assert_eq!(info.format, "png");
        assert_eq!((img.width, img.height), (4, 2));
        assert!(img.pixels.iter().all(|&v| v == 255));
    }

    #[test]
    fn applies_exif_rotation() {
        // 40x20 landscape tagged "rotate 90" must come out 20x40 portrait.
        let jpeg = testimg::jpeg_with_segments(40, 20, Some(6), None);
        let (img, info) = decode(&jpeg).unwrap();
        assert_eq!(info.exif_orientation, 6);
        assert_eq!((img.width, img.height), (20, 40));
    }

    #[test]
    fn converts_display_p3_to_srgb() {
        let p3 = ColorProfile::new_display_p3().encode().unwrap();
        let plain = testimg::jpeg_with_segments(32, 32, None, None);
        let tagged = testimg::jpeg_with_segments(32, 32, None, Some(&p3));
        let (a, ia) = decode(&plain).unwrap();
        let (b, ib) = decode(&tagged).unwrap();
        assert!(!ia.icc_converted);
        assert!(ib.icc_converted);
        // P3 colours are more saturated than the same numbers in sRGB, so pixels change.
        assert_ne!(a.pixels, b.pixels);
    }

    #[test]
    fn ignores_broken_icc_profile() {
        let jpeg = testimg::jpeg_with_segments(16, 16, None, Some(b"not a profile"));
        let (_, info) = decode(&jpeg).unwrap();
        assert!(!info.icc_converted);
    }

    #[test]
    fn rejects_garbage() {
        assert!(decode(b"hello").is_err());
    }
}
