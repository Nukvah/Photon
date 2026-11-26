//! Lanczos3 downscaling to a megapixel target. Never upscales.

use fast_image_resize::images::Image;
use fast_image_resize::{CpuExtensions, FilterType, PixelType, ResizeAlg, ResizeOptions, Resizer};

use crate::decode::RgbImage;

/// Output size for a megapixel target, keeping the aspect ratio.
/// Returns the input size when the photo is already small enough.
pub fn target_size(width: u32, height: u32, target_pixels: u64) -> (u32, u32) {
    let pixels = u64::from(width) * u64::from(height);
    if pixels <= target_pixels {
        return (width, height);
    }
    let scale = (target_pixels as f64 / pixels as f64).sqrt();
    let w = ((f64::from(width) * scale).round() as u32).clamp(1, width);
    let h = ((f64::from(height) * scale).round() as u32).clamp(1, height);
    (w, h)
}

pub fn downscale(src: &RgbImage, target_pixels: u64) -> RgbImage {
    let (w, h) = target_size(src.width, src.height, target_pixels);
    if (w, h) == (src.width, src.height) {
        return src.clone();
    }
    let src_img = Image::from_vec_u8(src.width, src.height, src.pixels.clone(), PixelType::U8x3)
        .expect("buffer matches size");
    let mut dst = Image::new(w, h, PixelType::U8x3);
    let mut resizer = Resizer::new();
    // Pin one SIMD path per CPU type, so every phone of that type gets the same bytes
    // (the default picks the fastest path the CPU has, which can differ between x86 CPUs).
    // SAFETY: the chosen extension is part of the target's baseline instruction set.
    unsafe { resizer.set_cpu_extensions(baseline_cpu_extensions()) };
    let options = ResizeOptions::new().resize_alg(ResizeAlg::Convolution(FilterType::Lanczos3));
    resizer
        .resize(&src_img, &mut dst, &options)
        .expect("same pixel type");
    RgbImage {
        width: w,
        height: h,
        pixels: dst.into_vec(),
    }
}

fn baseline_cpu_extensions() -> CpuExtensions {
    #[cfg(target_arch = "aarch64")]
    {
        CpuExtensions::Neon
    }
    #[cfg(not(target_arch = "aarch64"))]
    {
        CpuExtensions::None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keeps_aspect_ratio_and_pixel_budget() {
        // 12 MP 4:3 phone photo to 1 MP.
        let (w, h) = target_size(4000, 3000, 1_000_000);
        assert_eq!((w, h), (1155, 866));
        assert!(u64::from(w) * u64::from(h) <= 1_001_000);
    }

    #[test]
    fn never_upscales() {
        assert_eq!(target_size(800, 600, 1_000_000), (800, 600));
    }

    #[test]
    fn downscale_is_deterministic() {
        let src = RgbImage {
            width: 300,
            height: 200,
            pixels: crate::encode::synthetic_image(300, 200),
        };
        let a = downscale(&src, 15_000);
        let b = downscale(&src, 15_000);
        assert_eq!((a.width, a.height), (150, 100));
        assert_eq!(a.pixels.len(), 150 * 100 * 3);
        assert_eq!(a, b);
    }
}
