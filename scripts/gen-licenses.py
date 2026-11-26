#!/usr/bin/env python3
"""Writes the open source notices the app shows under Settings > Open source licenses.

Lists every library that ends up in the app: the Rust crates linked into the core
(for both Android ABIs), the C/C++ code built with jpegli, the Kotlin and AndroidX
libraries, and the bundled font. Each entry points to a text file with its licence,
copied from the library's own files where it ships them.

Usage: gen-licenses.py <assets dir>. Writes <assets dir>/licenses/index.tsv and one .txt per
distinct licence text. Gradle runs it before every build, so the notices are never committed
and always match Cargo.lock and the Gradle versions.
"""

import hashlib
import json
import os
import re
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CORE = ROOT / "core"
JPEGLI = CORE / "jpegli-sys" / "jpegli"
TEXTS = ROOT / "third_party" / "licenses"
TARGETS = ["aarch64-linux-android", "x86_64-linux-android"]
LICENSE_FILE = re.compile(r"(?i)^(licen[cs]e|copying|copyright|notice|unlicense)([-_.].*)?$")
OUR_CRATES = {"photon-core", "photon-jpegli-sys"}


def run(*args: str) -> str:
    return subprocess.run(args, cwd=CORE, check=True, capture_output=True, text=True).stdout


def linked_crates() -> set[tuple[str, str]]:
    """Crates compiled into the app's library: normal dependencies, no build-only or proc-macro crates."""
    found = set()
    for target in TARGETS:
        tree = run("cargo", "tree", "-e", "normal,no-proc-macro", "--target", target,
                   "-p", "photon-core", "--prefix", "none", "-f", "{p}")
        for line in tree.splitlines():
            name, version = line.split()[:2]
            if name not in OUR_CRATES:
                found.add((name, version.lstrip("v")))
    return found


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace").strip() + "\n"


def crate_texts(directory: Path, spdx: str) -> str:
    files = sorted(f for f in os.listdir(directory) if LICENSE_FILE.match(f) and (directory / f).is_file())
    if files:
        return "\n\n".join(f"--- {f} ---\n\n{read(directory / f)}" for f in files)
    # A few crates ship no licence file; fall back to the licence's standard text.
    for name in ["MPL-2.0", "Apache-2.0"]:
        if name in spdx:
            return read(TEXTS / f"{name}.txt")
    sys.exit(f"no licence text for crate in {directory} ({spdx})")


def gradle_versions() -> dict[str, str]:
    with open(ROOT / "gradle" / "libs.versions.toml", "rb") as f:
        return tomllib.load(f)["versions"]


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("usage: gen-licenses.py <assets dir>")
    out = Path(sys.argv[1]) / "licenses"
    metadata = json.loads(run("cargo", "metadata", "--format-version", "1", "--locked"))
    packages = {(p["name"], p["version"]): p for p in metadata["packages"]}

    entries = []  # (name, version, licence, text)
    for name, version in sorted(linked_crates()):
        p = packages[(name, version)]
        spdx = (p.get("license") or "").replace("/", " OR ")
        entries.append((name, version, spdx, crate_texts(Path(p["manifest_path"]).parent, spdx)))

    rust_mit = read(TEXTS / "Rust-LICENSE-MIT.txt")
    apache = read(TEXTS / "Apache-2.0.txt")
    llvm = apache + "\n" + read(TEXTS / "LLVM-exception.txt")
    entries += [
        ("Photon", "", "GPL-3.0-or-later", read(ROOT / "LICENSE")),
        ("Rust standard library", "", "MIT OR Apache-2.0", rust_mit),
        ("jpegli", "", "BSD-3-Clause", read(JPEGLI / "LICENSE")),
        ("Highway", "", "Apache-2.0 OR BSD-3-Clause", read(JPEGLI / "third_party" / "highway" / "LICENSE")),
        ("skcms", "", "BSD-3-Clause", read(JPEGLI / "third_party" / "skcms" / "LICENSE")),
        ("libjpeg-turbo (headers)", "", "IJG AND BSD-3-Clause AND Zlib",
         read(JPEGLI / "third_party" / "libjpeg-turbo" / "LICENSE.md")),
        ("LLVM libc++ (Android NDK)", "", "Apache-2.0 WITH LLVM-exception", llvm),
    ]

    v = gradle_versions()
    entries += [
        ("Kotlin standard library", v["kotlin"], "Apache-2.0", apache),
        ("kotlinx.coroutines", v["coroutines"], "Apache-2.0", apache),
        ("AndroidX Activity", v["activityCompose"], "Apache-2.0", apache),
        ("AndroidX Core", v["coreKtx"], "Apache-2.0", apache),
        ("AndroidX Lifecycle", v["lifecycle"], "Apache-2.0", apache),
        ("Jetpack Compose (BOM)", v["composeBom"], "Apache-2.0", apache),
        ("JNA", v["jna"], "Apache-2.0", apache),
        ("Space Grotesk", "", "OFL-1.1", read(ROOT / "third_party" / "fonts" / "SpaceGrotesk-OFL.txt")),
    ]

    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.txt"):
        old.unlink()
    rows = []
    for name, version, spdx, text in sorted(entries, key=lambda e: e[0].lower()):
        file = hashlib.sha256(text.encode()).hexdigest()[:12] + ".txt"
        (out / file).write_text(text, encoding="utf-8")
        rows.append("\t".join([name, version, spdx, file]))
    (out / "index.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"{len(rows)} libraries, {len(list(out.glob('*.txt')))} licence texts")


if __name__ == "__main__":
    main()
