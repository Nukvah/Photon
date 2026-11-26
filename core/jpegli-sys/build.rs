//! Builds jpegli (git submodule at `jpegli/`) with CMake and the C shim with `cc`,
//! for the host (tests, CLI) and for Android (via cargo-ndk).

use std::env;
use std::path::{Path, PathBuf};

fn main() {
    let manifest = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let src = manifest.join("jpegli");
    let target = env::var("TARGET").unwrap();
    let android = target.contains("android");

    for needed in [
        "CMakeLists.txt",
        "third_party/highway/CMakeLists.txt",
        "third_party/libjpeg-turbo/jpeglib.h",
    ] {
        if !src.join(needed).exists() {
            panic!(
                "jpegli sources missing ({needed}). Run scripts/fetch-jpegli.sh from the repo root."
            );
        }
    }

    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=shim");
    println!("cargo:rerun-if-env-changed=ANDROID_NDK_HOME");

    // --- jpegli + highway via CMake -------------------------------------------------
    let mut cfg = cmake::Config::new(&src);
    cfg.profile("Release")
        .define("BUILD_TESTING", "OFF")
        .define("BUILD_SHARED_LIBS", "OFF")
        .define("JPEGLI_ENABLE_TOOLS", "OFF")
        .define("JPEGLI_ENABLE_DOXYGEN", "OFF")
        .define("JPEGLI_ENABLE_MANPAGES", "OFF")
        .define("JPEGLI_ENABLE_BENCHMARK", "OFF")
        .define("JPEGLI_ENABLE_JNI", "OFF")
        .define("JPEGLI_ENABLE_SJPEG", "OFF")
        .define("JPEGLI_ENABLE_OPENEXR", "OFF")
        .define("JPEGLI_ENABLE_SKCMS", "ON")
        .define("JPEGLI_ENABLE_FUZZERS", "OFF")
        .define("JPEGLI_ENABLE_TCMALLOC", "OFF")
        // libjpeg 8 ABI (JPEG_LIB_VERSION 80) for the bundled jpeglib.h.
        .define("JPEGLI_LIBJPEG_LIBRARY_SOVERSION", "8")
        .define("JPEGLI_LIBJPEG_LIBRARY_VERSION", "8.2.2")
        .build_target("jpegli-static");

    if android {
        let ndk = ndk_dir();
        cfg.define(
            "CMAKE_TOOLCHAIN_FILE",
            ndk.join("build/cmake/android.toolchain.cmake"),
        )
        .define("ANDROID_ABI", android_abi(&target))
        .define("ANDROID_PLATFORM", "android-26")
        .define("ANDROID_STL", "c++_static");
    }

    let out = cfg.build();
    let build = out.join("build");
    println!(
        "cargo:rustc-link-search=native={}",
        build.join("lib").display()
    );
    println!(
        "cargo:rustc-link-search=native={}",
        build.join("third_party/highway").display()
    );

    // --- shim ----------------------------------------------------------------------
    let mut shim = cc::Build::new();
    shim.cpp(true)
        .std("c++17")
        .file("shim/photon_jpegli.cc")
        .include(&src)
        .include(build.join("lib/include"))
        .include(build.join("lib/include/jpegli"))
        .include("shim")
        .cpp_link_stdlib(None) // linked explicitly below, in the right order
        .warnings(false);
    shim.compile("photon_jpegli_shim");

    // Order matters for static linking: shim -> jpegli -> highway -> C++ runtime.
    println!("cargo:rustc-link-lib=static=jpegli-static");
    println!("cargo:rustc-link-lib=static=hwy");
    if android {
        println!("cargo:rustc-link-lib=c++_static");
        println!("cargo:rustc-link-lib=c++abi");
    } else if target.contains("apple") {
        println!("cargo:rustc-link-lib=c++");
    } else {
        println!("cargo:rustc-link-lib=stdc++");
    }
}

fn ndk_dir() -> PathBuf {
    for var in ["ANDROID_NDK_HOME", "ANDROID_NDK_ROOT", "ANDROID_NDK"] {
        if let Ok(path) = env::var(var) {
            if Path::new(&path).exists() {
                return PathBuf::from(path);
            }
        }
    }
    panic!("Android build needs ANDROID_NDK_HOME pointing at the NDK");
}

fn android_abi(target: &str) -> &'static str {
    match target.split('-').next().unwrap() {
        "aarch64" => "arm64-v8a",
        "x86_64" => "x86_64",
        "armv7" => "armeabi-v7a",
        "i686" => "x86",
        other => panic!("unsupported Android target arch {other}"),
    }
}
