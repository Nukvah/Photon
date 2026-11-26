//! Test inputs built in code, so the repository needs no binary fixtures.
//! Public only for the integration tests and the CLI; not part of the app API.

use crate::encode;

/// A solid-colour RGBA PNG.
pub fn png_rgba(width: u32, height: u32, px: [u8; 4]) -> Vec<u8> {
    let img = image::RgbaImage::from_pixel(width, height, image::Rgba(px));
    let mut out = Vec::new();
    img.write_to(&mut std::io::Cursor::new(&mut out), image::ImageFormat::Png)
        .unwrap();
    out
}

/// A synthetic JPEG with an optional EXIF orientation and an optional ICC profile,
/// laid out like a phone camera file (APP1 Exif and APP2 ICC right after SOI).
pub fn jpeg_with_segments(
    width: u32,
    height: u32,
    orientation: Option<u16>,
    icc: Option<&[u8]>,
) -> Vec<u8> {
    let rgb = encode::synthetic_image(width, height);
    let jpeg = encode::encode_rgb(&rgb, width, height, &encode::EncodeOptions::default()).unwrap();
    let mut segments = Vec::new();
    if let Some(o) = orientation {
        let mut exif = b"Exif\0\0II\x2a\0\x08\0\0\0\x01\0\x12\x01\x03\0\x01\0\0\0".to_vec();
        exif.extend_from_slice(&o.to_le_bytes());
        exif.extend_from_slice(&[0, 0, 0, 0, 0, 0]);
        push_segment(&mut segments, 0xE1, &exif);
    }
    if let Some(profile) = icc {
        let mut app2 = b"ICC_PROFILE\0\x01\x01".to_vec();
        app2.extend_from_slice(profile);
        push_segment(&mut segments, 0xE2, &app2);
    }
    let mut out = jpeg[..2].to_vec();
    out.extend_from_slice(&segments);
    out.extend_from_slice(&jpeg[2..]);
    out
}

fn push_segment(out: &mut Vec<u8>, marker: u8, payload: &[u8]) {
    let len = u16::try_from(payload.len() + 2).expect("segment too large");
    out.extend_from_slice(&[0xFF, marker]);
    out.extend_from_slice(&len.to_be_bytes());
    out.extend_from_slice(payload);
}

/// A synthetic RGB image as lossless WebP.
pub fn webp(width: u32, height: u32) -> Vec<u8> {
    let rgb = encode::synthetic_image(width, height);
    let img = image::RgbImage::from_raw(width, height, rgb).unwrap();
    let mut out = Vec::new();
    img.write_to(
        &mut std::io::Cursor::new(&mut out),
        image::ImageFormat::WebP,
    )
    .unwrap();
    out
}

/// A synthetic PNG whose alpha fades from opaque (left) to clear (right).
pub fn png_fading(width: u32, height: u32) -> Vec<u8> {
    let rgb = encode::synthetic_image(width, height);
    let img = image::RgbaImage::from_fn(width, height, |x, y| {
        let i = ((y * width + x) * 3) as usize;
        let a = 255 - (x * 255 / width.max(1)) as u8;
        image::Rgba([rgb[i], rgb[i + 1], rgb[i + 2], a])
    });
    let mut out = Vec::new();
    img.write_to(&mut std::io::Cursor::new(&mut out), image::ImageFormat::Png)
        .unwrap();
    out
}
