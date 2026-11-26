#!/usr/bin/env bash
# Builds photon-core for Android and generates its Kotlin bindings.
# Usage: scripts/build-rust.sh <jniLibs out dir> <kotlin out dir>
# Env: PHOTON_ABIS (default "arm64-v8a x86_64"), PHOTON_RUST_PROFILE (release|debug, default release).
set -euo pipefail

JNI_OUT=$1
KOTLIN_OUT=$2
ABIS=${PHOTON_ABIS:-"arm64-v8a x86_64"}
PROFILE=${PHOTON_RUST_PROFILE:-release}

cd "$(dirname "$0")/../core"

profile_flag=()
[[ "$PROFILE" == "release" ]] && profile_flag=(--release)

abi_flags=()
for abi in $ABIS; do abi_flags+=(-t "$abi"); done

rm -rf "$JNI_OUT" "$KOTLIN_OUT"
mkdir -p "$JNI_OUT" "$KOTLIN_OUT"

# 1. Native libraries for each Android ABI (minSdk 26).
cargo ndk "${abi_flags[@]}" --platform 26 -o "$JNI_OUT" build "${profile_flag[@]}"

# 2. Kotlin bindings, generated from a host build of the same crate.
cargo build --lib
cargo run --quiet --features cli --bin uniffi-bindgen -- \
  generate --library target/debug/libphoton_core.so \
  --language kotlin --no-format --out-dir "$KOTLIN_OUT"
