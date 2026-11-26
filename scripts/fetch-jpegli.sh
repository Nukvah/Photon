#!/usr/bin/env bash
# Fetches the jpegli submodule and only the nested submodules the build needs
# (highway, libjpeg-turbo headers, skcms), not test data or unused codecs.
set -euo pipefail
cd "$(dirname "$0")/.."
git submodule update --init core/jpegli-sys/jpegli
git -C core/jpegli-sys/jpegli submodule update --init --depth 1 \
  third_party/highway third_party/libjpeg-turbo third_party/skcms
