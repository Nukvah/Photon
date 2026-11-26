# Photon

Android app that takes photos from the share sheet, turns them into small, deterministic,
email-safe JPEGs, and hands them straight back to share.

- Kotlin + Jetpack Compose for the app (`app/`)
- Rust for every image step, with the jpegli encoder (`core/`, crate `photon-core`)
- uniffi generates the Kotlin bindings; the app calls Rust through JNA

## Layout

| Path | What |
| --- | --- |
| `app/` | Android app (min API 26, target API 36) |
| `app/.../ui/` | Compose screens and `PhotonViewModel`; pure Kotlin, tested on the JVM |
| `app/.../pipeline/` | `RustPipeline` (the real one) and `FakePipeline` (tests and screenshots) |
| `app/.../system/` | Android glue: reading shared photos, AVIF/HEIC decoding, thumbnails, FileProvider sharing, saved state |
| `core/` | Rust core library `photon-core` |
| `core/src/` | Pipeline: `decode` → `resize` → `encode` (jpegli), tied together in `pipeline` |
| `core/cli/` | `photon-cli`, a desktop tool that runs the pipeline over a folder |
| `core/tests/golden/` | Expected output hashes per CPU type |
| `core/jpegli-sys/` | Builds Google's jpegli (git submodule) and a small C shim over it |
| `core/uniffi.toml` | Kotlin package for the generated bindings |
| `core/deny.toml` | License policy: CI fails on licenses that are not GPLv3-compatible, or unknown |
| `scripts/build-rust.sh` | Builds the core for each Android ABI and generates bindings |
| `scripts/fetch-jpegli.sh` | Fetches the jpegli submodule and the parts of it the build needs |
| `scripts/check-16kb.sh` | Verifies native libraries in an APK support 16 KB pages |
| `scripts/check-permissions.sh` | Fails if an APK asks for any permission (see `PRIVACY.md`) |
| `scripts/gen-licenses.py` | Collects the licence texts shown under Settings > Open source licenses |
| `third_party/licenses/` | Standard licence texts for libraries that ship none |
| `store/` | Icon and artwork sources (`icon.py`, `orb.py`), Play Store icon and feature graphic (`render.js`, from `icon.svg`), listing text and Play Console answers (`listing.md`) |
| `PRIVACY.md` | Privacy policy, linked from Settings and the Play listing |

## Build

Needs: JDK 17, Android SDK with NDK `28.2.13676358`, Rust (stable), `cargo-ndk`, CMake, a C++ compiler
and Python 3.11+ (for the licence notices).

```sh
scripts/fetch-jpegli.sh        # once after cloning
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
./gradlew assembleDebug        # builds Rust first, then the app
```

Gradle runs `scripts/build-rust.sh` before every build. To build only one ABI while
developing, set `PHOTON_ABIS=arm64-v8a`.

Rust only:

```sh
cd core
cargo test --workspace --release
cargo run --release --example bench   # jpegli size/speed on a synthetic image
cargo run --release -p photon-cli -- --preset standard ~/Pictures/in /tmp/out
```

`photon-cli` writes one JPEG per photo plus `manifest.tsv` with sizes and SHA-256 hashes.

### Pipeline and determinism

Every photo goes through the same fixed steps: decode (JPEG, PNG, WebP), flatten
transparency onto white, convert the ICC profile to sRGB, bake in the EXIF rotation,
Lanczos3 downscale to the preset's pixel budget, then jpegli at distance 1.0. Only if
the file is over the preset's size cap does a fixed 5-step search lower quality.
Metadata is never copied.

Same input and preset give the same bytes on every device of one CPU type. To keep
that true, every SIMD path is chosen at compile time, not by probing the CPU: highway
is built with `HWY_COMPILE_ONLY_STATIC`, the resizer is pinned to NEON (arm64) or plain
code (x86_64), and moxcms is built with only its NEON paths. If an output change is
intended, re-bless the golden hashes with
`PHOTON_BLESS=1 cargo test --release --test golden`.

## 16 KB pages

Google Play requires native libraries to support 16 KB memory pages. The Rust libraries are
linked with `-z max-page-size=16384` (`core/.cargo/config.toml`), NDK r28 does the same for
C/C++, and CI checks the final APK with `scripts/check-16kb.sh`.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs on every push:

- Rust: format, clippy, tests
- License check with cargo-deny (every dependency must be GPLv3-compatible)
- Android: builds the debug APK for arm64-v8a and x86_64, runs unit tests, checks 16 KB
  alignment and permissions, and uploads the APK as a build artifact you can install on a phone.
  The screenshot tests (including the Play Store set in `app/src/test/screenshots/store/`) are uploaded too.

## Release

`.github/workflows/release.yml` builds the signed App Bundle for Google Play. It runs when a
tag like `v1.0.0` is pushed, or by hand from the Actions tab. The version code is the
workflow's run number; the version name comes from the tag.

It signs with the upload key, from four repository secrets (Settings > Secrets and
variables > Actions):

| Secret | Value |
| --- | --- |
| `PHOTON_KEYSTORE_BASE64` | The upload keystore, base64 encoded (`base64 -w0 upload.jks`) |
| `PHOTON_KEYSTORE_PASSWORD` | Keystore password |
| `PHOTON_KEY_ALIAS` | Key alias |
| `PHOTON_KEY_PASSWORD` | Key password |

Create the keystore once, keep it out of the repository, and back it up:

```sh
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Google Play App Signing holds the real app signing key, so a lost upload key can be reset.
Without the secrets, `./gradlew bundleRelease` still builds an unsigned bundle locally.

Settings > Open source licenses lists every bundled library. Gradle generates the list on
each build (`scripts/gen-licenses.py`), from `Cargo.lock`, the jpegli sources and
`gradle/libs.versions.toml`, so a new dependency shows up without any manual step.

## Docs

- Development plan, tech spec and UI mockups are kept in the project's Claude docs.

## Support the project

Photon is free and has no ads. If it saves you time, you can buy me a coffee on
[Ko-fi](https://ko-fi.com/xzpy1). The app itself links only to this page (Settings > About Photon),
because Google Play does not allow apps to link to outside payment pages.

## License

Photon is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version. See [LICENSE](LICENSE).

The bundled libraries keep their own licenses, listed under Settings > Open source licenses.
