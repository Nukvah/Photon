//! Host benchmark for the jpegli spike: `cargo run --release --example bench`.
use photon_core::{benchmark_encode, encode};

fn main() {
    // Optional: write one Standard-size sample to the given path for visual checks.
    if let Some(path) = std::env::args().nth(1) {
        let rgb = encode::synthetic_image(1224, 816);
        let jpeg = encode::encode_rgb(&rgb, 1224, 816, &encode::EncodeOptions::default()).unwrap();
        std::fs::write(&path, jpeg).unwrap();
        println!("wrote {path}");
    }
    // Preset-sized images (3:2): Small 0.5 MP, Standard 1 MP, Sharp 2 MP.
    let sizes = [
        (866, 577, "Small"),
        (1224, 816, "Standard"),
        (1732, 1155, "Sharp"),
    ];
    println!(
        "{:<9} {:>11} {:>8} {:>9} {:>9}  fingerprint",
        "preset", "size", "distance", "KB", "ms"
    );
    for (w, h, name) in sizes {
        for distance in [1.0_f32, 1.5, 2.0] {
            let b = benchmark_encode(w, h, distance, 5).expect("encode");
            println!(
                "{name:<9} {:>11} {distance:>8.1} {:>9.1} {:>9.1}  {}",
                format!("{w}x{h}"),
                b.bytes as f64 / 1024.0,
                b.encode_ms,
                b.fingerprint
            );
        }
    }
}
