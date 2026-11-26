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
| `core/` | Rust core library `photon-core` |
| `core/jpegli-sys/` | Builds Google's jpegli (git submodule) and a small C shim over it |
| `core/uniffi.toml` | Kotlin package for the generated bindings |
| `core/deny.toml` | License policy: CI fails on GPL or unknown licenses |
| `scripts/build-rust.sh` | Builds the core for each Android ABI and generates bindings |
| `scripts/fetch-jpegli.sh` | Fetches the jpegli submodule and the parts of it the build needs |
| `scripts/check-16kb.sh` | Verifies native libraries in an APK support 16 KB pages |

## Build

Needs: JDK 17, Android SDK with NDK `28.2.13676358`, Rust (stable), `cargo-ndk`, CMake and a C++ compiler.

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
cargo test --release
cargo run --release --example bench   # jpegli size/speed on a synthetic image
```

## 16 KB pages

Google Play requires native libraries to support 16 KB memory pages. The Rust libraries are
linked with `-z max-page-size=16384` (`core/.cargo/config.toml`), NDK r28 does the same for
C/C++, and CI checks the final APK with `scripts/check-16kb.sh`.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs on every push:

- Rust: format, clippy, tests
- License check with cargo-deny
- Android: builds the debug APK for arm64-v8a and x86_64, runs unit tests, checks 16 KB
  alignment, and uploads the APK as a build artifact you can install on a phone.

## Docs

- Development plan, tech spec and UI mockups are kept in the project's Claude docs.
