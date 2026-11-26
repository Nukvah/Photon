#!/usr/bin/env bash
# Fails if any native library in the given APK is not aligned for 16 KB memory pages.
# Usage: scripts/check-16kb.sh path/to/app.apk
set -euo pipefail

APK=$1
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
unzip -q "$APK" 'lib/*' -d "$WORK"

READELF=${READELF:-$(command -v llvm-readelf || command -v readelf)}
status=0
while IFS= read -r so; do
  # Every LOAD segment's alignment must be at least 2**14 (16 KB).
  bad=""
  for align in $("$READELF" -lW "$so" | awk '$1=="LOAD" { print $NF }'); do
    (( align < 16384 )) && bad+="$align "
  done
  if [[ -n "$bad" ]]; then
    echo "NOT 16 KB aligned: ${so#"$WORK"/} (align $bad)"
    status=1
  else
    echo "16 KB aligned: ${so#"$WORK"/}"
  fi
done < <(find "$WORK/lib" -name '*.so')
exit $status
