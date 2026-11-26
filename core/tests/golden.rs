//! Golden output tests: fixed inputs must give byte-identical JPEGs on every run.
//!
//! Expected SHA-256s live in `tests/golden/<cpu>.tsv`. A CPU type without a file only
//! checks run-to-run determinism. After an intended output change, regenerate with
//! `PHOTON_BLESS=1 cargo test --release --test golden` and commit the file.

use photon_core::pipeline::{process, Preset};
use photon_core::testimg;
use sha2::{Digest, Sha256};

fn cases() -> Vec<(&'static str, Vec<u8>, Preset)> {
    let p3 = moxcms::ColorProfile::new_display_p3().encode().unwrap();
    // A 12 MP portrait phone photo: stored sideways with EXIF 6 and a Display P3 profile.
    let phone = testimg::jpeg_with_segments(4032, 3024, Some(6), Some(&p3));
    let png = testimg::png_fading(1600, 1200);
    let webp = testimg::webp(2000, 1500);
    vec![
        ("phone-p3-rot90.small", phone.clone(), Preset::Small),
        ("phone-p3-rot90.standard", phone.clone(), Preset::Standard),
        ("phone-p3-rot90.sharp", phone, Preset::Sharp),
        ("png-alpha.standard", png, Preset::Standard),
        ("webp.standard", webp, Preset::Standard),
    ]
}

fn sha256(bytes: &[u8]) -> String {
    Sha256::digest(bytes)
        .iter()
        .map(|b| format!("{b:02x}"))
        .collect()
}

#[test]
fn golden_outputs() {
    let mut actual = String::from("case\twidth\theight\tbytes\tdistance\tsha256\n");
    for (name, input, preset) in cases() {
        let a = process(&input, preset).unwrap();
        let b = process(&input, preset).unwrap();
        assert_eq!(a.jpeg, b.jpeg, "{name}: two runs differ");
        actual.push_str(&format!(
            "{name}\t{}\t{}\t{}\t{}\t{}\n",
            a.width,
            a.height,
            a.jpeg.len(),
            a.distance,
            sha256(&a.jpeg)
        ));
    }

    let path = format!(
        "{}/tests/golden/{}.tsv",
        env!("CARGO_MANIFEST_DIR"),
        std::env::consts::ARCH
    );
    if std::env::var_os("PHOTON_BLESS").is_some() {
        std::fs::write(&path, &actual).unwrap();
        return;
    }
    match std::fs::read_to_string(&path) {
        Ok(expected) => assert_eq!(
            actual, expected,
            "output changed; if intended, re-bless (see top of tests/golden.rs)"
        ),
        Err(_) => eprintln!("no {path}; checked determinism only:\n{actual}"),
    }
}
